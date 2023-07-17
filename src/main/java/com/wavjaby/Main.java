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
    public static final URI courseQueryNckuUri;
    public static final URI portalNckuOrgUri;
    public static final URI stuIdSysNckuOrgUri;

    static {
        courseNckuOrgUri = URI.create(courseNckuOrg);
        courseQueryNckuUri = URI.create(courseQueryNckuOrg);
        portalNckuOrgUri = URI.create(portalNckuOrg);
        stuIdSysNckuOrgUri = URI.create(stuIdSysNckuOrg);
    }

    public static final String[] accessControlAllowOrigin = {
            "https://api.simon.chummydns.com",
            "https://wavjaby.github.io"
    };
    public static final String cookieDomain = "simon.chummydns.com";
    private final HttpServer server;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private boolean running = false;
    public static final File cacheFolder = new File("cache");


    Main() {
//        System.setProperty("javax.net.debug", "ssl,handshake");

        PropertiesReader serverSettings = new PropertiesReader("./server.properties");

        server = new HttpServer(serverSettings);
        if (!server.opened) return;
        if (!cacheFolder.exists())
            if (!cacheFolder.mkdir())
                logger.err("Cache folder can not create");
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll));
        ProxyManager proxyManager = new ProxyManager(serverSettings);

        SQLite sqLite = new SQLite();
        registerModule(sqLite);
        registerModule(new FileHost(serverSettings), "/NCKUpp/");
        registerModule(new IP(), "/api/ip");
        registerModule(new Route(), "/api/route");
        registerModule(new WebSocket(), "/api/v0/socket");

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
        registerModule(new PreCourseSchedule(proxyManager), "/api/preCourseSchedule");
        registerModule(new ExtractUrl(proxyManager), "/api/extract");
        registerModule(new PreferenceAdjust(proxyManager), "/api/preferenceAdjust");
        registerModule(new StudentIdentificationSystem(), "/api/stuIdSys");

        server.start();
        logger.log("Server started, " + server.hostname + ':' + server.port);

        startModules();
        logger.log("Ready");

//        GetCourseDataUpdate getCourseDataUpdate = new GetCourseDataUpdate(search, watchDog, serverSettings);

//        long start = System.currentTimeMillis();
//        Thread t1 = new Thread(() ->
//                logger.log(robotCode.getCode(courseNckuOrg + "/index.php?c=portal&m=robot", null, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA))
//        );
//        Thread t2 = new Thread(() ->
//                logger.log(robotCode.getCode(courseNckuOrg + "/index.php?c=portal&m=robot", null, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA))
//        );
//        Thread t3 =  new Thread(() ->
//                logger.log(robotCode.getCode(courseNckuOrg + "/index.php?c=portal&m=robot", null, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA))
//        );
//        Thread t4 =  new Thread(() ->
//                logger.log(robotCode.getCode(courseNckuOrg + "/index.php?c=portal&m=robot", null, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA))
//        );
//        Thread t5 =  new Thread(() ->
//                logger.log(robotCode.getCode(courseNckuOrg + "/index.php?c=portal&m=robot", null, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA))
//        );
//        Thread t6 =  new Thread(() ->
//                logger.log(robotCode.getCode(courseNckuOrg + "/index.php?c=portal&m=robot", null, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA))
//        );
//        t1.start();
//        t2.start();
//        t3.start();
//        t4.start();
//        t5.start();
//        t6.start();
//        try {
//            t1.join();
//            t2.join();
//            t3.join();
//            t4.join();
//            t5.join();
//            t6.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        logger.log(System.currentTimeMillis() - start);


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
            logger.log("##### " + module.getTag() + " Ready " + (System.currentTimeMillis() - start) + "ms #####");
        }
    }

    private void stopAll() {
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
