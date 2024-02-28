package com.wavjaby.api.search;

import com.wavjaby.Main;
import com.wavjaby.Module;
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
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLDriver;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wavjaby.Main.courseQueryNckuOrg;
import static com.wavjaby.Main.courseQueryNckuOrgUri;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class HistorySearch implements Module {
    private static final String FILE_PATH = "api_file/CourseHistory";
    private static final Logger logger = new Logger("History");
    private final ProxyManager proxyManager;
    private final RobotCheck robotCheck;

    private SQLDriver sqlDriver;
    private PreparedStatement getRoomStat, addRoomStat, editRoomStat,
            getTagIdStat, addTagStat,
            getInstructorIdStat, addInstructorStat,
            addCourseStat, getCourseStat;

    @Override
    public void start() {
        PropertiesReader properties = new PropertiesReader("database.properties");
        sqlDriver = new SQLDriver("course_data.mv.db", "jdbc:h2:./course_data;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;TRACE_LEVEL_SYSTEM_OUT=0",
                properties.getProperty("driverPath"), properties.getProperty("driverClass"),
                properties.getProperty("user"), properties.getProperty("password"));
        sqlDriver.start();
        try {
            java.sql.Connection connection = sqlDriver.getDatabase();
            getRoomStat = connection.prepareStatement("SELECT * FROM \"room\" WHERE \"building_id\"=? AND \"room_id\"=?");
            addRoomStat = connection.prepareStatement("INSERT INTO \"room\" VALUES (?,?,?,?)");
            editRoomStat = connection.prepareStatement("UPDATE \"room\" SET \"name_tw\"=?,\"name_en\"=? WHERE \"building_id\"=? AND \"room_id\"=?");

            getTagIdStat = connection.prepareStatement("SELECT \"id\" FROM \"tags\" WHERE \"name_tw\"=? OR \"name_en\"=?");
            addTagStat = connection.prepareStatement("INSERT INTO \"tags\" (\"name_tw\",\"name_en\",\"color\",\"url\") VALUES (?,?,?,?)");

            getInstructorIdStat = connection.prepareStatement("SELECT \"id\" FROM \"instructor\" WHERE \"name_tw\"=? OR \"name_en\"=?");
            addInstructorStat = connection.prepareStatement("INSERT INTO \"instructor\" (\"name_tw\",\"name_en\",\"urschool_id\") VALUES (?,?,?)");
            /* (
                department_id   CHAR(2)     not null,
                serial_id       INTEGER,
                attribute_code  VARCHAR(16) not null,
                system_id       VARCHAR(16) not null,
                for_grade       INTEGER,
                for_class       VARCHAR(16),
                for_class_group VARCHAR(16),
                category_tw     NVARCHAR(16),
                category_en     VARCHAR(64),
                name_tw         NVARCHAR(64),
                name_en         VARCHAR(128),
                note_tw         NVARCHAR(128),
                note_en         VARCHAR(256),
                limit_tw        NVARCHAR(2048),
                limit_en        VARCHAR(3072),
                tags            INTEGER ARRAY,
                credits         FLOAT(1),
                required        BOOLEAN,
                instructors     INTEGER ARRAY,
                selected        INTEGER,
                available       INTEGER,
                time            VARCHAR(64) ARRAY
            ) */
            addCourseStat = connection.prepareStatement("INSERT INTO \"course_112_1\" (" +
                    "\"department_id\",\"serial_id\",\"attribute_code\",\"system_id\"," +
                    "\"for_grade\",\"for_class\",\"for_class_group\"," +
                    "\"category_tw\",\"category_en\",\"name_tw\",\"name_en\",\"note_tw\",\"note_en\",\"limit_tw\",\"limit_en\"," +
                    "\"tags\",\"credits\",\"required\",\"instructors\",\"selected\",\"available\",\"time\") " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

            try {
                String arrayContains = getMethodFullPath(HistorySearch.class.getMethod("arrayContains", Integer.class, Integer[].class));
                connection.createStatement().execute(
                        "CREATE ALIAS IF NOT EXISTS arrayContains FOR \"" + arrayContains + "\"");
            } catch (NoSuchMethodException e) {
                logger.errTrace(e);
            }

            getCourseStat = connection.prepareStatement("SELECT" +
                    "\"department_id\",\"serial_id\",\"attribute_code\",\"system_id\"," +
                    "\"for_grade\",\"for_class\",\"for_class_group\"," +
                    "\"category_tw\",\"category_en\",\"name_tw\",\"name_en\",\"note_tw\",\"note_en\",\"limit_tw\",\"limit_en\"," +
                    "\"tags\",\"credits\",\"required\",\"instructors\",\"selected\",\"available\",\"time\"" +
                    "FROM \"course_112_1\" WHERE" +
                    "(? IS NULL OR \"department_id\"=?) AND" +
                    "(? IS NULL OR \"serial_id\"=?) AND" +
                    "(? IS NULL OR \"name_tw\" LIKE ? OR \"name_en\" LIKE ?) AND" +
                    "(? IS NULL OR arrayContains(?,\"tags\")) AND" +
                    "(? IS NULL OR arrayContains(?,\"instructors\")) AND" +
                    "(? IS NULL OR \"for_grade\"=?)");

        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            sqlDriver.stop();
        }
    }

    @Override
    public void stop() {
        sqlDriver.stop();
    }

    @Override
    public String getTag() {
        return null;
    }

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

        HistorySearch historySearch = new HistorySearch(proxyManager, robotCheck);
        historySearch.start();

//        historySearch.writeDatabase();
        historySearch.readDatabase();
//        historySearch.fetchCourse(historySearch, 112, 112);
        historySearch.stop();

        robotCode.stop();
        proxyManager.stop();
    }

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

    private Integer tagGetId(String nameTW, String nameEN) {
        try {
            getTagIdStat.setNString(1, nameTW);
            getTagIdStat.setString(2, nameEN);
            ResultSet resultSet = getTagIdStat.executeQuery();
            getTagIdStat.clearParameters();
            if (!resultSet.next()) {
                resultSet.close();
                return null;
            }
            int id = resultSet.getInt(1);
            resultSet.close();
            return id;
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            return null;
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

    private Integer instructorGetId(String nameTW, String nameEN) {
        try {
            getInstructorIdStat.setNString(1, nameTW);
            getInstructorIdStat.setString(2, nameEN);
            ResultSet resultSet = getInstructorIdStat.executeQuery();
            getInstructorIdStat.clearParameters();
            if (!resultSet.next()) {
                resultSet.close();
                return null;
            }
            int id = resultSet.getInt(1);
            resultSet.close();
            return id;
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
            return null;
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

    private void courseAdd(CourseData courseDataTW, CourseData courseDataEN) {
        try {
            java.sql.Connection connection = addCourseStat.getConnection();
            // Parse tags
            Object[] tagIds = new Object[courseDataTW.tags == null ? 0 : courseDataTW.tags.length];
            if (tagIds.length > 0 && courseDataEN != null && (courseDataEN.tags == null || courseDataEN.tags.length != tagIds.length)) {
                logger.err("Course: " + getCourseKey(courseDataTW) + ", tag count not match");
                return;
            }
            for (int i = 0; i < tagIds.length; i++) {
                Integer id = tagGetId(courseDataTW.tags[i].name, null);
                if (id == null) {
                    logger.err("Course: " + getCourseKey(courseDataTW) + ", tag: '" +
                            courseDataTW.tags[i].name + "' id not found");
                    return;
                }
                tagIds[i] = id;
            }

            // Parse instructors
            Object[] instructorIds = new Object[courseDataTW.instructors == null ? 0 : courseDataTW.instructors.length];
            for (int i = 0; i < instructorIds.length; i++) {
                Integer id = instructorGetId(courseDataTW.instructors[i], null);
                if (id == null) {
                    logger.err("Course: " + getCourseKey(courseDataTW) + ", instructor: '" +
                            courseDataTW.instructors[i] + "' id not found");
                    return;
                }
                instructorIds[i] = id;
            }

            Object[] timeArray = new Object[courseDataTW.timeList == null ? 0 : courseDataTW.timeList.length];
            if (timeArray.length > 0 && courseDataEN != null && (courseDataEN.timeList == null || courseDataEN.timeList.length != timeArray.length)) {
                logger.err("Course: " + getCourseKey(courseDataTW) + ", timeList count not match");
                return;
            }
            for (int i = 0; i < timeArray.length; i++) {
                timeArray[i] = courseDataTW.timeList[i].toStringShort();
            }

            addCourseStat.setString(1, courseDataTW.departmentId); // department_id
            addCourseStat.setObject(2, courseDataTW.serialNumber, Types.INTEGER); // serial_id
            addCourseStat.setString(3, courseDataTW.attributeCode); // attribute_code
            addCourseStat.setString(4, courseDataTW.systemNumber); // system_id
            addCourseStat.setObject(5, courseDataTW.forGrade, Types.INTEGER); // for_grade

            addCourseStat.setNString(8, courseDataTW.category); // category_tw
            addCourseStat.setNString(10, courseDataTW.courseName); // name_tw
            addCourseStat.setNString(12, courseDataTW.courseNote); // note_tw
            addCourseStat.setNString(14, courseDataTW.courseLimit); // limit_tw
            if (courseDataEN != null) {
                addCourseStat.setString(9, courseDataEN.category); // category_en
                addCourseStat.setString(11, courseDataEN.courseName); // name_en
                addCourseStat.setString(13, courseDataEN.courseNote); // note_en
                addCourseStat.setString(15, courseDataEN.courseLimit); // limit_en
            } else {
                addCourseStat.setString(9, null); // category_en
                addCourseStat.setString(11, null); // name_en
                addCourseStat.setString(13, null); // note_en
                addCourseStat.setString(15, null); // limit_en
            }

            addCourseStat.setString(6, courseDataTW.forClass); // for_class
            addCourseStat.setString(7, courseDataTW.forClassGroup); // for_class_group
            addCourseStat.setArray(16, connection.createArrayOf("INTEGER", tagIds)); // tags
            addCourseStat.setObject(17, courseDataTW.credits, Types.FLOAT); // credits
            addCourseStat.setObject(18, courseDataTW.required, Types.BOOLEAN); // required
            addCourseStat.setArray(19, connection.createArrayOf("INTEGER", instructorIds)); // instructors
            addCourseStat.setObject(20, courseDataTW.selected, Types.INTEGER); // selected
            addCourseStat.setObject(21, courseDataTW.available, Types.INTEGER); // available
            addCourseStat.setArray(22, connection.createArrayOf("TEXT", timeArray)); // time
            addCourseStat.executeUpdate();
            addCourseStat.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    private void courseGet(String deptId, Integer serialNumber, String courseName, String tagName, String instructorName, Integer grade) {
        try {
            if (deptId != null && deptId.isEmpty()) deptId = null;
            if (courseName != null && courseName.isEmpty()) courseName = null;
            if (tagName != null && tagName.isEmpty()) tagName = null;
            if (instructorName != null && instructorName.isEmpty()) instructorName = null;
            boolean withSerial = serialNumber != null;
            // Department id
            getCourseStat.setString(1, deptId);
            getCourseStat.setString(2, deptId);
            // Department id with serial number
            getCourseStat.setObject(3, serialNumber, Types.INTEGER);
            getCourseStat.setObject(4, serialNumber, Types.INTEGER);
            // Course name TW or EN
            getCourseStat.setNString(5, courseName);
            getCourseStat.setNString(6, courseName);
            getCourseStat.setNString(7, courseName);
            // TAG name
            Integer tageId;
            if (tagName == null) tageId = null;
            else tageId = tagGetId(tagName, tagName);
            getCourseStat.setObject(8, tageId, Types.INTEGER);
            getCourseStat.setObject(9, tageId, Types.INTEGER);
            // Instructor name
            Integer instructorId;
            if (instructorName == null) instructorId = null;
            else instructorId = instructorGetId(instructorName, instructorName);
            getCourseStat.setObject(10, instructorId, Types.INTEGER);
            getCourseStat.setObject(11, instructorId, Types.INTEGER);
            // For grade
            getCourseStat.setObject(12, grade, Types.INTEGER);
            getCourseStat.setObject(13, grade, Types.INTEGER);

            ResultSet result = getCourseStat.executeQuery();
            int count = 0;
            while (result.next()) {
                String name = result.getNString("name_tw");
                Object[] time = (Object[]) result.getArray("time").getArray();
                Object[] instructors = (Object[]) result.getArray("instructors").getArray();
                Object[] tags = (Object[]) result.getArray("tags").getArray();
                logger.log(name);
                logger.log(Arrays.toString(time));
                logger.log(Arrays.toString(instructors));
                logger.log(Arrays.toString(tags));
                count++;
            }
            logger.log(count);
            result.close();
            getCourseStat.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    public void readDatabase() {
        logger.log("Read database");
//        courseGet("F7", 6, null, null, null, null);
        courseGet(null, null, null, "英語授課", null, null);
    }

    public void writeDatabase() {
        logger.log("Write Database");
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

        HashMap<String, CourseData> courseSerialMapEN = new HashMap<>(), courseSerialMapTW = new HashMap<>();
        // Add room
        HashMap<String, RoomData> rooms = new HashMap<>();
        for (CourseData courseData : allCourseDataListTW) {
            if (courseData.timeList == null) continue;
            // Get room name tw
            for (CourseData.TimeData timeData : courseData.timeList) {
                if (!timeData.roomExist()) continue;
                rooms.put(timeData.buildingId + "_" + timeData.roomId,
                        new RoomData(timeData.buildingId, timeData.roomId, timeData.roomName, null));
            }
        }
        for (CourseData courseData : allCourseDataListEN) {
            String key = getCourseKey(courseData);
            courseSerialMapEN.put(key, courseData);

            if (courseData.timeList == null) continue;
            // Get room name en
            for (CourseData.TimeData timeData : courseData.timeList) {
                if (!timeData.roomExist()) continue;
                String roomKey = timeData.buildingId + "_" + timeData.roomId;
                RoomData room = rooms.get(roomKey);
                if (room == null) {
                    logger.warn("Room name TW not found");
                    rooms.put(roomKey,
                            new RoomData(timeData.buildingId, timeData.roomId, null, timeData.roomName));
                } else
                    room.setRoomNameEN(timeData.roomName);
            }
        }
        for (RoomData room : rooms.values()) {
            RoomData roomData = roomGet(room.buildingId, room.roomId);
            if (roomData == null)
                roomAdd(room);
            else
                roomEdit(room);
        }

        // Add tag
        for (CourseData courseDataTW : allCourseDataListTW) {
            if (courseDataTW.tags == null) continue;
            String key = getCourseKey(courseDataTW);
            CourseData courseDataEN = courseSerialMapEN.get(key);
            boolean tagENNotFound = courseDataEN == null || courseDataEN.tags == null;
            if (tagENNotFound) {
                logger.err("Tag EN not found: " + key);
            }
            for (int i = 0; i < courseDataTW.tags.length; i++) {
                CourseData.TagData tagTW = courseDataTW.tags[i];
                String tagNameEN = tagENNotFound ? null : courseDataEN.tags[i].name;
                if (tagGetId(tagTW.name, tagNameEN) == null)
                    tagAdd(tagTW.name, tagNameEN, tagTW.colorID, tagTW.url);
            }
        }

        // Add instructor
        Set<String> names = new HashSet<>();
        Set<String> namesRestore = new HashSet<>();
        for (CourseData courseDataTW : allCourseDataListTW) {
            if (courseDataTW.instructors == null) continue;

            String key = getCourseKey(courseDataTW);
            CourseData courseDataEN = courseSerialMapEN.get(key);
            if (courseDataEN == null || courseDataEN.instructors == null || courseDataEN.instructors.length != courseDataTW.instructors.length) {
                names.addAll(Arrays.asList(courseDataTW.instructors));
            } else
                for (int i = 0; i < courseDataTW.instructors.length; i++) {
                    String nameTW = courseDataTW.instructors[i];
                    String nameEN = courseDataEN.instructors[i];
                    // name EN too short
                    if (nameEN.length() < 4)
                        names.add(nameTW);
                    else {
                        namesRestore.add(nameTW);
                        if (instructorGetId(nameTW, nameEN) == null)
                            instructorAdd(nameTW, nameEN, null);
                    }
                }
        }
        names.removeAll(namesRestore);
        if (!names.isEmpty())
            logger.warn("Instructor english name not found: " + names.size());
        for (String name : names) {
            if (instructorGetId(name, name) == null)
                instructorAdd(name, name, null);
        }

        // Add course
        for (int i = 0; i < allCourseDataListTW.size(); i++) {
            CourseData courseDataTW = allCourseDataListTW.get(i);
            CourseData courseDataEN = allCourseDataListEN.get(i);

            String keyWithClassTW = getCourseKey(courseDataTW) + '_' + courseDataTW.forClass;
            if (courseSerialMapTW.containsKey(keyWithClassTW))
                logger.warn("Conflict course\n\t" +
                        courseSerialMapTW.get(keyWithClassTW) + "\n\t" +
                        courseDataTW
                );
            else
                courseSerialMapTW.put(keyWithClassTW, courseDataTW);

            String keyTW = getCourseKey(courseDataTW);
            String keyEN = getCourseKey(courseDataEN);
            // Add course to database
            if (!(keyTW.substring(2)).equals(keyEN.substring(2))) {
                logger.err("Course key not match: " + keyTW + ", " + keyEN);
                continue;
            }
            courseAdd(courseDataTW, courseDataEN);
        }

        logger.log("Mapped course TW size: " + courseSerialMapTW.size());
        logger.log("Mapped course EN size: " + courseSerialMapEN.size());
        logger.log("Original course TW size: " + allCourseDataListTW.size());
        logger.log("Original course EN size: " + allCourseDataListEN.size());
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
        return courseData.departmentId + "-" + courseData.serialNumber + "," + courseData.systemNumber + "," + courseData.attributeCode +
                "," + courseData.forGrade + "," + courseData.forClassGroup;
    }

    private void fetchCourse(HistorySearch historySearch, int from, int to) {
        AllDept allDept = new AllDept(robotCheck, proxyManager);
        allDept.start();

        CookieStore[] cookieCache = readCookieCache(4);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        for (int year = from; year >= to; year--) {
            long startYearTime = System.currentTimeMillis();
            logger.log("########## Fetch course: " + year);
            CountDownLatch tasks = new CountDownLatch(4);
            CourseHistorySearchQuery history = new CourseHistorySearchQuery(year, 1, year, 1);
            pool.execute(() -> {
                historySearch.search2(Language.TW, history, cookieCache[0]);
                tasks.countDown();
            });
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            pool.execute(() -> {
                historySearch.search2(Language.EN, history, cookieCache[1]);
                tasks.countDown();
            });
            CourseHistorySearchQuery history2 = new CourseHistorySearchQuery(year, 0, year, 0);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            pool.execute(() -> {
                historySearch.search2(Language.TW, history2, cookieCache[2]);
                tasks.countDown();
            });
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            pool.execute(() -> {
                historySearch.search2(Language.EN, history2, cookieCache[3]);
                tasks.countDown();
            });
            try {
                tasks.await();
            } catch (InterruptedException e) {
                break;
            }
            logger.log("########## Fetch course: " + year + ", use " + ((System.currentTimeMillis() - startYearTime) / 1000) + "s");
        }
        executorShutdown(pool, 1000, "HistorySearch");
        saveCookieCache(cookieCache);
    }

    private void search2(Language language, CourseHistorySearchQuery historySearch, CookieStore cookieStore) {
        long start = System.currentTimeMillis();

        Cookie.addCookie("cos_lang", language.code, courseQueryNckuOrgUri, cookieStore);
        logger.log("Getting SearchPage");
        String page = initSearchPage(cookieStore, 60 * 1000);
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
        Map<String, CourseData> deptWithSerial = new HashMap<>();
        for (CourseData courseData : allCourseData) {
            if (courseData.serialNumber == null) {
                nonSerialCount++;
                continue;
            }
            if (deptWithSerial.containsKey(courseData.getDeptWithSerial())) {
                conflictCount++;
                logger.log("\n" + courseData + "\n" + deptWithSerial.get(courseData.getDeptWithSerial()));
            } else
                deptWithSerial.put(courseData.getDeptWithSerial(), courseData);
        }
        logger.log("serial number count: " + deptWithSerial.size());
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

    private void search() {
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
        String homePage = initSearchPage(cookieCache[0], 20 * 1000);
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
                String page = initSearchPage(cookieCache[finalI], 20 * 1000);
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
        Set<String> deptWithSerial = new HashSet<>();
        for (CourseData courseData : courseDataList) {
            if (courseData.serialNumber == null) {
                nonSerialCount++;
                continue;
            }
            if (deptWithSerial.contains(courseData.getDeptWithSerial()))
                conflictCount++;
            else
                deptWithSerial.add(courseData.getDeptWithSerial());
        }

        logger.log("serial number count: " + deptWithSerial.size());
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

    private String initSearchPage(CookieStore cookieStore, int timeout) {
        Connection pageRequest = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=en_query")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .timeout(timeout)
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

            String searchId = Search.getSearchID(searchResultBody, result);
            if (searchId == null)
                return result;

            result.setSearchID(searchId);

            logger.log("Parsing result");
            try {
                Search.parseCourseTable(
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
            if (!result.isSuccess()) {
                logger.log(result.getErrorString());
                continue;
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
                            .timeout(30 * 1000)
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

    private String getMethodFullPath(Method method) {
        if (method == null) return null;
        return method.getDeclaringClass().getTypeName() + '.' + method.getName();
    }

    public static boolean arrayContains(Integer integer, Integer[] array) {
        if (integer == null || array == null) return false;
        for (Integer i : array) {
            if (integer.equals(i))
                return true;
        }
        return false;
    }
}
