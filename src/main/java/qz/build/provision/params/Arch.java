package qz.build.provision.params;

/**
 * Enumeration of system architectures
 */
public enum Arch {
    X86("x86", "i386", "i686"),
    X86_64("x86_64", "x64", "amd64"),
    ARM("arm"),
    ARM32("arm32"),
    ARM64("arm64", "aarch64"),
    AARCH64("aarch64", "arm64"),
    RISCV32("riscv32"),
    RISCV64("riscv64"),
    PPC64("ppc64");

    private final String[] aliases;

    Arch(String... aliases) {
        this.aliases = aliases;
    }

    /**
     * Find the best matching architecture based on the system property
     */
    public static Arch bestMatch(String archName) {
        if (archName == null) {
            return X86_64; // Default fallback
        }
        
        String lowerArchName = archName.toLowerCase();
        
        if (lowerArchName.contains("amd64") || lowerArchName.contains("x86_64") || lowerArchName.equals("x64")) {
            return X86_64;
        } else if (lowerArchName.contains("aarch64") || lowerArchName.contains("arm64")) {
            return ARM64;
        } else if (lowerArchName.contains("arm")) {
            return ARM;
        } else if (lowerArchName.contains("x86") || lowerArchName.contains("i386") || lowerArchName.contains("i686")) {
            return X86;
        } else {
            return X86_64; // Default fallback
        }
    }

    /**
     * Get the primary alias for this architecture
     */
    public String getAlias() {
        return aliases.length > 0 ? aliases[0] : name().toLowerCase();
    }

    /**
     * Check if this architecture matches the given name
     */
    public boolean matches(String archName) {
        if (archName == null) return false;
        String lowerArchName = archName.toLowerCase();
        for (String alias : aliases) {
            if (lowerArchName.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse architecture with fallback to host architecture
     */
    public static Arch parse(String archName, Arch hostArch) {
        if (archName == null || archName.trim().isEmpty()) {
            return hostArch != null ? hostArch : X86_64;
        }
        return bestMatch(archName);
    }
}
