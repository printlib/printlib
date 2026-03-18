package qz.utils;

/**
 * Utility class for retrieving version information.
 */
public class VersionUtilities {
    
    /**
     * Gets the version text from multiple sources to ensure we display the correct version.
     *
     * @return The version string to display
     */
    public static String getVersionText() {
        // First try to read from version.properties resource
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.InputStream is = VersionUtilities.class.getResourceAsStream("/version.properties");
            if (is != null) {
                props.load(is);
                is.close();
                String version = props.getProperty("version");
                if (version != null && !version.isEmpty()) {
                    System.out.println("VersionUtilities: U Print v " + version);
                    return version;
                }
            }
        } catch (Exception e) {
            System.err.println("VersionUtilities: Error reading version from properties: " + e.getMessage());
        }
        
        // Try to read from version.txt resource
        try {
            java.io.InputStream is = VersionUtilities.class.getResourceAsStream("/version.txt");
            if (is != null) {
                java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                if (scanner.hasNext()) {
                    String version = scanner.next().trim();
                    System.out.println("VersionUtilities: Version from text file: " + version);
                    scanner.close();
                    is.close();
                    return version;
                }
                scanner.close();
                is.close();
            }
        } catch (Exception e) {
            System.err.println("VersionUtilities: Error reading version from text file: " + e.getMessage());
        }
        
        // Fallback to Constants.VERSION
        return qz.common.Constants.VERSION.toString();
    }
}