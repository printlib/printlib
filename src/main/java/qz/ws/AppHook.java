package qz.ws;

/**
 * Provider interface for app features.
 */
public interface AppHook {

    /**
     * Display an informational message in the system tray
     */
    void displayInfoMessage(String message);

    /**
     * Display an error message in the system tray
     */
    void displayErrorMessage(String message);

    /**
     * Check if the user is currently logged in
     */
    boolean isLoggedIn();

    /**
     * Void any idle actions in progress
     */
    void voidIdleActions();

    /**
     * Get the server detection dialog provider
     */
    PromptHook getPromptHook();

    /**
     * Check if Monocle platform is preferred for headless environments
     */
    default boolean isMonoclePreferred() {
        return true; // Default to true for headless compatibility
    }

    /**
     * Check if the environment is running in headless mode
     */
    default boolean isHeadless() {
        return java.awt.GraphicsEnvironment.isHeadless();
    }
}