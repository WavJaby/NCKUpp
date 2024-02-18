package com.wavjaby;

import com.wavjaby.api.*;
import com.wavjaby.api.login.Login;
import com.wavjaby.api.search.RobotCheck;
import com.wavjaby.api.search.Search;
import com.wavjaby.lib.Lib;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.lib.restapi.RestApiServer;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLDriver;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import static com.wavjaby.lib.Lib.executorShutdown;
import static com.wavjaby.lib.Lib.setFilePermission;


public class Main {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36";
    private static final String TAG = "Main";
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
    private final RestApiServer server;
    private final List<Module> modules = new ArrayList<>();
    private boolean running = false;
    public static final File cacheFolder = new File("cache");


    Main() {
//        System.setProperty("javax.net.debug", "ssl,handshake");
        // Read settings and logger
        PropertiesReader serverSettings = new PropertiesReader("./server.properties");
        Logger.setLogFile(serverSettings.getProperty("logFilePath", "./server.log"));
        Logger.setTraceClassFilter(serverSettings.getProperty("traceFilter", "com.wavjaby"));
        cookieDomain = serverSettings.getProperty("domain", "localhost");

        // Set log permission
        setFilePermission(Logger.getLogFile(), Lib.userPrincipal, Lib.groupPrincipal, Lib.filePermission);

        if (!cacheFolder.exists())
            if (!cacheFolder.mkdir())
                logger.err("Cache folder can not create");

        // Create RestAPI server
        server = new RestApiServer(serverSettings);
        if (!server.ready || !server.start()) return;


        // Service
        addModule(new FileHost(serverSettings));
        ProxyManager proxyManager = new ProxyManager(serverSettings);
        addModule(proxyManager);
        String mainDatabasePath = "./database.db";
        SQLDriver SQLDriver = new SQLDriver(mainDatabasePath,"jdbc:sqlite:" + mainDatabasePath,
                "./sqlite-jdbc-3.40.0.0.jar", "org.sqlite.JDBC", null, null);
        addModule(SQLDriver);
        addModule(new WebSocket());
        addModule(new IP());
//        addModule(new Route());

        // API
        RobotCode robotCode = new RobotCode(serverSettings, proxyManager);
        addModule(robotCode);
        addModule(new NCKUHub());
        UrSchool urSchool = new UrSchool();
        addModule(urSchool);
        CourseFuncBtn courseFunctionButton = new CourseFuncBtn(proxyManager, robotCode);
        addModule(courseFunctionButton);
        CourseSchedule courseSchedule = new CourseSchedule(proxyManager);
        addModule(courseSchedule);
        RobotCheck robotCheck = new RobotCheck(robotCode, proxyManager); // Lib
        AllDept allDept = new AllDept(robotCheck, proxyManager);
        addModule(allDept);
        Search search = new Search(urSchool, allDept, robotCheck, proxyManager);
        addModule(search);
        Login login = new Login(search, courseFunctionButton, courseSchedule, SQLDriver, proxyManager);
        addModule(login);
        DeptWatchdog watchDog = new DeptWatchdog(login, SQLDriver);
        addModule(watchDog);
        CourseEnrollmentTracker enrollmentTracker = new CourseEnrollmentTracker(search, serverSettings);
        addModule(enrollmentTracker);
        addModule(new Logout(proxyManager));
        addModule(new A9Registered(proxyManager));
        addModule(new Profile(login, SQLDriver));
        addModule(new CourseRegister(proxyManager, robotCode));
        addModule(new ExtractUrl(proxyManager));
        addModule(new PreferenceAdjust(proxyManager));
        addModule(new HomeInfo(proxyManager));
        addModule(new UsefulWebsite());
        addModule(new ClientDebugLog());
        addModule(new StudentIdSys(SQLDriver, enrollmentTracker, login));

        if (serverSettings.getPropertyBoolean("courseWatcher", false))
            addModule(new CourseWatcher(search, allDept, watchDog, serverSettings));


//        logger.log("Server started, " + server.hostname + ':' + server.port);

        long start = System.currentTimeMillis();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopModules));
        startModules();
        logger.log("Ready in " + (System.currentTimeMillis() - start) + "ms");
        server.printStructure();


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
                    proxyManager.getUsingProxy();
                    break;
                case "p":
                    proxyManager.getUsingProxy();
                    break;
            }
        }

        stopModules();
        logger.log("Server stopped");
    }


    final Map<Module, List<Class<?>>> moduleDependencies = new HashMap<>();
    final List<Class<?>> loadedModule = new ArrayList<>();

    private void startModules() {
        if (running) return;
        running = true;
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4, new ThreadFactory(TAG + "-Module"));
        Semaphore lock = new Semaphore(executor.getMaximumPoolSize());
        CountDownLatch taskCount = new CountDownLatch(modules.size());
        for (Module module : modules) {
            // Get dependencies in constructor
            List<Class<?>> dependencies = new ArrayList<>();
            for (Constructor<?> constructor : module.getClass().getDeclaredConstructors()) {
                // Get parameters
                for (Parameter parameter : constructor.getParameters()) {
                    if (parameter.getType() == module.getClass())
                        continue;
                    // Check if parameter is module
                    Class<?> dependencyModule = null;
                    for (Class<?> anInterface : parameter.getType().getInterfaces()) {
                        if (anInterface == Module.class) {
                            dependencyModule = anInterface;
                            break;
                        }
                    }
                    if (dependencyModule == null || loadedModule.contains(parameter.getType()))
                        continue;

                    dependencies.add(parameter.getType());
                }
            }
            if (!dependencies.isEmpty()) {
                moduleDependencies.put(module, dependencies);
//                logger.log(dependencies);
                continue;
            }

            // Start load module
            try {
                lock.acquire();
            } catch (InterruptedException e) {
                logger.errTrace(e);
                return;
            }
            executor.execute(() -> addStartModuleTask(module, lock, taskCount, executor));
        }
        try {
            taskCount.await();
        } catch (InterruptedException e) {
            logger.errTrace(e);
        }
        executorShutdown(executor, 1000, "Start executor");
        moduleDependencies.clear();
        loadedModule.clear();
    }

    private void addStartModuleTask(Module module, Semaphore lock, CountDownLatch taskCount, ThreadPoolExecutor executor) {
        startModule(module);
        // Add to loaded module
        synchronized (loadedModule) {
            loadedModule.add(module.getClass());
        }
        // Check module have dependencies
        synchronized (moduleDependencies) {
            List<Module> removeList = new ArrayList<>();
            for (Map.Entry<Module, List<Class<?>>> i : moduleDependencies.entrySet()) {
                List<Class<?>> value = i.getValue();
                synchronized (value) {
                    value.remove(module.getClass());
                    // Load module
                    if (value.isEmpty()) {
                        executor.execute(() -> addStartModuleTask(i.getKey(), null, taskCount, executor));
                        removeList.add(i.getKey());
                    }
                }
            }
            for (Module i : removeList) {
                moduleDependencies.remove(i);
            }
        }
        if (lock != null)
            lock.release();
        taskCount.countDown();
    }

    private void startModule(Module module) {
        long start = System.currentTimeMillis();
        try {
            module.start();
            if (module.api())
                server.addEndpoint(module);
            logger.log("##### " + module.getTag() + " Ready " + (System.currentTimeMillis() - start) + "ms #####");
        } catch (Exception e) {
            logger.errTrace(e);
            logger.err("##### " + module.getTag() + " ERROR " + (System.currentTimeMillis() - start) + "ms #####");
        }
    }

    private void stopModules() {
        if (!running) return;
        running = false;
        for (Module module : modules) {
            logger.log("##### Stopping" + module.getTag() + " #####");
            module.stop();
//            Logger.log("##### ", module.getTag() + "Stopped #####");
        }
        server.stop();
        Logger.stopAndFlushLog();
    }

    private void addModule(Module module) {
        modules.add(module);
    }

    public static void main(String[] args) {
        new Main();
    }
}
