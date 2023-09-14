package com.wavjaby;

import com.wavjaby.api.*;
import com.wavjaby.lib.HttpServer;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;


public class Main {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36";
    private static final String TAG = "[Main]";
    private static final Logger logger = new Logger(TAG);
    public static final String courseNcku = "course.ncku.edu.tw";

    public static final String courseNckuOrg = "https://" + courseNcku;
    public static final String portalNcku = "fs.ncku.edu.tw";
    public static final String portalNckuOrg = "https://" + portalNcku;
    public static final String stuIdSysNcku = "qrys.ncku.edu.tw";
    public static final String stuIdSysNckuOrg = "https://" + stuIdSysNcku;
    public static final String courseQueryNcku = "course-query.acad.ncku.edu.tw";
    public static final String courseQueryNckuOrg = "https://" + courseQueryNcku;

    public static final URI courseNckuOrgUri;
    public static final URI courseQueryNckuOrgUri;
    public static final URI portalNckuOrgUri;
    public static final URI stuIdSysNckuOrgUri;

    static {
        courseNckuOrgUri = URI.create(courseNckuOrg);
        courseQueryNckuOrgUri = URI.create(courseQueryNckuOrg);
        portalNckuOrgUri = URI.create(portalNckuOrg);
        stuIdSysNckuOrgUri = URI.create(stuIdSysNckuOrg);
    }

    public static final String[] accessControlAllowOrigin = {
            "https://api.simon.chummydns.com",
            "https://wavjaby.github.io"
    };
    public static String cookieDomain;
    private final HttpServer server;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private boolean running = false;
    public static final File cacheFolder = new File("cache");


    Main() {
//        System.setProperty("javax.net.debug", "ssl,handshake");

        PropertiesReader serverSettings = new PropertiesReader("./server.properties");
        cookieDomain = serverSettings.getProperty("domain", "localhost");

        server = new HttpServer(serverSettings);
        if (!server.opened) return;
        if (!cacheFolder.exists())
            if (!cacheFolder.mkdir())
                logger.err("Cache folder can not create");
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopModules));

        ProxyManager proxyManager = new ProxyManager(serverSettings);
        registerModule(proxyManager);

        SQLite sqLite = new SQLite();
        registerModule(sqLite);
        registerModule(new FileHost(serverSettings), "/NCKUpp/");
        registerModule(new IP(), "/api/ip");
        registerModule(new Route(), "/api/route");
        registerModule(new WebSocket(), "/api/v0/socket");

        // API
        UrSchool urSchool = new UrSchool();
        registerModule(urSchool, "/api/urschool");
        RobotCode robotCode = new RobotCode(serverSettings, proxyManager);
        registerModule(robotCode, "/api/robotCode");
        Search search = new Search(urSchool, robotCode, proxyManager);
        registerModule(search, "/api/search");
        CourseFunctionButton courseFunctionButton = new CourseFunctionButton(proxyManager, robotCode);
        registerModule(courseFunctionButton, "/api/courseFuncBtn");
        CourseSchedule courseSchedule = new CourseSchedule(proxyManager);
        registerModule(courseSchedule, "/api/courseSchedule");
        Login login = new Login(search, courseFunctionButton, courseSchedule, sqLite, proxyManager);
        registerModule(login, "/api/login");
        DeptWatchDog watchDog = new DeptWatchDog(login, sqLite);
        registerModule(watchDog, "/api/watchdog");

        registerModule(new Profile(login, sqLite), "/api/profile");
        registerModule(new AllDept(search), "/api/alldept");
        registerModule(new HomeInfo(proxyManager), "/api/homeInfo");
        registerModule(new Logout(proxyManager), "/api/logout");
        registerModule(new ExtractUrl(proxyManager), "/api/extract");
        registerModule(new PreferenceAdjust(proxyManager), "/api/preferenceAdjust");
        registerModule(new A9Registered(proxyManager), "/api/A9Registered");
        registerModule(new CourseRegister(proxyManager), "/api/courseRegister");

        registerModule(new NCKUHub(), "/api/nckuhub");
        registerModule(new UsefulWebsite(), "/api/usefulWebsite");
        registerModule(new ClientDebugLog(), "/api/clientDebugLog");
        registerModule(new StudentIdentificationSystem(), "/api/stuIdSys");

        server.start();
        logger.log("Server started, " + server.hostname + ':' + server.port);

        startModules();
        logger.log("Ready");

        if (serverSettings.getPropertyBoolean("getCourseUpdate", false))
            new GetCourseDataUpdate(search, watchDog, serverSettings);

        // Stop
        Scanner scanner = new Scanner(System.in);
        String command;
        while (!(command = scanner.nextLine()).isEmpty()) {
            switch (command) {
                case "np":
                    proxyManager.nextProxy();
                    break;
                case "up":
                    proxyManager.updateProxy();
                    break;
                case "p":
                    proxyManager.getUsingProxy();
                    break;
            }
        }

        stopModules();
        logger.log("Server stopped");
    }

    private void startModules() {
        if (running) return;
        running = true;
        for (Module module : modules.values()) {
            long start = System.currentTimeMillis();
            try {
                module.start();
                logger.log("##### " + module.getTag() + " Ready " + (System.currentTimeMillis() - start) + "ms #####");
            } catch (Exception e) {
                logger.errTrace(e);
                logger.err("##### " + module.getTag() + " ERROR " + (System.currentTimeMillis() - start) + "ms #####");
            }
        }
    }

    private void stopModules() {
        if (!running) return;
        running = false;
        for (Module module : modules.values()) {
            logger.log("##### Stopping" + module.getTag() + " #####");
            module.stop();
//            Logger.log("##### ", module.getTag() + "Stopped #####");
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
