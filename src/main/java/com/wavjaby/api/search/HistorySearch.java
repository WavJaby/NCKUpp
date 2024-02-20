package com.wavjaby.api.search;

import com.wavjaby.Main;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.AllDept;
import com.wavjaby.api.RobotCode;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.Cookie;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.lib.Lib;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLDriver;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wavjaby.Main.courseQueryNckuOrg;
import static com.wavjaby.Main.courseQueryNckuOrgUri;
import static com.wavjaby.lib.Lib.*;

public class HistorySearch {
    private static final String FILE_PATH = "api_file/CourseHistory";
    private static final Logger logger = new Logger("History");
    private final ProxyManager proxyManager;
    private final RobotCheck robotCheck;

    private enum Language {
        TW("cht"),
        EN("eng"),
        ;

        public final String code;

        Language(String code) {
            this.code = code;
        }
    }

    private static class RoomData {
        final String buildingId, roomId;
        String roomNameTW, roomNameEN;

        private RoomData(String buildingId, String roomId, String roomNameTW, String roomNameEN) {
            this.buildingId = buildingId;
            this.roomId = roomId;
            this.roomNameTW = roomNameTW;
            this.roomNameEN = roomNameEN;
        }

        private RoomData(String buildingId, String roomId) {
            this.buildingId = buildingId;
            this.roomId = roomId;
            this.roomNameTW = this.roomNameEN = null;
        }

        public void setRoomNameTW(String roomNameTW) {
            this.roomNameTW = roomNameTW;
        }

        public void setRoomNameEN(String roomNameEN) {
            this.roomNameEN = roomNameEN;
        }

