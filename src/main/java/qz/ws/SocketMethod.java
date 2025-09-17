package qz.ws;

public enum SocketMethod {
    PRINTERS_FIND("printers.find", false, true, false, "access connected printers"),
    PRINTERS_START_LISTENING("printers.startListening", false, true, false, "listen for printer status"),
    PRINTERS_GET_STATUS("printers.getStatus", false, true, false),
    PRINTERS_STOP_LISTENING("printers.stopListening", false, true, false),
    PRINT("print", true, true, false, "print to %s"),

    // Fingerprint management methods
    FINGERPRINT_REGISTER_DEVICE("fingerprint.registerDevice", false, true, false, "register a device fingerprint"),
    FINGERPRINT_UNREGISTER_DEVICE("fingerprint.unregisterDevice", false, true, false, "unregister a device fingerprint"),
    FINGERPRINT_REGISTER_PRINTER("fingerprint.registerPrinter", false, true, false, "register a printer fingerprint"),
    FINGERPRINT_UNREGISTER_PRINTER("fingerprint.unregisterPrinter", false, true, false, "unregister a printer fingerprint"),

    GET_VERSION("getVersion", false, false, false),

    INVALID("", false, false, false);


    private String callName;
    private String promptMessage;
    private boolean fingerPrintRequired;
    private boolean requiresAuth;
    private boolean triggersProfileRefresh;

    SocketMethod(String callName, boolean fingerPrintRequired) {
        this(callName, fingerPrintRequired, true, false, "access local resources");
    }

    SocketMethod(String callName, boolean fingerPrintRequired, String promptMessage) {
        this(callName, fingerPrintRequired, true, false, promptMessage);
    }

    SocketMethod(String callName, boolean fingerPrintRequired, boolean requiresAuth) {
        this(callName, fingerPrintRequired, requiresAuth, false, "access local resources");
    }

    SocketMethod(String callName, boolean fingerPrintRequired, boolean requiresAuth, String promptMessage) {
        this(callName, fingerPrintRequired, requiresAuth, false, promptMessage);
    }

    SocketMethod(String callName, boolean fingerPrintRequired, boolean requiresAuth, boolean triggersProfileRefresh) {
        this(callName, fingerPrintRequired, requiresAuth, triggersProfileRefresh, "access local resources");
    }

    SocketMethod(String callName, boolean fingerPrintRequired, boolean requiresAuth, boolean triggersProfileRefresh, String promptMessage) {
        this.callName = callName;
        this.fingerPrintRequired = fingerPrintRequired;
        this.requiresAuth = requiresAuth;
        this.triggersProfileRefresh = triggersProfileRefresh;
        this.promptMessage = promptMessage;
    }

    public boolean isFingerPrintRequired() {
        return fingerPrintRequired;
    }

    /**
     * Checks if this method requires authentication for license validation.
     * This determines whether profile loading should be triggered.
     *
     * @return true if authentication is required, false otherwise
     */
    public boolean requiresAuth() {
        return requiresAuth;
    }

    /**
     * Checks if this method should trigger a profile refresh.
     * Profile refresh should only happen for actual authentication events,
     * not for component registration or other WebSocket operations.
     *
     * @return true if profile refresh should be triggered, false otherwise
     */
    public boolean triggersProfileRefresh() {
        return triggersProfileRefresh;
    }

    /**
     * Checks if this method requires authentication.
     * Currently, all methods that require fingerprints also require authentication.
     *
     * @return true if authentication is required, false otherwise
     * @deprecated Use requiresAuth() instead for clearer semantics
     */
    @Deprecated
    public boolean requiresAuthentication() {
        return fingerPrintRequired;
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    public static SocketMethod findFromCall(String call) {
        for(SocketMethod m : SocketMethod.values()) {
            if (m.callName.equals(call)) {
                return m;
            }
        }

        return INVALID;
    }

    public String getCallName() {
        return callName;
    }

}
