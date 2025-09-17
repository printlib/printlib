/**
 * @author Robert Casto
 *
 * Copyright (C) 2016 Tres Finocchiaro, QZ Industries, LLC
 *
 * LGPL 2.1 This is free software.  This software and source code are released under
 * the "LGPL 2.1 License".  A copy of this license should be distributed with
 * this software. http://www.gnu.org/licenses/lgpl-2.1.html
 */

package qz.ws;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.eclipse.jetty.ee9.servlet.FilterHolder;
import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.ee9.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.DispatcherType;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import qz.utils.ArgValue;
import qz.utils.PrefsSearch;

/**
 * Abstract base WebSocket server for handling print operations.
 * This server provides the core functionality for WebSocket communication
 * between web applications and print applications. Subclasses provide
 * specific implementations for certificate management, UI integration,
 * and event handling.
 *
 * @author Robert Casto
 * @since 1.0.0
 */
@Singleton
@Log4j2
public abstract class PrintSocketServer {

    private static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    // Base dependencies - immutable for thread safety
    @NonNull private final WebsocketPorts websocketPorts;
    @NonNull private final PrintSocketClientEventHandler eventHandler;

    // Instance state
    private Server server;
    private boolean httpsOnly;
    private boolean sniStrict;
    private String wssHost;

