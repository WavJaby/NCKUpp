package com.wavjaby;

import com.wavjaby.api.*;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class Main {
    private static final String TAG = "[Main] ";
    public static final String courseNcku = "course.ncku.edu.tw";
    public static final String portalNcku = "fs.ncku.edu.tw";

    public static final String courseNckuOrg = "https://" + courseNcku;
    public static final String courseQueryNckuOrg = "https://course-query.acad.ncku.edu.tw";
    public static final String portalNckuOrg = "https://" + portalNcku;

    public static final String[] accessControlAllowOrigin = {
            "https://api.simon.chummydns.com",
            "https://wavjaby.github.io",
            "http://localhost:63342",
    };
    public static final String cookieDomain = "simon.chummydns.com";

    private HttpServer server;
    private final Map<String, Module> modules = new HashMap<>();


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
            }
            InputStream in = Files.newInputStream(file.toPath());
            serverSettings.load(in);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        server = new HttpServer(serverSettings);
        if (!server.Opened) return;

        UrSchool urSchool = new UrSchool();
        Search search = new Search(urSchool);
        RobotCode robotCode = new RobotCode(serverSettings);
        registerModule("FileHost", new FileHost(serverSettings), "/NCKUpp/");

        // API
        registerModule("NCKUHub", new NCKUHub(), "/api/nckuhub");
        registerModule("UrSchool", urSchool, "/api/urschool");
        registerModule("Search", search, "/api/search");
        registerModule("Login", new Login(), "/api/login");
        registerModule("Logout", new Logout(), "/api/logout");
        registerModule("CourseSchedule", new CourseSchedule(), "/api/courseSchedule");
        registerModule("ExtractUrl", new ExtractUrl(), "/api/extract");
        registerModule("RobotCode", robotCode, "/api/robotCode");
        registerModule("PreferenceEnter", new PreferenceEnter(robotCode), "/api/preferenceEnter");

        server.start();
        Logger.log(TAG, "Server started, " + server.hostname + ':' + server.port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Module module : modules.values())
                module.stop();
        }));

        for (Module module : modules.values())
            module.start();

//        GetCourseDataUpdate getCourseDataUpdate = new GetCourseDataUpdate(search);
    }

    private void registerModule(String moduleName, Module module, String contextPath) {
        if (contextPath != null)
            server.createContext(contextPath, module.getHttpHandler());
        modules.put(moduleName, module);
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
