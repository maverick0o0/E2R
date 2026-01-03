package e2r;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;

import e2r.scanner.E2RHttpHandler;
import e2r.scanner.E2RContextMenuProvider;
import e2r.ui.E2RTab;
import e2r.core.EndpointStore;

/**
 * E2R (Endpoint To Request) - Main Extension Entry Point
 * 
 * A Burp Suite extension that discovers endpoints in JavaScript files
 * and uses AI to reconstruct valid HTTP requests.
 */
public class E2RExtension implements BurpExtension {
    
    public static final String EXTENSION_NAME = "E2R - Endpoint To Request";
    public static final String VERSION = "1.1.0";
    
    private static MontoyaApi api;
    private static Logging logging;
    private static EndpointStore endpointStore;
    
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        api = montoyaApi;
        logging = api.logging();
        
        // Set extension name
        api.extension().setName(EXTENSION_NAME);
        
        // Initialize the endpoint store (deduplication only, no auto-persistence)
        endpointStore = new EndpointStore();
        
        // Create the main UI tab
        E2RTab mainTab = new E2RTab(api, endpointStore);
        
        // Register the HTTP handler for passive scanning
        E2RHttpHandler httpHandler = new E2RHttpHandler(api, endpointStore, mainTab);
        api.http().registerHttpHandler(httpHandler);
        
        // Register context menu for Site Map
        E2RContextMenuProvider contextMenu = new E2RContextMenuProvider(api, endpointStore);
        api.userInterface().registerContextMenuItemsProvider(contextMenu);
        
        // Register the UI tab
        api.userInterface().registerSuiteTab(EXTENSION_NAME, mainTab);
        
        // Log startup message
        logging.logToOutput("=================================");
        logging.logToOutput(EXTENSION_NAME + " v" + VERSION);
        logging.logToOutput("Passive scanning enabled");
        logging.logToOutput("Right-click in Site Map to scan JS files");
        logging.logToOutput("=================================");
    }
    
    public static MontoyaApi getApi() {
        return api;
    }
    
    public static Logging getLogging() {
        return logging;
    }
    
    public static void log(String message) {
        if (logging != null) {
            logging.logToOutput("[E2R] " + message);
        }
    }
    
    public static void logError(String message) {
        if (logging != null) {
            logging.logToError("[E2R ERROR] " + message);
        }
    }
}
