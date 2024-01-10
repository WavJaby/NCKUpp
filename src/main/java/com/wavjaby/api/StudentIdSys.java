package com.wavjaby.api;

import com.sun.istack.internal.Nullable;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.CourseEnrollmentTracker;
import com.wavjaby.Module;
import com.wavjaby.api.login.Login;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.ArraysLib;
import com.wavjaby.lib.Lib;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;
import com.wavjaby.svgbuilder.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.Main.stuIdSysNckuOrg;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class StudentIdSys implements Module {
    private static final String TAG = "StuIdSys";
    private static final Logger logger = new Logger(TAG);
    private static final String loginCheckString = "/ncku/qrys02.asp";
    private static final String NORMAL_DIST_FOLDER = "./api_file/CourseGradesDistribution";
    private static final String COURSE_MAPPER_PATH = "courseMapping.json";
    private static final byte[][] numbers = {
            {0b00100, 0b01010, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100},
            {0b00100, 0b01100, 0b10100, 0b00100, 0b00100, 0b00100, 0b00100, 0b11111},
            {0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111},
            {0b01110, 0b10001, 0b00001, 0b00110, 0b00001, 0b00001, 0b10001, 0b01110},
            {0b00010, 0b00110, 0b01010, 0b10010, 0b10010, 0b11111, 0b00010, 0b00010},
            {0b11111, 0b10000, 0b11110, 0b10001, 0b00001, 0b00001, 0b10001, 0b01110},
            {0b00111, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b10001, 0b01110},
            {0b11111, 0b00001, 0b00010, 0b00010, 0b00100, 0b00100, 0b01000, 0b01000},
            {0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b10001, 0b01110},
            {0b01110, 0b10001, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b11100},
    };
    private final File normalDistFolder;
    private final SQLite sqLite;
    private final CourseEnrollmentTracker enrollmentTracker;
    private final Login login;
    private PreparedStatement sqlGetAllDistribution, sqlGetDistribution, sqlAddDistribution, sqlSetDistributionSerialNumber;
    // time with system code
    private Map<String, CourseGradeInfo> courseInfoCache;

    public static class SemesterOverview {
        final String semester;
        final String semID;
        final float requireCredits;
        final float electiveCredits;
        final float summerCourses;
        final float secondMajorCredits;
        final float equivalentCredits;
        final float earnedCredits;
        final float totalCredits;
        final float weightedGrades;
        final float averageScore;
        final int classRanking, classRankingTotal;
        final int deptRanking, deptRankingTotal;

        public SemesterOverview(Elements row) {
            semID = row.get(1).getElementsByTag("input").attr("value");
            semester = semesterIdToSemester(semID);
            requireCredits = Float.parseFloat(row.get(2).text().trim());
            electiveCredits = Float.parseFloat(row.get(3).text().trim());
            summerCourses = Float.parseFloat(row.get(4).text().trim());
            // Minor/Second Major
            secondMajorCredits = Float.parseFloat(row.get(5).text().trim());
            equivalentCredits = Float.parseFloat(row.get(6).text().trim());
            earnedCredits = Float.parseFloat(row.get(7).text().trim());
            totalCredits = Float.parseFloat(row.get(8).text().trim());
            weightedGrades = Float.parseFloat(row.get(9).text().trim());
            String averageScoreStr = row.get(10).text().trim();
            averageScore = averageScoreStr.isEmpty() ? -1 : Float.parseFloat(averageScoreStr);

            String classRankingStr = row.get(11).text().trim().replace("　", "");
            if (classRankingStr.isEmpty())
                classRanking = classRankingTotal = -1;
            else {
                String[] classRankingArr = classRankingStr.split("／", 2);
                int classRanking = -1, classRankingTotal = -1;
                try {
                    if (!classRankingArr[0].isEmpty())
                        classRanking = Integer.parseInt(classRankingArr[0]);
                    if (classRankingArr.length > 1 && !classRankingArr[1].isEmpty())
                        classRankingTotal = Integer.parseInt(classRankingArr[1]);
                } catch (NumberFormatException e) {
                    logger.err(classRankingStr);
                    logger.errTrace(e);
                }
                this.classRanking = classRanking;
                this.classRankingTotal = classRankingTotal;
            }

            String deptRankingStr = row.get(12).text().trim().replace("　", "");
            if (deptRankingStr.isEmpty())
                deptRanking = deptRankingTotal = -1;
            else {
                String[] deptRankingArr = deptRankingStr.split("／", 2);
                int deptRanking = -1, deptRankingTotal = -1;
                try {
                    if (!deptRankingArr[0].isEmpty())
                        deptRanking = Integer.parseInt(deptRankingArr[0]);
                    if (deptRankingArr.length > 1 && !deptRankingArr[1].isEmpty())
                        deptRankingTotal = Integer.parseInt(deptRankingArr[1]);
                } catch (NumberFormatException e) {
                    logger.err(deptRankingStr);
                    logger.errTrace(e);
                }
                this.deptRanking = deptRanking;
                this.deptRankingTotal = deptRankingTotal;
            }
        }

        public static String semesterIdToSemester(String semesterId) {
            int yearStart = -1, yearEnd = -1;
            for (int i = 0; i < semesterId.length(); i++) {
                char c = semesterId.charAt(i);
                if (yearStart == -1) {
                    if (c != '0')
                        yearStart = i;
                } else if (c < '0' || c > '9') {
                    yearEnd = i;
                    break;
                }
            }
            if (yearStart == -1)
                return null;
            return semesterId.substring(yearStart, yearEnd) +
                    (semesterId.endsWith("上") || semesterId.endsWith("Fall") ? '0' : '1');
        }

        @Override
        public String toString() {
            return new JsonObjectStringBuilder()
                    .append("semID", semID)
                    .append("semester", semester)
                    .append("requireC", requireCredits)
                    .append("electiveC", electiveCredits)
                    .append("summerC", summerCourses)
                    .append("secondMajorC", secondMajorCredits)
                    .append("equivalentC", equivalentCredits)
                    .append("earnedC", earnedCredits)
                    .append("totalC", totalCredits)
                    .append("weightedGrades", weightedGrades)
                    .append("averageScore", averageScore)
                    .append("classRanking", classRanking)
                    .append("classRankingTotal", classRankingTotal)
                    .append("deptRanking", deptRanking)
                    .append("deptRankingTotal", deptRankingTotal)
                    .toString();
        }
    }

    public static class CourseGrade {
        final CourseGradeInfo courseInfo;
        final String remark;
        final float credits;
        final String require;
        final float grade;
        final String gpa;
        final String normalDistImgQuery;

        public CourseGrade(Elements row, int year, byte semester) {
            String serialNumber = row.get(1).text().trim();
            if (!serialNumber.isEmpty()) {
                int split = serialNumber.indexOf(' ');
                if (split != -1) {
                    String dept = serialNumber.substring(0, split);
                    String num = serialNumber.substring(split + 1);
                    serialNumber = dept + '-' + leftPad(num, 3, '0');
                } else serialNumber = null;
            } else serialNumber = null;

            String courseName = row.get(3).text().trim();
            // 課程別
            String remark_ = row.get(4).text().trim();
            remark = remark_.isEmpty() ? null : remark_;
            String creditsStr = row.get(5).text().trim();
            credits = creditsStr.isEmpty() ? -1 : Float.parseFloat(creditsStr);
            // Required/Elective
            require = row.get(6).text().trim();
            // Parse grade
            String gradeStr = row.get(7).text().trim();
            float gradeFloat;
            try {
                gradeFloat = gradeStr.equals("通過") ? -3
                        : gradeStr.equals("抵免") ? -4
                        : gradeStr.equals("退選") ? -5
                        : gradeStr.equals("優良") ? -6
                        : gradeStr.equals("不通") ? -7
                        : Float.parseFloat(gradeStr);
            } catch (NumberFormatException e) {
                gradeFloat = -1;
                logger.errTrace(e);
            }
            grade = gradeFloat;
            // Gpa text
            gpa = row.get(8).text().trim();

            String[] link_ = row.get(2).children().attr("href").split("[?&=]", 9);
            String systemNumber, classCode, yearRaw, semRaw;
            if (link_.length < 9) {
                String systemNumberRaw = row.get(2).text().trim();
                if (systemNumberRaw.length() < 7) {
                    systemNumber = systemNumberRaw;
                    classCode = null;
                } else {
                    systemNumber = systemNumberRaw.substring(0, 7);
                    classCode = systemNumberRaw.length() == 7 ? null : systemNumberRaw.substring(7);
                }
                yearRaw = String.valueOf(year);
                semRaw = String.valueOf(semester);
            } else {
                int yStart;
                yearRaw = link_[2];
                for (yStart = 0; yStart < yearRaw.length(); yStart++) {
                    if (yearRaw.charAt(yStart) != '0')
                        break;
                }
                yearRaw = yearRaw.substring(yStart);
                semRaw = link_[4].equals("1") ? "0" : "1";
                systemNumber = link_[6];
                classCode = link_[8];
            }
            normalDistImgQuery = yearRaw + ',' + semRaw + ',' + systemNumber + ',' + classCode;
            courseInfo = new CourseGradeInfo(serialNumber, systemNumber, classCode, courseName, year, semester);
        }

        // Current semester data
        public CourseGrade(Elements row, int year, byte semester, boolean ignoredCurrent) {
            String[] requireAndRemark = row.get(0).text().trim().split("/", 2);
            require = requireAndRemark[0];
            remark = requireAndRemark[1].equals("通") ? "通識" : requireAndRemark[1];

            String dept = row.get(1).text().trim();
            String num = row.get(2).text().trim();
            String serialNumber = dept + '-' + leftPad(num, 3, '0');

            String courseName = row.get(4).text().trim();
            credits = Float.parseFloat(row.get(5).text().trim());
            String grade_ = row.get(6).text().trim();
            float gradeFloat;
            try {
                gradeFloat = grade_.equals("成績未到") ? -2
                        : grade_.equals("抵免") ? -4
                        : grade_.equals("退選") ? -5
                        : Float.parseFloat(grade_);
            } catch (NumberFormatException e) {
                gradeFloat = -1;
                logger.errTrace(e);
            }
            grade = gradeFloat;

            gpa = null;

            String systemNumber = row.get(3).text().trim(), classCode;
            if (systemNumber.length() < 7) {
                classCode = null;
            } else {
                classCode = systemNumber.length() == 7 ? null : systemNumber.substring(7);
                systemNumber = systemNumber.substring(0, 7);
            }
            normalDistImgQuery = String.valueOf(year) + ',' + (semester == 0 ? '1' : '2') + ',' +
                    systemNumber + (classCode == null ? ',' : ',' + classCode);
            courseInfo = new CourseGradeInfo(serialNumber, systemNumber, classCode, courseName, year, semester);
        }

        @Override
        public String toString() {
            return new JsonObjectStringBuilder()
                    .append("serialNumber", courseInfo.serialNumber)
                    .append("systemNumber", courseInfo.systemNumber)
                    .append("classCode", courseInfo.classCode)
                    .append("courseName", courseInfo.courseName)
                    .append("remark", remark)
                    .append("credits", credits)
                    .append("require", require)
                    .append("grade", grade)
                    .append("gpa", gpa)
                    .append("imgQuery", normalDistImgQuery)
                    .toString();
        }
    }

    public static class CourseGradeInfo {
        final String serialNumber, systemNumber, classCode;
        final String courseName;
        final int year;
        final byte semester;

        public CourseGradeInfo(String serialNumber, String systemNumber, String classCode, String courseName, int year, byte semester) {
            this.serialNumber = serialNumber;
            this.systemNumber = systemNumber;
            this.classCode = classCode == null || classCode.isEmpty() ? null : classCode;
            this.courseName = courseName;
            this.year = year;
            this.semester = semester;
        }

        public String toKey() {
            return toKey(year, semester, systemNumber, classCode);
        }

        public static String toKey(int year, byte semester, String systemNumber, @Nullable String classCode) {
            return String.valueOf(year) + '_' + semester + '_' + systemNumber +
                    (classCode == null ? '_' : '_' + classCode);
        }
    }

    public static class DistributionImage {
        public final int year;
        public final byte semester;
        public final String systemNumber, classCode;
        private String courseName;
        private String[] serialNumbers;
        private int[] studentCount;

        public DistributionImage(int year, byte semester, String systemNumber, String classCode) {
            this.year = year;
            this.semester = semester;
            this.systemNumber = systemNumber;
            this.classCode = classCode == null || classCode.isEmpty() ? null : classCode;
        }

        public DistributionImage(int year, byte semester, String systemNumber, String classCode, String serialNumbersStr, String courseName, String distStr) {
            this(year, semester, systemNumber, classCode);
            this.courseName = courseName;
            String[] dist = Lib.simpleSplit(distStr, ',');
            studentCount = new int[dist.length];
            for (int i = 0; i < dist.length; i++) {
                studentCount[i] = Integer.parseInt(dist[i]);
            }
            serialNumbers = Lib.simpleSplit(serialNumbersStr, ',');
        }

        public String getQuery() {
            return "syear=" + leftPad(String.valueOf(year), 4, '0') + "&sem=" + (semester == 0 ? '1' : '2') + "&co_no=" + systemNumber +
                    (classCode == null ? "&class_code=" : "&class_code=" + classCode);
        }

        public void setDistData(int[] studentCount) {
            this.studentCount = studentCount;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            for (int i = 0; i < studentCount.length; i++) {
                if (i > 0) builder.append(',');
                builder.append(studentCount[i]);
            }
            builder.append(']');

            return new JsonObjectStringBuilder()
                    .append("courseName", courseName)
                    .append("systemNumber", systemNumber)
                    .append("classCode", classCode)
                    .append("year", year)
                    .append("semester", semester)
                    .appendRaw("studentCount", builder.toString())
                    .toString();
        }

        public String studentCountToString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < studentCount.length; i++) {
                if (i > 0) builder.append(',');
                builder.append(studentCount[i]);
            }
            return builder.toString();
        }

        public String serialNumbersToString() {
            return String.join(",", serialNumbers);
        }

        public void AddSerialNumber(CourseGradeInfo courseInfo) {
            boolean find = false;
            for (String serialNumber : serialNumbers) {
                if (serialNumber.equals(courseInfo.serialNumber)) {
                    find = true;
                    break;
                }
            }
            if (find)
                return;
            // Append
            String[] serialNumbers = new String[this.serialNumbers.length + 1];
            System.arraycopy(this.serialNumbers, 0, serialNumbers, 0, this.serialNumbers.length);
            serialNumbers[this.serialNumbers.length] = courseInfo.serialNumber;
            this.serialNumbers = serialNumbers;
        }
    }

    public StudentIdSys(SQLite sqLite, CourseEnrollmentTracker enrollmentTracker, Login login) {
        this.sqLite = sqLite;
        this.enrollmentTracker = enrollmentTracker;
        this.login = login;
        this.normalDistFolder = Lib.getDirectoryFromPath(NORMAL_DIST_FOLDER, true);
    }

    @Override
    public void start() {
        courseInfoCache = new HashMap<>();
        try {
            sqlGetAllDistribution = sqLite.getDatabase().prepareStatement("SELECT * FROM grades_distribution_contribute");
            sqlGetDistribution = sqLite.getDatabase().prepareStatement("SELECT * FROM grades_distribution_contribute " +
                    "WHERE \"year\"=? AND semester=? AND system_code=? AND class_code is ? AND distribution=?"
            );
            sqlAddDistribution = sqLite.getDatabase().prepareStatement("INSERT INTO grades_distribution_contribute " +
                    "(\"year\", semester, system_code, class_code, serial_number, name, student_id, upload, distribution) " +
                    "VALUES (?,?,?,?,?,?,?,?,?)"
            );
            sqlSetDistributionSerialNumber = sqLite.getDatabase().prepareStatement("UPDATE grades_distribution_contribute " +
                    "SET serial_number=?, name=? " +
                    "WHERE \"year\"=? AND semester=? AND system_code=? AND class_code is ?"
            );
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @SuppressWarnings("unused")
    @RequestMapping("/stuIdSys")
    public RestApiResponse stuIdSys(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();

        ApiResponse response = new ApiResponse();
        String loginState = unpackStudentIdSysLoginStateCookie(splitCookie(req), cookieStore);
        studentIdSysGet(req.getRequestURI().getRawQuery(), cookieStore, response);

        packStudentIdSysLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    private void studentIdSysGet(String rawQuery, CookieStore cookieStore, ApiResponse response) {
        Map<String, String> query = parseUrlEncodedForm(rawQuery);
        String mode = query.get("mode");
        if (mode == null) {
            response.errorBadQuery("Query require 'mode'");
            return;
        }

        switch (mode) {
            // Get all distribution data from database
            case "allDistribution":
                getAllDistribution(cookieStore, response);
                break;
            // Get semester info
            case "semInfo":
                getSemestersOverview(cookieStore, response);
                break;
            // Get current semester info
            case "currentSemInfo":
                getCurrentSemesterGradeTable(cookieStore, response);
                break;
            // Get semester course grade
            case "semCourse":
                String semId = query.get("semId");
                if (semId == null) {
                    response.errorBadQuery("Query 'semCourse' mode require 'semId'");
                    return;
                }
                CourseGrade[] courseGrades = getSemesterGradeTable(semId, cookieStore, response);
                if (courseGrades != null)
                    response.setData(ArraysLib.toString(courseGrades));
                break;
            // Get semester course grade normal distribution
            case "courseGradesDistribution":
                String imageQueryRaw = query.get("imgQuery");
                if (imageQueryRaw == null) {
                    response.errorBadQuery("Query 'courseGradesDistribution' mode require 'imgQuery'");
                    return;
                }

                String[] cache = imageQueryRaw.split(",", 4);
                if (cache.length != 4) {
                    int len = cache.length == 1 && cache[0].isEmpty() ? 0 : cache.length;
                    response.errorBadQuery("'" + imageQueryRaw + "' (Only give " + len + " value instead of 4)");
                    return;
                }
                // Get image
                getDistributionGraph(new DistributionImage(Integer.parseInt(cache[0]), Byte.parseByte(cache[1]), cache[2], cache[3]), false, cookieStore, response);
                break;
            case "myContribute":

                break;
            case "addContribute":
                String studentId = query.get("studentId");
                if (studentId == null) {
                    response.errorBadQuery("Query require 'studentId'");
                    return;
                }
                String semesterIds = query.get("semesterIds");
                if (semesterIds == null) {
                    response.errorBadQuery("Query 'addContribute' mode require 'semesterIds'");
                    return;
                }
                addUserContribute(Lib.simpleSplit(semesterIds, ','), studentId, cookieStore, response);
                break;
            default:
                // Unknown mode
                response.errorBadQuery("Unknown mode: " + mode);
        }
    }

    private synchronized List<DistributionImage> sqlGetAllDistribution() {
        try {
            ResultSet result = sqlGetAllDistribution.executeQuery();
            List<DistributionImage> allDestImages = new ArrayList<>();
            while (result.next()) {
                allDestImages.add(new DistributionImage(
                        result.getInt("year"),
                        result.getByte("semester"),
                        result.getString("system_code"),
                        result.getString("class_code"),
                        result.getString("serial_number"),
                        result.getString("name"),
                        result.getString("distribution")
                ));
            }
            result.close();
            return allDestImages;
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
        return null;
    }

    private synchronized DistributionImage sqlGetDistribution(CourseGradeInfo courseInfo, DistributionImage distImageInfo) {
        try {
            sqlGetDistribution.setInt(1, courseInfo.year);
            sqlGetDistribution.setInt(2, courseInfo.semester);
            sqlGetDistribution.setString(3, courseInfo.systemNumber);
            sqlGetDistribution.setString(4, courseInfo.classCode);
            sqlGetDistribution.setString(5, distImageInfo.studentCountToString());
            ResultSet result = sqlGetDistribution.executeQuery();
            sqlGetDistribution.clearParameters();
            if (!result.next())
                return null;

            DistributionImage distributionImage = new DistributionImage(
                    result.getInt("year"),
                    result.getByte("semester"),
                    result.getString("system_code"),
                    result.getString("class_code"),
                    result.getString("serial_number"),
                    result.getString("name"),
                    result.getString("distribution")
            );
            result.close();
            return distributionImage;
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
        return null;
    }

    private synchronized int sqlSetDistributionSerialNumber(DistributionImage courseInfo) {
        try {
            sqlSetDistributionSerialNumber.setString(1, courseInfo.serialNumbersToString());
            sqlSetDistributionSerialNumber.setString(2, courseInfo.courseName);
            sqlSetDistributionSerialNumber.setInt(3, courseInfo.year);
            sqlSetDistributionSerialNumber.setInt(4, courseInfo.semester);
            sqlSetDistributionSerialNumber.setString(5, courseInfo.systemNumber);
            sqlSetDistributionSerialNumber.setString(6, courseInfo.classCode);
            int result = sqlSetDistributionSerialNumber.executeUpdate();
            sqlSetDistributionSerialNumber.clearParameters();
            return result;
        } catch (SQLException e) {
            SQLite.printSqlError(e);
            return -1;
        }
    }

    private synchronized int sqlAddDistribution(CourseGradeInfo courseInfo, DistributionImage distributionImage, String studentId) {
        try {
            sqlAddDistribution.setInt(1, courseInfo.year);
            sqlAddDistribution.setInt(2, courseInfo.semester);
            sqlAddDistribution.setString(3, courseInfo.systemNumber);
            sqlAddDistribution.setString(4, courseInfo.classCode);
            sqlAddDistribution.setString(5, courseInfo.serialNumber);
            sqlAddDistribution.setString(6, courseInfo.courseName);
            sqlAddDistribution.setString(7, studentId);
            sqlAddDistribution.setTimestamp(8, Timestamp.from(ZonedDateTime.now(ZoneId.systemDefault()).toInstant()));
            sqlAddDistribution.setString(9, distributionImage.studentCountToString());
            int result = sqlAddDistribution.executeUpdate();
            sqlAddDistribution.clearParameters();
            return result;
        } catch (SQLException e) {
            SQLite.printSqlError(e);
            return -1;
        }
    }

    private void getAllDistribution(CookieStore cookieStore, ApiResponse response) {
        List<DistributionImage> allDistribution = sqlGetAllDistribution();
        if (allDistribution == null) {
            response.errorServerDatabase("Failed to get all distribution image");
            return;
        }
        JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
        for (DistributionImage distributionImage : allDistribution)
            builder.appendRaw(distributionImage.toString());
        response.setData(builder.toString());
    }

    private SemesterOverview[] getSemestersOverviewArray(CookieStore cookieStore, ApiResponse response) {
        Element tbody;
        try {
            Connection.Response res = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys05.asp")
                    .header("Connection", "keep-alive")
                    .followRedirects(false)
                    .cookieStore(cookieStore).execute();
            String body = res.body();
            if (body.contains(loginCheckString)) {
                response.errorLoginRequire();
                return null;
            }
            Elements tbodyElements = Jsoup.parse(body).body().getElementsByTag("tbody");
            if (tbodyElements.size() < 4 || (tbody = tbodyElements.get(3)) == null) {
                response.errorParse("Semester overview table not found");
                return null;
            }
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return null;
        }

        Elements tableRows = tbody.children();
        if (tableRows.size() < 3) {
            response.errorParse("Semester overview table row not found");
            return null;
        }

        // Parse semester overview
        List<SemesterOverview> semesterOverviewList = new ArrayList<>(tableRows.size() - 3);
        for (int i = 2; i < tableRows.size() - 1; i++) {
            Element row = tableRows.get(i);
            if (row.childrenSize() < 13) {
                response.errorParse("Semester overview table row parse error");
                return null;
            }
            semesterOverviewList.add(new SemesterOverview(row.children()));
        }

        return semesterOverviewList.toArray(new SemesterOverview[0]);
    }

    private void getSemestersOverview(CookieStore cookieStore, ApiResponse response) {
        SemesterOverview[] result = getSemestersOverviewArray(cookieStore, response);
        if (result == null)
            return;
        response.setData(ArraysLib.toString(result));
    }

    private void getCurrentSemesterGradeTable(CookieStore cookieStore, ApiResponse response) {
        Element tbody;
        try {
            Connection.Response res = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys02.asp")
                    .header("Connection", "keep-alive")
                    .followRedirects(false)
                    .cookieStore(cookieStore).execute();
            String body = res.body();
            if (body.contains(loginCheckString)) {
                response.errorLoginRequire();
                return;
            }
            Elements tbodyElements = Jsoup.parse(body).body().getElementsByTag("tbody");
            if (tbodyElements.size() < 4 || (tbody = tbodyElements.get(3)) == null) {
                response.errorParse("IdSys table not found");
                return;
            }
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return;
        }

        // Find current semester data url
        String url = null;
        for (Element i : tbody.getElementsByAttribute("href")) {
            String href = i.attr("href");
            if (!href.startsWith("qrys03") &&
                    !href.startsWith("qrys05") &&
                    !href.startsWith("qrys08")) {
                url = href;
                break;
            }
        }
        if (url == null) {
            response.errorParse("current semester url not found");
            return;
        }

        // Get current semester table
        try {
            Connection conn = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/" + url)
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore);
            Elements tbodyElements = conn.get().getElementsByTag("tbody");
            if (tbodyElements.size() < 4 || (tbody = tbodyElements.get(3)) == null) {
                response.errorParse("Current semester table not found");
                return;
            }
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return;
        }

        Elements tableRows = tbody.children();
        if (tableRows.size() < 4) {
            response.errorParse("Current semester table row not found");
            return;
        }

        // Parse semester
        int year;
        byte sem;
        {
            String semesterRaw = tableRows.get(0).text();
            int firstIndex, secondIndex, thirdIndex, forthIndex;
            int i = 0;
            char c;
            while ((c = semesterRaw.charAt(i)) < '0' || c > '9') i++;
            firstIndex = i;
            while ((c = semesterRaw.charAt(i)) >= '0' && c <= '9') i++;
            secondIndex = i;
            while ((c = semesterRaw.charAt(i)) < '0' || c > '9') i++;
            thirdIndex = i;
            while ((c = semesterRaw.charAt(i)) >= '0' && c <= '9') i++;
            forthIndex = i;
            if (thirdIndex + 1 != forthIndex) {
                response.errorParse("Current semester time parse error");
                return;
            }
            year = Integer.parseInt(semesterRaw.substring(firstIndex, secondIndex));
            sem = (byte) (semesterRaw.substring(thirdIndex, forthIndex).equals("1") ? 0 : 1);
        }

        // Parse course grade
        List<CourseGrade> courseGradeList = new ArrayList<>(tableRows.size() - 4);
        for (int i = 3; i < tableRows.size() - 1; i++) {
            CourseGrade courseGrade = new CourseGrade(tableRows.get(i).children(), year, sem, true);
            courseGradeList.add(courseGrade);
            courseInfoCache.put(courseGrade.courseInfo.toKey(), courseGrade.courseInfo);
        }

        JsonObjectStringBuilder out = new JsonObjectStringBuilder();
        out.append("semester", String.valueOf(year) + sem);
        out.appendRaw("courseGrades", courseGradeList.toString());
        response.setData(out.toString());
    }

    private CourseGrade[] getSemesterGradeTable(String semesterID, CookieStore cookieStore, ApiResponse response) {
        Element tbody;
        try {
            Connection.Response res = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys05.asp")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .followRedirects(false)
                    .method(Connection.Method.POST)
                    .requestBody("submit1=" + semesterID)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").execute();
            String body = res.body();
            if (body.contains(loginCheckString)) {
                response.errorLoginRequire();
                return new CourseGrade[0];
            }
            Elements tbodyElements = Jsoup.parse(body).body().getElementsByTag("tbody");
            if (tbodyElements.size() < 4 || (tbody = tbodyElements.get(3)) == null) {
                response.errorParse("Semester grade table not found");
                return new CourseGrade[0];
            }
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return new CourseGrade[0];
        }

        Elements tableRows = tbody.children();
        if (tableRows.size() < 4) {
            response.errorParse("Semester grade table row not found");
            return new CourseGrade[0];
        }

        String semesterStr = SemesterOverview.semesterIdToSemester(semesterID);
        int year;
        byte semester;
        if (semesterStr == null)
            year = semester = -1;
        else {
            year = Integer.parseInt(semesterStr.substring(0, semesterStr.length() - 1));
            semester = (byte) (semesterStr.charAt(semesterStr.length() - 1) == '0' ? 0 : 1);
        }

        List<CourseGrade> courseGradeList = new ArrayList<>(tableRows.size() - 4);
        for (int i = 2; i < tableRows.size() - 2; i++) {
            Element row = tableRows.get(i);
            if (row.childrenSize() < 10) {
                response.errorParse("Semester grade table row not found");
                return null;
            }
            CourseGrade courseGrade = new CourseGrade(row.children(), year, semester);
            courseGradeList.add(courseGrade);
            courseInfoCache.put(courseGrade.courseInfo.toKey(), courseGrade.courseInfo);
        }
        return courseGradeList.toArray(new CourseGrade[0]);
    }

    private void getDistributionGraph(DistributionImage distImageInfo, boolean addToDatabase, CookieStore cookieStore, ApiResponse response) {
        BufferedImage image;
        try {
            BufferedInputStream in = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/histogram.asp?" + distImageInfo.getQuery())
                    .header("Connection", "keep-alive")
                    .ignoreContentType(true)
                    .cookieStore(cookieStore).execute().bodyStream();
            image = ImageIO.read(in);

        } catch (IOException e) {
            response.errorNetwork(e);
            logger.errTrace(e);
            return;
        }
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int[] imageRGB = image.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
        int[] studentCount = parseImage(imageRGB, imageWidth, imageHeight, false);

//        image.setRGB(0, 0, imageWidth, imageHeight, imageRGB, 0, imageWidth);

        if (studentCount == null) {
            response.errorParse("Normal distribution graph not found");
            response.setMessageDisplay("Normal distribution graph not found");
            return;
        }
        distImageInfo.setDistData(studentCount);

        // Add distribution to database if 'addToDatabase' parameter is true
        String key = CourseGradeInfo.toKey(distImageInfo.year, distImageInfo.semester, distImageInfo.systemNumber, distImageInfo.classCode);
        CourseGradeInfo courseInfo = courseInfoCache.get(key);
        if (courseInfo != null && addToDatabase) {
            logger.log(courseInfo.toKey());
            DistributionImage result = sqlGetDistribution(courseInfo, distImageInfo);
            // No record found
            if (result == null) {
                sqlAddDistribution(courseInfo, distImageInfo, "SYSTEM");
            } else {
                result.courseName = courseInfo.courseName;
                result.AddSerialNumber(courseInfo);
                sqlSetDistributionSerialNumber(result);
            }
        }

        try {
            File imageFile = new File(normalDistFolder, key + ".png");
            ImageIO.write(image, "png", imageFile);
            setFilePermission(imageFile, Lib.userPrincipal, Lib.groupPrincipal, Lib.filePermission);
        } catch (IOException e) {
            logger.errTrace(e);
        }

        response.setData(distImageInfo.toString());
    }

    private void addUserContribute(String[] semesterIds, String studentId, CookieStore cookieStore, ApiResponse response) {
        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, cookieStore);
        if (!login.getUserLoginState(studentId, PHPSESSID)) {
            response.errorLoginRequire();
            return;
        }
        SemesterOverview[] result = getSemestersOverviewArray(cookieStore, response);
        if (result == null)
            return;
        for (SemesterOverview overview : result) {
            if (!ArraysLib.contains(semesterIds, overview.semID)) continue;

            CourseGrade[] courseGrades = getSemesterGradeTable(overview.semID, cookieStore, response);
            if (courseGrades == null)
                return;

            for (CourseGrade grade : courseGrades) {
                CourseGradeInfo info = grade.courseInfo;
                getDistributionGraph(new DistributionImage(info.year, info.semester, info.systemNumber, info.classCode), true, cookieStore, response);
            }
        }
    }

    private void getUserContribute() {

    }

    private String buildNormalDistSvg(int[] studentCount) {
        int totalStudentCount = 0;
        float highestPercent = 0;
        for (int i = 0; i < 11; i++)
            totalStudentCount += studentCount[i];
        for (int i = 0; i < 11; i++) {
            float percent = (float) studentCount[i] / totalStudentCount;
            if (percent > highestPercent)
                highestPercent = percent;
        }
        highestPercent = 1 / highestPercent;


        int svgWidth = 600, svgHeight = 450;
        Svg svg = new Svg(svgWidth, svgHeight);
        svg.setBackgroundColor("#181A1B");
        svg.setAttribute("font-family", "monospace");
        int graphPaddingX = 20;
        int graphPaddingY = 35;
        int graphPaddingTopY = 80;
        int xAxisWidth = svgWidth - graphPaddingX - graphPaddingX;
        int yAxisHeight = svgHeight - graphPaddingY - graphPaddingTopY;
        float xAxisPadding = xAxisWidth / 11f;
        // X axis
        for (int i = 0; i < 11; i++) {
            float x = graphPaddingX + xAxisPadding * i;
            // Axis
            svg.appendChild(
                    new SvgLine(x, svgHeight - graphPaddingY, x, svgHeight - graphPaddingY + 10)
                            .setStrokeColor("#AAA")
                            .setStrokeWidth(2)
            );
            svg.appendChild(
                    new SvgText(x, svgHeight - graphPaddingY + 28, String.valueOf(i * 10))
                            .setTextAnchor(TextAnchor.MIDDLE)
                            .setFontSize("18px")
                            .setFontColor("#DDD")
            );
            // Bar
            float height = (float) yAxisHeight * studentCount[i] / totalStudentCount * highestPercent;
            float barY = svgHeight - graphPaddingY - height;
            svg.appendChild(
                    new SvgRect(x, barY, xAxisPadding, height)
                            .setBackgroundColor("#3376BD")
                            .setStrokeColor("#3390FF")
                            .setStrokeWidth(2)
                            .setClass("grow")
                            .addAttrStyle(new AttrStyle()
                                    .addStyle("transform-origin", "0 " + (barY + height + 2) + "px;")
                            )
            );
            if (studentCount[i] > 0)
                svg.appendChild(
                        new SvgText(x + xAxisPadding * 0.5f, svgHeight - graphPaddingY - height - 5, String.valueOf(studentCount[i]))
                                .setTextAnchor(TextAnchor.MIDDLE)
                                .setFontSize("20px")
                                .setFontColor("#DDD")
                );
        }
        // Baseline
        svg.appendChild(
                new SvgLine(graphPaddingX, svgHeight - graphPaddingY, svgWidth - graphPaddingX, svgHeight - graphPaddingY)
                        .setStrokeColor("#AAA")
                        .setStrokeWidth(2)
                        .setStrokeLinecap(StrokeLinecap.SQUARE)
        );

        svg.appendChild(new SvgStyle()
                .addStyle(".grow",
                        "animation: grow 1s;")
                .addStyle("@keyframes grow",
                        "from{transform: scaleY(0);}" +
                                "to{transform: scaleY(1);}")
        );

        double avg = 0, stdDev = 0;
        for (int i = 0; i < 11; i++) {
            float score = (i < 10 ? (i + i + 1) : (i * 2)) * 5;
            avg += score * studentCount[i];
            stdDev += score * score * studentCount[i];
        }
        avg /= totalStudentCount;
        stdDev = Math.sqrt(stdDev / totalStudentCount - avg * avg);

        int peakStudentCount = 0;
        for (int i = 0; i < 11; i++) {
            float score = (i < 10 ? (i + i + 1) : (i * 2)) * 5;
            if (avg - stdDev < score && score < avg + stdDev)
                peakStudentCount += studentCount[i];
        }

        // Create standard deviation curve
        int totalPoints = 220;
        int maxValue = 110;
        double step = (double) maxValue / totalPoints;
        float stdDevCurveDelta = (float) xAxisWidth / (totalPoints - 1);
        float baseY = svgHeight - graphPaddingY - 2;
        float[][] points = new float[totalPoints][2];
        for (int i = 0; i < totalPoints; i++) {
            float y = (float) (stdDevFunction(i * step, stdDev, avg) * yAxisHeight * peakStudentCount / totalStudentCount);
            float x = graphPaddingX + i * stdDevCurveDelta;
            points[i][0] = x;
            points[i][1] = baseY - y;
        }
        // Build smooth curve
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < totalPoints; i++) {
            if (i == 0)
                builder.append("M ").append(points[i][0]).append(',').append(points[i][1]);
            else
            // Create the Bézier curve command
            {
                // start control point
                float[] cps = controlPoint(points[i - 1], i < 2 ? null : points[i - 2], points[i], false);
                // end control point
                float[] cpe = controlPoint(points[i], points[i - 1], i >= points.length - 1 ? null : points[i + 1], true);
                builder.append("C ").append(cps[0]).append(',').append(cps[1]).append(' ')
                        .append(cpe[0]).append(',').append(cpe[1]).append(' ')
                        .append(points[i][0]).append(',').append(points[i][1]);
            }
        }
        // Apply curve
        SvgPath stdDevLine = new SvgPath()
                .setStrokeWidth(2)
                .setStrokeColor("#85A894");
        stdDevLine.addPoint(builder.toString());
        svg.appendChild(stdDevLine);
        return svg.toString();
    }

    private float[] controlPoint(float[] current, float[] previous, float[] next, boolean reverse) {
        // The smoothing ratio
        float smoothing = 0.1f;
        // When 'current' is the first or last point of the array
        // 'previous' or 'next' don't exist.
        // Replace with 'current'
        float[] p = previous != null ? previous : current;
        float[] n = next != null ? next : current;

        // Properties of the opposed-line
        float lengthX = n[0] - p[0];
        float lengthY = n[1] - p[1];
        double length = Math.sqrt(Math.pow(lengthX, 2) + Math.pow(lengthY, 2));
        double angle = Math.atan2(lengthY, lengthX);

        // If is end-control-point, add PI to the angle to go backward
        angle = angle + (reverse ? Math.PI : 0);
        length = length * smoothing;
        // The control point position is relative to the current point
        float x = (float) (current[0] + Math.cos(angle) * length);
        float y = (float) (current[1] + Math.sin(angle) * length);

        return new float[]{x, y};
    }

    private int[] parseImage(int[] imageRGB, int imageWidth, int imageHeight, boolean debug) {
        // Get x axis base posY
        int xAxisLineY = -1;
        for (int y = imageHeight - 25; y > -1; y--) {
            for (int x = 0; x < imageWidth; x++) {
                // Find black grid line
                if ((imageRGB[y * imageWidth + x] & 0xFFFFFF) != 0)
                    continue;
                ++x;
                // Find base posY
                for (; y > -1; y--) {
                    if ((imageRGB[y * imageWidth + x] & 0xFFFF00) != 0xFFFF00) {
                        xAxisLineY = y;
                        break;
                    }
                }
                break;
            }
            if (xAxisLineY != -1)
                break;
        }
        if (xAxisLineY == -1) {
            return null;
        }

        // Get xAxisGrid
        int[] xAxisGridPosX = new int[11];
        for (int x = 0, i = 0; x < imageWidth; x++) {
            if ((imageRGB[(xAxisLineY + 1) * imageWidth + x] & 0xFFFFFF) == 0) {
                if (i == xAxisGridPosX.length)
                    break;
                xAxisGridPosX[i++] = x;
            }
        }

        // Get bar height
        int[] barTopPosY = new int[xAxisGridPosX.length];
        for (int i = 0; i < xAxisGridPosX.length; i++) {
            int baseX = xAxisGridPosX[i] + 1;
            int nextX = (i + 1 == xAxisGridPosX.length) ? imageWidth : xAxisGridPosX[i + 1];
            for (int y = xAxisLineY - 1; y > -1; y--) {
                if ((imageRGB[y * imageWidth + baseX] & 0xFFFFFF) <= 0x00FFFF)
                    continue;
                // Find if any pixel in the section of row is white
                boolean find = false;
                for (int x = baseX; x < nextX; x++) {
                    int color = imageRGB[y * imageWidth + x] & 0xFFFF00;
                    // Skip if touch wall
                    if (color == 0)
                        break;
                    if (color == 0xFFFF00) {
                        barTopPosY[i] = y;
                        find = true;
                        break;
                    }
                }
                if (find)
                    break;
            }
        }

        // Get number
        Digits[] barNumbers = new Digits[barTopPosY.length];
        for (int i = 0; i < xAxisGridPosX.length; i++) {
            int baseX = xAxisGridPosX[i] + 1;
            int baseY = barTopPosY[i];
            Digits barNum = barNumbers[i] = new Digits(xAxisLineY - baseY - 1);
            int nextX = (i + 1 == xAxisGridPosX.length) ? imageWidth : xAxisGridPosX[i + 1];
            // Find text location
            int minY = Math.max(-1, baseY - 20);
            int textBaseX = -1, textBaseY = -1;
            for (int y = baseY; y > minY; y--) {
                boolean emptyLine = true;
                for (int x = baseX; x < nextX; x++) {
                    int color = imageRGB[y * imageWidth + x] & 0x00FFFF;
                    // Find blue text
                    if (color != 0 && (color & 0x00FF00) != 0x00FF00) {
                        if (textBaseX == -1 || textBaseX > x)
                            textBaseX = x;
                        emptyLine = false;
                        break;
                    }
                }
                if (!emptyLine) {
                    textBaseY = y;
                } else if (textBaseY != -1) {
                    // Found text top
                    break;
                }
            }

            // Empty
            if (textBaseX == -1) {
                barNum.addSureDigit(0);
                continue;
            }

            // Parse digit
            int nextTextBaseX = textBaseX;
            while (nextTextBaseX < imageWidth - 5) {
                int[] score = getDigit(imageRGB, imageWidth, nextTextBaseX, textBaseY);
                nextTextBaseX += 6;

                int maxScoreIndex = maxArr(score);
                // 100%
                if (score[maxScoreIndex] == 40) {
                    barNum.addSureDigit(maxScoreIndex);
                } else {
                    barNum.addUncertainDigit(score);
                }

                // Find next digit
                if (nextTextBaseX >= imageWidth - 5)
                    break;
                boolean fineNextDigit = false;
                for (int offY = 0; offY < 8; offY++) {
                    if ((imageRGB[(textBaseY + offY) * imageWidth + nextTextBaseX] & 0xFFFFFF) <= 0x00FFFF) {
                        fineNextDigit = true;
                        break;
                    }
                }
                if (!fineNextDigit)
                    break;
            }
        }

        // Parse uncertain digits
        int[] studentCount = new int[barNumbers.length];
        int sureNumberCount = 0;
        float heightPerStudent = 0;
        // Get sure digits
        for (int i = 0; i < barNumbers.length; i++) {
            Digits barNum = barNumbers[i];
            if (barNum.isSure()) {
                int num = studentCount[i] = barNum.getParsedNumber();
                if (num > 2) {
                    ++sureNumberCount;
                    heightPerStudent += (float) barNum.getBarHeight() / num;
                }
            }
        }
        if (sureNumberCount == 0)
            return null;
        // Calibrate uncertain digits
        heightPerStudent /= sureNumberCount;
        for (int i = 0; i < barNumbers.length; i++) {
            Digits barNum = barNumbers[i];
            if (barNum.isSure())
                continue;
            studentCount[i] = barNum.calibrateDigit(heightPerStudent);
        }

        // Debug
        if (debug) {
            logger.log(Arrays.toString(studentCount));
            for (int i = 0; i < xAxisGridPosX.length; i++) {
                imageRGB[(xAxisLineY + 1) * imageWidth + xAxisGridPosX[i]] = 0xFF0000;

                int baseX = xAxisGridPosX[i] + 1;
                int nextX = (i + 1 == xAxisGridPosX.length) ? imageWidth : xAxisGridPosX[i + 1];
                for (int x = baseX; x < nextX; x++) {
                    imageRGB[barTopPosY[i] * imageWidth + x] = 0xFF0000;
                }
            }
        }

        return studentCount;
    }

    private static class Digits {
        private final List<int[]> digits = new ArrayList<>();
        private final int barHeight;
        private boolean sure = true;

        public Digits(int barHeight) {
            this.barHeight = barHeight;
        }

        public void addSureDigit(int digit) {
            this.digits.add(new int[]{digit});
        }

        public void addUncertainDigit(int[] digitScore) {
            this.digits.add(digitScore);
            sure = false;
        }

        public boolean isSure() {
            return sure;
        }

        public int getBarHeight() {
            return barHeight;
        }

        public int getParsedNumber() {
            int number = 0;
            for (int[] digit : digits)
                number = number * 10 + digit[0];
            return number;
        }

        private void flattenPossibilities(int prevNumber, int index, List<Integer[]> sortedDigits, List<Integer> numbers) {
            if (index + 1 == sortedDigits.size()) {
                for (Integer digit : sortedDigits.get(index)) {
                    numbers.add(prevNumber * 10 + digit);
                }
            } else {
                for (Integer digit : sortedDigits.get(index)) {
                    if (numbers.size() > 1000)
                        break;
                    if (digit != 0)
                        flattenPossibilities(prevNumber * 10 + digit, index + 1, sortedDigits, numbers);
                }
            }
        }

        public int calibrateDigit(float heightPerStudent) {
            float approximateCount = barHeight / heightPerStudent;
//            logger.log(approximateCount);
            if (digits.isEmpty()) {
                logger.warn("Digits not found");
                return (int) approximateCount;
            }

            // Sort digits
            List<Integer[]> sortedDigits = new ArrayList<>();
            for (int[] digitsScore : digits) {
                Integer[] index = new Integer[digitsScore.length];
                // Sure digit
                if (digitsScore.length == 1) {
                    index[0] = digitsScore[0];
                }
                // Possible digits
                else {
                    for (int j = 0; j < index.length; j++)
                        index[j] = j;
                    Arrays.sort(index, (a, b) -> digitsScore[b] - digitsScore[a]);
                }
                sortedDigits.add(index);
            }

            // List all possible number
            List<Integer> numbers = new ArrayList<>();
            flattenPossibilities(0, 0, sortedDigits, numbers);
            // Find min diff
            int numberOut = numbers.get(0);
            float minDiff = Math.abs(numberOut - approximateCount);
            for (Integer number : numbers) {
                float diff = Math.abs(number - approximateCount);
                if (diff < minDiff) {
                    minDiff = diff;
                    numberOut = number;
                    if (minDiff < 1e-8)
                        break;
                }
            }

            if (minDiff < 1e-8)
                return numberOut;

            // Use approximate count
            logger.warn("Use approximate count");
            return (int) approximateCount;
        }
    }

    private int maxArr(int[] arr) {
        int maxIndex = 0;
        for (int j = 0; j < arr.length; j++) {
            if (arr[j] > arr[maxIndex])
                maxIndex = j;
        }
        return maxIndex;
    }

    private int[] getDigit(int[] imageRGB, int imageWidth, int startX, int startY) {
        int[] score = new int[numbers.length];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 5; x++) {
                // Blue
                boolean isNum = (imageRGB[(startY + y) * imageWidth + startX + x] & 0xFFFFFF) <= 0x00FFFF;
                for (int k = 0; k < numbers.length; k++) {
                    if (isNum == (((numbers[k][y] >> (4 - x)) & 0x1) == 0x1))
                        score[k]++;
                }
            }
        }
        return score;
    }

    private double stdDevFunction(double x, double stdDev, double avg) {
//        final double sqrt2pi = Math.sqrt(2 * Math.PI);
        double a = x - avg;
//        return Math.pow(Math.E, -(a * a) / (2 * stdDev * stdDev)) / (stdDev * sqrt2pi);
        return Math.pow(Math.E, -(a * a) / (2 * stdDev * stdDev));
    }
}
