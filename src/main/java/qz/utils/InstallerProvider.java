package qz.utils;

import java.util.List;

/**
 * Provider interface for installer-related functionality used by ArgParser.
 * This interface allows ArgParser to work with installer managers without direct dependencies.
 */
public interface InstallerProvider {
    
    /**
     * Perform preinstall operations
     * @return true if preinstall was successful, false otherwise
     */
    boolean preinstall();
    
    /**
     * Install the application to the specified destination
     * @param dest destination directory path (null for default)
     * @param silent whether to perform a silent installation
     * @throws Exception if installation fails
     */
    void install(String dest, boolean silent) throws Exception;
    
    /**
     * Generate SSL certificates
     * @param overwrite whether to overwrite existing certificates
     * @throws Exception if certificate generation fails
     */
    void certGen(boolean overwrite) throws Exception;
    
    /**
     * Generate SSL certificates for specific hosts
     * @param overwrite whether to overwrite existing certificates
     * @param hosts array of hostnames to generate certificates for
     * @throws Exception if certificate generation fails
     */
    void certGen(boolean overwrite, String[] hosts) throws Exception;
    
    /**
     * Uninstall the application
     * @throws Exception if uninstallation fails
     */
    void uninstall() throws Exception;
    
    /**
     * Perform enhanced uninstallation with command line arguments
     * @param args command line arguments (e.g., --silent, --force)
     * @throws Exception if enhanced uninstallation fails
     */
    void enhancedUninstall(String[] args) throws Exception;
    
    /**
     * Spawn a new process with the given arguments
     * @param args command line arguments to spawn
     * @throws Exception if spawning fails
     */
    void spawn(List<String> args) throws Exception;
}