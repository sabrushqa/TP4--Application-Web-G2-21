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
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Assistant RAG avec fonctionnalités avancées :
 * - Chargement de 2+ documents PDF
 * - Routage intelligent (RAG ou pas)
 * - Logging détaillé
 * - Gestion de la mémoire de conversation
 */
@ApplicationScoped
public class RagAssistant implements Serializable {

    private static final String API_KEY_ENV_VAR = "GEMINI";
    private static final String MODEL_NAME = "gemini-2.0-flash-exp";
    private static final Logger LOGGER = Logger.getLogger(RagAssistant.class.getName());

    private ChatModel chatModel;
    private ChatMemory chatMemory;
    private Assistant assistant;
    private ContentRetriever ragContentRetriever;
    private boolean derniereQuestionAvecRAG = false;

    /**
     * Configure le logging pour voir les détails des opérations
     */
    private void configureLogging() {
        Logger langchainLogger = Logger.getLogger("dev.langchain4j");
        langchainLogger.setLevel(Level.FINE);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        langchainLogger.addHandler(handler);

        LOGGER.info("=== Logging configuré pour RAG ===");
    }

    /**
     * Initialise le système RAG complet
     */
    public void initialiser() {
        LOGGER.info("=== Initialisation du système RAG ===");
        configureLogging();

        // 1. Configuration de l'API
        String apiKey = System.getenv(API_KEY_ENV_VAR);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "La clé API Gemini doit être définie dans la variable d'environnement " + API_KEY_ENV_VAR
            );
        }

        this.chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .temperature(0.3)
                .logRequestsAndResponses(true)  // Logging activé
                .build();

        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 2. Message système pour guider le comportement
        String systemMessage = """
                Vous êtes un assistant intelligent qui peut accéder à des documents techniques.
                Lorsque vous répondez à partir de documents, citez les informations de manière précise.
                Pour les questions générales de conversation, répondez naturellement sans faire référence aux documents.
                Soyez concis et professionnel.
                """;
        this.chatMemory.add(SystemMessage.from(systemMessage));

        // 3. Chargement et ingestion des documents PDF
        LOGGER.info("=== Phase 1 : Ingestion des documents ===");
        this.ragContentRetriever = creerContentRetriever();

        // 4. Création du routeur personnalisé (Test 4 : Pas de RAG)
        QueryRouter routeurIntelligent = creerRouteurIntelligent();

        // 5. Configuration du RetrievalAugmentor
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(routeurIntelligent)
                .build();

        // 6. Création de l'assistant
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(this.chatModel)
                .chatMemory(this.chatMemory)
                .retrievalAugmentor(retrievalAugmentor)
                .build();

        LOGGER.info("=== Système RAG initialisé avec succès ===");
    }

    /**
     * Crée le ContentRetriever avec tous les documents PDF
     * FONCTIONNALITÉ : Chargement de 2+ fichiers PDF
     */
    private ContentRetriever creerContentRetriever() {
        Path docsPath = Paths.get("src/main/resources/documents/");

        // Vérifier que le répertoire existe
        if (!Files.exists(docsPath)) {
            LOGGER.warning("Le répertoire documents/ n'existe pas. Création...");
            try {
                Files.createDirectories(docsPath);
            } catch (IOException e) {
                throw new RuntimeException("Impossible de créer le répertoire documents/", e);
            }
        }

        // Charger tous les PDF du répertoire
        List<Document> documents;
        try (Stream<Path> stream = Files.list(docsPath)) {
            documents = stream
                    .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                    .map(path -> {
                        LOGGER.info("Chargement du document : " + path.getFileName());
                        return FileSystemDocumentLoader.loadDocument(
                                path,
                                new ApacheTikaDocumentParser()
                        );
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.severe("Erreur lors du chargement des documents : " + e.getMessage());
            documents = List.of();
        }

        if (documents.size() < 2) {
            LOGGER.warning(
                    "ATTENTION : Seulement " + documents.size() +
                            " document(s) PDF trouvé(s). Le TP exige au moins 2 fichiers."
            );
        } else {
            LOGGER.info(documents.size() + " documents PDF chargés avec succès");
        }

        // Découpage en segments
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.splitAll(documents);
        LOGGER.info(segments.size() + " segments créés au total");

        // Création des embeddings
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (!segments.isEmpty()) {
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
            LOGGER.info(embeddings.size() + " embeddings stockés");
        }

        // Retourner le ContentRetriever configuré
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();
    }

    /**
     * Crée un routeur intelligent qui décide d'utiliser le RAG ou pas
     * FONCTIONNALITÉ : Test 4 - Routage "utiliser le RAG ou pas"
     */
    private QueryRouter creerRouteurIntelligent() {
        return new QueryRouter() {
            @Override
            public Collection<ContentRetriever> route(Query query) {
                String questionUtilisateur = query.text();

                LOGGER.info("=== Analyse de routage pour : " + questionUtilisateur + " ===");

                // Détection de questions conversationnelles simples
                String questionLower = questionUtilisateur.toLowerCase().trim();
                if (estQuestionConversationnelle(questionLower)) {
                    LOGGER.info("→ Décision : SANS RAG (question conversationnelle)");
                    derniereQuestionAvecRAG = false;
                    return Collections.emptyList();
                }

                // Demander au LLM si la question nécessite le RAG
                String promptAnalyse = String.format("""
                    La question suivante nécessite-t-elle une recherche dans des documents techniques 
                    sur l'IA, le RAG, LangChain4j, ou des sujets techniques similaires ?
                    
                    Question : "%s"
                    
                    Réponds uniquement par 'oui' ou 'non'.
                    """, questionUtilisateur);

                try {
                    String reponse = chatModel.generate(promptAnalyse).toLowerCase().trim();
                    LOGGER.info("→ Analyse LLM : " + reponse);

                    if (reponse.contains("oui")) {
                        LOGGER.info("→ Décision : AVEC RAG (recherche dans les documents)");
                        derniereQuestionAvecRAG = true;
                        return Collections.singletonList(ragContentRetriever);
                    } else {
                        LOGGER.info("→ Décision : SANS RAG (pas de recherche nécessaire)");
                        derniereQuestionAvecRAG = false;
                        return Collections.emptyList();
                    }
                } catch (Exception e) {
                    LOGGER.warning("Erreur lors de l'analyse : " + e.getMessage());
                    // En cas d'erreur, utiliser le RAG par défaut
                    derniereQuestionAvecRAG = true;
                    return Collections.singletonList(ragContentRetriever);
                }
            }
        };
    }

    /**
     * Détecte si une question est conversationnelle (salutations, politesse, etc.)
     */
    private boolean estQuestionConversationnelle(String question) {
        String[] motsConversationnels = {
                "bonjour", "salut", "hello", "hi", "bonsoir",
                "comment vas-tu", "comment allez-vous", "ça va",
                "merci", "au revoir", "bye", "à bientôt"
        };

        for (String mot : motsConversationnels) {
            if (question.contains(mot)) {
                return true;
            }
        }

        return question.length() < 20 && !question.contains("?");
    }

    /**
     * Pose une question à l'assistant RAG
     */
    public String poserQuestion(String question) {
        if (assistant == null) {
            throw new IllegalStateException("Le système RAG n'est pas initialisé. Appelez initialiser() d'abord.");
        }

        LOGGER.info("=== Question posée : " + question + " ===");
        String reponse = assistant.chat(question);
        LOGGER.info("=== Réponse générée ===");

        return reponse;
    }

    /**
     * Indique si la dernière question a utilisé le RAG
     */
    public boolean isDerniereQuestionAvecRAG() {
        return derniereQuestionAvecRAG;
    }
}