package ma.emsi.applicationweb.llm;

import dev.langchain4j.model.chat.ChatModel; // Remplacé ChatLanguageModel par ChatModel (pour les versions 0.x)
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.data.message.SystemMessage;

import jakarta.enterprise.context.Dependent;
import java.io.Serializable;

/**
 * Gère l'interface avec l'API de Gemini (Modèle gemini-2.5-flash) via LangChain4j.
 * Utilise la portée Dependent pour la gestion de l'injection CDI.
 */
@Dependent
public class LlmClient implements Serializable {
    private static final String API_KEY_ENV_VAR = "GEMINI";
    private static final String MODEL_NAME = "gemini-2.5-flash"; // Modèle demandé

    // Rôle système actuel (conservé pour le setter)
    private String systemRole;

    // L'interface Assistant, dont l'implémentation est fournie par LangChain4j
    private Assistant assistant;

    // Mémoire de conversation gérée par LangChain4j
    private final ChatMemory chatMemory;

    public LlmClient() {
        // 1. Récupère la clé secrète en utilisant la variable d'environnement
        String key = System.getenv(API_KEY_ENV_VAR);

        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("La variable d'environnement '" + API_KEY_ENV_VAR + "' (clé API) n'est pas définie ou est vide.");
        }

        // 2. Configuration de la mémoire : fenêtre de 10 messages
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 3. Création du modèle de chat
        // Utilisation de GoogleAiGeminiChatModel pour des raisons de compilation
        ChatModel model = GoogleAiGeminiChatModel.builder() // Changement du type local en ChatModel
                .apiKey(key)
                .modelName(MODEL_NAME)
                .build();

        // 4. Création de l'Assistant (instance proxy) via AiServices
        // CORRECTION : Retour à chatModel pour les versions 0.x
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model) // Changement de la méthode en chatModel
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * Définit le rôle système pour l'assistant.
     * Vide la mémoire avant d'ajouter le nouveau rôle système pour un nouveau contexte.
     *
     * @param newSystemRole Le rôle à attribuer à l'assistant.
     */
    public void setSystemRole(String newSystemRole) {
        if (!newSystemRole.equals(this.systemRole)) {
            // Le rôle a changé : vider la mémoire et ajouter le nouveau rôle système
            this.chatMemory.clear();
            this.systemRole = newSystemRole;

            // Ajouter le rôle système comme SystemMessage à la mémoire
            this.chatMemory.add(SystemMessage.from(newSystemRole));
        }
    }

    /**
     * Envoie la question de l'utilisateur au LLM et reçoit la réponse.
     *
     * @param question La question de l'utilisateur.
     * @return La réponse générée par le LLM.
     */
    public String envoyerQuestion(String question) {
        // LangChain4j gère l'ajout de la question/réponse à la mémoire via l'interface Assistant
        return this.assistant.chat(question);
    }
}