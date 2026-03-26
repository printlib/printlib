package qz.ws;

/**
 * Listener interface for receiving print job metrics (success/failure) with printer identity.
 * 
 * <p>Unlike {@link MessageProcessedListener} which only provides the message type,
 * this listener provides the printer fingerprint and success/failure status for
 * per-printer metrics tracking.
 * 
 * <p>Implementations should be lightweight — the callback is invoked on the WebSocket
 * processing thread, so heavy work should be offloaded asynchronously.
 */
public interface PrintMetricsListener {
    
    /**
     * Called after a print job completes (success or failure).
     *
     * @param printerName  The printer name from the print request params
     * @param success      {@code true} if printing succeeded, {@code false} on error or cancellation
     */
    void onPrintResult(String printerName, boolean success);
}
