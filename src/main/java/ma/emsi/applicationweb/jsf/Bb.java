package ma.emsi.applicationweb.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.applicationweb.llm.LlmClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Backing Bean JSF pour l'application de Chat/RAG.
 * L'annotation @Named sans argument attribue l'identifiant 'bb' au bean,
 * résolvant ainsi l'erreur 'identifier 'bb' resolved to null'.
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Bb.class.getName());

    private String roleSysteme;
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;
    private String question;
    private String reponse;

    // Utilisation de StringBuilder pour la performance et l'historique
    private StringBuilder conversation = new StringBuilder();

    @Inject
    private FacesContext facesContext;

    /**
     * Instance du client LLM (RAG, Routage, Logging) initialisée ici.
     * Dans un contexte Java EE bien configuré, on pourrait aussi utiliser @Inject LlmClient.
     */
    private LlmClient llmClient = new LlmClient();


    // --- Getters et Setters ---

    // Getter pour la conversation (retourne String pour l'affichage JSF)
    public String getConversation() {
        return conversation.toString();
    }

    // Setter de conversation (non utilisé par JSF, mais maintenu)
    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    public String getRoleSysteme() {
        if (roleSysteme == null) {
            // Définit le rôle par défaut au premier accès
            if (getRolesSysteme() != null && !getRolesSysteme().isEmpty()) {
                roleSysteme = (String) getRolesSysteme().get(0).getValue();
            }
        }
        return roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    // --- Actions ---

    /**
     * Gère l'envoi de la question à l'assistant LLM (RAG/Routage).
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Champ vide", "Veuillez poser une question.");
            facesContext.addMessage(null, message);
            return null;
        }

        // Si c'est le premier message, on configure le rôle système et on le verrouille
        if (this.conversation.isEmpty() && this.roleSystemeChangeable) {
            if (roleSysteme != null && !roleSysteme.isBlank()) {
                llmClient.setSystemRole(roleSysteme);
                this.roleSystemeChangeable = false;
            } else {
                LOGGER.warning("Rôle système non sélectionné. Le LLM utilise son rôle par défaut.");
            }
        }

        LOGGER.log(Level.INFO, "Question utilisateur reçue: {0}", question);

        // Appel au client LLM avec RAG et Routage
        this.reponse = llmClient.PoserQuestion(question);

        // Mise à jour de l'historique de la conversation
        afficherConversation();

        // Réinitialise le champ de question
        this.question = null;

        return null; // Reste sur la même page (AJAX)
    }

    /**
     * Démarre un nouveau chat en forçant le rechargement de la page.
     */
    public String nouveauChat() {
        // Le retour d'une page (ici "index") force la destruction du bean @ViewScoped
        // et la création d'une nouvelle instance, réinitialisant tout.
        return "index?faces-redirect=true"; // Ajout de faces-redirect pour une redirection propre
    }

    /**
     * Met à jour le StringBuilder de la conversation.
     */
    private void afficherConversation() {
        // Ajout de retours à la ligne pour un affichage lisible dans l'inputTextarea
        this.conversation.append("\n== User:\n").append(question).append("\n== Assistant:\n").append(reponse).append("\n");
    }

    // --- Configuration des rôles prédéfinis ---

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            // Rôle pour le RAG/Routage
            String roleAssistant = """
                    You are a helpful assistant. You help the user to find the information they need,
                    using the provided context (RAG documents) if available. If the question is outside
                    the scope of RAG documents, use your general knowledge.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleAssistant, "Assistant RAG (Par défaut)"));

            // Autres rôles pour démontrer le changement de Persona
            String roleTraducteur = """
                    You are an interpreter. You translate from English to French and from French to English.
                    Do not use RAG documents for translation tasks.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleTraducteur, "Traducteur Anglais-Français"));

            String roleGuide = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit and the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleGuide, "Guide touristique"));
        }

        return this.listeRolesSysteme;
    }
}