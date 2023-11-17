package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.api.login.Login;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RequestMethod;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;
import static com.wavjaby.lib.Lib.readRequestBody;

@RequestMapping("/api/v0")
public class Profile implements Module {
    private static final String TAG = "Profile";
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

    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public RestApiResponse getProfile(HttpExchange req) {
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

        getUserProfile(req.getRequestURI().getRawQuery(), PHPSESSID, apiResponse);

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return apiResponse;
    }

    @RequestMapping(value = "/profile", method = RequestMethod.POST)
    public RestApiResponse postProfile(HttpExchange req) {
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

        Map<String, String> query = null;
        try {
            query = parseUrlEncodedForm(readRequestBody(req, StandardCharsets.UTF_8));
        } catch (IOException e) {
            response.errorBadPayload("Read payload failed");
            logger.errTrace(e);
        }
        if (query != null) {
            String studentID = query.get("studentID");
            String courseSerial;
            if (studentID == null) {
                response.errorBadPayload("Form require \"studentID\"");
            } else if ((courseSerial = query.get("courseSerial")) != null) {
                updateUserProfile(courseSerial, studentID, PHPSESSID, response);
            }
        }

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

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
}
