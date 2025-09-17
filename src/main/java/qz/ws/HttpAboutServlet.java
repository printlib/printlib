package qz.ws;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.ee9.servlet.DefaultServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Iterator;

/**
 * Abstract HTTP JSON endpoint for serving print application information.
 * Subclasses provide certificate management implementation.
 */
public abstract class HttpAboutServlet extends DefaultServlet {

    private static final Logger logger = LogManager.getLogger(HttpAboutServlet.class);

    private static final int JSON_INDENT = 2;
    private WebsocketPorts websocketPorts;

    /**
     * Base constructor for HttpAboutServlet.
     * 
     * @param websocketPorts the websocket port configuration
     */
    public HttpAboutServlet(@lombok.NonNull WebsocketPorts websocketPorts) {
        this.websocketPorts = websocketPorts;
    }

    // Abstract methods to replace CertificateManager dependencies
    protected abstract JSONObject gatherAboutInfo(String serverName, WebsocketPorts websocketPorts);
    protected abstract String getCertificateData(String alias) throws Exception;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        if ("application/json".equals(request.getHeader("Accept")) || "/json".equals(request.getServletPath())) {
            generateJsonResponse(request, response);
        } else if ("application/x-x509-ca-cert".equals(request.getHeader("Accept")) || request.getServletPath().startsWith("/cert/")) {
            generateCertResponse(request, response);
        } else {
            generateHtmlResponse(request, response);
        }
    }

    private void generateHtmlResponse(HttpServletRequest request, HttpServletResponse response) {
        StringBuilder display = new StringBuilder();

        display.append("<html>")
                .append("<head><meta charset=\"UTF-8\"></head>")
                .append("<body>")
                .append("<h1>About</h1>");

        display.append(newTable());

        JSONObject aboutData = gatherAboutInfo(request.getServerName(), websocketPorts);
        try {
            display.append(generateFromKeys(aboutData, true));
        }
        catch(JSONException e) {
            logger.error("Failed to read JSON data", e);
            display.append("<tr><td>Failed to write information</td></tr>");
        }
        display.append("</table>");

        display.append("</body></html>");

        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            response.getOutputStream().print(display.toString());
        }
        catch(Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            logger.warn("Exception occurred loading html page {}", e.getMessage());
        }
    }

    private void generateJsonResponse(HttpServletRequest request, HttpServletResponse response) {
        JSONObject aboutData = gatherAboutInfo(request.getServerName(), websocketPorts);

        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getOutputStream().write(aboutData.toString(JSON_INDENT).getBytes(StandardCharsets.UTF_8));
        }
        catch(Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            logger.warn("Exception occurred writing JSONObject {}", aboutData);
        }
    }

    private void generateCertResponse(HttpServletRequest request, HttpServletResponse response) {
        try {
            String alias = request.getServletPath().split("/")[2];
            String certData = getCertificateData(alias);

            if (certData != null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/x-x509-ca-cert");

                response.getOutputStream().print(certData);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getOutputStream().print("Could not find certificate with alias \"" + alias + "\" to download.");
            }
        }
        catch(Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            logger.warn("Exception occurred loading certificate: {}", e.getMessage());
        }
    }

    private StringBuilder generateFromKeys(JSONObject obj, boolean printTitle) throws JSONException {
        StringBuilder rows = new StringBuilder();

        @SuppressWarnings("unchecked")
        Iterator<String> itr = obj.keys();
        while(itr.hasNext()) {
            String key = itr.next();

            if (printTitle) {
                rows.append(titleRow(key));
            }

            if (obj.optJSONObject(key) != null) {
                rows.append(generateFromKeys(obj.getJSONObject(key), false));
            } else {
                if ("data".equals(key)) { //special case - replace with a "Download" button
                    obj.put(key, "<a href='/cert/" + obj.optString("alias") + "'>Download certificate</a>");
                }
                rows.append(contentRow(key, obj.get(key)));
            }
        }

        return rows;
    }


    private String newTable() {
        return "<table border='1' cellspacing='0' cellpadding='5'>";
    }

    private String titleRow(String title) {
        return "<tr><th colspan='99'>" + title + "</th></tr>";
    }

    private String contentRow(String key, Object value) throws JSONException {
        if (value instanceof JSONArray) {
            return contentRow(key, (JSONArray)value);
        } else {
            return contentRow(key, String.valueOf(value));
        }
    }

    private String contentRow(String key, JSONArray value) throws JSONException {
        StringBuilder valueCell = new StringBuilder();
        for(int i = 0; i < value.length(); i++) {
            if (value.optJSONObject(i) != null) {
                valueCell.append(newTable());
                valueCell.append(generateFromKeys(value.getJSONObject(i), false));
                valueCell.append("</table>");
            } else {
                valueCell.append(value.getString(i)).append("<br/>");
            }
        }

        return contentRow(key, valueCell.toString());
    }

    private String contentRow(String key, String value) {
        return "<tr><td>" + key + "</td> <td>" + value + "</td></tr>";
    }

}
