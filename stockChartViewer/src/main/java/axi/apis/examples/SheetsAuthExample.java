package axi.apis.examples;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public class SheetsAuthExample {

    // Fabbrica JSON predefinita per la serializzazione/deserializzazione.
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    // Trasporto HTTP per le richieste di rete.
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    // Directory dove verranno memorizzati i token OAuth.
    // Assicurati che questa directory sia scrivibile dall'applicazione.
    private static final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".credentials/sheets-java-quickstart");

    // Scope delle API Google Sheets. Questi definiscono a quali dati l'applicazione avrà accesso.
    // Qui si richiede l'accesso in sola lettura ai fogli di calcolo.
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);

    // Percorso del file client_secrets.json.
    // Questo file contiene l'ID client e il segreto del client ottenuti da Google Cloud Console.
    // Dovrebbe essere posizionato nella directory 'src/main/resources' del tuo progetto Maven/Gradle.
    //private static final String CLIENT_SECRETS_FILE = "/client_secrets.json";
    private static final String CLIENT_SECRETS_FILE = "/resources/client_secret_2358418628-m0u3icddo97u6khog0shurit381tvmoi.apps.googleusercontent.com.json";

    /**
     * Autorizza l'applicazione e recupera le credenziali dell'utente.
     * Se è la prima volta, l'utente verrà reindirizzato a Google per il consenso.
     * Le credenziali (incluso il refresh token) verranno memorizzate localmente.
     *
     * @return Un oggetto Credential contenente i token di accesso e refresh.
     * @throws IOException Se si verifica un errore durante il caricamento delle credenziali o l'autorizzazione.
     */
    public static Credential authorize() throws IOException {
        // Carica le credenziali del client (client_id, client_secret) dal file JSON.
        InputStream in = SheetsAuthExample.class.getResourceAsStream(CLIENT_SECRETS_FILE);
        if (in == null) {
            System.err.println("Errore: Il file client_secrets.json non è stato trovato nel classpath.");
            System.err.println("Assicurati di averlo inserito in src/main/resources o nella root del classpath.");
            // Fornisci un esempio di struttura del file per aiutare l'utente.
            String exampleJson = "{\n" +
                                 "  \"web\": {\n" +
                                 "    \"client_id\": \"YOUR_CLIENT_ID\",\n" +
                                 "    \"project_id\": \"YOUR_PROJECT_ID\",\n" +
                                 "    \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                                 "    \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                                 "    \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                                 "    \"client_secret\": \"YOUR_CLIENT_SECRET\",\n" +
                                 "    \"redirect_uris\": [\"http://localhost:8080/oauth2callback\"]\n" +
                                 "  }\n" +
                                 "}";
            System.err.println("Esempio di client_secrets.json (sostituisci con i tuoi valori reali):\n" + exampleJson);
            throw new IOException("client_secrets.json non trovato. Impossibile procedere con l'autorizzazione.");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Costruisci il flusso di autorizzazione OAuth 2.0.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(DATA_STORE_DIR)) // Specifica dove memorizzare i token.
                .setAccessType("offline") // Questo è FONDAMENTALE: richiede un refresh token.
                .setApprovalPrompt("force") // Forza il consenso dell'utente ogni volta (utile per il testing).
                                            // In produzione, potresti volerlo impostare su "auto" o rimuoverlo.
                .build();

        // Ottieni le credenziali dell'utente.
        // Questo aprirà una finestra del browser per l'autorizzazione.
        // Il LocalServerReceiver avvia un server HTTP locale per intercettare il reindirizzamento di Google.
        Credential credential = new com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(
                flow, new com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver())
                .authorize("user"); // "user" è un identificatore per l'utente, usato per la memorizzazione dei token.

        System.out.println("Credenziali autorizzate e memorizzate in: " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Avvio del processo di autorizzazione e gestione dei token...");
        Credential credential = authorize();

        // *** Recupero del Refresh Token ***
        // Il refresh token viene automaticamente memorizzato dalla libreria nel DataStore specificato.
        // Puoi recuperarlo dall'oggetto Credential se hai bisogno di memorizzarlo in un tuo database.
        String initialRefreshToken = credential.getRefreshToken();
        System.out.println("\n--- Dettagli del Token Iniziale ---");
        System.out.println("Refresh Token (se disponibile): " + (initialRefreshToken != null ? initialRefreshToken : "N/A - Potrebbe non essere stato rilasciato se l'utente aveva già dato il consenso in precedenza e non è stato richiesto un nuovo consenso."));
        System.out.println("Access Token (iniziale): " + credential.getAccessToken());
        System.out.println("Scadenza Access Token (iniziale): " + credential.getExpiresInSeconds() + " secondi");
       // System.out.println("Scope autorizzati: " + credential.getScope());

        System.out.println("\n--- Simulazione dell'utilizzo delle credenziali e del refresh automatico ---");

        // Per dimostrare il meccanismo di refresh, forziamo la scadenza dell'access token.
        // ATTENZIONE: NON farlo in un'applicazione di produzione! Questo è solo a scopo dimostrativo.
        credential.setExpiresInSeconds(0L); // Imposta la scadenza a 0 secondi per forzare il refresh.

        System.out.println("\nAccess Token impostato per scadere immediatamente per la dimostrazione.");
        System.out.println("Tentativo di aggiornare le credenziali (simulando una chiamata API che richiede un token valido)...");

        // *** Utilizzo del Refresh Token per l'Aggiornamento delle Credenziali ***
        // La libreria client di Google per Java gestisce il refresh in modo automatico.
        // Quando effettui una chiamata API utilizzando un oggetto Credential che contiene un refresh token,
        // la libreria rileverà se l'access token è scaduto e, in tal caso, userà il refresh token
        // per ottenere un nuovo access token prima di eseguire la richiesta API.
        // Non è necessario chiamare esplicitamente `credential.refreshToken()` nella maggior parte dei casi.
        // Lo chiamiamo qui solo per dimostrare il processo di refresh.

        boolean refreshed = false;
        try {
            refreshed = credential.refreshToken(); // Tenta di rinfrescare il token.
        } catch (IOException e) {
            System.err.println("Errore durante il refresh del token: " + e.getMessage());
            System.err.println("Questo potrebbe accadere se il refresh token è invalido o revocato.");
        }


        if (refreshed) {
            System.out.println("\nCredenziali aggiornate con successo tramite refresh token!");
            System.out.println("Nuovo Access Token: " + credential.getAccessToken());
            System.out.println("Nuova scadenza Access Token: " + credential.getExpiresInSeconds() + " secondi");

            // Se Google ha emesso un nuovo refresh token (rotazione del refresh token),
            // la libreria lo avrà già aggiornato nel suo store.
            String newRefreshToken = credential.getRefreshToken();
            if (newRefreshToken != null && !newRefreshToken.equals(initialRefreshToken)) {
                System.out.println("Ricevuto un nuovo Refresh Token (rotazione): " + newRefreshToken);
                System.out.println("Assicurati di aggiornare il tuo storage persistente con questo nuovo refresh token.");
            }
        } else {
            System.out.println("\nImpossibile aggiornare le credenziali. Potrebbe non esserci un refresh token valido,");
            System.out.println("o il refresh token è scaduto/revocato dall'utente o da Google.");
            System.out.println("Sarà necessario ri-autorizzare l'utente da capo.");
        }

        System.out.println("\nEsempio completato.");
    }
}
