package qz.utils;

/**
 * Provider interface for task killer functionality used by ArgParser.
 * This interface allows ArgParser to work with task killers without direct dependencies.
 */
public interface TaskKillerProvider {
    
    /**
     * Kill all instances of the application
     */
    void killAll();
}