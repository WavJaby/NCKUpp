package com.wavjaby;

import com.sun.net.httpserver.HttpServer;
import com.wavjaby.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Main {
    public static final String courseNcku = "course.ncku.edu.tw";
    public static final String portalNcku = "fs.ncku.edu.tw";

    public static final String courseNckuOrg = "https://" + courseNcku;
    public static final String portalNckuOrg = "https://" + portalNcku;

    public static final String[] accessControlAllowOrigin = {
            "https://api.simon.chummydns.com",
            "https://wavjaby.github.io",
    };
    public static final String cookieDomain = "simon.chummydns.com";
    public static ExecutorService pool = Executors.newCachedThreadPool();


    Main() {
//        System.setProperty("javax.net.debug", "ssl,handshake");
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // server
//        HttpsServer server = new HttpsServer(443, "key/key.keystore", "key/key.properties");

        server.createContext("/NCKUpp/", new FileHost());
        server.createContext("/api/login", new Login());
        server.createContext("/api/logout", new Logout());
        server.createContext("/api/courseSchedule", new CourseSchedule());
        server.createContext("/api/search", new Search());
        server.createContext("/api/extract", new ExtractUrl());
        server.createContext("/api/nckuhub", new NCKUHub());

        server.start();
        System.out.println("Server started");
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
