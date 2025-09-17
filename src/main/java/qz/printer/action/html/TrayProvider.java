package qz.printer.action.html;

/**
 * Interface for providing tray management functionality to WebApp.
 * This allows WebApp to work without direct dependency on TrayManager.
 */
public interface TrayProvider {
    /**
     * @return true if monocle platform is preferred
     */
    boolean isMonoclePreferred();
    
    /**
     * @return true if running in headless environment
     */
    boolean isHeadless();
}