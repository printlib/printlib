package qz.utils;

import java.util.Properties;

/**
 * Provider interface for loading application properties.
 * This interface allows PrefsSearch to load properties without direct certificate dependencies.
 */
public interface PropertiesProvider {
    
    /**
     * Load application properties, typically from configuration files secured with certificates.
     * 
     * @return Properties object containing application configuration
     * @throws Exception if properties cannot be loaded
     */
    Properties loadApplicationProperties() throws Exception;
}