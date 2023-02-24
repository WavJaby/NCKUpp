package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.ApiResponse;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.*;


public class Login implements EndpointModule {
    private static final String TAG = "[Login] ";
    private static final String loginCheckString = "/index.php?c=auth&m=logout";

    private final SQLite sqLite;
    private PreparedStatement loginDataAddPre, loginDataEditPre;

    public Login(SQLite sqLite) {
        this.sqLite = sqLite;
    }


    @Override
    public void start() {
        try {
            loginDataAddPre = sqLite.getDatabase().prepareStatement(
                    "INSERT INTO login_data (student_id, name,dept_grade_info, PHPSESSID) VALUES (?, ?, ?, ?)"
            );
            loginDataEditPre = sqLite.getDatabase().prepareStatement(
                    "UPDATE login_data SET student_id=?, name=?, dept_grade_info=?, PHPSESSID=? WHERE student_id=?"
            );
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
    }

    @Override
    public void stop() {

    }

    private void loginDataEdit(String studentID, String studentName, String deptGradeInfo, String PHPSESSID) {
        try {
            loginDataEditPre.setString(1, studentID);
            loginDataEditPre.setString(2, studentName);
            loginDataEditPre.setString(3, deptGradeInfo);
            loginDataEditPre.setString(4, PHPSESSID);
            loginDataEditPre.setString(5, studentID);
            int returnValue = loginDataEditPre.executeUpdate();

            // Value not exist
            if (returnValue == 0) {
                Logger.log(TAG, "New user login: " + studentID);
                loginDataAdd(studentID, studentName, deptGradeInfo, PHPSESSID);
            }
            loginDataEditPre.clearParameters();
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
    }

    private void loginDataAdd(String studentID, String studentName, String deptGradeInfo, String PHPSESSID) {
        try {
            loginDataAddPre.setString(1, studentID);
            loginDataAddPre.setString(2, studentName);
            loginDataAddPre.setString(3, deptGradeInfo);
            loginDataAddPre.setString(4, PHPSESSID);
            loginDataAddPre.executeUpdate();
            loginDataAddPre.clearParameters();
        } catch (SQLException e) {
            SQLite.printSqlError(e, TAG);
        }
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String refererUrl = getRefererUrl(requestHeaders);
        String loginState = getDefaultLoginCookie(requestHeaders, cookieStore);

        try {
            // Login
            ApiResponse apiResponse = new ApiResponse();
            login(req, apiResponse, cookieStore);

            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
            packAuthCookie(responseHeader, refererUrl, cookieStore);
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
        Logger.log(TAG, "Login " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void login(HttpExchange req, ApiResponse response, CookieStore cookieStore) {
        try {
            boolean get = req.getRequestMethod().equals("GET");
            Connection.Response checkLoginPage;
            if (get) {
                // GET
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .execute();
            } else {
                // POST
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .execute();
            }

            // check login state
            String checkResult = checkLoginPage.body();
            int loginState;
            if ((get || checkLoginPage.url().toString().endsWith("/index.php?c=portal")) && // check if already login
                    (loginState = checkResult.indexOf(loginCheckString)) != -1 &&
                    (loginState = checkResult.indexOf(loginCheckString, loginState + loginCheckString.length())) != -1) {
                // POST and already login
                if (!get)
                    response.addWarn(TAG + "Already login");
                packUserLoginInfo(checkResult, loginState + loginCheckString.length(), cookieStore, response);
                return;
            }

            // start login
            Connection.Response toPortal = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=oauth&time=" + (System.currentTimeMillis() / 1000))
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .execute();
            String toPortalBody = toPortal.body();
            Connection.Response loginRes;
            if (toPortal.url().getHost().equals(courseNcku)) {
                // portal auto login
                loginRes = toPortal;
            } else if (get) {
                // GET
                response.setData("{\"login\":false}");
                return;
            } else {
                // POST
                // in portal
                Map<String, String> query = parseUrlEncodedForm(readResponse(req));
                String username = query.get("username");
                String password = query.get("password");
                if (username == null || password == null) {
                    response.addError(TAG + "Login data not send");
                    return;
                }

                // login use portal
                int loginFormIndex = toPortalBody.indexOf("id=\"loginForm\"");
                if (loginFormIndex == -1) {
                    response.addError(TAG + "Login form not found");
                    return;
                }
                int actionLink = toPortalBody.indexOf("action=\"", loginFormIndex + 14);
                if (actionLink == -1) {
                    response.addError(TAG + "Login form action link not found");
                    return;
                }
                actionLink += 8;
                int actionLinkEnd = toPortalBody.indexOf('"', actionLink);
                String loginUrl = portalNckuOrg + toPortalBody.substring(actionLink, actionLinkEnd);

                // use password login
                Connection postLogin = HttpConnection.connect(loginUrl)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .method(Connection.Method.POST)
                        .header("Referer", loginUrl)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .requestBody("UserName=" + URLEncoder.encode(username, "UTF-8") +
                                "&Password=" + URLEncoder.encode(password, "UTF-8") +
                                "&AuthMethod=FormsAuthentication");
                loginRes = postLogin.execute();
            }

            // check if portal login error
            if (toPortal.url().getHost().equals(portalNcku)) {
                String loginPage = loginRes.body();
                int errorStart = loginPage.indexOf("id=\"errorText\"");
                if (errorStart != -1) {
                    String errorMessage = null;
                    errorStart = loginPage.indexOf('>', errorStart + 14);
                    if (errorStart != -1) {
                        int errorEnd = loginPage.indexOf('<', errorStart + 1);
                        if (errorEnd != -1)
                            errorMessage = loginPage.substring(errorStart + 1, errorEnd);
                    }
                    if (errorMessage != null)
                        response.setMessage(errorMessage
                                .replace("\\", "\\\\")
                                .replace("&quot;", "\\\""));
                    else
                        response.setMessage(TAG + "Unknown error");
                    response.setData("{\"login\":false}");
                    return;
                }
            }

            // redirect to home page
            String redirect = loginRes.header("refresh");
            int redirectUrlStart;
            if (redirect == null || (redirectUrlStart = redirect.indexOf("url=")) == -1) {
                response.addError(TAG + "Refresh url not found");
                return;
            }
            redirect = redirect.substring(redirectUrlStart + 4);

            String result = HttpConnection.connect(redirect)
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .execute().body();

            // check if force login
            if (result.contains("/index.php?c=auth&m=force_login")) {
                response.addWarn(TAG + "Force login");
                result = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=force_login")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .execute().body();
            }

            // check login state
            if ((loginState = result.indexOf(loginCheckString)) == -1 ||
                    (loginState = result.indexOf(loginCheckString, loginState + loginCheckString.length())) == -1) {
                response.setData("{\"login\":false}");
            } else {
                packUserLoginInfo(result, loginState + loginCheckString.length(), cookieStore, response);
                cosPreCheck(result, cookieStore, response);
            }
        } catch (Exception e) {
            response.addError(TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
    }

    private void packUserLoginInfo(String result, int infoStart, CookieStore cookie, ApiResponse response) {
        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("login", true);
        int start, end = infoStart;
        // dept/grade
        String deptGradeInfo = null;
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1) {
            builder.append("dept", deptGradeInfo = result.substring(start, end++).trim());
        }
        else
            response.addError(TAG + "Student dept/grade info not found");

        // name
        String name = null;
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1)
            builder.append("name", name = result.substring(start, end++));
        else
            response.addError(TAG + "Student name not found");

        // student ID
        String studentID = null;
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1) {
            int cache = result.indexOf('（', start);
            if (cache != -1 && cache < end) start = cache + 1;
            cache = result.lastIndexOf('）', end);
            if (cache != -1 && cache > start) end = cache;
            builder.append("studentID", studentID = result.substring(start, end));
        } else
            response.addError(TAG + "Student id not found");

        // year/semester
        if ((start = result.indexOf("\"apName")) != -1 &&
                (start = result.indexOf("span>", start + 7)) != -1) {
            char c;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            builder.append("year", Integer.parseInt(result.substring(start, end)));

            start = end;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            builder.append("semester", Integer.parseInt(result.substring(start, end)));
        }

        // PHPSESSID
        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrg, cookie);
        if (PHPSESSID == null)
            response.addError(TAG + "User PHPSESSID id not found");
        loginDataEdit(studentID, name, deptGradeInfo, PHPSESSID);

        response.setData(builder.toString());
    }
}
