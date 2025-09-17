package qz.ws;

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
}