    /**
     * Base constructor for PrintSocketServer.
     *
     * @param websocketPorts the websocket port configuration
     * @param eventHandler the WebSocket event handler
     */
    @Inject
    public PrintSocketServer(@NonNull WebsocketPorts websocketPorts,
                           @NonNull PrintSocketClientEventHandler eventHandler) {
        this.websocketPorts = websocketPorts;
        this.eventHandler = eventHandler;
        
        // Additional validation for complex parameters
        if (websocketPorts.getSecurePort() <= 0) {
            throw new IllegalArgumentException("WebsocketPorts must have valid secure port");
        }
        
        log.info("PrintSocketServer initialized with secure port: {}, insecure port: {}",
                 websocketPorts.getSecurePort(), websocketPorts.getInsecurePort());
    }    /**
     * Starts the WebSocket server with the specified configuration.
     *
     * @param headless whether to run in headless mode (no GUI)
     * @throws InterruptedException if the server startup is interrupted
     * @throws InvocationTargetException if there's an error during UI initialization
     */
    public void runServer(boolean headless) throws InterruptedException, InvocationTargetException {
        log.info("Starting PrintSocketServer.runServer with headless={}", headless);

        // Guard against multiple server starts
        if (running.get()) {
            log.warn("WebSocket server is already running, ignoring duplicate start request");
            return;
        }

        // Check if server instance already exists and is started
        if (server != null && server.isStarted()) {
            log.warn("WebSocket server instance already exists and is started, ignoring duplicate start request");
            return;
        }

        wssHost = PrefsSearch.getString(ArgValue.SECURITY_WSS_HOST, getSSLProperties());
        log.info("WebSocket server host configured from preferences: {}", wssHost);
        httpsOnly = PrefsSearch.getBoolean(ArgValue.SECURITY_WSS_HTTPSONLY, getSSLProperties());
        sniStrict = PrefsSearch.getBoolean(ArgValue.SECURITY_WSS_SNISTRICT, getSSLProperties());

        server = findAvailableSecurePort();

        Connector secureConnector = null;
        if (server.getConnectors().length > 0 && !server.getConnectors()[0].isFailed()) {
            secureConnector = server.getConnectors()[0];
        }

        if (httpsOnly && secureConnector == null) {
            log.error("Failed to start in https-only mode");
            return;
        }

        while(!running.get() && websocketPorts.insecureBoundsCheck()) {
            try {
                ServerConnector connector = new ServerConnector(server);
                connector.setPort(websocketPorts.getInsecurePort());
                connector.setHost(wssHost);
                log.info("=== INSECURE CONNECTOR SETUP ===");
                log.info("Insecure connector port set to: {}", websocketPorts.getInsecurePort());
                log.info("Insecure connector host set to: {}", wssHost);
                
                if(httpsOnly) {
                    log.info("HTTPS-only mode: Using only secure connector");
                    server.setConnectors(new Connector[] {secureConnector});
                } else if (secureConnector != null) {
                    //setup insecure connector before secure
                    log.info("Mixed mode: Using both insecure and secure connectors");
                    log.info("Connectors: insecure={}:{}, secure={}:{}",
                            wssHost, websocketPorts.getInsecurePort(),
                            wssHost, websocketPorts.getSecurePort());
                    server.setConnectors(new Connector[] {connector, secureConnector});
                } else {
                    log.info("Insecure-only mode: Using only insecure connector");
                    log.info("Insecure connector host set to: {}", wssHost);
                    server.setConnectors(new Connector[] {connector});
                }

                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/"); // Ensure context path is set
                
                // Add CORS filter to handle cross-origin WebSocket requests
                FilterHolder corsFilterHolder = new FilterHolder(new CorsFilter());
                context.addFilter(corsFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
                log.info("CORS filter added to handle cross-origin WebSocket requests");
                
                JettyWebSocketServletContainerInitializer.configure(context, (ctx, container) -> {
                    log.debug("Configuring WebSocket container - maxTextMessageSize: {}, idleTimeout: 5 minutes", MAX_MESSAGE_SIZE);
                    
                    container.addMapping("/", (req, resp) -> {
                        log.info("=== WEBSOCKET UPGRADE REQUEST ===");
                        log.info("WebSocket upgrade request received from: {} to: {}",
                                req.getRemoteSocketAddress(), req.getRequestURI());
                        log.info("Remote address details: {}", req.getRemoteSocketAddress());
                        log.info("Local address details: {}", req.getLocalSocketAddress());
                        log.info("WebSocket upgrade headers: {}", req.getHeaders());
                        
                        List<String> originHeaders = req.getHeaders().get("Origin");
                        List<String> hostHeaders = req.getHeaders().get("Host");
                        String origin = (originHeaders != null && !originHeaders.isEmpty()) ? originHeaders.get(0) : null;
                        String host = (hostHeaders != null && !hostHeaders.isEmpty()) ? hostHeaders.get(0) : null;
                        log.info("Request origin: {}", origin);
                        log.info("Request host: {}", host);
                        
                        // Allow cross-origin WebSocket connections by not rejecting any origins
                        // This is necessary because CORS doesn't apply to WebSocket connections
                        // and browsers block cross-origin WebSocket connections by default
                        log.info("Allowing WebSocket connection from origin: {} (cross-origin connections enabled)", origin);
                        
                        PrintSocketClient client = createPrintSocketClient(server);
                        log.info("Created new PrintSocketClient instance: {}", client);
                        
                        // Register the client with the event handler to receive hook updates
                        eventHandler.registerClient(client);
                        log.info("Registered PrintSocketClient with event handler for hook updates");
                        
                        return client;
                    });
                    
                    container.setMaxTextMessageSize(MAX_MESSAGE_SIZE);
                    container.setIdleTimeout(Duration.ofMinutes(5));
                    
                    log.debug("WebSocket container configuration completed");
                });

                // Handle HTTP landing page
                ServletHolder httpServlet = new ServletHolder();
                httpServlet.setServlet(createHttpAboutServlet());

                context.addServlet(httpServlet, "/");
                context.addServlet(httpServlet, "/json");

                server.setHandler(context);
                server.setStopAtShutdown(true);
                server.start();

                setupReloadThread();

                running.set(true);

                log.info("=== SERVER STARTUP COMPLETE ===");
                log.info("Server started on port(s) " + getPorts(server));
                log.info("HTTPS Only mode: {}", httpsOnly);
                log.info("Secure connector available: {}", secureConnector != null);
                websocketPorts.setHttpsOnly(httpsOnly);
                websocketPorts.setHttpOnly(secureConnector == null);
                
                // Log detailed connector information
                for (Connector conn : server.getConnectors()) {
                    if (conn instanceof ServerConnector) {
                        ServerConnector sc = (ServerConnector) conn;
                        log.info("Active connector: {}:{} (host: {}, port: {})",
                                sc.getHost() != null ? sc.getHost() : "ALL_INTERFACES",
                                sc.getPort(),
                                sc.getHost(),
                                sc.getPort());
                    }
                }
                // Call subclass hook for post-startup operations
                onServerStarted(headless);
                
                // Register a MessageProcessedListener to refresh the HomeDialog UI when socket messages are processed
                PrintSocketClient.addMessageProcessedListener(new MessageProcessedListener() {
                    @Override
                    public void onMessageProcessed(String messageType) {
                        // Update the UI on the Event Dispatch Thread
                        SwingUtilities.invokeLater(() -> {
                            // Subclasses can override onServerStarted to handle UI updates
                            log.debug("Message processed: {}", messageType);
                        });
                    }
                });
                log.info("Registered HomeDialog refresh listener with PrintSocketClient");
                
                // Start server.join() in a separate thread to avoid blocking the main startup sequence
                // This allows the main thread to continue to performAsyncSetup() in App.main()
                Thread serverJoinThread = new Thread(() -> {
                    try {
                        log.info("Server join thread started - waiting for server to stop");
                        server.join();
                        log.info("Server has stopped");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.info("Server join thread was interrupted");
                    } catch (Exception e) {
                        log.error("Error in server join thread", e);
                    }
                });
                serverJoinThread.setName("ServerJoinThread");
                serverJoinThread.setDaemon(true); // Don't prevent JVM shutdown
                serverJoinThread.start();
                log.info("Server join thread started - main thread can continue");
            }
            catch(IOException e) {
                //order of getConnectors is the order we added them -> insecure first
                if (server.isFailed()) {
                    websocketPorts.nextInsecureIndex();
                }

                //explicitly stop the server, because if only 1 port has an exception the other will still be opened
                try { server.stop(); }catch(Exception stopEx) { stopEx.printStackTrace(); }
            }
            catch(Exception e) {
                e.printStackTrace();
                onServerError(e);
                break;
            }
        }
    }

    /**
     * Finds and configures an available secure port for the WebSocket server.
     *
     * @return configured Server instance with secure connector
     */
    private Server findAvailableSecurePort() {
        Server server = new Server();

        try {
            log.info("=== SECURE PORT CONFIGURATION START ===");
            log.info("SNI Strict mode: {}", sniStrict);
            log.info("WSS Host: {}", wssHost);
            log.info("Target secure port: {}", websocketPorts.getSecurePort());
            
            final AtomicBoolean runningSecure = new AtomicBoolean(false);
            while(!runningSecure.get() && websocketPorts.secureBoundsCheck()) {
                try {
                    log.info("=== ATTEMPTING SECURE CONNECTOR SETUP ===");
                    log.info("Attempting secure port: {}", websocketPorts.getSecurePort());
                    
                    // Configure SSL Context Factory via subclass hook
                    log.info("Calling createSslContextFactory()...");
                    SslContextFactory.Server sslContextFactory = createSslContextFactory();
                    log.info("SSL Context Factory configured successfully");
                    
                    // Log SSL Context Factory details
                    try {
                        log.info("SSL Context Factory KeyStore: {}", sslContextFactory.getKeyStore() != null ? "Available" : "NULL");
                        log.info("SSL Context Factory TrustAll: {}", sslContextFactory.isTrustAll());
                        log.info("SSL Context Factory ValidateCerts: {}", sslContextFactory.isValidateCerts());
                        log.info("SSL Context Factory ValidatePeerCerts: {}", sslContextFactory.isValidatePeerCerts());
                        log.info("SSL Context Factory EndpointIdentificationAlgorithm: {}", sslContextFactory.getEndpointIdentificationAlgorithm());
                    } catch (Exception e) {
                        log.warn("Could not log SSL Context Factory details: {}", e.getMessage());
                    }
                    
                    // Create SSL Connection Factory
                    log.info("Creating SslConnectionFactory with HTTP/1.1...");
                    SslConnectionFactory sslConnection = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
                    log.info("SslConnectionFactory created successfully");

                    // Disable SNI checks for easier print-server testing (replicates Jetty 9.x behavior)
                    log.info("Configuring HTTPS configuration...");
                    HttpConfiguration httpsConfig = new HttpConfiguration();
                    SecureRequestCustomizer customizer = new SecureRequestCustomizer();
                    customizer.setSniHostCheck(sniStrict);
                    log.info("SNI Host Check set to: {}", sniStrict);
                    httpsConfig.addCustomizer(customizer);
                    log.info("HTTPS configuration completed");

                    log.info("Creating HTTP Connection Factory...");
                    HttpConnectionFactory httpConnection = new HttpConnectionFactory(httpsConfig);
                    log.info("HTTP Connection Factory created successfully");

                    log.info("Creating ServerConnector with SSL and HTTP factories...");
                    ServerConnector secureConnector = new ServerConnector(server, sslConnection, httpConnection);
                    log.info("ServerConnector created successfully");
                    
                    secureConnector.setHost(wssHost);
                    secureConnector.setPort(websocketPorts.getSecurePort());
                    log.info("=== SECURE CONNECTOR SETUP ===");
                    log.info("Secure connector host set to: {}", wssHost);
                    log.info("Secure connector port set to: {}", websocketPorts.getSecurePort());
                    
                    log.info("Setting connectors on server...");
                    server.setConnectors(new Connector[] {secureConnector});
                    log.info("Connectors set successfully");

                    log.info("Starting server to test secure port availability...");
                    server.start();
                    log.info("Server started successfully on secure port {}", websocketPorts.getSecurePort());
                    log.trace("Established secure WebSocket on port {}", websocketPorts.getSecurePort());

                    // Log server status after start
                    log.info("Server state after start: {}", server.getState());
                    log.info("Server is started: {}", server.isStarted());
                    log.info("Server is running: {}", server.isRunning());
                    log.info("Server is failed: {}", server.isFailed());
                    
                    // Log connector status
                    for (Connector conn : server.getConnectors()) {
                        if (conn instanceof ServerConnector) {
                            ServerConnector sc = (ServerConnector) conn;
                            log.info("Connector state: {} (host: {}, port: {}, failed: {})",
                                    sc.getState(), sc.getHost(), sc.getPort(), sc.isFailed());
                        }
                    }

                    //only starting to test port availability; insecure port will actually start
                    log.info("Stopping server after successful port test...");
                    server.stop();
                    log.info("Server stopped successfully");
                    runningSecure.set(true);
                    log.info("Secure port configuration completed successfully");
                }
                catch(IOException e) {
                    log.error("=== SECURE CONNECTOR SETUP FAILED - IOException ===");
                    log.error("IOException during secure connector setup: {}", e.getMessage(), e);
                    log.error("Server failed state: {}", server.isFailed());
                    log.error("Current secure port: {}", websocketPorts.getSecurePort());
                    
                    if (server.isFailed()) {
                        log.info("Moving to next secure port index due to server failure");
                        websocketPorts.nextSecureIndex();
                        log.info("Next secure port to try: {}", websocketPorts.getSecurePort());
                    }

                    try {
                        log.info("Stopping failed server...");
                        server.stop();
                        log.info("Failed server stopped successfully");
                    } catch(Exception stopEx) {
                        log.error("Error stopping failed server: {}", stopEx.getMessage(), stopEx);
                        stopEx.printStackTrace();
                    }
                }
                catch(Exception e) {
                    log.error("=== SECURE CONNECTOR SETUP FAILED - General Exception ===");
                    log.error("General exception during secure connector setup: {}", e.getMessage(), e);
                    log.error("Exception type: {}", e.getClass().getSimpleName());
                    log.error("Server state: {}", server.getState());
                    log.error("Server failed: {}", server.isFailed());
                    log.error("Current secure port: {}", websocketPorts.getSecurePort());
                    
                    e.printStackTrace();
                    onServerError(e);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error in SSL configuration", e);
            onServerError(e);
        }

        log.info("=== SECURE PORT CONFIGURATION FINAL CHECK ===");
        log.info("Number of connectors: {}", server.getConnectors().length);
        
        if (server.getConnectors().length == 0) {
            log.error("CRITICAL: No connectors found on server - secure WebSocket setup completely failed");
            log.error("This means SSL certificate configuration failed entirely");
        } else {
            Connector firstConnector = server.getConnectors()[0];
            log.info("First connector failed: {}", firstConnector.isFailed());
            
            if (firstConnector.isFailed()) {
                log.error("CRITICAL: First connector failed - secure WebSocket setup failed");
                log.error("This indicates SSL certificate or port binding issues");
                
                // Try to get failure reason
                if (firstConnector instanceof ServerConnector) {
                    ServerConnector sc = (ServerConnector) firstConnector;
                    log.error("Failed connector details - Host: {}, Port: {}", sc.getHost(), sc.getPort());
                }
            } else {
                log.info("SUCCESS: Secure connector setup completed successfully");
            }
        }
        
        if (server.getConnectors().length == 0 || server.getConnectors()[0].isFailed()) {
            log.warn("Could not start secure WebSocket - SSL certificate configuration failed");
            log.warn("Check certificate generation, keystore creation, and file permissions");
        } else {
            log.info("Secure WebSocket configuration completed successfully");
        }

        log.info("=== SECURE PORT CONFIGURATION END ===");
        return server;
    }

    /**
     * Gets the current server instance.
     *
     * @return the current Server instance, or null if not started
     */
    public Server getServer() {
        return server;
    }

    /**
     * Gets the running state of the server.
     *
     * @return AtomicBoolean indicating if the server is running
     */
    public static AtomicBoolean getRunning() {
        return running;
    }



    /**
     * Gets the WebsocketPorts configuration.
     *
     * @return the WebsocketPorts instance
     */
    public WebsocketPorts getWebsocketPorts() {
        return websocketPorts;
    }

    @Deprecated
    public static void main(String ... args) {
        // This method is deprecated - use the main application entry point instead
        System.err.println("PrintSocketServer.main() is deprecated. Use the main application entry point.");
        System.exit(1);
    }

    /**
     * Returns a String representation of the ports assigned to the specified Server
     */
    public String getPorts(Server server) {
        StringBuilder ports = new StringBuilder();
        for(Connector c : server.getConnectors()) {
            if (ports.length() > 0) {
                ports.append(", ");
            }

            ports.append(((ServerConnector)c).getLocalPort());
        }

        return ports.toString();
    }

    // Abstract methods for subclass implementation
    
    /**
     * Gets configuration properties for SSL setup.
     * Subclasses provide their specific property source.
     */
    protected abstract java.util.Properties getSSLProperties();
    
    /**
     * Creates an SSL context factory for secure connections.
     * Subclasses provide their certificate management implementation.
     */
    protected abstract SslContextFactory.Server createSslContextFactory();
    
    /**
     * Creates a PrintSocketClient instance.
     * Subclasses provide their specific client implementation with dependencies.
     */
    protected abstract PrintSocketClient createPrintSocketClient(Server server);
    
    /**
     * Creates an HTTP servlet for the about page.
     * Subclasses provide their specific servlet implementation.
     */
    protected abstract HttpAboutServlet createHttpAboutServlet();
    
    /**
     * Hook for setting up reload functionality.
     * Subclasses can implement their specific reload behavior.
     */
    protected void setupReloadThread() {
        // Default implementation: no reload thread
    }
    
    /**
     * Hook for post-server-start operations.
     * Subclasses can implement their specific post-start behavior.
     */
    protected void onServerStarted(boolean headless) {
        // Default implementation: no post-start operations
    }
    
    /**
     * Hook for handling server startup errors.
     * Subclasses can implement their specific error handling.
     */
    protected void onServerError(Exception e) {
        log.error("Server error: {}", e.getLocalizedMessage(), e);
    }

}
