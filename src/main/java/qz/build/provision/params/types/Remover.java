package qz.build.provision.params.types;

/**
 * Enumeration of remover types
 */
public enum Remover {
    CUSTOM("custom") {
        @Override
        public String getAboutTitle() {
            return "Custom Remover";
        }

    },
    DEFAULT("default") {
        @Override
        public String getAboutTitle() {
            return "Default Remover";
        }

    };

    private final String type;

    Remover(String type) {
        this.type = type;
    }

    /**
     * Parse remover type from data object
     */
    public static Remover parse(Object data) {
        if (data == null) return DEFAULT;
        
        String dataStr = data.toString().toLowerCase();
        
        for (Remover remover : values()) {
            if (dataStr.contains(remover.type) || dataStr.contains(remover.name().toLowerCase())) {
                return remover;
            }
        }
        
        return DEFAULT;
    }

    /**
     * Get the about title for this remover type
     */
    public abstract String getAboutTitle();


    /**
     * Get the type string
     */
    public String getType() {
        return type;
    }
}
