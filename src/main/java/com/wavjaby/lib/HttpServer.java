package com.wavjaby.lib;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
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
import java.util.concurrent.Executors;

public class HttpServer {
    private static final String TAG = "[HttpServer]";
    private static final Logger logger = new Logger(TAG);
    private static final int defaultPort = 443;
    private com.sun.net.httpserver.HttpServer httpServer;

    public boolean opened = false;
    public boolean error = false;
    public int port;
    public String hostname;

    public HttpServer(Properties serverSettings) {
        try {
            String portStr = serverSettings.getProperty("port");
            if (portStr == null) {
                port = 443;
                logger.warn("Property \"port\" not found, using default: " + port);
            } else
                port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            port = defaultPort;
            logger.warn("Property \"port\": \"" + serverSettings.get("port") + "\" have wrong format, using default: " + port);
        }
        String protocolName = serverSettings.getProperty("protocol");
        hostname = serverSettings.getProperty("hostname");
        if (protocolName == null) {
            protocolName = "https";
            logger.warn("Property \"protocol\" not found, using default: " + protocolName);
        }
        if (hostname == null) {
            hostname = "localhost";
            logger.warn("Property \"hostname\" not found, using default: " + hostname);
        }

        if (protocolName.equals("https"))
            error = createHttpsServer(hostname, port, serverSettings);
        else if (protocolName.equals("http"))
            error = createHttpServer(hostname, port);
    }

    private synchronized boolean createHttpsServer(String hostname, int port, Properties serverSettings) {
        String keystoreFilePath = serverSettings.getProperty("keystorePath");
        String keystorePropertyPath = serverSettings.getProperty("keystorePropertyPath");
        if (keystoreFilePath == null) {
            keystoreFilePath = "key/key.keystore";
            logger.warn("Protocol name not found, using default: " + keystoreFilePath);
        }
        if (keystorePropertyPath == null) {
            keystorePropertyPath = "key/key.properties";
            logger.warn("Host name not found, using default: " + keystorePropertyPath);
        }

        if (opened) {
            logger.warn("Server already opened");
            return false;
        }
        HttpsServer httpsServer;
        SSLContext sslContext;
        try {
            Properties prop = new Properties();
            File keystoreProperties = new File(keystorePropertyPath);
            if (!keystoreProperties.exists()) {
                logger.err("Keystore info at: \"" + keystoreProperties.getAbsolutePath() + "\" not found");
                return false;
            }
            InputStream keystorePropertiesIn = Files.newInputStream(keystoreProperties.toPath());
            prop.load(keystorePropertiesIn);
            keystorePropertiesIn.close();

            String storePassword = prop.getProperty("storePassword");
            String keyPassword = prop.getProperty("keyPassword");
            if (storePassword == null) {
                logger.warn("Keystore password not found, fill with empty");
                storePassword = "";
            }
            if (keyPassword == null) {
                logger.warn("Key password not found, fill with empty");
                keyPassword = "";
            }

            // load certificate
            char[] storepass = storePassword.toCharArray();
            char[] keypass = keyPassword.toCharArray();
//        String alias = prop.getProperty("alias");

            // Initialise the keystore
            File keystoreFile = new File(keystoreFilePath);
            if (!keystoreFile.exists()) {
                logger.err("Keystore file at: \"" + keystoreFile.getAbsolutePath() + "\" not found");
                return false;
            }
            InputStream keystoreIn = Files.newInputStream(keystoreFile.toPath());
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(keystoreIn, storepass);
            keystoreIn.close();

            // display certificate
//            Certificate cert = keystore.getCertificate(alias);
//            logger.log(cert);

            // Set up the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, keypass);

            // Set up the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(keystore);

            // create https server
            httpsServer = HttpsServer.create(new InetSocketAddress(hostname, port), 0);
            // create ssl context
            sslContext = SSLContext.getInstance("TLSv1");
            // setup HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException | KeyStoreException |
                 UnrecoverableKeyException | CertificateException e) {
            logger.errTrace(e);
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
                } catch (Exception e) {
                    logger.errTrace(e);
                    logger.err("Failed to create HTTPS server");
                }
            }
        });
        httpServer = httpsServer;
        opened = true;
        return true;
    }

    private synchronized boolean createHttpServer(String hostname, int port) {
        if (opened) {
            logger.warn("Server already opened");
            return false;
        }
        try {
            httpServer = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(hostname, port), 0
            );
        } catch (IOException e) {
            return false;
        }
        opened = true;
        return true;
    }

    public void start() {
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
    }

    public void createContext(String path, HttpHandler handler) {
        httpServer.createContext(path, handler);
    }

    public void stop() {
        httpServer.stop(0);
    }
}
