package com.wavjaby;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.wavjaby.logger.Logger;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Properties;

public class HttpServer {
    private static final String TAG = "[HttpServer] ";
    private static final int defaultPort = 443;
    private com.sun.net.httpserver.HttpsServer httpsServer;
    private com.sun.net.httpserver.HttpServer httpServer;

    private int protocol = -1;
    public boolean Opened = false;
    public boolean Error = false;
    public int port;
    public String hostname;

    HttpServer(Properties serverSettings) {
        try {
            String portStr = serverSettings.getProperty("port");
            if (portStr == null) {
                port = 443;
                Logger.warn(TAG, "Port not found, using default: " + port);
            } else
                port = Integer.parseInt(portStr);
        } catch (ClassCastException | NumberFormatException e) {
            port = defaultPort;
            Logger.warn(TAG, "Wrong port format: \"" + serverSettings.get("port") + "\", Using Default " + port);
        }
        String protocolName = serverSettings.getProperty("protocol");
        hostname = serverSettings.getProperty("hostname");
        if (protocolName == null) {
            protocolName = "https";
            Logger.warn(TAG, "Protocol name not found, using default: " + protocolName);
        }
        if (hostname == null) {
            hostname = "localhost";
            Logger.warn(TAG, "Host name not found, using default: " + hostname);
        }

        if (protocolName.equals("https"))
            Error = createHttpsServer(hostname, port, serverSettings);
        else if (protocolName.equals("http"))
            Error = createHttpServer(hostname, port);
    }

    private synchronized boolean createHttpsServer(String hostname, int port, Properties serverSettings) {
        String keystoreFilePath = serverSettings.getProperty("keystorePath");
        String keystorePropertyPath = serverSettings.getProperty("keystorePropertyPath");
        if (keystoreFilePath == null) {
            keystoreFilePath = "key/key.keystore";
            Logger.warn(TAG, "Protocol name not found, using default: " + keystoreFilePath);
        }
        if (keystorePropertyPath == null) {
            keystorePropertyPath = "key/key.properties";
            Logger.warn(TAG, "Host name not found, using default: " + keystorePropertyPath);
        }

        if (Opened) {
            Logger.warn(TAG, "Server already opened");
            return false;
        }
        protocol = 0;
        SSLContext sslContext;
        try {
            Properties prop = new Properties();
            File keystoreProperties = new File(keystorePropertyPath);
            if (!keystoreProperties.exists()) {
                Logger.err(TAG, "Keystore info at: \"" + keystoreProperties.getAbsolutePath() + "\" not found.");
                return false;
            }
            InputStream keystorePropertiesIn = Files.newInputStream(keystoreProperties.toPath());
            prop.load(keystorePropertiesIn);
            keystorePropertiesIn.close();

            String storePassword = prop.getProperty("storePassword");
            String keyPassword = prop.getProperty("keyPassword");
            if (storePassword == null) {
                Logger.warn(TAG, "Keystore password not found, fill with empty");
                storePassword = "";
            }
            if (keyPassword == null) {
                Logger.warn(TAG, "Key password not found, fill with empty");
                keyPassword = "";
            }

            // load certificate
            char[] storepass = storePassword.toCharArray();
            char[] keypass = keyPassword.toCharArray();
//        String alias = prop.getProperty("alias");

            // Initialise the keystore
            File keystoreFile = new File(keystoreFilePath);
            if (!keystoreFile.exists()) {
                Logger.err(TAG, "Keystore file at: \"" + keystoreFile.getAbsolutePath() + "\" not found.");
                return false;
            }
            InputStream keystoreIn = Files.newInputStream(keystoreFile.toPath());
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(keystoreIn, storepass);
            keystoreIn.close();

            // display certificate
//            Certificate cert = keystore.getCertificate(alias);
//            System.out.println(cert);

            // Set up the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, keypass);

            // Set up the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(keystore);

            // create https server
            httpsServer = com.sun.net.httpserver.HttpsServer.create(
                    new InetSocketAddress(hostname, port), 0);
            // create ssl context
            sslContext = SSLContext.getInstance("TLSv1");
            // setup HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException | KeyStoreException |
                 UnrecoverableKeyException | CertificateException e) {
            e.printStackTrace();
            return false;
        }
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
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
                    Logger.err(TAG, "Failed to create HTTPS server");
                }
            }
        });
        Opened = true;
        return true;
    }

    private synchronized boolean createHttpServer(String hostname, int port) {
        if (Opened) {
            Logger.warn(TAG, "Server already opened");
            return false;
        }
        protocol = 1;
        try {
            httpServer = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(hostname, port), 0
            );
        } catch (IOException e) {
            return false;
        }
        Opened = true;
        return true;
    }

    public void start() {
        if (protocol == 0) {
            httpsServer.setExecutor(null); // creates a default executor
            httpsServer.start();
        } else if (protocol == 1) {
            httpServer.setExecutor(null); // creates a default executor
            httpServer.start();
        }
    }

    public void createContext(String path, HttpHandler handler) {
        if (protocol == 0)
            httpsServer.createContext(path, handler);
        else if (protocol == 1)
            httpServer.createContext(path, handler);
    }
}
