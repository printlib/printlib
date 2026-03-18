package qz.common;

import com.sun.jna.Native;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jetty.util.Jetty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;

/**
 * Created by Kyle B. on 10/27/2017.
 */
public class SecurityInfo {


    private static final Logger log = LogManager.getLogger(SecurityInfo.class);

    public static KeyStore getKeyStore(Properties props) {
        if (props != null) {
            String store = props.getProperty("wss.keystore", "");
            char[] pass = props.getProperty("wss.storepass", "").toCharArray();

            try {
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(new FileInputStream(store), pass);
                return keystore;
            }
            catch(GeneralSecurityException | IOException e) {
                log.warn("Unable to create keystore from properties file: {}", e.getMessage());
            }
        }

        return null;
    }


}
