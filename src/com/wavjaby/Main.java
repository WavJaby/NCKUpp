package com.wavjaby;

import com.wavjaby.api.*;

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
    public static final String cookieDomain = "api.simon.chummydns.com";
    public static ExecutorService pool = Executors.newCachedThreadPool();


    Main() {
        // server
        HttpsServer server = new HttpsServer("key/key.keystore", "key/key.properties");
        server.start(443);

        server.createContext("/NCKUpp/", new FileHost());
        server.createContext("/api/login", new Login());
        server.createContext("/api/logout", new Logout());
        server.createContext("/api/courseSchedule", new CourseSchedule());
        server.createContext("/api/search", new Search());
        server.createContext("/api/extract", new ExtractUrl());

        System.out.println("Server started");
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
