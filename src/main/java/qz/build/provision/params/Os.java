package qz.build.provision.params;

/**
 * Enumeration of operating systems
 */
public enum Os {
    WINDOWS("windows", "win"),
    MAC("mac", "osx", "darwin"),
    LINUX("linux", "unix"),
    SOLARIS("solaris"),
    UNKNOWN("unknown");

    private final String[] aliases;

    Os(String... aliases) {
        this.aliases = aliases;
    }

    /**
     * Find the best matching OS based on the system property
     */
    public static Os bestMatch(String osName) {
        if (osName == null) {
            return LINUX; // Default fallback
        }
        
        String lowerOsName = osName.toLowerCase();
        
        if (lowerOsName.contains("windows")) {
            return WINDOWS;
        } else if (lowerOsName.contains("mac") || lowerOsName.contains("darwin") || lowerOsName.contains("osx")) {
            return MAC;
        } else {
            return LINUX;
        }
    }

    /**
     * Get the primary alias for this OS
     */
    public String getAlias() {
        return aliases.length > 0 ? aliases[0] : name().toLowerCase();
    }

    /**
     * Check if this OS matches the given name
     */
    public boolean matches(String osName) {
        if (osName == null) return false;
        String lowerOsName = osName.toLowerCase();
        for (String alias : aliases) {
            if (lowerOsName.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given OS matches the current host OS
     */
    public static boolean matchesHost(Os os) {
        if (os == null) return true; // null means any OS
        Os hostOs = bestMatch(System.getProperty("os.name"));
        return hostOs == os;
    }
}
