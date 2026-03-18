package qz.utils;

/**
 * Provider interface for system folder paths.
 * This interface allows FileUtilities to access system folders without direct Windows-specific dependencies.
 */
public interface SystemFoldersProvider {
    
    /**
     * Get the desktop folder path.
     * 
     * @return Path to the desktop folder
     */
    String getDesktopPath();
    
    /**
     * Get the user application data (roaming) folder path.
     * 
     * @return Path to the roaming appdata folder
     */
    String getRoamingAppDataPath();
    
    /**
     * Get the system-wide program data folder path.
     * 
     * @return Path to the program data folder
     */
    String getProgramDataPath();
    
    /**
     * Get a writable location for configuration files and certificates.
     * 
     * @return Path to a writable location
     */
    String getWritableLocation();
}