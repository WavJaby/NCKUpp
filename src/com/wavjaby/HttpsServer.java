package com.wavjaby;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Properties;

public class HttpsServer {
    private KeyManagerFactory kmf;
    private TrustManagerFactory tmf;
    com.sun.net.httpserver.HttpsServer server;

    HttpsServer(int port, String keystoreFilePath, String keystoreInfoPath) {
        SSLContext sslContext;
        try {
            Properties prop = new Properties();
            prop.load(Files.newInputStream(Paths.get(keystoreInfoPath)));
            // load certificate
            char[] storepass = prop.getProperty("storePassword").toCharArray();
            char[] keypass = prop.getProperty("keyPassword").toCharArray();
//        String alias = prop.getProperty("alias");

            // Initialise the keystore
            FileInputStream fIn = new FileInputStream(keystoreFilePath);
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(fIn, storepass);

            // display certificate
//            Certificate cert = keystore.getCertificate(alias);
//            System.out.println(cert);

            // Set up the key manager factory
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, keypass);

            // Set up the trust manager factory
            tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(keystore);

            // create https server
            server = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(port), 0);
            // create ssl context
            sslContext = SSLContext.getInstance("TLSv1");
            // setup HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException | KeyStoreException |
                 UnrecoverableKeyException | CertificateException e) {
            e.printStackTrace();
            return;
        }
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            public void configure(HttpsParameters params) {
                try {
                    // Initialise the SSL context
                    SSLContext c = SSLContext.getDefault();
                    SSLEngine engine = c.createSSLEngine();
                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());

                    // Get the default parameters
                    SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                    params.setSSLParameters(defaultSSLParameters);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.out.println("Failed to create HTTPS server");
                }
            }
        });
    }

    public void start() {
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void createContext(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }
}
