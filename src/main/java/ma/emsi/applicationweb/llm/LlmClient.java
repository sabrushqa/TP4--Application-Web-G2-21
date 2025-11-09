package ma.emsi.applicationweb.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
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
import dev.langchain4j.service.AiServices;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client pour le Large Language Model (LLM) utilisant LangChain4j,
 * intégrant le RAG, le Routage de Requête et la gestion du chat.
 */
public class LlmClient {

    private Assistant assistant;
    private ChatMemory memory;

    // --- Configuration des documents et des modèles ---
    private static final Path DOC_RAG = Paths.get("src/main/resources/support_rag.pdf");
    private static final Path DOC_AUTRE = Paths.get("src/main/resources/MobileAI-2.pdf");

    // Le Logger est utilisé pour voir les étapes de LangChain4j (Routage, LLM calls)
    private static final Logger LOGGER = Logger.getLogger(LlmClient.class.getName());

    public LlmClient(){
        // 1. Configuration du Logging pour LangChain4j
        configureLangChain4jLogging();

        String llmKey = System.getenv("GEMINI");
        if (llmKey == null) {
            LOGGER.severe("Erreur: La variable d'environnement 'GEMINI' n'est pas définie.");
            return;
        }

        // 2. Initialisation des modèles et du ChatModel avec Logging
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(llmKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.3)
                .logRequestsAndResponses(true) // Active le logging détaillé (Fonctionnalité 1)
                .build();

        // 3. Ingestion des documents et préparation des Retrievers
        EmbeddingStore<TextSegment> store1 = ingestDocument(DOC_RAG, embeddingModel);
        EmbeddingStore<TextSegment> store2 = ingestDocument(DOC_AUTRE, embeddingModel);

        ContentRetriever retriever1 = createContentRetriever(store1, embeddingModel);
        ContentRetriever retriever2 = createContentRetriever(store2, embeddingModel);

        // 4. Configuration du Routage (Fonctionnalité 2)
        Map<ContentRetriever, String> retrieverDescriptions = new HashMap<>();
        retrieverDescriptions.put(retriever1,
                "Documents techniques sur l'intelligence artificielle, le RAG, LangChain4j, et les LLM.");
        retrieverDescriptions.put(retriever2,
                "Documents sur le développement d'applications mobiles, Android, Kotlin et bases de données Room.");

        QueryRouter queryRouter = new LanguageModelQueryRouter(chatModel, retrieverDescriptions);

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        // 5. Construction de l'Assistant
        this.memory = MessageWindowChatMemory.withMaxMessages(10);
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .retrievalAugmentor(retrievalAugmentor)
                .build();
    }

    /**
     * Configure le logger LangChain4j pour voir le routage et les requêtes/réponses.
     */
    private void configureLangChain4jLogging() {
        Logger packageLogger = Logger.getLogger("dev.langchain4j");
        packageLogger.setLevel(Level.FINE);
        LOGGER.info("Logging de LangChain4j configuré sur le niveau FINE.");
    }

    /**
     * Ingère un document : charge, découpe, crée les embeddings et les stocke.
     */
    private EmbeddingStore<TextSegment> ingestDocument(
            Path documentPath,
            EmbeddingModel embeddingModel) {

        if (!documentPath.toFile().exists()) {
            LOGGER.warning("Le document n'existe pas : " + documentPath + ". Création d'un store vide.");
            return new InMemoryEmbeddingStore<>();
        }

        DocumentParser parser = new ApacheTikaDocumentParser();
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);

        LOGGER.info("Ingestion du document: " + documentPath.getFileName());
        Document document = FileSystemDocumentLoader.loadDocument(documentPath, parser);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);
        LOGGER.info(String.format("   - %d segments et embeddings créés.", segments.size()));

        return embeddingStore;
    }

    /**
     * Crée un ContentRetriever pour l'EmbeddingStore donné.
     */
    private ContentRetriever createContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.5)
                .build();
    }

    /**
     * Définit le rôle système (persona) de l'assistant et réinitialise la mémoire.
     * @param systemRole Le nouveau rôle système.
     */
    public void setSystemRole(String systemRole) {
        this.memory.clear();
        this.memory.add(SystemMessage.from(systemRole));
        LOGGER.info("Rôle système défini: " + systemRole.substring(0, Math.min(systemRole.length(), 50)) + "...");
    }

    /**
     * Pose une question à l'assistant RAG et obtient une réponse.
     * @param question La question de l'utilisateur.
     * @return La réponse générée par le LLM.
     */
    public String PoserQuestion(String question) {
        if (assistant == null) {
            return "Erreur de configuration: L'assistant n'a pas pu être initialisé (vérifiez la clé GEMINI).";
        }
        try {
            return assistant.chat(question);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la requête LLM/RAG", e);
            return "Une erreur est survenue lors du traitement de la requête: " + e.getMessage();
        }
    }
}