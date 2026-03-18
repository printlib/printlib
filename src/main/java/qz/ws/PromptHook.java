package qz.ws;

import java.awt.Point;

/**
 * Provider interface for prompt services.
 */
public interface PromptHook {

    /**
     * Prompt the user with a server detection dialog
     * 
     * @param serverName The name of the server requesting access
     * @param position The position where the dialog should appear
     * @return true if the user allows the connection, false otherwise
     */
    boolean prompt(String serverName, Point position);
}