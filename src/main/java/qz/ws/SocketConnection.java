package qz.ws;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import qz.auth.Certificate;
import qz.communication.*;
import qz.printer.status.StatusMonitor;

import java.io.IOException;
import java.util.HashMap;

public class SocketConnection {

    private static final Logger log = LogManager.getLogger(SocketConnection.class);


    private Certificate certificate;
    private String originHeader; // Store the Origin header from WebSocket request
    
    // No longer storing fingerprints in the connection

    private DeviceListener deviceListener;


    public SocketConnection(Certificate cert) {
        certificate = cert;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate newCert) {
        certificate = newCert;
    }
    
    public String getOriginHeader() {
        return originHeader;
    }
    
    public void setOriginHeader(String originHeader) {
        this.originHeader = originHeader;
    }
    
    // Removed fingerprint getters and setters as they should be handled per-message


    public boolean isDeviceListening() {
        return deviceListener != null;
    }

    public void startDeviceListening(DeviceListener listener) {
        deviceListener = listener;
    }

    public void stopDeviceListening() {
        if (deviceListener != null) {
            deviceListener.close();
        }
        deviceListener = null;
    }


    /**
     * Explicitly closes all open network and usb connections setup through this object
     */
    public synchronized void disconnect() throws DeviceException, IOException {
        log.info("Closing all communication channels for {}", certificate.getCommonName());

        stopDeviceListening();
        StatusMonitor.stopListening(this);
    }

}