package qz.build.provision.params.types;

import java.nio.file.Path;

/**
 * Enumeration of script types
 */
public enum Script {
    PS1("ps1"),
    PY("py"),
    BAT("bat", "cmd"),
    RB("rb"),
    SH("sh");

    private final String[] extensions;

    Script(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * Parse script type from data object
     */
    public static Script parse(Object data) {
        if (data == null) return null;
        
        String dataStr = data.toString().toLowerCase();
        
        for (Script script : values()) {
            for (String ext : script.extensions) {
                if (dataStr.endsWith("." + ext)) {
                    return script;
                }
            }
        }
        
        // Try to match by script type name
        for (Script script : values()) {
            if (dataStr.contains(script.name().toLowerCase())) {
                return script;
            }
        }
        
        return null;
    }

    /**
     * Parse script type from path
     */
    public static Script parse(Path path) {
        if (path == null) return null;
        return parse(path.toString());
    }

    /**
     * Get the primary extension for this script type
     */
    public String getExtension() {
        return extensions.length > 0 ? extensions[0] : name().toLowerCase();
    }

    /**
     * Check if this script type matches the given extension
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