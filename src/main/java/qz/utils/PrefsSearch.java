package qz.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Convenience class for searching for preferences on a user, app and <code>System.getProperty(...)</code> level
 */
public class PrefsSearch {
    private static final Logger log = LogManager.getLogger(PrefsSearch.class);
    private static Properties appProps = null;
    private static PropertiesProvider propertiesProvider;
    
    /**
     * Set the properties provider for loading application properties.
     * This should be called during application initialization.
     */
    public static void setPropertiesProvider(PropertiesProvider provider) {
        propertiesProvider = provider;
        // Reset cached properties so they will be reloaded with new provider
        appProps = null;
    }

    private static String getProperty(String[] names, String defaultVal, boolean searchSystemProperties, Properties ... propArray) {
        String returnVal;

        // If none are provided, ensure we have some types of properties to iterate over
        if(propArray.length == 0) {
            if(appProps == null) {
                try {
                    if (propertiesProvider != null) {
                        appProps = propertiesProvider.loadApplicationProperties();
                    } else {
                        log.warn("No properties provider configured, using defaults");
                        appProps = createDefaultProperties();
                    }
                } catch (Exception e) {
                    log.warn("Could not load properties file, using defaults: {}", e.getMessage());
                    appProps = createDefaultProperties();
                }
            }
            propArray = new Properties[]{ appProps };
        }

        for(String n : names) {
            // First, honor System property
            if (searchSystemProperties && (returnVal = System.getProperty(n)) != null) {
                log.info("Picked up system property {}={}", n, returnVal);
                return returnVal;
            }

            for(Properties props : propArray) {
                // Second, honor properties file(s)
                if (props != null) {
                    if ((returnVal = props.getProperty(n)) != null) {
                        log.info("Picked up property {}={}", n, returnVal);
                        return returnVal;
                    }
                }
            }
        }

        // Last, return default property
        return defaultVal;
    }

    /*
     * Typed String[] helper implementations
     */
    private static int getInt(String[] names, int defaultVal, boolean searchSystemProperties, Properties ... propsArray) {
        try {
            return Integer.parseInt(getProperty(names, "", searchSystemProperties, propsArray));
        } catch(NumberFormatException ignore) {}
        return defaultVal;
    }

    private static boolean getBoolean(String[] names, boolean defaultVal, boolean searchSystemProperties, Properties ... propsArray) {
        return Boolean.parseBoolean(getProperty(names, "" + defaultVal, searchSystemProperties, propsArray));
    }

    /*
     * Typed ArgValue implementations
     */
    public static String getString(ArgValue argValue, boolean searchSystemProperties, Properties ... propsArray) {
        return getProperty(argValue.getMatches(), (String)argValue.getDefaultVal(), searchSystemProperties, propsArray);
    }

    public static int getInt(ArgValue argValue, boolean searchSystemProperties, Properties ... propsArray) {
        return getInt(argValue.getMatches(), (Integer)argValue.getDefaultVal(), searchSystemProperties, propsArray);
    }

    public static boolean getBoolean(ArgValue argValue, boolean searchSystemProperties, Properties ... propsArray) {
        return getBoolean(argValue.getMatches(), (Boolean)argValue.getDefaultVal(), searchSystemProperties, propsArray);
    }

    /*
     * Typed ArgValue implementations (searchSystemProperties = true)
     */
    public static String getString(ArgValue argValue, Properties ... propsArray) {
        return getString(argValue, true, propsArray);
    }

    public static int getInt(ArgValue argValue, Properties ... propsArray) {
        return getInt(argValue, true, propsArray);
    }

    public static List<Integer> getIntegerArray(ArgValue argValue, Properties ... propsArray) {
        return parseIntegerArray(getString(argValue, propsArray));
    }

    public static List<Integer> parseIntegerArray(String commaSeparated) {
        List<Integer> parsed = new ArrayList<>();
        try {
            if (commaSeparated != null && !commaSeparated.isEmpty()) {
                String[] split = commaSeparated.split(",");
                for(String item : split) {
                    parsed.add(Integer.parseInt(item));
                }
            }
        } catch(NumberFormatException nfe) {
            log.warn("Failed parsing {} as a valid integer array", commaSeparated, nfe);
        }
        return parsed;
    }

    public static boolean getBoolean(ArgValue argValue, Properties ... propsArray) {
        return getBoolean(argValue, true, propsArray);
    }

    /**
     * Creates a default Properties object populated with ArgValue default values
     * Used as fallback when properties file cannot be loaded
     */
    private static Properties createDefaultProperties() {
        Properties props = new Properties();
        
        // Populate with ArgValue defaults for all properties that have default values
        for (ArgValue argValue : ArgValue.values()) {
            if (argValue.getDefaultVal() != null && argValue.getMatches() != null && argValue.getMatches().length > 0) {
                // Use the first match as the property key
                String key = argValue.getMatches()[0];
                String value = argValue.getDefaultVal().toString();
                props.setProperty(key, value);
                log.debug("Set default property {}={}", key, value);
            }
        }
        
        log.info("Created default properties with {} entries", props.size());
        return props;
    }
}
