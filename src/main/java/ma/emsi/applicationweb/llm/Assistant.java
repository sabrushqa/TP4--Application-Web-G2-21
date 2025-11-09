package ma.emsi.applicationweb.llm; // À ajuster

public interface Assistant {
    /**
     * Envoie une requête (prompt) à l'Assistant et reçoit une réponse.
     * LangChain4j gère automatiquement l'historique (ChatMemory).
     * @param prompt La question de l'utilisateur.
     * @return La réponse du LLM.
     */
    String chat(String prompt);
}