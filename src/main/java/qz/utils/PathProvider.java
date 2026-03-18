package qz.utils;

/**
 * Provider interface for system paths and installation locations.
 * This interface allows SystemUtilities to access paths without direct installer dependencies.
 */
public interface PathProvider {
    
    /**
     * Get the installation destination path.
     * 
     * @return Path to the installation directory
     */
    String getInstallationDestination();
}