/*
 * Copyright (C) 2025 printlib
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package qz.common;

import com.github.zafarkhaja.semver.Version;
import qz.utils.SystemUtilities;

import java.awt.*;

/**
 * Created by robert on 7/9/2014.
 */
public class Constants {
    public static final String PROPS_FILE = "qz"; // .properties extension is assumed
    public static final String DATA_DIR = "data";
    public static final String ABOUT_TITLE = "PrintLib";
    public static final String ALLOW_FILE = "allowed"; 
    public static final String BLOCK_FILE = "blocked";
    public static final String STEAL_WEBSOCKET_PROPERTY = "websocket.steal";
    public static final String HEXES = "0123456789ABCDEF";
    public static final char[] HEXES_ARRAY = HEXES.toCharArray();
    public static final int BYTE_BUFFER_SIZE = 8192;
    // Read version from manifest or properties file, with fallback to hardcoded value
    public static final Version VERSION = getVersionFromBuild();
    
    /**
     * Gets the version from the build system.
     * This method attempts to read the version from multiple sources:
     * 1. From the MANIFEST.MF file (when running from a packaged JAR)
     * 2. From a generated properties file (when running from an IDE)
     * 3. From a version.txt file (simpler format)
     * 4. Falls back to a hardcoded value if none of the above are available
     *
     * @return The version as a Version object
     */
    private static Version getVersionFromBuild() {
        String versionStr = null;
        
        // Try to get version from manifest (when packaged as JAR)
        try {
            Package pkg = Constants.class.getPackage();
            if (pkg != null) {
                String implVersion = pkg.getImplementationVersion();
                if (implVersion != null && !implVersion.isEmpty()) {
                    versionStr = implVersion;
                    System.out.println("Version from manifest: " + versionStr);
                }
            }
        } catch (Exception e) {
            System.err.println("U Print version error : " + e.getMessage());
        }
        
        // Try to get version from a properties file (useful when running from IDE)
        if (versionStr == null) {
            try {
                java.util.Properties props = new java.util.Properties();
                java.io.InputStream is = Constants.class.getResourceAsStream("/version.properties");
                if (is != null) {
                    props.load(is);
                    is.close();
                    versionStr = props.getProperty("version");
                    if (versionStr != null && !versionStr.isEmpty()) {
                        System.out.println("U Print v" + versionStr);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error reading version: " + e.getMessage());
            }
        }
        
        // Try to get version from a simple text file
        if (versionStr == null) {
            try {
                java.io.InputStream is = Constants.class.getResourceAsStream("/version.txt");
                if (is != null) {
                    java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                    if (scanner.hasNext()) {
                        versionStr = scanner.next().trim();
                        System.out.println("Version from text file: " + versionStr);
                    }
                    scanner.close();
                    is.close();
                }
            } catch (Exception e) {
                System.err.println("U Print version error : " + e.getMessage());
            }
        }
        
        // Fallback to hardcoded value
        if (versionStr == null || versionStr.isEmpty()) {
            versionStr = "1.0.225";
            System.out.println("Using fallback version: " + versionStr);
        }
        
        try {
            // Fix version string to be semantic version compliant
            // Convert formats like "1.2025.06.20.1005" to "1.2025.6.20" (removing leading zeros)
            String fixedVersionStr = fixVersionFormat(versionStr);
            return Version.valueOf(fixedVersionStr);
        } catch (Exception e) {
            System.err.println("Error parsing version string '" + versionStr + "': " + e.getMessage());
            return Version.valueOf("1.0.225");
        }
    }
    
    /**
     * Fixes version format to be semantic version compliant by removing leading zeros.
     * Converts formats like "1.2025.06.20.1005" to "1.2025.6.20" (removing leading zeros and extra segments)
     *
     * @param versionStr the original version string
     * @return a semantic version compliant string
     */
    private static String fixVersionFormat(String versionStr) {
        if (versionStr == null || versionStr.isEmpty()) {
            return versionStr;
        }
        
        try {
            String[] parts = versionStr.split("\\.");
            if (parts.length >= 3) {
                // Take only the first 3 parts for semantic versioning (major.minor.patch)
                // Remove leading zeros from each part
                String major = removeLeadingZeros(parts[0]);
                String minor = removeLeadingZeros(parts[1]);
                String patch = removeLeadingZeros(parts[2]);
                
                return major + "." + minor + "." + patch;
            }
            return versionStr;
        } catch (Exception e) {
            System.err.println("Error fixing version format for '" + versionStr + "': " + e.getMessage());
            return versionStr;
        }
    }
    
    /**
     * Removes leading zeros from a version component while preserving "0" if the entire string is zeros.
     *
     * @param component the version component
     * @return the component without leading zeros
     */
    private static String removeLeadingZeros(String component) {
        if (component == null || component.isEmpty()) {
            return component;
        }
        
        // Remove leading zeros but keep at least one digit
        String result = component.replaceFirst("^0+", "");
        return result.isEmpty() ? "0" : result;
    }
    
    public static final Version JAVA_VERSION = SystemUtilities.getJavaVersion();
    public static final String JAVA_VENDOR = System.getProperty("java.vendor");

    /* QZ-Tray Constants */

    public static final String TEMP_FILE = "temp";
    public static final String LOG_FILE = "debug";
    public static final String PREFS_FILE = "prefs"; // .properties extension is assumed
    public static final String[] PERSIST_PROPS = {"file.whitelist", "file.allow", STEAL_WEBSOCKET_PROPERTY };
    public static final String AUTOSTART_FILE = ".autostart";

    public static final int BORDER_PADDING = 10;

    public static final String SPONSORED_TOOLTIP = "Sponsored organization";
    public static final String UNTRUSTED_CERT = "Untrusted website";
    public static final String NO_TRUST = "Cannot verify trust";

    public static final String PROBE_REQUEST = "getProgramName";
    public static final String PROBE_RESPONSE = "tray";

    public static final String ALLOW_SITES_TEXT = "Permanently allowed \"%s\" to access local resources";
    public static final String BLOCK_SITES_TEXT = "Permanently blocked \"%s\" from accessing local resources";

    public static final String REMEMBER_THIS_DECISION = "Remember this decision";
    public static final String STRICT_MODE_LABEL = "Use strict certificate mode";
    public static final String STRICT_MODE_TOOLTIP = String.format("Prevents the ability to select \"%s\" for most websites", REMEMBER_THIS_DECISION);
    public static final String STRICT_MODE_CONFIRM = String.format("Set strict certificate mode?  Most websites will stop working");
    public static final String ALLOW_SITES_LABEL = "Sites permanently allowed access";
    public static final String BLOCK_SITES_LABEL = "Sites permanently blocked from access";


    public static final String ALLOWED = "Allowed";
    public static final String BLOCKED = "Blocked";

    public static final String OVERRIDE_CERT = "override.crt";
    public static final String WHITELIST_CERT_DIR = "whitelist";
    public static final String PROVISION_DIR = "provision";
    public static final String PROVISION_FILE = "provision.json";

    public static final String SIGNING_PRIVATE_KEY = "private-key.pem";
    public static final String SIGNING_CERTIFICATE = "digital-certificate.txt";

    public static final long VALID_SIGNING_PERIOD = 15 * 60 * 1000; //millis
    public static final int EXPIRY_WARN = 30;   // days
    public static final Color WARNING_COLOR_LITE = Color.RED;
    public static final Color TRUSTED_COLOR_LITE = Color.BLUE;
    public static final Color WARNING_COLOR_DARK = Color.decode("#EB6261");
    public static final Color TRUSTED_COLOR_DARK = Color.decode("#589DF6");
    public static Color WARNING_COLOR = WARNING_COLOR_LITE;
    public static Color TRUSTED_COLOR = TRUSTED_COLOR_LITE;

    public static boolean MASK_TRAY_SUPPORTED = true;

    public static final long MEMORY_PER_PRINT = 512; //MB

    public static final String RAW_PRINT = " Raw Print";
    public static final String IMAGE_PRINT = " Pixel Print";
    public static final String PDF_PRINT = " PDF Print";
    public static final String HTML_PRINT = " HTML Print";

    public static final Integer[] DEFAULT_WSS_PORTS = {8181, 8282, 8383, 8484};
    public static final Integer[] DEFAULT_WS_PORTS = {8182, 8283, 8384, 8485};
    public static final Integer[] CUPS_RSS_PORTS = {8586, 8687, 8788, 8889};
}
