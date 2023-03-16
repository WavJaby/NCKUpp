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

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;

public class DeptWatchDog implements EndpointModule {
    private static final String TAG = "[DeptWatchDog] ";
    private final SQLite sqLite;
    private PreparedStatement watchListAdd, watchListRemove, getWatchedUser, getUserLoginData, getWatchedCourse, getAllCourse;

    private final Set<String> newDeptData = new HashSet<>();

    public DeptWatchDog(SQLite sqLite) {
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
                    "SELECT login_data.student_id, login_data.discord_id, watch_list.watched_serial_id FROM watch_list" +
                            " JOIN login_data on watch_list.student_id=login_data.student_id WHERE watch_list.watched_serial_id=?"
            );
            getUserLoginData = sqLite.getDatabase().prepareStatement(
                    "SELECT student_id FROM login_data WHERE student_id=? AND PHPSESSID=?"
            );
            getWatchedCourse = sqLite.getDatabase().prepareStatement(
                    "SELECT watched_serial_id FROM watch_list WHERE student_id=?"
            );
            getAllCourse = sqLite.getDatabase().prepareStatement(
                    "SELECT watched_serial_id FROM watch_list"
            );
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
//        loginDataEdit("F74114760", "F7-109");
//        List<String> discordIDs = getWatchedUserDiscordID("F7-109");
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
            SQLite.printSqlError(e, TAG);
        }
    }

    private void watchListRemove(String studentID, String watchSerialID) {
        try {
            watchListRemove.setString(1, studentID);
            watchListRemove.setString(2, watchSerialID);
            int returnValue = watchListRemove.executeUpdate();
            watchListRemove.clearParameters();
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
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
            SQLite.printSqlError(e, TAG);
        }
        return null;
    }

    private boolean getUserLoginData(String studentID, String PHPSESSID) {
        try {
            getUserLoginData.setString(1, studentID);
            getUserLoginData.setString(2, PHPSESSID);
            ResultSet result = getUserLoginData.executeQuery();
            boolean login = result.next() && result.getString("student_id") != null;
            getUserLoginData.clearParameters();
            result.close();
            return login;
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
        return false;
    }

    private Set<String> getWatchedCourse(String studentID) {
        try {
            getWatchedCourse.setString(1, studentID);
            ResultSet result = getWatchedCourse.executeQuery();
            Set<String> watchedCurse = new HashSet<>();
            while (result.next())
                watchedCurse.add(result.getString("watched_serial_id"));
            getWatchedCourse.clearParameters();
            result.close();
            return watchedCurse;
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
        return null;
    }

    public Set<String> getAllCourse() {
        try {
            ResultSet result = getAllCourse.executeQuery();
            Set<String> watchedCurse = new HashSet<>();
            while (result.next()) {
                String dept = result.getString("watched_serial_id");
                int index = dept.indexOf('-');
                if (index == -1) continue;
                watchedCurse.add(dept.substring(0, index));
            }
            getAllCourse.clearParameters();
            result.close();
            return watchedCurse;
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
        return null;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String originUrl = getOriginUrl(requestHeaders);
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();

            String method = req.getRequestMethod();
            String PHPSESSID = getCookie("PHPSESSID", courseNckuOrg, cookieStore);
            if (PHPSESSID == null) {
                apiResponse.addError(TAG + "Cookie \"PHPSESSID\" not found");
            } else {
                if (method.equalsIgnoreCase("POST")) {
                    Map<String, String> query = parseUrlEncodedForm(readResponse(req));
                    String studentID = query.get("studentID");
                    String courseSerial = query.get("courseSerial");
                    if (courseSerial != null) {
                        addWatchDog(courseSerial, studentID, PHPSESSID, apiResponse);
                    } else {
                        String removeCourseSerial = query.get("removeCourseSerial");
                        if (removeCourseSerial != null) {
                            removeWatchDog(removeCourseSerial, studentID, PHPSESSID, apiResponse);
                        } else
                            apiResponse.addError(TAG + "Posted data \"courseSerial\" not found");

                    }
                } else if (method.equalsIgnoreCase("GET")) {
                    Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getQuery());
                    String studentID = query.get("studentID");
                    getWatchDog(studentID, PHPSESSID, apiResponse);
                }
            }

            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, originUrl, cookieStore);
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.isSuccess() ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
            e.printStackTrace();
        }
        Logger.log(TAG, "Done in " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void addWatchDog(String courseSerial, String studentID, String PHPSESSID, ApiResponse apiResponse) {
        if (studentID == null) {
            apiResponse.addError(TAG + "Posted data \"studentID\" not found");
            return;
        }
        boolean login = getUserLoginData(studentID, PHPSESSID);
        if (!login) {
            apiResponse.addError(TAG + "Not login");
            return;
        }
        int index = courseSerial.indexOf('-');
        if (index == -1) {
            apiResponse.addError(TAG + "Posted data \"courseSerial\" syntax error");
            return;
        }
        synchronized (newDeptData) {
            newDeptData.add(courseSerial.substring(0, index));
        }
        watchListAdd(studentID, courseSerial);
    }

    private void removeWatchDog(String courseSerial, String studentID, String PHPSESSID, ApiResponse apiResponse) {
        if (studentID == null) {
            apiResponse.addError(TAG + "Posted data \"studentID\" not found");
            return;
        }
        boolean login = getUserLoginData(studentID, PHPSESSID);
        if (!login) {
            apiResponse.addError(TAG + "Not login");
            return;
        }
        watchListRemove(studentID, courseSerial);
    }


    private void getWatchDog(String studentID, String PHPSESSID, ApiResponse apiResponse) {
        if (studentID == null) {
            apiResponse.addError(TAG + "Posted data \"studentID\" not found");
            return;
        }
        boolean login = getUserLoginData(studentID, PHPSESSID);
        if (!login) {
            apiResponse.addError(TAG + "Not login");
            return;
        }
        Set<String> watchedCourse = getWatchedCourse(studentID);
        if (watchedCourse == null) {
            apiResponse.addError(TAG + "Can not find watched course");
            return;
        }

        Map<String, List<String>> data = new HashMap<>();
        for (String i : watchedCourse) {
            String[] serial = i.split("-");
            List<String> dept = data.computeIfAbsent(serial[0], k -> new ArrayList<>());
            dept.add(serial[1]);
        }

        apiResponse.setData(new JsonObject(data).toString());
    }

    public List<String> getNewDept() {
        List<String> copy;
        synchronized (newDeptData) {
            copy = new ArrayList<>(newDeptData);
            newDeptData.clear();
        }
        return copy;
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
