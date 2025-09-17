package qz.ws;

import java.awt.Point;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.ee9.websocket.api.Session;
import org.eclipse.jetty.ee9.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.ee9.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.ee9.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.ee9.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.ee9.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.ee9.websocket.api.exceptions.CloseException;
import org.eclipse.jetty.ee9.websocket.api.exceptions.WebSocketException;
import org.eclipse.jetty.server.Server;

import qz.auth.Certificate;
import qz.auth.RequestState;
import qz.common.Constants;
import qz.communication.DeviceException;
import qz.communication.DeviceListener;
import qz.printer.PrintServiceMatcher;
import qz.printer.status.StatusMonitor;
import qz.utils.PrintingUtilities;
import qz.ws.substitutions.Substitutions;

@WebSocket
public class PrintSocketClient {

    private static final Logger log = LogManager.getLogger(PrintSocketClient.class);

    private final AppHook trayProvider;
    private final PromptHook serverDialogProvider;
    private PrintValidationHook printValidationHook;

    private static final Semaphore dialogAvailable = new Semaphore(1, true);

    // websocket port -> Connection
    private static final HashMap<Integer, SocketConnection> openConnections = new HashMap<>();
    
    // List of listeners to be notified when messages are processed
    private static final List<MessageProcessedListener> messageProcessedListeners = new ArrayList<>();

    public PrintSocketClient(Server server, @lombok.NonNull AppHook trayProvider, @lombok.NonNull PromptHook serverDialogProvider, @lombok.NonNull PrintValidationHook printValidationHook) {
        this.trayProvider = trayProvider;
        this.serverDialogProvider = serverDialogProvider;
        this.printValidationHook = printValidationHook;
    }
    
    /**
     * Update the hook info in this client instance.
     * Called by PrintSocketClientEventHandler when hook info changes.
     */
    public void updateSubscriptionInfo(PrintValidationHook printValidationHook) {
        this.printValidationHook = printValidationHook;
        log.info("Subscription info updated in PrintSocketClient ");
        
    }
    /**
     * Adds a listener to be notified when messages are processed.
     *
     * @param listener The listener to add
     */
    public static void addMessageProcessedListener(MessageProcessedListener listener) {
        if (listener != null && !messageProcessedListeners.contains(listener)) {
            messageProcessedListeners.add(listener);
            log.debug("Added message processed listener: {}", listener.getClass().getName());
        }
    }
    
    /**
     * Removes a listener.
     *
     * @param listener The listener to remove
     */
    public static void removeMessageProcessedListener(MessageProcessedListener listener) {
        if (listener != null) {
            messageProcessedListeners.remove(listener);
            log.debug("Removed message processed listener: {}", listener.getClass().getName());
        }
    }
    
