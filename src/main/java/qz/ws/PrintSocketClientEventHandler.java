package qz.ws;

import jakarta.inject.Singleton;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event handler that manages hook info updates for PrintSocketClient instances.
 * Since PrintSocketClient instances are manually created for WebSocket connections,
 * this service acts as a bridge to propagate events to those instances.
 */
@Singleton
@Log4j2
public class PrintSocketClientEventHandler {
    
    private final CopyOnWriteArrayList<PrintValidationHook> printValidationHooks = new CopyOnWriteArrayList<>();
    private volatile Object latestSubscriptionData;
    
    /**
     * Register a SubscriptionProvider to receive hook info updates.
     */
    public void registerSubscriptionProvider(PrintValidationHook provider) {
        printValidationHooks.add(provider);
        log.debug("Registered SubscriptionProvider for hook updates. Total providers: {}", printValidationHooks.size());
        
        // If we already have hook data, immediately update the new provider
        // This handles the case where a provider connects after hook data is already loaded
        if (latestSubscriptionData != null) {
            log.info("Immediately updating new provider with latest hook data");
            try {
                provider.onHookUpdated(latestSubscriptionData);
            } catch (Exception e) {
                log.error("Error updating hook data for newly registered provider: {}", provider, e);
            }
        } else {
            log.debug("No hook data available yet for new provider");
        }
    }
    
    /**
     * Unregister a SubscriptionProvider.
     */
    public void unregisterSubscriptionProvider(PrintValidationHook provider) {
        printValidationHooks.remove(provider);
        log.debug("Unregistered SubscriptionProvider. Total providers: {}", printValidationHooks.size());
    }
    
    /**
     * Handle hook info updates and propagate to all registered providers.
     */
    public void onSubscriptionDataUpdated(Object hookData) {
        this.latestSubscriptionData = hookData;
        log.info("Received hook data update - propagating to {} providers",
                printValidationHooks.size());
        
        for (PrintValidationHook provider : printValidationHooks) {
            try {
                provider.onHookUpdated(hookData);
            } catch (Exception e) {
                log.error("Error updating hook data for provider: {}", provider, e);
            }
        }
    }
    
    // Backward compatibility methods for PrintSocketClient usage
    // These will be removed when PrintSocketClient is refactored
    
    /**
     * @deprecated Use registerSubscriptionProvider instead
     */
    @Deprecated
    public void registerClient(Object client) {
        // For now, don't register anything since PrintSocketClient isn't refactored yet
        log.debug("Legacy registerClient called - PrintSocketClient refactoring pending");
    }
    
    /**
     * @deprecated Use unregisterSubscriptionProvider instead  
     */
    @Deprecated
    public void unregisterClient(Object client) {
        // For now, don't unregister anything since PrintSocketClient isn't refactored yet
        log.debug("Legacy unregisterClient called - PrintSocketClient refactoring pending");
    }
}