        @Override
        public int hashCode() {
            return buildingId.hashCode() + roomId.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof RoomData) {
                RoomData other = (RoomData) obj;
                return Objects.equals(other.buildingId, buildingId) && Objects.equals(other.roomId, roomId);
            }
            return false;
        }
    }

    public HistorySearch(ProxyManager proxyManager, RobotCheck robotCheck) {
        this.proxyManager = proxyManager;
        this.robotCheck = robotCheck;
    }

    public static void main(String[] args) {
        PropertiesReader serverSettings = new PropertiesReader("./server.properties");
        ProxyManager proxyManager = new ProxyManager(serverSettings);
        proxyManager.start();
        RobotCode robotCode = new RobotCode(serverSettings, proxyManager);
        robotCode.start();
        RobotCheck robotCheck = new RobotCheck(robotCode, proxyManager);
        AllDept allDept = new AllDept(robotCheck, proxyManager);
        allDept.start();

        HistorySearch historySearch = new HistorySearch(proxyManager, robotCheck);
        historySearch.writeDatabase();
//        historySearch.search();

//        CookieStore[] cookieCache = readCookieCache(4);
//        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
////        for (int year = 112; year >= 95; year--) {
//        for (int year = 100; year >= 95; year--) {
//            long startYearTime = System.currentTimeMillis();
//            logger.log("########## Fetch course: " + year);
//            CountDownLatch tasks = new CountDownLatch(4);
//            CourseHistorySearchQuery history = new CourseHistorySearchQuery(year, 1, year, 1);
//            pool.execute(() -> {
//                historySearch.search2(Language.TW, history, cookieCache[0]);
//                tasks.countDown();
//            });
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                break;
//            }
//            pool.execute(() -> {
//                historySearch.search2(Language.EN, history, cookieCache[1]);
//                tasks.countDown();
//            });
//            CourseHistorySearchQuery history2 = new CourseHistorySearchQuery(year, 0, year, 0);
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                break;
//            }
//            pool.execute(() -> {
//                historySearch.search2(Language.TW, history2, cookieCache[2]);
//                tasks.countDown();
//            });
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                break;
//            }
//            pool.execute(() -> {
//                historySearch.search2(Language.EN, history2, cookieCache[3]);
//                tasks.countDown();
//            });
//            try {
//                tasks.await();
//            } catch (InterruptedException e) {
//                break;
//            }
//            logger.log("########## Fetch course: " + year + ", use " + ((System.currentTimeMillis() - startYearTime) / 1000) + "s");
//        }
//        executorShutdown(pool, 1000, "HistorySearch");
//        saveCookieCache(cookieCache);


        if (historySearch.sqlDriver != null)
            historySearch.sqlDriver.stop();
        robotCode.stop();
        proxyManager.stop();
    }

    private SQLDriver sqlDriver;
    private PreparedStatement getRoomStat, addRoomStat, editRoomStat,
            getTagStat, addTagStat,
            getInstructorStat, addInstructorStat;

    private RoomData roomGet(String location, String roomId) {
        try {
            getRoomStat.setString(1, location);
            getRoomStat.setString(2, roomId);
            ResultSet resultSet = getRoomStat.executeQuery();
            getRoomStat.clearParameters();
            if (!resultSet.next()) {
                resultSet.close();
                return null;
            }
            RoomData timeData = new RoomData(
                    resultSet.getString("building_id"),
                    resultSet.getString("room_id"),
                    resultSet.getString("name_tw"),
                    resultSet.getString("name_en")
            );
            resultSet.close();
            return timeData;
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            return null;
        }
    }

    private void roomAdd(RoomData roomData) {
        try {
            addRoomStat.setString(1, roomData.buildingId);
            addRoomStat.setString(2, roomData.roomId);
            addRoomStat.setNString(3, roomData.roomNameTW);
            addRoomStat.setString(4, roomData.roomNameEN);
            addRoomStat.executeUpdate();
            addRoomStat.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    private void roomEdit(RoomData roomData) {
        try {
            editRoomStat.setString(3, roomData.buildingId);
            editRoomStat.setString(4, roomData.roomId);

            editRoomStat.setNString(1, roomData.roomNameTW);
            editRoomStat.setString(2, roomData.roomNameEN);

            editRoomStat.executeUpdate();
            editRoomStat.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    private boolean tagGet(String nameTW, String nameEN) {
        try {
            getTagStat.setNString(1, nameTW);
            getTagStat.setString(2, nameEN);
            ResultSet resultSet = getTagStat.executeQuery();
            getTagStat.clearParameters();
            if (!resultSet.next()) {
                resultSet.close();
                return false;
            }
            resultSet.close();
            return true;
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            return false;
        }
    }

    private void tagAdd(String nameTW, String nameEN, String color, String url) {
        try {
            addTagStat.setNString(1, nameTW);
            addTagStat.setNString(2, nameEN);
            addTagStat.setString(3, color);
            addTagStat.setString(4, url);
            addTagStat.executeUpdate();
            addTagStat.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    private boolean instructorGet(String nameTW, String nameEN) {
        try {
            getInstructorStat.setNString(1, nameTW);
            getInstructorStat.setString(2, nameEN);
            ResultSet resultSet = getInstructorStat.executeQuery();
            getInstructorStat.clearParameters();
            if (!resultSet.next()) {
                resultSet.close();
                return false;
            }
            resultSet.close();
            return true;
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            return false;
        }
    }

    private void instructorAdd(String nameTW, String nameEN, String urschoolId) {
        try {
            addInstructorStat.setNString(1, nameTW);
            addInstructorStat.setNString(2, nameEN);
            addInstructorStat.setString(3, urschoolId);
            addInstructorStat.executeUpdate();
            addInstructorStat.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    public void writeDatabase() {
        PropertiesReader properties = new PropertiesReader("database.properties");
        sqlDriver = new SQLDriver("course_data.mv.db", "jdbc:h2:file:./course_data;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE",
                properties.getProperty("driverPath"), properties.getProperty("driverClass"),
                properties.getProperty("user"), properties.getProperty("password"));
        sqlDriver.start();
        try {
            java.sql.Connection connection = sqlDriver.getDatabase();
            getRoomStat = connection.prepareStatement("SELECT * FROM \"room\" WHERE \"building_id\"=? AND \"room_id\"=?");
            addRoomStat = connection.prepareStatement("INSERT INTO \"room\" VALUES (?,?,?,?)");
            editRoomStat = connection.prepareStatement("UPDATE \"room\" SET \"name_tw\"=?,\"name_en\"=? WHERE \"building_id\"=? AND \"room_id\"=?");

            getTagStat = connection.prepareStatement("SELECT * FROM \"tags\" WHERE \"name_tw\"=? OR \"name_en\"=?");
            addTagStat = connection.prepareStatement("INSERT INTO \"tags\" (\"name_tw\",\"name_en\",\"color\",\"url\") VALUES (?,?,?,?)");

            getInstructorStat = connection.prepareStatement("SELECT * FROM \"instructor\" WHERE \"name_tw\"=? OR \"name_en\"=?");
            addInstructorStat = connection.prepareStatement("INSERT INTO \"instructor\" (\"name_tw\",\"name_en\",\"urschool_id\") VALUES (?,?,?)");
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            sqlDriver.stop();
            return;
        }

        logger.log("writeDatabase");
        CourseHistorySearchQuery historySearch = new CourseHistorySearchQuery(
                112, 1, 112, 1
        );
        List<CourseData> allCourseDataListTW = loadCourseList(FILE_PATH, Language.TW, historySearch);
        if (allCourseDataListTW == null)
            return;
        List<CourseData> allCourseDataListEN = loadCourseList(FILE_PATH, Language.EN, historySearch);
        if (allCourseDataListEN == null)
            return;
        Map<String, String[]> allDept = loadAllDept("api_file/allDept.json");
        if (allDept == null)
            return;

        HashMap<String, CourseData> courseSerialMapEN = new HashMap<>();
        // Add room
        HashSet<RoomData> rooms = new HashSet<>();
        for (CourseData courseData : allCourseDataListTW) {
            if (courseData.timeList == null) continue;
            // Get room name tw
            for (CourseData.TimeData timeData : courseData.timeList) {
                if (!timeData.roomExist()) continue;
                rooms.add(new RoomData(timeData.buildingId, timeData.roomId, timeData.roomName, null));
            }
        }
        for (CourseData courseData : allCourseDataListEN) {
            String key = getCourseKey(courseData);
            courseSerialMapEN.put(key, courseData);

            if (courseData.timeList == null) continue;
            // Get room name en
            for (CourseData.TimeData timeData : courseData.timeList) {
                if (!timeData.roomExist()) continue;
                RoomData roomData = new RoomData(timeData.buildingId, timeData.roomId, null, timeData.roomName);
                boolean notExist = true;
                for (RoomData room : rooms) {
                    if (room.equals(roomData)) {
                        room.setRoomNameEN(roomData.roomNameEN);
                        notExist = false;
                        break;
                    }
                }
                if (notExist)
                    rooms.add(roomData);
            }
        }
//        for (RoomData room : rooms) {
//            RoomData roomData = roomGet(room.buildingId, room.roomId);
//            if (roomData == null)
//                roomAdd(room);
//            else
//                roomEdit(room);
//        }
//
//        // Add tag
//        for (CourseData courseDataTW : allCourseDataListTW) {
//            if (courseDataTW.tags == null) continue;
//            String key = getCourseKey(courseDataTW);
//            CourseData courseDataEN = courseSerialMapEN.get(key);
//            assert courseDataEN.tags != null;
//            for (int i = 0; i < courseDataTW.tags.length; i++) {
//                CourseData.TagData tagTW = courseDataTW.tags[i];
//                CourseData.TagData tagEN = courseDataEN.tags[i];
//                if (!tagGet(tagTW.name, tagEN.name))
//                    tagAdd(tagTW.name, tagEN.name, tagTW.colorID, tagTW.url == null ? tagEN.url : tagTW.url);
//            }
//        }
//
//        // Add instructor
//        Set<String> names = new HashSet<>();
//        Set<String> namesRestore = new HashSet<>();
//        for (CourseData courseDataTW : allCourseDataListTW) {
//            if (courseDataTW.instructors == null) continue;
//
//            String key = getCourseKey(courseDataTW);
//            CourseData courseDataEN = courseSerialMapEN.get(key);
//            if (courseDataEN.instructors == null || courseDataEN.instructors.length != courseDataTW.instructors.length) {
//                names.addAll(Arrays.asList(courseDataTW.instructors));
//            } else
//                for (int i = 0; i < courseDataTW.instructors.length; i++) {
//                    String nameTW = courseDataTW.instructors[i];
//                    String nameEN = courseDataEN.instructors[i];
//                    // name EN too short
//                    if (nameEN.length() < 4)
//                        names.add(nameTW);
//                    else {
//                        namesRestore.add(nameTW);
//                        if (!instructorGet(nameTW, nameEN))
//                            instructorAdd(nameTW, nameEN, null);
//                    }
//                }
//        }
//        names.removeAll(namesRestore);
//        if (!names.isEmpty())
//            logger.warn("Instructor english name not found: " + names.size());
//        for (String name : names) {
//            if (!instructorGet(name, name))
//                instructorAdd(name, name, null);
//        }


        // Add course

        int count = 0;
        HashMap<String, CourseData> systemNumberCourseData = new HashMap<>();
        for (CourseData courseDataTW : allCourseDataListTW) {
            if (courseDataTW.courseName == null || courseDataTW.courseName.isEmpty())
                logger.log(courseDataTW);
//            CourseData sameSystemNumber = systemNumberCourseData.get(courseDataTW.systemNumber);
//            if (sameSystemNumber == null)
//                systemNumberCourseData.put(courseDataTW.systemNumber, courseDataTW);
//            else {
//                if (Objects.equals(sameSystemNumber.courseName, courseDataTW.courseName) &&
//                        Arrays.equals(sameSystemNumber.instructors, courseDataTW.instructors) &&
//                        Objects.equals(sameSystemNumber.courseNote, courseDataTW.courseNote)) {
////                    if(!Arrays.equals(sameSystemNumber.tags, courseDataTW.tags))
//                    if (!Objects.equals(sameSystemNumber.courseLimit, courseDataTW.courseLimit))
//                        logger.log("\n" + sameSystemNumber + "\n" + courseDataTW);
//                    count++;
//                }
////                logger.log(
////                        Objects.equals(sameSystemNumber.courseName, courseDataTW.courseName) + " "
////                );
//            }
        }
        logger.log(count);


        logger.log(allCourseDataListTW.size());
        logger.log(allCourseDataListEN.size());
    }

    private Map<String, String[]> loadAllDept(String path) {
        try {
            Map<String, String[]> allCourseDataList = new HashMap<>();
            JsonObject jsonObject = new JsonObject(Files.newInputStream(Paths.get(path)));
            for (Object i : jsonObject.getArray("deptGroup")) {
                for (Object j : ((JsonObject) i).getArray("dept")) {
                    JsonArray deptInfo = (JsonArray) j;
                    allCourseDataList.put(deptInfo.getString(0), new String[]{
                            deptInfo.getString(1),
                            deptInfo.getString(2),
                    });
                }
            }
            return allCourseDataList;
        } catch (IOException e) {
            logger.errTrace(e);
            return null;
        }
    }

    private List<CourseData> loadCourseList(String path, Language language, CourseHistorySearchQuery historySearch) {
        try {
            List<CourseData> allCourseDataList = new ArrayList<>();
            for (Object o : new JsonArray(Files.newInputStream(
                    Paths.get(path, historySearch + "_" + language.name() + ".json")
            ))) {
                CourseData courseData = new CourseData((JsonObject) o);
                allCourseDataList.add(courseData);
            }
            return allCourseDataList;
        } catch (IOException e) {
            logger.errTrace(e);
            return null;
        }
    }

    private String getCourseKey(CourseData courseData) {
        return courseData.departmentId + "_" + courseData.serialNumber + "_" + courseData.systemNumber + "_" + courseData.attributeCode +
                "_" + courseData.forGrade + "_" + courseData.forClassGroup;
    }

    private void search2(Language language, CourseHistorySearchQuery historySearch, CookieStore cookieStore) {
        long start = System.currentTimeMillis();

        Cookie.addCookie("cos_lang", language.code, courseQueryNckuOrgUri, cookieStore);
        logger.log("Getting SearchPage");
        String page = initSearchPage(cookieStore);
        if (page == null) {
            logger.err("Failed to get page");
            return;
        }
        String searchId = Search.getSearchID(page, new Search.SearchResult());
        if (searchId == null) {
            logger.err("Search id not found");
            return;
        }
        logger.log("Getting resource");
        getResources(cookieStore);

        logger.log("Start search");
        String query = createSearchQuery(null, "0", historySearch, searchId, cookieStore);
        // Simulate page reload
        if (query != null && query.equals("&m=en_query")) {
            logger.err("Can not create save query");
            return;
        }
        logger.log("Fetching result");
        long start1 = System.currentTimeMillis();
        Search.SearchResult result = getSearchResult(null, query, cookieStore, 60 * 1000);
        logger.log("Fetching result " + (System.currentTimeMillis() - start1) + "ms");
        if (!result.isSuccess()) {
            logger.err(result.getErrorString());
            return;
        }
        List<CourseData> allCourseData = result.getCourseDataList();

        int conflictCount = 0, nonSerialCount = 0;
        Map<String, CourseData> serialNumber = new HashMap<>();
        for (CourseData courseData : allCourseData) {
            if (courseData.serialNumber == null) {
                nonSerialCount++;
                continue;
            }
            if (serialNumber.containsKey(courseData.departmentId + '-' + courseData.serialNumber)) {
                conflictCount++;
                logger.log("\n" + courseData + "\n" + serialNumber.get(courseData.departmentId + '-' + courseData.serialNumber));
            } else
                serialNumber.put(courseData.departmentId + '-' + courseData.serialNumber, courseData);
        }
        logger.log("serial number count: " + serialNumber.size());
        logger.log("conflict count: " + conflictCount);
        logger.log("non-serial count: " + nonSerialCount);
        logger.log("Get " + allCourseData.size() + " course, use " + ((System.currentTimeMillis() - start) / 1000) + "s");

        File rawHistoryFileFolder = Lib.getDirectoryFromPath(FILE_PATH, true);
        if (!rawHistoryFileFolder.isDirectory())
            return;
        try {
            FileWriter rawHistoryFile = new FileWriter(new File(rawHistoryFileFolder,
                    historySearch + "_" + language.name() + ".json"
            ));
            rawHistoryFile.write(new JsonArray(allCourseData).toString());
            rawHistoryFile.close();
        } catch (IOException e) {
            logger.errTrace(e);
        }
    }

    public void search() {
        Language language = Language.EN;
        CourseHistorySearchQuery historySearch = new CourseHistorySearchQuery(
                112, 2, 112, 2
        );

        // Load cookies
        CookieStore[] cookieCache = readCookieCache(8);
        // Set language
        for (CookieStore store : cookieCache)
            Cookie.addCookie("cos_lang", language.code, courseQueryNckuOrgUri, store);

        long start = System.currentTimeMillis();
        // Get all dept
        logger.log("Getting all department");
        List<String> allDeptNo;
        String homePage = initSearchPage(cookieCache[0]);
        if (homePage == null) {
            logger.err("Failed to init search page");
            return;
        }
        String allDeptStr = findStringBetween(homePage, "var collist", "=", "<");
        if (allDeptStr == null) {
            logger.err("Department data not found");
            return;
        }
        allDeptNo = new ArrayList<>();
        JsonObject data = new JsonObject(allDeptStr);
        for (Object object : data.getMap().values()) {
            JsonObject i = (JsonObject) object;
            for (Object deptList : i.getArray("deptlist")) {
                JsonObject j = (JsonObject) deptList;
                allDeptNo.add(j.getString("dept_no"));
            }
        }
        logger.log(allDeptNo.size() + " department find");


        logger.log("Getting resource");
        ThreadPoolExecutor queryPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(cookieCache.length);
        Semaphore queryPoolLock = new Semaphore(queryPool.getMaximumPoolSize());
        String[] searchIds = new String[cookieCache.length];
        CountDownLatch resourceTasks = new CountDownLatch(cookieCache.length);
        AtomicBoolean resourceFailed = new AtomicBoolean();
        for (int i = 0; i < cookieCache.length; i++) {
            final int finalI = i;
            queryPool.submit(() -> {
                String page = initSearchPage(cookieCache[finalI]);
                if (page == null) {
                    logger.err("Failed to get page");
                    resourceFailed.set(true);
                    return;
                }
                searchIds[finalI] = Search.getSearchID(page, new Search.SearchResult());
                if (searchIds[finalI] == null) {
                    logger.err("Search id not found");
                    resourceFailed.set(true);
                    return;
                }
                getResources(cookieCache[finalI]);
            });
            resourceTasks.countDown();
        }
        try {
            resourceTasks.await();
        } catch (InterruptedException e) {
            logger.errTrace(e);
            return;
        }
        if (resourceFailed.get())
            return;


        logger.log("Start search");
        ThreadPoolExecutor resultPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(16);
        Semaphore resultPoolLock = new Semaphore(resultPool.getMaximumPoolSize());
        CountDownLatch tasks = new CountDownLatch(allDeptNo.size());
        List<CourseData> courseDataList = new ArrayList<>();
        Map<String, Integer> eachDeptCount = new HashMap<>();
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int i = 0, cacheIndex = 0; i < allDeptNo.size(); ) {
            // Abort remaining task if any error
            if (failed.get()) {
                tasks.countDown();
                i++;
                continue;
            }

            // Create save query
            final String deptNo = allDeptNo.get(i);

            try {
                queryPoolLock.acquire();
            } catch (InterruptedException e) {
                logger.errTrace(e);
                failed.set(true);
                return;
            }
            if (++cacheIndex == cookieCache.length)
                cacheIndex = 0;
            final CookieStore cookieStore = cookieCache[cacheIndex];
            final String searchId = searchIds[cacheIndex];
            queryPool.submit(() -> {
                long queryStart = System.currentTimeMillis();
                String query = null;
                // Get save query
                for (int j = 0; j < 3; j++) {
                    query = createSearchQuery(deptNo, null, historySearch, searchId, cookieStore);
                    // Simulate page reload
                    if (query != null && query.equals("&m=en_query")) {
                        logger.warn("Can not create save query");
                        getResources(cookieStore);
                        continue;
                    }
                    break;
                }
                logger.log("Searching dept: " + deptNo + ", " + (System.currentTimeMillis() - queryStart) + "ms");
                // Save query error
                if (query == null) {
                    failed.set(true);
                    queryPoolLock.release();
                    tasks.countDown();
                    return;
                }

                // Fetch result
                try {
                    resultPoolLock.acquire();
                } catch (InterruptedException e) {
                    logger.errTrace(e);
                    failed.set(true);
                    queryPoolLock.release();
                    tasks.countDown();
                    return;
                }
                queryPoolLock.release();
                final String finalQuery = query;
                resultPool.execute(() -> {
                    long start1 = System.currentTimeMillis();
                    Search.SearchResult result = getSearchResult(deptNo, finalQuery, cookieStore, 10000);
                    logger.log("Fetching result " + (System.currentTimeMillis() - start1) + "ms");
                    if (!result.isSuccess()) {
                        logger.err(result.getErrorString());
                        failed.set(true);
                    } else if (!failed.get()) {
                        synchronized (eachDeptCount) {
                            eachDeptCount.put(deptNo, result.getCourseDataList().size());
                        }
                        synchronized (courseDataList) {
                            courseDataList.addAll(result.getCourseDataList());
                        }
                    }
                    resultPoolLock.release();
                    tasks.countDown();
                });
            });
            i++;
        }

        try {
            tasks.await();
            Lib.executorShutdown(queryPool, 1000, "Query pool");
            Lib.executorShutdown(resultPool, 1000, "Result pool");
        } catch (InterruptedException e) {
            logger.errTrace(e);
            return;
        }
        if (failed.get()) {
            return;
        }

        int conflictCount = 0, nonSerialCount = 0;
        Set<String> serialNumber = new HashSet<>();
        for (CourseData courseData : courseDataList) {
            if (courseData.serialNumber == null) {
                nonSerialCount++;
                continue;
            }
            if (serialNumber.contains(courseData.departmentId + '-' + courseData.serialNumber))
                conflictCount++;
            else
                serialNumber.add(courseData.departmentId + '-' + courseData.serialNumber);
        }

        logger.log("serial number count: " + serialNumber.size());
        logger.log("conflict count: " + conflictCount);
        logger.log("non-serial count: " + nonSerialCount);
        logger.log("Get " + courseDataList.size() + " course, use " + ((System.currentTimeMillis() - start) / 1000) + "s");
        logger.log(eachDeptCount);

        saveCookieCache(cookieCache);

        File rawHistoryFileFolder = Lib.getDirectoryFromPath(FILE_PATH, true);
        if (!rawHistoryFileFolder.isDirectory())
            return;
        try {
            FileWriter rawHistoryFile = new FileWriter(new File(rawHistoryFileFolder,
                    historySearch + "_" + language.name() + ".json"
            ));
            rawHistoryFile.write(new JsonArray(courseDataList).toString());
            rawHistoryFile.close();
        } catch (IOException e) {
            logger.errTrace(e);
        }
    }

    private String initSearchPage(CookieStore cookieStore) {
        Connection pageRequest = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=en_query")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy());
        HttpResponseData responseData = robotCheck.sendRequest(courseQueryNckuOrg, pageRequest, cookieStore);
        if (responseData.isSuccess() && responseData.data != null)
            cosPreCheck(courseQueryNckuOrg, responseData.data, cookieStore, null, proxyManager);
        return responseData.data;
    }

    private String createSearchQuery(String deptNo, String instructorName, CourseHistorySearchQuery historySearch, String searchId, CookieStore cookieStore) {
        String postData = "id=" + searchId +
                "&syear_b=" + historySearch.yearBegin +
                "&syear_e=" + historySearch.yearEnd +
                "&sem_b=" + (historySearch.semBegin + 1) +
                "&sem_e=" + (historySearch.semEnd + 1) +
                "&teaname=" + (instructorName == null ? "" : instructorName) +
                "&dept_no=" + (deptNo == null ? "" : deptNo);
        IOException lastError = null;
        String body = null;
        for (int i = 0; i < 3; i++) {
            Connection request = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=save_qry")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(Main.USER_AGENT)
                    .method(Connection.Method.POST)
                    .requestBody(postData)
                    .timeout(10000)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest");
            try {
                body = request.execute().body();
                break;
            } catch (IOException e) {
                lastError = e;
                logger.warn("Create search query failed(" + (i + 1) + "): " + e.getMessage() + ", Retry...");
            }
        }
        if (body == null) {
            logger.errTrace(lastError);
            return null;
        }

        if (body.equals("0")) {
            logger.err("Condition not set");
            return null;
        }
        if (body.equals("1")) {
            logger.err("Wrong condition format");
            return null;
        }
        return body;
    }

    private Search.SearchResult getSearchResult(String deptNo, String query, CookieStore cookieStore, int timeout) {
        Search.SearchResult result = null;
        int empty = 2;
        for (int i = 0; i < 5; i++) {
            result = new Search.SearchResult();
            Connection request = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215" + query)
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(Main.USER_AGENT)
                    .timeout(timeout)
                    .maxBodySize(50 * 1024 * 1024);
            HttpResponseData httpResponseData = robotCheck.sendRequest(courseQueryNckuOrg, request, cookieStore);
            String searchResultBody = httpResponseData.data;
            if (!httpResponseData.isSuccess() || searchResultBody == null) {
                result.errorFetch("Failed fetch result");
                return result;
            }

            cosPreCheck(courseQueryNckuOrg, searchResultBody, cookieStore, null, proxyManager);

            Element table = Search.findCourseTable(searchResultBody, "Dept " + deptNo, result);
            if (table == null)
                return result;

            String searchId = Search.getSearchID(searchResultBody, result);
            if (searchId == null)
                return result;

            result.setSearchID(searchId);

            try {
                Search.parseCourseTable(
                        table,
                        searchResultBody,
                        null,
                        true,
                        result
                );
            } catch (Exception e) {
                logger.errTrace(e);
            }
            boolean wrongDept = false;
            for (CourseData courseData : result.getCourseDataList()) {
                if (deptNo != null && courseData.departmentId != null && !courseData.departmentId.equals(deptNo)) {
                    wrongDept = true;
                    break;
                }
            }
            if (result.getCourseDataList().isEmpty() && --empty > 0)
                continue;
            if (!wrongDept)
                return result;
        }
        logger.warn("Dept " + deptNo + " get wrong data");
        return result;
    }

    private void getResources(CookieStore cookieStore) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String[] requests = new String[]{
                courseQueryNckuOrg + "/js/modernizr-custom.js?" + date,
                courseQueryNckuOrg + "/js/bootstrap-select/css/bootstrap-select.min.css?" + date,
                courseQueryNckuOrg + "/js/bootstrap-select/js/bootstrap-select.min.js?" + date,
                courseQueryNckuOrg + "/js/jquery.cookie.js?" + date,
                courseQueryNckuOrg + "/js/common.js?" + date,
                courseQueryNckuOrg + "/js/mis_grid.js?" + date,
                courseQueryNckuOrg + "/js/jquery-ui/jquery-ui.min.css?" + date,
                courseQueryNckuOrg + "/js/jquery-ui/jquery-ui.min.js?" + date,
                courseQueryNckuOrg + "/js/fontawesome/css/solid.min.css?" + date,
                courseQueryNckuOrg + "/js/fontawesome/css/regular.min.css?" + date,
                courseQueryNckuOrg + "/js/fontawesome/css/fontawesome.min.css?" + date,
                courseQueryNckuOrg + "/js/epack/css/font-awesome.min.css?" + date,
                courseQueryNckuOrg + "/js/epack/css/elements/list.css?" + date,
                courseQueryNckuOrg + "/js/epack/css/elements/note.css?" + date,
                courseQueryNckuOrg + "/js/performance.now-polyfill.js?" + date,
                courseQueryNckuOrg + "/js/mdb-sortable/js/addons/jquery-ui-touch-punch.min.js?" + date,
                courseQueryNckuOrg + "/js/jquery.taphold.js?" + date,
                courseQueryNckuOrg + "/js/jquery.patch.js?" + date,
        };
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        for (String url : requests) {
            executor.execute(() -> {
                try {
                    HttpConnection.connect(url)
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .userAgent(Main.USER_AGENT)
                            .execute();
                } catch (IOException e) {
                    logger.errTrace(e);
                }
//                logger.log("get: " + url.substring(courseQueryNckuOrg.length()));
            });
        }
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                executor.shutdownNow();
        } catch (InterruptedException e) {
            logger.errTrace(e);
        }
    }

    private static CookieStore[] readCookieCache(int count) {
        List<CookieStore> cookieCache = new ArrayList<>();
        // Read cookie cache
        File cookieCacheFile = new File(FILE_PATH, "cookie.json");
        if (cookieCacheFile.exists()) {
            try {
                JsonArray jsonArray = new JsonArray(Files.newInputStream(cookieCacheFile.toPath()));
                for (Object i : jsonArray) {
                    JsonArray cookies = (JsonArray) i;
                    CookieStore cookieStore = new CookieManager().getCookieStore();
                    cookieCache.add(cookieStore);
                    for (Object j : cookies) {
                        JsonArray cookie = (JsonArray) j;
                        Cookie.addCookie(cookie.getString(0), cookie.getString(1),
                                new URI(null, cookie.getString(2), cookie.getString(3), null),
                                cookieStore);
                    }
                }
            } catch (IOException | URISyntaxException e) {
                logger.errTrace(e);
            }
        }

        while (cookieCache.size() < count)
            cookieCache.add(new CookieManager().getCookieStore());
        return cookieCache.toArray(new CookieStore[0]);
    }

    private static void saveCookieCache(CookieStore[] cookieCache) {
        // Store cookies
        JsonArrayStringBuilder cookieCacheData = new JsonArrayStringBuilder();
        for (CookieStore cookieStore : cookieCache) {
            JsonArrayStringBuilder cookieStoreData = new JsonArrayStringBuilder();
            for (HttpCookie cookie : cookieStore.getCookies()) {
                JsonArrayStringBuilder cookieData = new JsonArrayStringBuilder();
                cookieData.append(cookie.getName());
                cookieData.append(cookie.getValue());
                cookieData.append(cookie.getDomain());
                cookieData.append(cookie.getPath());
                cookieStoreData.append(cookieData);
            }
            cookieCacheData.append(cookieStoreData);
        }

        File cookieCacheFile = new File(FILE_PATH, "cookie.json");
        try {
            FileWriter cookieCacheWriter = new FileWriter(cookieCacheFile);
            cookieCacheWriter.write(cookieCacheData.toString());
            cookieCacheWriter.close();
        } catch (IOException e) {
            logger.errTrace(e);
        }
    }
}
