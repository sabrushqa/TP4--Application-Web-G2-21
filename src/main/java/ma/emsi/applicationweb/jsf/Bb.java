package ma.emsi.applicationweb.jsf; // Assurez-vous d'ajuster le package

import ma.emsi.applicationweb.llm.LlmClient; // Importe le client LLM
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation (chat) entre les requêtes HTTP.
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    // --- VARIABLES D'INSTANCE ---

    /**
     * Client LLM injecté pour interagir avec l'API Gemini via LangChain4j.
     */
    @Inject
    private LlmClient llmClient;

    /**
     * Rôle "système" sélectionné par l'utilisateur.
     */
    private String roleSysteme;

    /**
     * Indique si le rôle système peut être modifié (false après la première question).
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste des rôles système prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;

    /**
     * Dernière réponse du LLM.
     */
    private String reponse;

    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Contexte JSF, utilisé pour afficher des messages d'erreur.
     */
    @Inject
    private FacesContext facesContext;

    // --- CONSTRUCTEUR ---

    public Bb() {
        // Constructeur obligatoire pour un bean CDI
    }

    // --- GETTERS ET SETTERS ---

    public String getRoleSysteme() {
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

    public String getConversation() {
        return conversation.toString();
    }

    // Setter non strictement nécessaire mais gardé pour la complétude
    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    // --- LOGIQUE MÉTIER ---

    /**
     * Envoie la question au LLM via LlmClient.
     *
     * @return null pour rester sur la même page (portée view).
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        // Si la conversation n'a pas encore commencé, initialiser le rôle système
        if (this.conversation.isEmpty()) {
            try {
                // Vider la mémoire et définir le rôle système dans LlmClient
                // (Le setter gère la réinitialisation de la mémoire et l'ajout du SystemMessage)
                llmClient.setSystemRole(roleSysteme);
                this.roleSystemeChangeable = false; // Invalide le changement de rôle
            } catch (Exception e) {
                // Gérer les erreurs de configuration (ex: clé API manquante)
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Erreur de configuration", "Impossible de démarrer le client LLM: " + e.getMessage());
                facesContext.addMessage(null, message);
                return null;
            }
        }

        try {
            // Déléguer l'appel à l'API LLM au LlmClient
            this.reponse = llmClient.envoyerQuestion(question);
        } catch (Exception e) {
            this.reponse = "ERREUR LORS DE L'APPEL AU LLM : " + e.getMessage();
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur LLM", this.reponse);
            facesContext.addMessage(null, message);
        }

        // Ajoute la paire question-réponse à l'historique affiché
        afficherConversation();
        return null; // Reste sur la même page
    }

    /**
     * Pour un nouveau chat.
     * Retourne "index" pour invalider la portée view et créer une nouvelle instance du backing bean.
     * @return "index"
     */
    public String nouveauChat() {
        return "index?faces-redirect=true"; // Utilise faces-redirect pour assurer une nouvelle requête GET propre
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     */
    private void afficherConversation() {
        this.conversation.append("== User:\n").append(question).append("\n== Assistant:\n").append(reponse).append("\n");
        // Efface la dernière question pour que le champ de saisie soit vide
        this.question = null;
    }

    /**
     * Génère la liste des rôles système prédéfinis pour la liste déroulante JSF.
     */
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            String roleAssistant = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleAssistant, "Assistant Général"));

            String roleTranslator = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleTranslator, "Traducteur Anglais-Français"));

            String roleTravelGuide = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    are you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleTravelGuide, "Guide touristique"));

            // Initialisation du rôle par défaut
            if (this.roleSysteme == null || this.roleSysteme.isBlank()) {
                this.roleSysteme = roleAssistant;
            }
        }
        return this.listeRolesSysteme;
    }

}