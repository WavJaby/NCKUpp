package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.api.login.Login;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RequestMethod;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.lib.restapi.request.RequestBody;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLDriver;

import java.net.CookieManager;
import java.net.CookieStore;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;

@RequestMapping("/api/v0")
public class DeptWatchdog implements Module {
    private static final String TAG = "Watchdog";
    private static final Logger logger = new Logger(TAG);
    private final SQLDriver sqlDriver;
    private final Login login;
    private PreparedStatement watchListAdd, watchListRemove, getWatchedUser, getUserWatchedCourse, getAllCourse;

    private final Set<String> newDeptData = new HashSet<>();

    public DeptWatchdog(Login login, SQLDriver sqlDriver) {
        this.login = login;
        this.sqlDriver = sqlDriver;
    }

    @Override
    public void start() {
        try {
            watchListAdd = sqlDriver.getDatabase().prepareStatement(
                    "INSERT INTO watch_list (student_id, watched_serial_id) VALUES (?, ?)"
            );
            watchListRemove = sqlDriver.getDatabase().prepareStatement(
                    "DELETE FROM watch_list WHERE student_id=? AND watched_serial_id=?"
            );
            getWatchedUser = sqlDriver.getDatabase().prepareStatement(
                    "SELECT login_data.student_id, user_data.discord_id, watch_list.watched_serial_id FROM watch_list, user_data" +
                            " JOIN login_data on user_data.student_id=login_data.student_id WHERE watch_list.watched_serial_id=?"
            );
            getUserWatchedCourse = sqlDriver.getDatabase().prepareStatement(
                    "SELECT watched_serial_id FROM watch_list WHERE student_id=?"
            );
            getAllCourse = sqlDriver.getDatabase().prepareStatement(
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
            sqlDriver.printStackTrace(e);
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
            sqlDriver.printStackTrace(e);
        }
    }

    private void watchListRemove(String studentID, String watchSerialID) {
        try {
            watchListRemove.setString(1, studentID);
            watchListRemove.setString(2, watchSerialID);
            int returnValue = watchListRemove.executeUpdate();
            watchListRemove.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    public List<String> getWatchedUserDiscordID(String deptWithSerial) {
        try {
            getWatchedUser.setString(1, deptWithSerial);
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
            sqlDriver.printStackTrace(e);
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
            sqlDriver.printStackTrace(e);
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

    @SuppressWarnings("unused")
    @RequestMapping(value = "/watchdog", method = RequestMethod.GET)
    public RestApiResponse getWatchdog(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();
        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, cookieStore);
        if (PHPSESSID == null) {
            apiResponse.errorCookie("Cookie \"PHPSESSID\" not found");
            logger.log((System.currentTimeMillis() - startTime) + "ms");
            return apiResponse;
        }

        getUserWatchdog(req.getRequestURI().getRawQuery(), PHPSESSID, apiResponse);

        packCourseLoginStateCookie(req, loginState, cookieStore);
        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return apiResponse;
    }

    public static class WatchDogRequest {
        public String studentID, courseSerial, removeCourseSerial;
    }

    @SuppressWarnings("unused")
    @RequestMapping(value = "/watchdog", method = RequestMethod.POST)
    public RestApiResponse postWatchdog(HttpExchange req, @RequestBody WatchDogRequest request) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse response = new ApiResponse();
        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, cookieStore);
        if (PHPSESSID == null) {
            response.errorCookie("Cookie \"PHPSESSID\" not found");
            logger.log((System.currentTimeMillis() - startTime) + "ms");
            return response;
        }

        String studentID = request.studentID;
        String courseSerial;
        if (studentID == null) {
            response.errorBadPayload("Form require \"studentID\"");
        } else if ((courseSerial = request.courseSerial) != null) {
            addWatchDog(courseSerial, studentID, PHPSESSID, response);
        } else if ((courseSerial = request.removeCourseSerial) != null) {
            removeWatchDog(courseSerial, studentID, PHPSESSID, response);
        } else
            response.errorBadPayload("Form require one of \"courseSerial\" or \"removeCourseSerial\"");

        packCourseLoginStateCookie(req, loginState, cookieStore);
        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

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


    private void getUserWatchdog(String rawQuery, String PHPSESSID, ApiResponse response) {
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
}
