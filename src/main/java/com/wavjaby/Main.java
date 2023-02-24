package com.wavjaby;

import com.wavjaby.api.*;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
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
            "http://localhost:52441",
    };
    public static final String cookieDomain = "simon.chummydns.com";

    private HttpServer server;
    private final Map<String, Module> modules = new LinkedHashMap<>();


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

        RobotCode robotCode = new RobotCode(serverSettings);
        SQLite sqLite = new SQLite();
        registerModule("FileHost", new FileHost(serverSettings), "/NCKUpp/");
        registerModule("SQLite", sqLite);

        // API
        DeptWatchDog watchDog = new DeptWatchDog(sqLite);
        UrSchool urSchool = new UrSchool();
        Search search = new Search(urSchool, robotCode);
        registerModule("DeptWatchDog", watchDog, "/api/watchdog");
        registerModule("NCKUHub", new NCKUHub(), "/api/nckuhub");
        registerModule("UrSchool", urSchool, "/api/urschool");
        registerModule("AllDept", new AllDept(search), "/api/alldept");
        registerModule("Search", search, "/api/search");
        registerModule("Login", new Login(sqLite), "/api/login");
        registerModule("Logout", new Logout(), "/api/logout");
        registerModule("CourseSchedule", new CourseSchedule(), "/api/courseSchedule");
        registerModule("ExtractUrl", new ExtractUrl(), "/api/extract");
        registerModule("RobotCode", robotCode, "/api/robotCode");
        registerModule("CourseFunctionButton", new CourseFunctionButton(robotCode), "/api/courseFuncBtn");
        registerModule("PreferenceAdjust", new PreferenceAdjust(), "/api/preferenceAdjust");

        server.start();
        Logger.log(TAG, "Server started, " + server.hostname + ':' + server.port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Module module : modules.values())
                module.stop();
        }));

        for (Module module : modules.values())
            module.start();
        Logger.log(TAG, "Ready");

//        GetCourseDataUpdate getCourseDataUpdate = new GetCourseDataUpdate(search, watchDog, serverSettings);
    }

    private void registerModule(String moduleName, EndpointModule module, String contextPath) {
        if (contextPath != null)
            server.createContext(contextPath, module.getHttpHandler());
        modules.put(moduleName, module);
    }

    private void registerModule(String moduleName, Module module) {
        modules.put(moduleName, module);
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
