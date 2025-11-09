package ma.emsi.applicationweb.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.router.RoutingRule; // L'import correct
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gère l'interface avec l'API de Gemini pour le RAG avec Routage.
 */
@ApplicationScoped
public class LlmClient implements Serializable {
    private static final String API_KEY_ENV_VAR = "GEMINI";
    private static final String MODEL_NAME = "gemini-2.5-flash";

    private String systemRole;
    private final Assistant assistant;
    private final ChatMemory chatMemory;

    public LlmClient() {
        // 1. Configuration de base
        String apiKey = System.getenv(API_KEY_ENV_VAR);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("La clé API Gemini doit être définie dans la variable d'environnement GEMINI.");
        }

        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .build();

        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);


        // --- LOGIQUE RAG (2+ PDF) / ROUTAGE ---

        // 3. Charger et parser les documents PDF
        Path docsPath = Paths.get("src/main/resources/documents/");

        List<Document> documents;
        try (Stream<Path> stream = java.nio.file.Files.list(docsPath)) {
            documents = stream
                    .filter(path -> path.toString().endsWith(".pdf"))
                    .map(path -> FileSystemDocumentLoader.loadDocument(path, new ApacheTikaDocumentParser()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("ERREUR RAG: Impossible de charger les documents PDF. " + e.getMessage());
            documents = List.of();
        }

        if (documents.size() < 2) {
            System.err.println("AVERTISSEMENT: Seulement " + documents.size() + " document(s) PDF trouvé(s). Le TP exige au moins 2.");
        }

        // 4. Splitter, Embedder et Ingester les segments
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.splitAll(documents);

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (!segments.isEmpty()) {
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
        }

        // 5. Créer le ContentRetriever (Source RAG)
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        // 6. Configurer le Routage de Requête (Utilisation de RoutingRule)

        // Crée la règle de routage explicite
        RoutingRule ragRule = RoutingRule.builder()
                .contentRetriever(contentRetriever)
                .description("Répondre aux questions spécifiques basées sur les documents PDF chargés par l'utilisateur (lois, règles, documents internes).")
                .build();

        // Ajout de la règle au routeur
        QueryRouter queryRouter = LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .add(ragRule)
                .build();

        // 7. Créer l'Augmenteur de Recherche
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        // 8. Création de l'Assistant via AiServices
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(this.chatMemory)
                .retrievalAugmentor(retrievalAugmentor) // Active le RAG et le Routage
                .build();
    }

    /**
     * Définit le rôle système pour l'assistant (Rôle Système Dynamique).
     */
    public void setSystemRole(String newSystemRole) {
        if (newSystemRole != null && !newSystemRole.equals(this.systemRole)) {
            this.chatMemory.clear();
            this.systemRole = newSystemRole;
            this.chatMemory.add(SystemMessage.from(newSystemRole));
        }
    }

    /**
     * Envoie la question de l'utilisateur au LLM (RAG avec Routage).
     */
    public String envoyerQuestion(String question) {
        return this.assistant.chat(question);
    }
}