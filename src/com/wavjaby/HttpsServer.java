package com.wavjaby;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Properties;

public class HttpsServer {
    private KeyManagerFactory kmf;
    private TrustManagerFactory tmf;
    com.sun.net.httpserver.HttpsServer server;

    HttpsServer(String keystoreFilePath, String keystoreInfoPath) {
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(Paths.get(keystoreInfoPath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // load certificate
        char[] storepass = prop.getProperty("storePassword").toCharArray();
        char[] keypass = prop.getProperty("keyPassword").toCharArray();
        String alias = prop.getProperty("alias");
        try {
            FileInputStream fIn = new FileInputStream(keystoreFilePath);
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(fIn, storepass);
            // display certificate
            Certificate cert = keystore.getCertificate(alias);
//            System.out.println(cert);
            // setup key manager factory
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, keypass);
            // setup trust manager factory
            tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(keystore);
        } catch (UnrecoverableKeyException | CertificateException | KeyStoreException | IOException |
                 NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void start(int port) {
        // create https server
        SSLContext sslContext;
        try {
            server = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(port), 0);
            // create ssl context
            sslContext = SSLContext.getInstance("TLSv1");
            // setup HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            public void configure(HttpsParameters params) {
                try {
                    // initialise the SSL context
                    SSLContext c = SSLContext.getDefault();
                    SSLEngine engine = c.createSSLEngine();
                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());
                    // get the default parameters
                    SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                    params.setSSLParameters(defaultSSLParameters);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.out.println("Failed to create HTTPS server");
                }
            }
        });
        server.setExecutor(null);
        server.start();
    }

    public void createContext(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }
}