    /**
     * Notifies all registered listeners that a message has been processed.
     *
     * @param messageType The type of message that was processed
     */
    private static void notifyMessageProcessed(String messageType) {
        // Skip notification for ping messages as they are too frequent
        if ("ping".equals(messageType)) {
            return;
        }
        
        // Only notify for methods that trigger profile refresh
        // This prevents profile loading for component registration and other operations
        SocketMethod method = SocketMethod.findFromCall(messageType);
        if (method == SocketMethod.INVALID || !method.triggersProfileRefresh()) {
            log.debug("Skipping profile refresh for method that doesn't trigger profile refresh: {}", messageType);
            return;
        }
        
        log.debug("Triggering profile refresh for method: {}", messageType);
        for (MessageProcessedListener listener : messageProcessedListeners) {
            try {
                listener.onMessageProcessed(messageType);
            } catch (Exception e) {
                log.error("Error notifying listener about processed message", e);
            }
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        log.info("=== WebSocket Connection Established ===");
        log.info("Connection opened from {} on socket port {}", session.getRemoteAddress(),
                ((InetSocketAddress) session.getLocalAddress()).getPort());
        log.info("Remote IP: {}", ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress());
        log.info("Remote hostname: {}", ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostName());
        log.info("Local binding: {}", session.getLocalAddress());
        log.debug("Session details - ID: {}, Protocol Version: {}",
                session.hashCode(), session.getProtocolVersion());
        log.debug("Session upgrade request: {}", session.getUpgradeRequest());
        log.debug("Session upgrade response: {}", session.getUpgradeResponse());
        log.debug("Session policy - Max text message size: {}, Idle timeout: {}",
                session.getPolicy().getMaxTextMessageSize(), session.getPolicy().getIdleTimeout());
        
        // Extract Origin header from WebSocket upgrade request
        String originHeader = null;
        if (session.getUpgradeRequest() != null && session.getUpgradeRequest().getHeaders() != null) {
            originHeader = session.getUpgradeRequest().getHeader("Origin");
            if (originHeader != null) {
                log.info("=== ORIGIN_HEADER_EXTRACTION === | Origin header found: {}", originHeader);
                // Extract domain from Origin header (remove protocol)
                try {
                    java.net.URI uri = new java.net.URI(originHeader);
                    String domain = uri.getHost();
                    if (domain != null) {
                        originHeader = domain;
                        log.info("=== ORIGIN_HEADER_EXTRACTION === | Extracted domain from Origin: {}", originHeader);
                    }
                } catch (Exception e) {
                    log.warn("=== ORIGIN_HEADER_EXTRACTION === | Failed to parse Origin header as URI: {}, using as-is", originHeader);
                }
            } else {
                log.info("=== ORIGIN_HEADER_EXTRACTION === | No Origin header found in request");
            }
        } else {
            log.warn("=== ORIGIN_HEADER_EXTRACTION === | Upgrade request or headers are null");
        }
        
        trayProvider.displayInfoMessage("Client connected");

        // new connections are unknown until they send a proper certificate
        SocketConnection connection = new SocketConnection(Certificate.UNKNOWN);
        
        // Store the Origin header in the connection
        connection.setOriginHeader(originHeader);
        
        Integer remotePort = ((InetSocketAddress) session.getRemoteAddress()).getPort();
        
        log.debug("Storing connection for remote port: {} with Origin: {}", remotePort, originHeader);
        openConnections.put(remotePort, connection);
        log.debug("Total active connections: {}", openConnections.size());
    }

    @OnWebSocketClose
    public void onClose(Session session, int closeCode, String reason) {
        log.info("=== WebSocket Connection Closed ===");
        log.info("Connection closed from {}: code={}, reason={}", session.getRemoteAddress(), closeCode, reason);
        log.debug("Close code details - Normal: {}, Going Away: {}, Protocol Error: {}",
                closeCode == 1000, closeCode == 1001, closeCode == 1002);
        
        trayProvider.displayInfoMessage("Client disconnected");

        Integer port = ((InetSocketAddress) session.getRemoteAddress()).getPort();
        log.debug("Removing connection for remote port: {}", port);
        
        SocketConnection closed = openConnections.remove(port);
        if (closed != null) {
            log.debug("Found and removing connection: {}", closed);
            try {
                closed.disconnect();
                log.debug("Successfully disconnected communication channel");
            } catch (Exception e) {
                log.error("Failed to close communication channel", e);
            }
        } else {
            log.warn("No connection found for port {} during close", port);
        }
        
        log.debug("Remaining active connections: {}", openConnections.size());
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        log.error("=== WebSocket Error Occurred ===");
        log.error("Error from {}: {}", session.getRemoteAddress(), error.getClass().getSimpleName());
        log.error("Error message: {}", error.getMessage());
        log.error("Full error details:", error);
        
        if (error instanceof EOFException || error instanceof ClosedChannelException) {
            log.info("Ignoring expected connection closure error: {}", error.getClass().getSimpleName());
            return;
        }

        if (error instanceof CloseException && error.getCause() instanceof TimeoutException) {
            log.error("Timeout error (Lost connection with client) from {}", session.getRemoteAddress(), error);
            return;
        }

        log.error("Connection error from {}: {}", session.getRemoteAddress(), error.getClass().getSimpleName(), error);
        trayProvider.displayErrorMessage(error.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, Reader reader) throws IOException {
        String message = IOUtils.toString(reader);
        
        log.debug("=== WebSocket Message Received ===");
        log.debug("Message from {}: {}", session.getRemoteAddress(),
                message != null && message.length() > 200 ? message.substring(0, 200) + "..." : message);
        log.debug("Message length: {} bytes", message != null ? message.length() : 0);

        if (message == null || message.isEmpty()) {
            log.warn("Received empty message from {}", session.getRemoteAddress());
            sendError(session, null, "Message is empty");
            return;
        }
        if (Constants.PROBE_REQUEST.equals(message)) {
            log.debug("Received probe request from {}", session.getRemoteAddress());
            try {
                session.getRemote().sendString(Constants.PROBE_RESPONSE);
                log.debug("Sent probe response to {}", session.getRemoteAddress());
            } catch (Exception ignore) {
                log.warn("Failed to send probe response", ignore);
            }
            log.warn("Second instance of {} likely detected, asking it to close", Constants.ABOUT_TITLE);
            return;
        }
        if ("ping".equals(message)) {
            log.trace("Received ping from {}", session.getRemoteAddress());
            return;
        } // keep-alive call / no need to process

        String UID = null;
        try {
            JSONObject json = cleanupMessage(new JSONObject(message));
            log.debug("Message: {}", json);
            UID = json.optString("uid");

            Integer connectionPort = ((InetSocketAddress) session.getRemoteAddress()).getPort();
            SocketConnection connection = openConnections.get(connectionPort);
            RequestState request = new RequestState(connection.getCertificate(), json);
            String callAttr = json.optString("call", "");
            var params = json.optJSONObject("params");
            var dataArray = params != null ? params.optJSONArray("data") : null;
            var dataObj = dataArray != null && dataArray.length() > 0 ? dataArray.optJSONObject(0) : null;

            String deviceFingerprint = dataObj != null ? dataObj.optString("deviceFingerprint", "") : "";
            if (deviceFingerprint == null) {
                var deviceInfo = json.optJSONObject("hostInfo");
                deviceFingerprint = deviceInfo != null ? deviceInfo.optString("fingerprint", "") : null;
            }
            SocketMethod call = SocketMethod.findFromCall(callAttr);
            if (call.isFingerPrintRequired()) {
                // Validate fingerprints if they are present in the message
                if (deviceFingerprint == null) {
                    sendError(session, UID, "UNKNOWN_CLIENT");
                    return;
                } else {
                    // Use injected SubscriptionProvider
                    PrintValidationHook.FingerprintPair fingerprints = printValidationHook.extractFingerprints(dataObj);

                    // Validate the fingerprints for this specific message using the cached list
                    PrintValidationHook.ValidationResult validationResult = printValidationHook.validateFingerprints(fingerprints);

                    if (!validationResult.isValid()) {
                        log.warn("Fingerprint validation failed: {}", validationResult.getErrorMessage());
                        sendError(session, UID, validationResult.getErrorMessage());
                        return;
                    }
                }
            }
            // if sent a certificate use that instead for this connection
            if (json.has("certificate")) {
                try {
                    Certificate certificate = new Certificate(json.optString("certificate"));
                    connection.setCertificate(certificate);

                    request.markNewConnection(certificate);

                    log.debug("Received new certificate from connection through {}", connectionPort);
                } catch (CertificateException ignore) {
                    request.markNewConnection(Certificate.UNKNOWN);
                }

                // Use ServerDetectionDialog for all certificate validation (unified approach)
                if (allowedFromDialog(request, "connect to " + Constants.ABOUT_TITLE,
                        findDialogPosition(session, json.optJSONObject("position")))) {
                    sendResult(session, UID, null);
                } else {
                    sendError(session, UID, "Connection blocked by client");
                    session.disconnect();
                }

                return; // this is a setup call, so no further processing is needed
            }

            if (request.hasCertificate() && call.isFingerPrintRequired()) {
                if (json.optLong("timestamp") + Constants.VALID_SIGNING_PERIOD < System.currentTimeMillis()
                        || json.optLong("timestamp") - Constants.VALID_SIGNING_PERIOD > System.currentTimeMillis()) {
                    // bad timestamps use the expired certificate
                    log.warn("Expired signature on request");
                    request.setStatus(RequestState.Validity.EXPIRED);
                } else if (json.isNull("signature") || !validSignature(request.getCertUsed(), json)) {
                    // bad signatures use the unsigned certificate
                    log.warn("Bad signature on request");
                    request.setStatus(RequestState.Validity.UNSIGNED);
                } else {
                    log.trace("Valid signature from {}", request.getCertName());
                    request.setStatus(RequestState.Validity.TRUSTED);
                }
            }

            // spawn thread to prevent long processes from blocking
            final String tUID = UID;
            new Thread(() -> {
                try {
                    processMessage(session, json, connection, request);
                } catch (JSONException e) {
                    log.error("Bad JSON: {}", e.getMessage());
                    sendError(session, tUID, e);
                } catch (Exception e) {
                    log.error("Problem processing message", e);
                    sendError(session, tUID, e);
                }
            }).start();
        } catch (JSONException e) {
            log.error("Bad JSON: {}", e.getMessage());
            sendError(session, UID, e);
        } catch (Exception e) {
            log.error("Problem processing message", e);
            sendError(session, UID, e);
        }
    }

    private JSONObject cleanupMessage(JSONObject msg) {
        msg.remove("promise"); // never needed java side

        // remove unused properties from older js api's
        SocketMethod call = SocketMethod.findFromCall(msg.optString("call"));
        if (!call.isFingerPrintRequired()) {
            msg.remove("signature");
            msg.remove("signAlgorithm");
        }

        return msg;
    }

    private boolean validSignature(Certificate certificate, JSONObject message) throws JSONException {
        JSONObject copy = new JSONObject(message, new String[] { "call", "params", "timestamp" });
        String signature = message.optString("signature");
        String algorithm = message.optString("signAlgorithm", "SHA1").toUpperCase(Locale.ENGLISH);

        return certificate.isSignatureValid(Certificate.Algorithm.valueOf(algorithm), signature,
                copy.toString().replaceAll("\\\\/", "/"));
    }

    /**
     * Determine which method was called from web API
     *
     * @param session WebSocket session
     * @param json    JSON received from web API
     */
    private void processMessage(Session session, JSONObject json, SocketConnection connection, RequestState request)
            throws JSONException, DeviceException, IOException {
        // perform client-side substitutions
        if (Substitutions.areActive()) {
            Substitutions substitutions = Substitutions.getInstance();
            if (substitutions != null) {
                json = substitutions.replace(json);
            }
        }

        String UID = json.optString("uid");
        SocketMethod call = SocketMethod.findFromCall(json.optString("call"));
        var params = json.optJSONObject("params");
        String fingerprint = params != null ? params.optString("fingerprint", "") : null;

        if (params == null) {
            params = new JSONObject();
        }

        if (call == SocketMethod.INVALID && (UID == null || UID.isEmpty())) {
            session.close(4003, "Connected to incompatible " + Constants.ABOUT_TITLE + " version");
            return;
        }

        // Check authentication for methods that require it
        if (call.requiresAuth() && trayProvider != null && !trayProvider.isLoggedIn()) {
            log.warn("Rejecting {} request - user not authenticated", call.getCallName());
            sendError(session, UID, "Authentication required. Please log in to use this feature.");
            return;
        }

        String prompt = call.getPromptMessage();
        if (call == SocketMethod.PRINT) {
            // special formatting for print dialogs
            JSONObject pr = params.optJSONObject("printer");
            if (pr != null) {
                prompt = String.format(prompt,
                        pr.optString("name", pr.optString("file", pr.optString("host", "an undefined location"))));
            } else {
                sendError(session, UID, "A printer must be specified before printing");
                return;
            }
        }

        if (call.isFingerPrintRequired()
                && !allowedFromDialog(request, prompt, findDialogPosition(session, json.optJSONObject("position")))) {
            sendError(session, UID, "Request blocked");
            return;
        }

        if (call != SocketMethod.GET_VERSION) {
            trayProvider.voidIdleActions();
        }

        // call appropriate methods
        switch (call) {
        case FINGERPRINT_REGISTER_DEVICE:
            if (fingerprint == null || fingerprint.isEmpty()) {
                log.warn("=== DEVICE_REGISTRATION === | Device fingerprint is required but was null/empty");
                sendError(session, UID, "Device fingerprint is required");
                return;
            }
            
            // Extract name from the JSON message if provided
            String deviceName = params.optString("name", null);
            
            log.info("=== DEVICE_REGISTRATION === | Starting device registration | FINGERPRINT: {} | NAME: {} | CURRENT_CACHE_SIZE: {} | MAX_DEVICES: {}",
                fingerprint, deviceName != null ? deviceName : "auto-generated",
                printValidationHook.getCurrentDeviceCount(), printValidationHook.getDeviceLimit());
            
            boolean deviceRegistered = printValidationHook.registerFingerprint("device", fingerprint, deviceName);
            if (deviceRegistered) {
                log.info("=== DEVICE_REGISTRATION === | SUCCESS | Device fingerprint registered: {} with name: {} | NEW_CACHE_SIZE: {}",
                    fingerprint, deviceName != null ? deviceName : "auto-generated", printValidationHook.getCurrentDeviceCount());
                
                // Log cache state after registration
                log.debug("=== DEVICE_REGISTRATION === | POST_REGISTRATION_CACHE_STATE | Device cache size: {} | Printer cache size: {} | Server cache size: {}",
                    printValidationHook.getCurrentDeviceCount(), printValidationHook.getCurrentPrinterCount(), printValidationHook.getCurrentServerCount());
                
                sendResult(session, UID, true);
            } else {
                log.error("=== DEVICE_REGISTRATION === | FAILED | Failed to register device fingerprint: {} | CURRENT_CACHE_SIZE: {} | MAX_DEVICES: {}",
                    fingerprint, printValidationHook.getCurrentDeviceCount(), printValidationHook.getDeviceLimit());
                sendError(session, UID, "Failed to register device fingerprint");
            }
            break;

        case FINGERPRINT_UNREGISTER_DEVICE:
            if (fingerprint == null || fingerprint.isEmpty()) {
                log.warn("=== DEVICE_UNREGISTRATION === | Device fingerprint is required but was null/empty");
                sendError(session, UID, "Device fingerprint is required");
                return;
            }

            log.info("=== DEVICE_UNREGISTRATION === | Starting device unregistration | FINGERPRINT: {} | CURRENT_CACHE_SIZE: {} | MAX_DEVICES: {}",
                fingerprint, printValidationHook.getCurrentDeviceCount(), printValidationHook.getDeviceLimit());

            boolean deviceUnregistered = printValidationHook.unregisterFingerprint("device", fingerprint);
            if (deviceUnregistered) {
                log.info("=== DEVICE_UNREGISTRATION === | SUCCESS | Device fingerprint unregistered: {} | NEW_CACHE_SIZE: {}",
                    fingerprint, printValidationHook.getCurrentDeviceCount());
                
                // Log cache state after unregistration
                log.debug("=== DEVICE_UNREGISTRATION === | POST_UNREGISTRATION_CACHE_STATE | Device cache size: {} | Printer cache size: {} | Server cache size: {}",
                    printValidationHook.getCurrentDeviceCount(), printValidationHook.getCurrentPrinterCount(), printValidationHook.getCurrentServerCount());
                
                sendResult(session, UID, true);
            } else {
                log.error("=== DEVICE_UNREGISTRATION === | FAILED | Failed to unregister device fingerprint: {} | CURRENT_CACHE_SIZE: {}",
                    fingerprint, printValidationHook.getCurrentDeviceCount());
                sendError(session, UID, "Failed to unregister device fingerprint");
            }
            break;

        case FINGERPRINT_REGISTER_PRINTER:
            if (fingerprint == null || fingerprint.isEmpty()) {
                log.warn("=== PRINTER_REGISTRATION === | Printer fingerprint is required but was null/empty");
                sendError(session, UID, "Printer fingerprint is required");
                return;
            }
            
            // Extract name from the JSON message if provided
            String printerName = params.optString("name", null);
            
            log.info("=== PRINTER_REGISTRATION === | Starting printer registration | FINGERPRINT: {} | NAME: {} | CURRENT_CACHE_SIZE: {} | MAX_PRINTERS: {}",
                fingerprint, printerName != null ? printerName : "auto-generated",
                printValidationHook.getCurrentPrinterCount(), printValidationHook.getPrinterLimit());
            
            boolean printerRegistered = printValidationHook.registerFingerprint("printer", fingerprint, printerName);
            if (printerRegistered) {
                log.info("=== PRINTER_REGISTRATION === | SUCCESS | Printer fingerprint registered: {} with name: {} | NEW_CACHE_SIZE: {}",
                    fingerprint, printerName != null ? printerName : "auto-generated", printValidationHook.getCurrentPrinterCount());
                
                // Log cache state after registration
                log.debug("=== PRINTER_REGISTRATION === | POST_REGISTRATION_CACHE_STATE | Device cache size: {} | Printer cache size: {} | Server cache size: {}",
                    printValidationHook.getCurrentDeviceCount(), printValidationHook.getCurrentPrinterCount(), printValidationHook.getCurrentServerCount());
                
                sendResult(session, UID, true);
            } else {
                log.error("=== PRINTER_REGISTRATION === | FAILED | Printer already registered or registration failed: {} | CURRENT_CACHE_SIZE: {} | MAX_PRINTERS: {}",
                    fingerprint, printValidationHook.getCurrentPrinterCount(), printValidationHook.getPrinterLimit());
                sendError(session, UID, "Failed to register printer fingerprint");
            }
            break;

        case FINGERPRINT_UNREGISTER_PRINTER:
            if (fingerprint == null || fingerprint.isEmpty()) {
                log.warn("=== PRINTER_UNREGISTRATION === | Printer fingerprint is required but was null/empty");
                sendError(session, UID, "Printer fingerprint is required");
                return;
            }

            log.info("=== PRINTER_UNREGISTRATION === | Starting printer unregistration | FINGERPRINT: {} | CURRENT_CACHE_SIZE: {} | MAX_PRINTERS: {}",
                fingerprint, printValidationHook.getCurrentPrinterCount(), printValidationHook.getPrinterLimit());

            boolean printerUnregistered = printValidationHook.unregisterFingerprint("printer", fingerprint);
            if (printerUnregistered) {
                log.info("=== PRINTER_UNREGISTRATION === | SUCCESS | Printer fingerprint unregistered: {} | NEW_CACHE_SIZE: {}",
                    fingerprint, printValidationHook.getCurrentPrinterCount());
                
                // Log cache state after unregistration
                log.debug("=== PRINTER_UNREGISTRATION === | POST_UNREGISTRATION_CACHE_STATE | Device cache size: {} | Printer cache size: {} | Server cache size: {}",
                    printValidationHook.getCurrentDeviceCount(), printValidationHook.getCurrentPrinterCount(), printValidationHook.getCurrentServerCount());
                
                sendResult(session, UID, true);
            } else {
                log.warn("=== PRINTER_UNREGISTRATION === | NOT_FOUND | Printer not found or already unregistered: {} | CURRENT_CACHE_SIZE: {}",
                    fingerprint, printValidationHook.getCurrentPrinterCount());
                sendResult(session, UID, "Printer not found or already unregistered");
            }
            break;

        case PRINTERS_FIND:
            if (params.has("query")) {
                String name = PrintServiceMatcher.findPrinterName(params.getString("query"));
                if (name != null) {
                    sendResult(session, UID, name);
                } else {
                    sendError(session, UID, "Specified printer could not be found.");
                }
            } else {
                JSONArray services = PrintServiceMatcher.getPrintersJSON(false);
                JSONArray names = new JSONArray();
                for (int i = 0; i < services.length(); i++) {
                    names.put(services.getJSONObject(i).getString("name"));
                }
                // Add available device count to the response
                JSONObject response = new JSONObject();
                response.put("printers", names);
                response.put("currentPrinters", printValidationHook.getCurrentPrinterCount());
                response.put("maxPrinters", printValidationHook.getPrinterLimit());
                sendResult(session, UID, response);
            }
            break;
        case PRINTERS_START_LISTENING:
            if (StatusMonitor.startListening(connection, session, params)) {
                sendResult(session, UID, null);
            } else {
                sendError(session, UID, "Listening failed.");
            }
            break;
        case PRINTERS_GET_STATUS:
            if (StatusMonitor.isListening(connection)) {
                StatusMonitor.sendStatuses(connection);
            } else {
                sendError(session, UID, "No printer listeners started for this client.");
            }
            sendResult(session, UID, null);
            break;
        case PRINTERS_STOP_LISTENING:
            StatusMonitor.stopListening(connection);
            sendResult(session, UID, null);
            break;
        case PRINT:
            PrintingUtilities.processPrintRequest(session, UID, params);
            break;

        case GET_VERSION:
            sendResult(session, UID, Constants.VERSION);
            break;
        case INVALID:
        default:
            sendError(session, UID, "Invalid function call: " + json.optString("call", "NONE"));
            break;
        }
        
        // Notify listeners that a message has been processed
        // This will trigger UI updates in the HomeDialog
        notifyMessageProcessed(call.getCallName());
    }

    /**
     * Check if the user has allowed the connection from the dialog
     *
     * @param request RequestState object
     * @param prompt  Prompt message to show in the dialog
     * @param position Position of the dialog
     * @return true if allowed, false otherwise
     */

    private boolean allowedFromDialog(RequestState request, String prompt, Point position) {
        // Get the connection to access the Origin header
        SocketConnection connection = null;
        String originDomain = null;
        String identificationMethod = "certificate CN";
        
        // Try to find the connection for this request to get the Origin header
        for (Integer port : openConnections.keySet()) {
            SocketConnection conn = openConnections.get(port);
            if (conn != null && conn.getCertificate() == request.getCertUsed()) {
                connection = conn;
                break;
            }
        }
        
        // Determine the server name to use for validation
        String serverName;
        if (connection != null && connection.getOriginHeader() != null && !connection.getOriginHeader().trim().isEmpty()) {
            originDomain = connection.getOriginHeader();
            serverName = originDomain;
            identificationMethod = "Origin header";
            log.info("=== DIALOG_VALIDATION === | Using Origin header for server identification: {}", serverName);
        } else {
            serverName = request.getCertName();
            log.info("=== DIALOG_VALIDATION === | Origin header not available, falling back to certificate CN: {}", serverName);
        }
        
        log.info("=== DIALOG_VALIDATION === | allowedFromDialog() called for server: {} (method: {})",
                 serverName != null ? serverName : "unknown", identificationMethod);
        
        // If cert can be resolved before the lock, do so and return
        if (request.hasBlockedCert()) {
            log.info("=== DIALOG_VALIDATION === | Certificate is blocked, denying access");
            return false;
        }
        if (request.hasSavedCert()) {
            log.info("=== DIALOG_VALIDATION === | Certificate is saved/trusted, allowing access");
            return true;
        }

        log.info("=== DIALOG_VALIDATION === | Certificate is not saved/trusted, showing dialog for: {} (using {})",
                 serverName, identificationMethod);

        // wait until previous prompts are closed
        try {
            dialogAvailable.acquire();
        } catch (InterruptedException e) {
            log.warn("Failed to acquire dialog", e);
            return false;
        }

        // Use ServerDetectionDialog for all certificate validation (unified approach)
        boolean allowed = false;
        
        if (serverName != null) {
            // Check if server is already registered
            if (shouldShowServerDetectionDialog(serverName)) {
                log.info("=== DIALOG_VALIDATION === | Showing ServerDetectionDialog for: {} (identified via {})",
                         serverName, identificationMethod);
                allowed = serverDialogProvider.prompt(serverName, position);
                
                if (allowed) {
                    log.info("=== DIALOG_VALIDATION === | User approved server: {} (identified via {})",
                             serverName, identificationMethod);
                    // Server registration is handled within ServerDetectionDialog.prompt()
                } else {
                    log.info("=== DIALOG_VALIDATION === | User denied server: {} (identified via {})",
                             serverName, identificationMethod);
                }
            } else {
                // Server is already registered, allow automatically
                log.info("=== DIALOG_VALIDATION === | Server {} is already registered, allowing access (identified via {})",
                         serverName, identificationMethod);
                allowed = true;
            }
        } else {
            // For unknown certificates, show the dialog
            log.info("=== DIALOG_VALIDATION === | Unknown server, showing ServerDetectionDialog");
            allowed = serverDialogProvider.prompt("Unknown Server", position);
        }

        dialogAvailable.release();

        return allowed;
    }

    private Point findDialogPosition(Session session, JSONObject positionData) {
        Point pos = new Point(0, 0);
        if (((InetSocketAddress) session.getRemoteAddress()).getAddress().isLoopbackAddress() && positionData != null
                && !positionData.isNull("x") && !positionData.isNull("y")) {
            pos.move(positionData.optInt("x"), positionData.optInt("y"));
        }

        return pos;
    }

    /**
     * Send JSON reply to web API for call {@code messageUID}
     *
     * @param session     WebSocket session
     * @param messageUID  ID of call from web API
     * @param returnValue Return value of method call, can be {@code null}
     */
    public static void sendResult(Session session, String messageUID, Object returnValue) {
        try {
            JSONObject reply = new JSONObject();
            reply.put("uid", messageUID);
            reply.put("result", returnValue);
            send(session, reply);
            log.debug("Sent result: {}", reply);
        } catch (JSONException | ClosedChannelException e) {
            log.error("Send result failed", e);
        }
    }

    /**
     * Send JSON error reply to web API for call {@code messageUID}
     *
     * @param session    WebSocket session
     * @param messageUID ID of call from web API
     * @param ex         Exception to get error message from
     */
    public static void sendError(Session session, String messageUID, Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isEmpty()) {
            message = ex.getClass().getSimpleName();
        }

        sendError(session, messageUID, message);
    }

    /**
     * Send JSON error reply to web API for call {@code messageUID}
     *
     * @param session    WebSocket session
     * @param messageUID ID of call from web API
     * @param errorMsg   Error from method call
     */
    public static void sendError(Session session, String messageUID, String errorMsg) {
        try {
            JSONObject reply = new JSONObject();
            reply.putOpt("uid", messageUID);
            reply.put("error", errorMsg);
            send(session, reply);
        } catch (JSONException | ClosedChannelException e) {
            log.error("Send error failed", e);
        }
    }

    /**
     * Send JSON data to web API, to be retrieved by callbacks. Used for data sent
     * apart from API calls, since UID's correspond to a single response.
     *
     * @param session WebSocket session
     * @param event   StreamEvent with data to send down to web API
     */
    public static void sendStream(Session session, StreamEvent event) throws ClosedChannelException {
        try {
            JSONObject stream = new JSONObject();
            stream.put("type", event.getStreamType());
            stream.put("event", event.toJSON());
            send(session, stream);
        } catch (JSONException e) {
            log.error("Send stream failed", e);
        }
    }

    public static void sendStream(Session session, StreamEvent event, DeviceListener listener) {
        try {
            sendStream(session, event);
        } catch (ClosedChannelException e) {
            log.error("Stream is closed, could not send message");
            if (listener != null) {
                listener.close();
            } else {
                log.error("Channel was closed before stream could be sent, but no close handler is configured.");
            }
        }
    }

    public static void sendStream(Session session, StreamEvent event, Runnable closeHandler) {
        try {
            sendStream(session, event);
        } catch (ClosedChannelException e) {
            log.error("Stream is closed, could not send message");
            if (closeHandler != null) {
                closeHandler.run();
            } else {
                log.error("Channel was closed before stream could be sent, but no close handler is configured.");
            }
        }
    
    }

    /**
     * Determines if the server detection dialog should be shown for a given website.
     * The dialog is shown if the server is not already registered.
     *
     * @param websiteName The name of the website requesting access
     * @return true if the dialog should be shown, false otherwise
     */
    private boolean shouldShowServerDetectionDialog(String websiteName) {
        if (websiteName == null || websiteName.isEmpty()) {
            return false;
        }
        
        try {
            // Use injected SubscriptionProvider
            String fingerprint = java.util.Base64.getEncoder().encodeToString(websiteName.getBytes());
            log.info("=== FINGERPRINT_VALIDATION === | Checking server registration for: {} -> fingerprint: {}", websiteName, fingerprint);
            
            // Check if server is already registered
            PrintValidationHook.ValidationResult result = printValidationHook.validateFingerprint("server", fingerprint);
            
            boolean shouldShow = !result.isValid();
            log.info("=== FINGERPRINT_VALIDATION === | Server {} registration check result: {} (shouldShow: {})",
                     websiteName, result.isValid() ? "REGISTERED" : "NOT_REGISTERED", shouldShow);
            
            // Show dialog only if server is not already registered
            return shouldShow;
        } catch (Exception e) {
            log.error("Error checking server registration status for {}: {}", websiteName, e.getMessage());
            // On error, show the dialog to be safe
            return true;
        }
    }


    /**
     * Raw send method for replies
     *
     * @param session WebSocket session
     * @param reply   JSON Object of reply to web API
     */
    private static synchronized void send(Session session, JSONObject reply)
            throws WebSocketException, ClosedChannelException {
        try {
            session.getRemote().sendString(reply.toString());
        } catch (IOException e) {
            if (e instanceof ClosedChannelException) {
                throw (ClosedChannelException) e;
            } else if (e.getCause() instanceof ClosedChannelException) {
                throw (ClosedChannelException) e.getCause();
            }
            log.error("Could not send message", e);
        }
    }

}
