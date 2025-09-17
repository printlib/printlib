package qz.utils;

import java.nio.file.Path;

/**
 * Provider interface for provision-related functionality.
 * This interface allows FileUtilities to check provision requirements without direct dependencies.
 */
public interface ProvisionProvider {
    
    /**
     * Check if a file path should be marked as executable for provision operations.
     * 
     * @param path the file path to check
     * @return true if the file should be executable, false otherwise
     */
    boolean shouldBeExecutable(Path path);
}