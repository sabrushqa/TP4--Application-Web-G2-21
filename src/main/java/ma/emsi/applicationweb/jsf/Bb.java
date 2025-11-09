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


// Utilisation de "ragBean" pour correspondre à l'appel dans index.xhtml : #{ragBean.conversation}
@Named("ragBean")
@ViewScoped
public class Bb implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Bb.class.getName());

    /**
     * Rôle "système" que l'on attribuera au LLM.
     */
    private String roleSysteme;

    /**
     * Empêche la modification du rôle après le premier envoi.
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste des rôles prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;

    /**
     * Dernière réponse de l'API Gemini.
     */
    private String reponse;

    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Contexte JSF pour afficher les messages d'erreur.
     */
    @Inject
    private FacesContext facesContext;

    /**
     * Instance du client LLM qui gère le RAG, le Routage et la connexion à Gemini.
     */
    // Initialisation immédiate du client LLM (qui gère l'ingestion RAG au constructeur)
    private LlmClient llmClient = new LlmClient();

    // Constructeur par défaut obligatoire pour un bean CDI
    public Bb() {
        // Le RAG/Routage est initialisé dans le constructeur de LlmClient
        LOGGER.info("Initialisation du Backing Bean 'ragBean'.");
    }

    // --- Getters et Setters ---

    public String getRoleSysteme() {
        if (roleSysteme == null) {
            // Définir le rôle par défaut si non initialisé
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

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    // --- Actions ---

    /**
     * Envoie la question à l'assistant RAG.
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            // Message d'erreur si la question est vide
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Champ vide", "Veuillez poser une question.");
            facesContext.addMessage(null, message);
            return null;
        }

        // Si c'est le premier message, on configure le rôle système et on le verrouille
        if (this.conversation.isEmpty()) {
            if (roleSysteme != null && !roleSysteme.isBlank()) {
                llmClient.setSystemRole(roleSysteme);
                this.roleSystemeChangeable = false;
            } else {
                LOGGER.warning("Rôle système non sélectionné, utilisation du rôle par défaut du LLM.");
            }
        }

        LOGGER.log(Level.INFO, "Question utilisateur reçue: {0}", question);

        // Appel au client LLM (qui utilise RAG et Routage)
        this.reponse = llmClient.PoserQuestion(question);

        // Afficher la nouvelle conversation
        afficherConversation();
        // Réinitialise le champ de question après l'envoi
        this.question = null;

        return null;
    }

    /**
     * Démarre un nouveau chat en retournant "index".
     * Le retour d'une page (ici "index") force la destruction de l'instance @ViewScoped et
     * la création d'une nouvelle, réinitialisant la conversation, la mémoire du LLM et le rôle.
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Met à jour le StringBuilder pour affichage.
     */
    private void afficherConversation() {
        this.conversation.append("== User:\n").append(question).append("\n== Assistant:\n").append(reponse).append("\n");
    }

    // --- Configuration des rôles prédéfinis ---

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            String roleAssistant = """
                    You are a helpful assistant. You help the user to find the information they need,
                    using the provided context (RAG documents) if available.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleAssistant, "Assistant RAG (Défaut)"));

            String roleTraducteur = """
                    You are an interpreter. You translate from English to French and from French to English.
                    Do not use RAG documents for translation tasks.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleTraducteur, "Traducteur Anglais-Français"));

            String roleGuide = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit and the average price of a meal.
                    Do not use RAG documents unless they specifically contain travel information.
                    """;
            this.listeRolesSysteme.add(new SelectItem(roleGuide, "Guide touristique"));
        }

        return this.listeRolesSysteme;
    }

}