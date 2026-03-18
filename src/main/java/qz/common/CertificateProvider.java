package qz.common;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;

import java.security.GeneralSecurityException;

/**
 * Provider interface for certificate-related functionality used by AboutInfo.
 * This interface allows AboutInfo to work with certificate managers without direct dependencies.
 */
public interface CertificateProvider {
    
    /**
     * Check if SSL is currently active/configured
     * @return true if SSL is active, false otherwise
     */
    boolean isSslActive();
    
    /**
     * Generate SSL certificate information as JSON
     * @return JSONArray containing certificate details
     * @throws JSONException if JSON generation fails  
     * @throws GeneralSecurityException if certificate access fails
     */
    JSONArray generateSslCertificateInfo() throws JSONException, GeneralSecurityException;
    
    /**
     * Format certificate data for a specific alias
     * @param alias certificate alias
     * @return formatted certificate string
     * @throws Exception if certificate retrieval or formatting fails
     */
    String formatCertificateByAlias(String alias) throws Exception;
    
    /**
     * Get the writable location for certificate and configuration files
     * @return the writable location path
     */
    String getWritableLocation();
}