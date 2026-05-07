package ir.bita.esb.logging;

/**
 * Interface for log destinations.
 */
public interface LogSender {
    
    /**
     * Send a log entry to the destination.
     */
    void send(LogEntry entry);
    
    /**
     * Close the sender and release resources.
     */
    default void close() {}
}
