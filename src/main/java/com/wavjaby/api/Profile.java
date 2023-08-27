package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;

public class Profile implements EndpointModule {
    private static final String TAG = "[Profile]";
    private static final Logger logger = new Logger(TAG);
    private final SQLite sqLite;
    private final Login login;
    private PreparedStatement getUserSettings;

    public Profile(Login login, SQLite sqLite) {
        this.login = login;
        this.sqLite = sqLite;
    }

    @Override
    public void start() {
        try {
            getUserSettings = sqLite.getDatabase().prepareStatement(
                    "SELECT discord_id FROM user_data WHERE student_id=?"
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
                        updateUserProfile(courseSerial, studentID, PHPSESSID, apiResponse);
                    }
                } else if (method.equalsIgnoreCase("GET")) {
                    getUserProfile(req.getRequestURI().getRawQuery(), PHPSESSID, apiResponse);
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
        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void updateUserProfile(String courseSerial, String studentID, String PHPSESSID, ApiResponse response) {

    }

    private void getUserProfile(String rawQuery, String PHPSESSID, ApiResponse response) {
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
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
