package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.wavjaby.api.CourseSchedule;
import com.wavjaby.api.Login;
import com.wavjaby.api.Logout;
import com.wavjaby.api.Search;

import java.io.*;
import java.nio.file.Files;
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
            "https://localhost",
    };
    public static final String cookieDomain = "api.simon.chummydns.com";
    public static ExecutorService pool = Executors.newCachedThreadPool();


    Main() {
        // server
        HttpsServer server = new HttpsServer("key/key.keystore", "key/key.properties");
        server.start(443);

        server.createContext("/NCKU/", req -> pool.submit(() -> {
            String path = req.getRequestURI().getPath();
            Headers responseHeader = req.getResponseHeaders();
            OutputStream response = req.getResponseBody();
            System.out.println(path);

            try {
                if (path.equals("/NCKU/")) {
                    responseHeader.set("Content-Type", "text/html; charset=utf-8");
                    File file = new File("./index.html");

                    InputStream in = Files.newInputStream(file.toPath());
                    req.sendResponseHeaders(200, in.available());
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        response.write(buff, 0, len);

                } else {
                    File file = new File("./", path.substring(6));
                    if (!file.exists())
                        req.sendResponseHeaders(404, 0);
                    else {
                        if (path.endsWith(".js"))
                            responseHeader.set("Content-Type", "text/javascript; charset=utf-8");
                        else if (path.endsWith(".css"))
                            responseHeader.set("Content-Type", "text/css; charset=utf-8");

                        InputStream in = Files.newInputStream(file.toPath());
                        req.sendResponseHeaders(200, in.available());
                        byte[] buff = new byte[1024];
                        int len;
                        while ((len = in.read(buff, 0, buff.length)) > 0)
                            response.write(buff, 0, len);
                    }
                }
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("Done");
        }));

        server.createContext("/api/login", new Login());
        server.createContext("/api/logout", new Logout());
        server.createContext("/api/courseSchedule", new CourseSchedule());
        server.createContext("/api/search", new Search());

        System.out.println("Server started");
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
