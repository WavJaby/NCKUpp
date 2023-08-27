package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;

public class DeptWatchDog implements EndpointModule {
    private static final String TAG = "[DeptWatchDog]";
    private static final Logger logger = new Logger(TAG);
    private final SQLite sqLite;
    private final Login login;
    private PreparedStatement watchListAdd, watchListRemove, getWatchedUser, getUserWatchedCourse, getAllCourse;

    private final Set<String> newDeptData = new HashSet<>();

    public DeptWatchDog(Login login, SQLite sqLite) {
        this.login = login;
        this.sqLite = sqLite;
    }

    @Override
    public void start() {
        try {
            watchListAdd = sqLite.getDatabase().prepareStatement(
                    "INSERT INTO watch_list (student_id, watched_serial_id) VALUES (?, ?)"
            );
            watchListRemove = sqLite.getDatabase().prepareStatement(
                    "DELETE FROM watch_list WHERE student_id=? AND watched_serial_id=?"
            );
            getWatchedUser = sqLite.getDatabase().prepareStatement(
                    "SELECT login_data.student_id, user_data.discord_id, watch_list.watched_serial_id FROM watch_list, user_data" +
                            " JOIN login_data on user_data.student_id=login_data.student_id WHERE watch_list.watched_serial_id=?"
            );
            getUserWatchedCourse = sqLite.getDatabase().prepareStatement(
                    "SELECT watched_serial_id FROM watch_list WHERE student_id=?"
            );
            getAllCourse = sqLite.getDatabase().prepareStatement(
                    "SELECT watched_serial_id FROM watch_list"
            );

            // Get watching course
            ResultSet result = getAllCourse.executeQuery();
            while (result.next()) {
                String dept = result.getString("watched_serial_id");
                int index = dept.indexOf('-');
                if (index == -1) continue;
                newDeptData.add(dept.substring(0, index));
            }
            result.close();

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

    private void watchListAdd(String studentID, String watchSerialID) {
        try {
            watchListAdd.setString(1, studentID);
            watchListAdd.setString(2, watchSerialID);
            int returnValue = watchListAdd.executeUpdate();
            watchListAdd.clearParameters();
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
    }

    private void watchListRemove(String studentID, String watchSerialID) {
        try {
            watchListRemove.setString(1, studentID);
            watchListRemove.setString(2, watchSerialID);
            int returnValue = watchListRemove.executeUpdate();
            watchListRemove.clearParameters();
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
    }

    public List<String> getWatchedUserDiscordID(String serialNumber) {
        try {
            getWatchedUser.setString(1, serialNumber);
            ResultSet result = getWatchedUser.executeQuery();
            List<String> discordIDs = new ArrayList<>();
            while (result.next()) {
                String discordID = result.getString("discord_id");
                if (discordID != null)
                    discordIDs.add(discordID);
            }
            getWatchedUser.clearParameters();
            result.close();
            return discordIDs;
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
        return null;
    }

    private Set<String> getUserWatchedCourse(String studentID) {
        try {
            getUserWatchedCourse.setString(1, studentID);
            ResultSet result = getUserWatchedCourse.executeQuery();
            Set<String> watchedCurse = new HashSet<>();
            while (result.next())
                watchedCurse.add(result.getString("watched_serial_id"));
            getUserWatchedCourse.clearParameters();
            result.close();
            return watchedCurse;
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        }
        return null;
    }

    public String[] getNewDept() {
        synchronized (newDeptData) {
            if (newDeptData.isEmpty())
                return null;
            String[] copy = newDeptData.toArray(new String[0]);
            newDeptData.clear();
            return copy;
        }
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();

            String method = req.getRequestMethod();
            String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, cookieStore);
            if (PHPSESSID == null) {
                apiResponse.errorCookie("Cookie \"PHPSESSID\" not found");
            } else {
                if (method.equalsIgnoreCase("POST")) {
                    Map<String, String> query = parseUrlEncodedForm(readRequestBody(req, StandardCharsets.UTF_8));
                    String studentID = query.get("studentID");
                    String courseSerial;
                    if (studentID == null) {
                        apiResponse.errorBadPayload("Form require \"studentID\"");
                    } else if ((courseSerial = query.get("courseSerial")) != null) {
                        addWatchDog(courseSerial, studentID, PHPSESSID, apiResponse);
                    } else if ((courseSerial = query.get("removeCourseSerial")) != null) {
                        removeWatchDog(courseSerial, studentID, PHPSESSID, apiResponse);
                    } else
                        apiResponse.errorBadPayload("Form require one of \"courseSerial\" or \"removeCourseSerial\"");
                } else if (method.equalsIgnoreCase("GET")) {
                    getWatchDog(req.getRequestURI().getRawQuery(), PHPSESSID, apiResponse);
                } else
                    apiResponse.errorUnsupportedHttpMethod(method);
            }

            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Done in " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void addWatchDog(String courseSerial, String studentID, String PHPSESSID, ApiResponse response) {
        if (!login.getUserLoginState(studentID, PHPSESSID)) {
            response.errorLoginRequire();
            return;
        }
        int index = courseSerial.indexOf('-');
        if (index == -1) {
            response.errorBadPayload("Form \"courseSerial\" syntax error: " + courseSerial);
            return;
        }
        synchronized (newDeptData) {
            newDeptData.add(courseSerial.substring(0, index));
        }
        watchListAdd(studentID, courseSerial);
    }

    private void removeWatchDog(String courseSerial, String studentID, String PHPSESSID, ApiResponse response) {
        if (!login.getUserLoginState(studentID, PHPSESSID)) {
            response.errorLoginRequire();
            return;
        }
        watchListRemove(studentID, courseSerial);
    }


    private void getWatchDog(String rawQuery, String PHPSESSID, ApiResponse response) {
        Map<String, String> query = parseUrlEncodedForm(rawQuery);
        String studentID = query.get("studentID");
        if (studentID == null) {
            response.errorBadQuery("Query \"studentID\" not found");
            return;
        }
        if (!login.getUserLoginState(studentID, PHPSESSID)) {
            response.errorLoginRequire();
            return;
        }
        Set<String> watchedCourse = getUserWatchedCourse(studentID);
        if (watchedCourse == null) {
            response.errorServerDatabase("Can not find watched course");
            return;
        }

        Map<String, List<String>> data = new HashMap<>();
        for (String i : watchedCourse) {
            String[] serial = i.split("-");
            List<String> dept = data.computeIfAbsent(serial[0], k -> new ArrayList<>());
            dept.add(serial[1]);
        }

        response.setData(new JsonObject(data).toString());
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
