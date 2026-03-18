package qz.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import qz.common.Constants;

import java.time.Instant;
import java.util.Arrays;

public class RequestState {

    private static final Logger log = LogManager.getLogger(RequestState.class);

    public enum Validity {
        TRUSTED("Valid"),
        EXPIRED("Expired Signature"),
        UNSIGNED("Invalid Signature"),
        EXPIRED_CERT("Expired Certificate"),
        FUTURE_CERT("Future Certificate"),
        INVALID_CERT("Invalid Certificate"),
        UNKNOWN("Invalid");

        private String formatted;

        Validity(String formatted) {
            this.formatted = formatted;
        }

        public String getFormatted() {
            return formatted;
        }
    }

    Certificate certUsed;
    JSONObject requestData;

    boolean initialConnect;
    Validity status;

    public RequestState(Certificate cert, JSONObject data) {
        certUsed = cert;
        requestData = data;
        status = Validity.UNKNOWN;
    }

    public Certificate getCertUsed() {
        return certUsed;
    }

    public JSONObject getRequestData() {
        return requestData;
    }

    public boolean isInitialConnect() {
        return initialConnect;
    }

    public void markNewConnection(Certificate cert) {
        certUsed = cert;
        initialConnect = true;

        checkCertificateState(cert);
    }

    public void checkCertificateState(Certificate cert) {
        if (cert.isTrusted()) {
            status = Validity.TRUSTED;
        } else if (cert.getValidToDate().isBefore(Instant.now())) {
            status = Validity.EXPIRED_CERT;
        } else if (cert.getValidFromDate().isAfter(Instant.now())) {
            status = Validity.FUTURE_CERT;
        } else if (!cert.isValid()) {
            status = Validity.INVALID_CERT;
        } else {
            status = Validity.UNKNOWN;
        }
    }

    public Validity getStatus() {
        return status;
    }

    public void setStatus(Validity state) {
        status = state;
    }

    public boolean hasCertificate() {
        return certUsed != null && certUsed != Certificate.UNKNOWN;
    }

    public boolean hasSavedCert() {
        log.info("=== REQUEST_STATE_VALIDATION === | hasSavedCert() called for certificate: {}",
                 getCertName() != null ? getCertName() : "unknown");
        
        boolean verified = isVerified();
        log.info("=== REQUEST_STATE_VALIDATION === | Certificate verified status: {}", verified);
        
        if (!verified) {
            log.info("=== REQUEST_STATE_VALIDATION === | Certificate not verified, returning false");
            return false;
        }
        
        boolean saved = certUsed.isSaved();
        log.info("=== REQUEST_STATE_VALIDATION === | Certificate saved status: {}", saved);
        
        boolean result = verified && saved;
        log.info("=== REQUEST_STATE_VALIDATION === | Final hasSavedCert result: {}", result);
        return result;
    }

    public boolean hasBlockedCert() {
        return certUsed == null || certUsed.isBlocked();
    }

    public String getCertName() {
        return certUsed.getCommonName();
    }

    public boolean isVerified() {
        return certUsed.isTrusted() && status == Validity.TRUSTED;
    }

    public boolean isSponsored() {
        return certUsed.isSponsored();
    }



}
