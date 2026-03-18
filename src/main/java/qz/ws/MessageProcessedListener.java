package qz.ws;

/**
 * Interface for listeners that want to be notified when a WebSocket message is processed.
 * This is used to update the UI when relevant changes occur in the system.
 */
public interface MessageProcessedListener {
    
    /**
     * Called when a WebSocket message has been processed.
     * 
     * @param messageType The type of message that was processed
     */
    void onMessageProcessed(String messageType);
}