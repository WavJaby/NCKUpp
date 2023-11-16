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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HttpServer {
    private static final Logger logger = new Logger("HttpServer");
    private static final int defaultPort = 443;
    private com.sun.net.httpserver.HttpServer httpServer;

    public final boolean ready;
    public boolean running;
    public String hostname;
    public final int port;

    public HttpServer(Properties serverSettings) {
        int port;
        String portStr = serverSettings.getProperty("port");
        if (portStr == null) {
            port = 443;
            logger.warn("Property \"port\" not found, use default: " + port);
        } else {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                port = defaultPort;
                logger.warn("Property \"port\": \"" + portStr + "\"is invalid, use default: " + port);
            }
        }
        this.port = port;

        String protocolName = serverSettings.getProperty("protocol");
        hostname = serverSettings.getProperty("hostname");
        if (protocolName == null) {
            protocolName = "https";
            logger.warn("Property \"protocol\" not found, use default: " + protocolName);
        }
        if (hostname == null) {
            hostname = "localhost";
            logger.warn("Property \"hostname\" not found, use default: " + hostname);
        }

        if (protocolName.equals("https"))
            ready = createHttpsServer(hostname, port, serverSettings);
        else if (protocolName.equals("http"))
            ready = createHttpServer(hostname, port);
        else {
            logger.err("Unknown protocol: " + protocolName);
            ready = false;
        }
    }

    private boolean createHttpsServer(String hostname, int port, Properties serverSettings) {
        String keystoreFilePath = serverSettings.getProperty("keystorePath");
        String keystorePropertyPath = serverSettings.getProperty("keystorePropertyPath");
        if (keystoreFilePath == null) {
            keystoreFilePath = "key/key.keystore";
            logger.warn("Protocol name not found, use default: " + keystoreFilePath);
        }
        if (keystorePropertyPath == null) {
            keystorePropertyPath = "key/key.properties";
            logger.warn("Host name not found, use default: " + keystorePropertyPath);
        }

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
//            String alias = prop.getProperty("alias");
//            Certificate cert = keystore.getCertificate(alias);
//            logger.log(cert);

            // Set up the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, keypass);
            // Set up the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(keystore);
            // create ssl context
            sslContext = SSLContext.getInstance("TLSv1");
            // setup HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            // create https server
            httpServer = HttpsServer.create(new InetSocketAddress(hostname, port), 0);
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException |
                 UnrecoverableKeyException | CertificateException | IOException e) {
            logger.errTrace(e);
            return false;
        }
        ((HttpsServer) httpServer).setHttpsConfigurator(new HttpsConfigurator(sslContext) {
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
        return true;
    }

    private boolean createHttpServer(String hostname, int port) {
        try {
            httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(hostname, port), 0);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public boolean start() {
        return start(Executors.newCachedThreadPool());
    }

    public boolean start(Executor executor) {
        if (!ready) {
            logger.err("Server is not ready, failed to start.");
            return false;
        }
        if (running) {
            logger.err("Server already started");
            return false;
        }
        httpServer.setExecutor(executor);
        httpServer.start();
        return running = true;
    }

    public void createContext(String path, HttpHandler handler) {
        if (httpServer == null)
            return;
        httpServer.createContext(path, handler);
    }

    public void stop() {
        if (!running) {
            logger.err("Server is not running");
            return;
        }
        httpServer.stop(0);
        running = false;
    }
}
