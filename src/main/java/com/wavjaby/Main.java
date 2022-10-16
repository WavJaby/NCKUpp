package com.wavjaby;

import com.wavjaby.api.*;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Main {
    private static final String TAG = "[Main] ";
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

        Properties serverSettings = new Properties();
        try {
            File file = new File("./server.properties");
            if (!file.exists()) {
                Logger.log(TAG, "Server properties not found, create default.");
                InputStream stream = Main.class.getResourceAsStream("/server.properties");
                if (stream == null) {
                    Logger.log(TAG, "Default server properties not found.");
                    return;
                }
                Files.copy(stream, file.toPath());
                stream.close();
                stream = Main.class.getResourceAsStream("/server.properties");
                assert stream != null;
                serverSettings.load(stream);
                stream.close();
            } else
                serverSettings.load(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        HttpServer server = new HttpServer(serverSettings);
        if (!server.Opened) return;

        server.createContext("/NCKUpp/", new FileHost(serverSettings));
        server.createContext("/api/login", new Login());
        server.createContext("/api/logout", new Logout());
        server.createContext("/api/courseSchedule", new CourseSchedule());
        server.createContext("/api/search", new Search());
        server.createContext("/api/extract", new ExtractUrl());
        server.createContext("/api/nckuhub", new NCKUHub());
        server.createContext("/api/urschool", new UrSchool());

        server.start();
        Logger.log(TAG, "Server started, " + server.hostname + ':' + server.port);
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
