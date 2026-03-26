package qz.ws;

import java.time.Instant;
import java.util.List;

/**
 * Provider interface for hook information for validation services.
 */
public interface PrintValidationHook {

    /**
     * Inner classes to represent complex types used by hook operations
     */
    public static class FingerprintPair {
        public final String deviceFingerprint;
        public final String printerFingerprint;
        
        public FingerprintPair(String deviceFingerprint, String printerFingerprint) {
            this.deviceFingerprint = deviceFingerprint;
            this.printerFingerprint = printerFingerprint;
        }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Represents detailed info about a registered component, used to enrich
     * WebSocket responses (e.g., printers.find).
     */
    public static class RegisteredComponent {
        private final String fingerprint;
        private final String name;
        private final String type;
        private final String createdDate;

        public RegisteredComponent(String fingerprint, String name, String type, String createdDate) {
            this.fingerprint = fingerprint;
            this.name = name;
            this.type = type;
            this.createdDate = createdDate;
        }

        public String getFingerprint() { return fingerprint; }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getCreatedDate() { return createdDate; }
    }

    /**
     * Called when hook information is updated.
     * Implementors should handle the hook update logic specific to their domain.
     * 
     * @param hook the hook data (implementation-specific type)
     */
    void onHookUpdated(Object hook);

    /**
     * Extract fingerprints from JSON data object
     */
    FingerprintPair extractFingerprints(Object dataObj);

    /**
     * Validate multiple fingerprints at once
     */
    ValidationResult validateFingerprints(FingerprintPair fingerprints);

    /**
     * Validate a single fingerprint for a specific type
     */
    ValidationResult validateFingerprint(String type, String fingerprint);

    /**
     * Register a fingerprint for a specific type with optional name
     */
    boolean registerFingerprint(String type, String fingerprint, String name);

    /**
     * Unregister a fingerprint for a specific type
     */
    boolean unregisterFingerprint(String type, String fingerprint);

    /**
     * Get current device count
     */
    int getCurrentDeviceCount();

    /**
     * Get current printer count
     */
    int getCurrentPrinterCount();

    /**
     * Get current server count
     */
    int getCurrentServerCount();

    /**
     * Get device limit
     */
    int getDeviceLimit();

    /**
     * Get printer limit
     */
    int getPrinterLimit();

    /**
     * Get server limit
     */
    int getServerLimit();

    /**
     * Get all registered components of a given type (device, printer, server).
     * Used to enrich WebSocket responses with registration metadata.
     *
     * @param type component type: "device", "printer", or "server"
     * @return list of registered components, never null
     */
    List<RegisteredComponent> getRegisteredComponents(String type);

    /**
     * Check whether a device has any child printers registered.
     * A printer belongs to a device when its fingerprint starts with "{deviceFingerprint}/".
     *
     * @param deviceFingerprint the device fingerprint to check
     * @return true if at least one printer is registered under this device
     */
    boolean deviceHasPrinters(String deviceFingerprint);

    /**
     * Check whether a device fingerprint is registered.
     *
     * @param deviceFingerprint the device fingerprint to check
     * @return true if the device is registered
     */
    boolean isDeviceRegistered(String deviceFingerprint);
}