package ma.emsi.applicationweb.jsf;

import ma.emsi.applicationweb.llm.LlmClient;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/**
 * Backing bean pour la page JSF de RAG.
 * Utilise le LlmClient qui contient déjà le système RAG avec routage.
 */
@Named
@ViewScoped
public class RagBean implements Serializable {

    @Inject
    private LlmClient llmClient;

    @Inject
    private FacesContext facesContext;

    private String question;
    private String reponse;
    private StringBuilder conversation = new StringBuilder();

    public RagBean() {
        // Constructeur obligatoire pour CDI
    }

    // --- GETTERS ET SETTERS ---

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    // --- LOGIQUE MÉTIER ---

    /**
     * Envoie la question au système RAG via LlmClient
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Question vide",
                    "Veuillez saisir une question"
            );
            facesContext.addMessage(null, message);
            return null;
        }

        try {
            // Utiliser le LlmClient qui gère déjà le RAG
            this.reponse = llmClient.envoyerQuestion(question);
            afficherConversation();
        } catch (Exception e) {
            this.reponse = "ERREUR : " + e.getMessage();
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Erreur RAG",
                    this.reponse
            );
            facesContext.addMessage(null, message);
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Nouveau chat - réinitialise la conversation
     */
    public String nouveauChat() {
        return "rag?faces-redirect=true";
    }

    /**
     * Affiche la conversation dans l'interface
     */
    private void afficherConversation() {
        conversation.append("👤 Utilisateur:\n")
                .append(question)
                .append("\n\n🤖 Assistant:\n")
                .append(reponse)
                .append("\n\n")
                .append("=".repeat(80))
                .append("\n\n");
        this.question = null;
    }
}