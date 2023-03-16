package com.wavjaby;

import com.wavjaby.api.*;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;


public class Main {
    private static final String TAG = "[Main] ";
    public static final String courseNcku = "course.ncku.edu.tw";

    public static final String courseNckuOrg = "https://" + courseNcku;
    public static final String portalNcku = "fs.ncku.edu.tw";
    public static final String portalNckuOrg = "https://" + portalNcku;
    public static final String stuIdSysNcku = "qrys.ncku.edu.tw";
    public static final String stuIdSysNckuOrg = "https://" + stuIdSysNcku;

    public static final URI courseNckuOrgUri;
    public static final URI portalNckuOrgUri;
    public static final URI stuIdSysNckuOrgUri;

    static {
        try {
            courseNckuOrgUri = new URI(courseNckuOrg);
            portalNckuOrgUri = new URI(portalNckuOrg);
            stuIdSysNckuOrgUri = new URI(stuIdSysNckuOrg);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String courseQueryNckuOrg = "https://course-query.acad.ncku.edu.tw";

    public static final String[] accessControlAllowOrigin = {
            "https://api.simon.chummydns.com",
            "https://wavjaby.github.io"
    };
    public static final String cookieDomain = "simon.chummydns.com";
    private HttpServer server;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private boolean running = false;


    Main() {
//        System.setProperty("javax.net.debug", "ssl,handshake");

        Properties serverSettings = new Properties();
        try {
            File file = new File("./server.properties");
            if (!file.exists()) {
                Logger.log(TAG, "Server properties not found, create default");
                InputStream stream = Main.class.getResourceAsStream("/server.properties");
                if (stream == null) {
                    Logger.log(TAG, "Default server properties not found");
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

        SQLite sqLite = new SQLite();
        registerModule(sqLite);
        registerModule(new FileHost(serverSettings), "/NCKUpp/");
        ProxyManager proxyManager = new ProxyManager();

        // API
        DeptWatchDog watchDog = new DeptWatchDog(sqLite);
        registerModule(watchDog, "/api/watchdog");
        UrSchool urSchool = new UrSchool();
        registerModule(urSchool, "/api/urschool");
        RobotCode robotCode = new RobotCode(serverSettings, proxyManager);
        registerModule(robotCode, "/api/robotCode");
        Search search = new Search(urSchool, robotCode, proxyManager);
        registerModule(search, "/api/search");
        registerModule(new AllDept(search), "/api/alldept");
        registerModule(new Login(search, sqLite, proxyManager), "/api/login");
        registerModule(new CourseFunctionButton(robotCode), "/api/courseFuncBtn");
        registerModule(new NCKUHub(), "/api/nckuhub");
        registerModule(new Logout(proxyManager), "/api/logout");
        registerModule(new CourseSchedule(proxyManager), "/api/courseSchedule");
        registerModule(new ExtractUrl(proxyManager), "/api/extract");
        registerModule(new PreferenceAdjust(proxyManager), "/api/preferenceAdjust");
        registerModule(new StudentIdentificationSystem(), "/api/stuIdSys");

        server.start();
        Logger.log(TAG, "Server started, " + server.hostname + ':' + server.port);

        startModules();
        Logger.log(TAG, "Ready");

//        GetCourseDataUpdate getCourseDataUpdate = new GetCourseDataUpdate(search, watchDog, serverSettings);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll));

        // Stop
        new Scanner(System.in).nextLine();
        stopAll();
    }

    private void startModules() {
        if (running) return;
        running = true;
        for (Module module : modules.values()) {
            long start = System.currentTimeMillis();
            module.start();
            Logger.log("##### ", module.getTag() + "Ready " + (System.currentTimeMillis() - start) + "ms #####");
        }
    }

    private void stopAll() {
        if (!running) return;
        running = false;
        for (Module module : modules.values()) {
            module.stop();
            Logger.log("##### ", module.getTag() + "Stopped #####");
        }
        server.stop();
    }

    private void registerModule(EndpointModule module, String contextPath) {
        if (contextPath != null)
            server.createContext(contextPath, module.getHttpHandler());
        modules.put(module.getTag(), module);
    }

    private void registerModule(Module module) {
        modules.put(module.getTag(), module);
    }

    @SuppressWarnings("ALL")
    public static void main(String[] args) {
        new Main();
    }
}
