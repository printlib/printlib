package qz.common;

import com.github.zafarkhaja.semver.Version;
import qz.utils.SystemUtilities;

import org.apache.commons.lang3.time.DurationFormatUtils;
import java.util.Base64;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import qz.utils.StringUtilities;
import qz.ws.WebsocketPorts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

public class AboutInfo {

    private static final Logger logger = LogManager.getLogger(AboutInfo.class);

    private static String preferredHostname = "localhost";

    public static JSONObject gatherAbout(String domain, CertificateProvider certificateProvider, WebsocketPorts websocketPorts) {
        JSONObject about = new JSONObject();

        try {
            about.put("product", product());
            about.put("socket", socket(certificateProvider, domain, websocketPorts));
            about.put("environment", environment());
            about.put("ssl", ssl(certificateProvider));
            about.put("charsets", charsets());
        }
        catch(JSONException | GeneralSecurityException e) {
            logger.error("Failed to write JSON data", e);
        }

        return about;
    }

    private static JSONObject product() throws JSONException {
        JSONObject product = new JSONObject();

        product.put("version", Constants.VERSION);

        return product;
    }

    private static JSONObject socket(CertificateProvider certificateProvider, String domain, WebsocketPorts websocketPorts) throws JSONException {
        JSONObject socket = new JSONObject();
        String sanitizeDomain = StringUtilities.escapeHtmlEntities(domain);

        // Gracefully handle XSS per https://github.com/qzind/tray/issues/1099
        if(sanitizeDomain.contains("&lt;") || sanitizeDomain.contains("&gt;")) {
            logger.warn("Something smells fishy about this domain: \"{}\", skipping", domain);
            sanitizeDomain = "unknown";
        }

        socket
                .put("domain", sanitizeDomain)
                .put("secureProtocol", "wss")
                .put("securePort", certificateProvider.isSslActive() ? websocketPorts.getSecurePort() : "none")
                .put("insecureProtocol", "ws")
                .put("insecurePort", websocketPorts.getInsecurePort());

        return socket;
    }

    private static JSONObject environment() throws JSONException {
        JSONObject environment = new JSONObject();

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        environment
                .put("os", SystemUtilities.getOsDisplayName())
                .put("os version", SystemUtilities.getOsDisplayVersion())
                .put("java", String.format("%s (%s)", Constants.JAVA_VERSION, SystemUtilities.getArch().toString().toLowerCase()))
                .put("java (location)", System.getProperty("java.home"))
                .put("java (vendor)", Constants.JAVA_VENDOR)
                .put("uptime", DurationFormatUtils.formatDurationWords(uptime, true, false))
                .put("uptimeMillis", uptime)
                .put("sandbox", false);

        return environment;
    }

    private static JSONObject ssl(CertificateProvider certificateProvider) throws JSONException, GeneralSecurityException {
        JSONObject ssl = new JSONObject();

        JSONArray certs = certificateProvider.generateSslCertificateInfo();
        ssl.put("certificates", certs);

        return ssl;
    }

    public static String formatCert(byte[] encoding) {
        return "-----BEGIN CERTIFICATE-----\r\n" +
                new String(Base64.getEncoder().encode(encoding), StandardCharsets.UTF_8) +
                "-----END CERTIFICATE-----\r\n";
    }


    private static JSONObject charsets() throws JSONException {
        JSONObject charsets = new JSONObject();

        SortedMap<String,Charset> avail = Charset.availableCharsets();
        ArrayList<String> names = new ArrayList<>();
        for(Map.Entry<String,Charset> entry : avail.entrySet()) {
            names.add(entry.getValue().name());
        }

        charsets.put("charsets", Arrays.toString(names.toArray()));
        return charsets;
    }

    public static String getPreferredHostname() {
        return preferredHostname;
    }

}
