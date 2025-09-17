package qz.build.provision.params.types;

import java.nio.file.Path;

/**
 * Enumeration of software installer types
 */
public enum Software {
    EXE("exe"),
    MSI("msi"),
    PKG("pkg"),
    DMG("dmg"),
    RUN("run"),
    DEB("deb"),
    RPM("rpm"),
    UNKNOWN("unknown");

    private final String[] extensions;

    Software(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * Parse software type from data object
     */
    public static Software parse(Object data) {
        if (data == null) return UNKNOWN;
        
        String dataStr = data.toString().toLowerCase();
        
        for (Software software : values()) {
            if (software == UNKNOWN) continue;
            for (String ext : software.extensions) {
                if (dataStr.endsWith("." + ext)) {
                    return software;
                }
            }
        }
        
        // Try to match by software type name
        for (Software software : values()) {
            if (software == UNKNOWN) continue;
            if (dataStr.contains(software.name().toLowerCase())) {
                return software;
            }
        }
        
        return UNKNOWN;
    }

    /**
     * Parse software type from path
     */
    public static Software parse(Path path) {
        if (path == null) return UNKNOWN;
        return parse(path.toString());
    }

    /**
     * Get the primary extension for this software type
     */
    public String getExtension() {
        return extensions.length > 0 ? extensions[0] : name().toLowerCase();
    }

    /**
     * Check if this software type matches the given extension
     */
    public boolean matches(String extension) {
        if (extension == null) return false;
        String lowerExt = extension.toLowerCase();
        for (String ext : extensions) {
            if (lowerExt.equals(ext) || lowerExt.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }
}
