package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLite;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.*;

import static com.wavjaby.Main.*;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;


public class Login implements EndpointModule {
    private static final String TAG = "[Login]";
    private static final Logger logger = new Logger(TAG);
    private static final String loginCheckString = "/index.php?c=auth&m=logout";

    private final SQLite sqLite;
    private final Search search;
    private final ProxyManager proxyManager;
    private PreparedStatement loginDataAddPre, loginDataEditPre;

    private final Map<String, CookieStore> loginUserCookie = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepLoginUpdater = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor loginCosPreCheckPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

    public Login(Search search, SQLite sqLite, ProxyManager proxyManager) {
        this.search = search;
        this.sqLite = sqLite;
        this.proxyManager = proxyManager;
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
            SQLite.printSqlError(e);
        }

        keepLoginUpdater.scheduleAtFixedRate(() -> {
            try {
                for (Map.Entry<String, CookieStore> entry : loginUserCookie.entrySet()) {
                    search.getAllDeptData(entry.getValue(), null);

                    Connection.Response checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal")
                            .header("Connection", "keep-alive")
                            .cookieStore(entry.getValue())
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .execute();

                    String result = checkLoginPage.body();
                    int loginState;
                    if (checkLoginPage.url().toString().endsWith("/index.php?c=portal") && // check if already login
                            (loginState = result.indexOf(loginCheckString)) != -1 &&
                            (loginState = result.indexOf(loginCheckString, loginState + loginCheckString.length())) != -1) {

                        int start, end = loginState + loginCheckString.length();
                        // dept/grade
                        String deptGradeInfo = null;
                        if ((start = result.indexOf('>', end)) != -1 &&
                                (end = result.indexOf('<', ++start)) != -1) {
                            deptGradeInfo = result.substring(start, end++);
                        }

                        // name
                        String name = null;
                        if ((start = result.indexOf('>', end)) != -1 &&
                                (end = result.indexOf('<', ++start)) != -1)
                            name = result.substring(start, end++);

                        // student ID
                        String studentID = null;
                        if ((start = result.indexOf('>', end)) != -1 &&
                                (end = result.indexOf('<', ++start)) != -1) {
                            int cache = result.indexOf('（', start);
                            if (cache != -1 && cache < end) start = cache + 1;
                            cache = result.lastIndexOf('）', end);
                            if (cache != -1 && cache > start) end = cache;
                            studentID = result.substring(start, end);
                        }

                        logger.log(studentID + " is login");
                    } else {
                        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, entry.getValue());
                        logger.log(PHPSESSID + " is logout");
                        loginUserCookie.remove(entry.getKey());
                    }
                }

            } catch (IOException e) {
                logger.errTrace(e);
            }
        }, 0, 1000 * 60 * 2, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        keepLoginUpdater.shutdown();
        try {
            if (!keepLoginUpdater.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                logger.warn("KeepLoginUpdater pool close timeout");
                keepLoginUpdater.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.errTrace(e);
            logger.warn("KeepLoginUpdater pool close error");
            keepLoginUpdater.shutdownNow();
        }

        loginCosPreCheckPool.shutdown();
        try {
            if (!loginCosPreCheckPool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                logger.warn("loginCosPreCheckPool pool close timeout");
                loginCosPreCheckPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.errTrace(e);
            logger.warn("loginCosPreCheckPool pool close error");
            loginCosPreCheckPool.shutdownNow();
        }
    }

    @Override
    public String getTag() {
        return TAG;
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
                logger.log("New user login: " + studentID);
                loginDataAdd(studentID, studentName, deptGradeInfo, PHPSESSID);
            }
            loginDataEditPre.clearParameters();
        } catch (SQLException e) {
            SQLite.printSqlError(e);
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
            SQLite.printSqlError(e);
        }
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();

        try {
            // Login
            ApiResponse apiResponse = new ApiResponse();
            login(req, apiResponse);

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(req.getRequestHeaders(), responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Login " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void login(HttpExchange req, ApiResponse response) {
        // Setup cookies
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        Headers responseHeader = req.getResponseHeaders();
        String[] cookies = splitCookie(requestHeaders);
        String authState = unpackAuthCookie(cookies, cookieStore);

        Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
        String mode = query.get("mode");
        String method = req.getRequestMethod();
        boolean post = method.equalsIgnoreCase("POST");
        boolean get = method.equalsIgnoreCase("GET");
        // Unknown http method
        if (!post && !get) {
            response.errorUnsupportedHttpMethod(method);
        } else if (mode == null) {
            response.errorBadQuery("Query require \"mode\", value should be one of \"course\" or \"stuId\"");
        }
        // Login course ncku
        else if (mode.equals("course")) {
            String loginState = unpackCourseLoginStateCookie(cookies, cookieStore);
            String postData = post ? readRequestBody(req) : null;
            loginCourseNcku(get, postData, response, cookieStore);
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
        }
        // Login student identification system
        else if (mode.equals("stuId")) {
            String loginState = unpackStudentIdSysLoginStateCookie(cookies, cookieStore);
            String postData = post ? readRequestBody(req) : null;
            loginNckuStudentIdSystem(get, postData, response, cookieStore);
            packStudentIdSysLoginStateCookie(responseHeader, loginState, cookieStore);
        }
        // Unknown mode
        else
            response.errorBadQuery("Unknown login mode: " + mode);

        packAuthCookie(responseHeader, authState, cookieStore);
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void loginCourseNcku(boolean get, String postData, ApiResponse response, CookieStore cookieStore) {
        try {
            logger.log("Check login state");
            Connection.Response checkLoginPage;
            if (get) {
                // GET
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .execute();
            } else {
                // POST
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
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
                    response.addWarn("Already login");
                packUserLoginResponse(checkResult, loginState + loginCheckString.length(), cookieStore, response);
                return;
            }

            logger.log("Login use portal");
            // Use portal
            Connection.Response portalPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=oauth&time=" + (System.currentTimeMillis() / 1000))
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .execute();
            // If redirect to portal (portal not auto login)
            if (!portalPage.url().getHost().equals(courseNcku)) {
                if (get) {
                    // GET
                    response.setData("{\"login\":false}");
                    return;
                } else {
                    // POST
                    // Portal login
                    logger.log("POST portal login data");
                    portalPage = portalLogin(postData, portalPage.body(), cookieStore, response);
                    if (portalPage == null) {
                        response.setData("{\"login\":false}");
                        return;
                    }
                }
            }

            // Check if login success
            logger.log("Check if login success");
            Connection.Response loginCheck = checkPortalLogin(portalPage, cookieStore, response, proxyManager.getProxy());
            if (loginCheck == null) {
                response.setData("{\"login\":false}");
                return;
            }
            String result = loginCheck.body();

            // check if force login
            if (result.contains("/index.php?c=auth&m=force_login")) {
                logger.log("Force login");
                response.addWarn("Force login");
                result = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=force_login")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .execute().body();
            }

            // check login state
            logger.log("Check login state");
            if ((loginState = result.indexOf(loginCheckString)) == -1 ||
                    (loginState = result.indexOf(loginCheckString, loginState + loginCheckString.length())) == -1) {
                response.setData("{\"login\":false}");
                return;
            }
            packUserLoginResponse(result, loginState + loginCheckString.length(), cookieStore, response);

            String finalResult = result;
            loginCosPreCheckPool.execute(() ->
                    cosPreCheck(courseNckuOrg, finalResult, cookieStore, null, proxyManager)
            );
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    public void loginNckuStudentIdSystem(boolean get, String postData, ApiResponse response, CookieStore cookieStore) {
        try {
            Connection.Response checkLoginPage;
            if (get) {
                // GET
                checkLoginPage = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys02.asp")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .execute();
            } else {
                // POST
                checkLoginPage = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys02.asp?oauth=" + (System.currentTimeMillis() / 1000))
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .execute();
            }

            // check login state
            String checkResult = checkLoginPage.body();
            // check if already login
            if (checkResult.lastIndexOf("logouts.asp") != -1) {
                // POST and already login
                if (!get)
                    response.addWarn("Already login");
                response.setData("{\"login\":true}");
                return;
            }

            // Remove cookie
            for (HttpCookie httpCookie : cookieStore.get(stuIdSysNckuOrgUri))
                cookieStore.remove(stuIdSysNckuOrgUri, httpCookie);

            // Use portal
            Connection.Response portalPage = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys02.asp?oauth=" + (System.currentTimeMillis() / 1000))
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .execute();
            // If redirect to portal (portal not auto login)
            if (!portalPage.url().getHost().equals(stuIdSysNcku)) {
                if (get) {
                    // GET
                    response.setData("{\"login\":false}");
                    return;
                } else {
                    // POST
                    // Portal login
                    portalPage = portalLogin(postData, portalPage.body(), cookieStore, response);
                    if (portalPage == null) {
                        response.setData("{\"login\":false}");
                        return;
                    }
                }
            }

            // Check if login success
            Connection.Response loginCheck = checkPortalLogin(portalPage, cookieStore, response, null);
            if (loginCheck == null) {
                response.setData("{\"login\":false}");
                return;
            }
            String result = loginCheck.body();
            if (result.lastIndexOf("logouts.asp") != -1)
                response.setData("{\"login\":true}");
            else
                response.setData("{\"login\":false}");
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    private Connection.Response checkPortalLogin(Connection.Response portalPage, CookieStore cookieStore, ApiResponse response, Proxy proxy) {
        // check if portal login error
        if (portalPage.url().getHost().equals(portalNcku)) {
            String loginPage = portalPage.body();
            int errorStart = loginPage.indexOf("id=\"errorText\""), errorEnd;
            if (errorStart != -1 &&
                    (errorStart = loginPage.indexOf('>', errorStart + 14)) != -1 &&
                    (errorEnd = loginPage.indexOf('<', errorStart + 1)) != -1) {
                response.setMessageDisplay(Parser.unescapeEntities(loginPage.substring(errorStart + 1, errorEnd), true).replace("\\", "\\\\"));
                return null;
            }
        }

        // Redirect
        String redirect = portalPage.header("refresh");
        int redirectUrlStart;
        if (redirect != null && (redirectUrlStart = redirect.indexOf("url=")) != -1) {
            try {
                return HttpConnection.connect(redirect.substring(redirectUrlStart + 4))
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxy)
                        .execute();
            } catch (IOException e) {
                logger.errTrace(e);
                response.errorNetwork(e);
                return null;
            }
        }
        return portalPage;
    }

    private Connection.Response portalLogin(String postData, String portalBody, CookieStore cookieStore, ApiResponse response) {
        // In portal page
        Map<String, String> loginForm = parseUrlEncodedForm(postData);
        String username = loginForm.get("username");
        String password = loginForm.get("password");
        if (username == null || password == null) {
            response.errorBadPayload("Login data not send");
            return null;
        }

        // login use portal
        int loginFormIndex = portalBody.indexOf("id=\"loginForm\"");
        if (loginFormIndex == -1) {
            response.errorParse("Login form not found");
            return null;
        }
        int actionLink = portalBody.indexOf("action=\"", loginFormIndex + 14);
        if (actionLink == -1) {
            response.errorParse("Login form action link not found");
            return null;
        }
        actionLink += 8;
        int actionLinkEnd = portalBody.indexOf('"', actionLink);
        String loginUrl = portalNckuOrg + portalBody.substring(actionLink, actionLinkEnd);

        try {
            // use password login
            Connection postLogin = HttpConnection.connect(loginUrl)
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .followRedirects(false)
                    .method(Connection.Method.POST)
                    .header("Referer", loginUrl)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .requestBody("UserName=" + URLEncoder.encode(username, "UTF-8") +
                            "&Password=" + URLEncoder.encode(password, "UTF-8") +
                            "&AuthMethod=FormsAuthentication");
            Connection.Response portalResponse = postLogin.execute();

            // Redirect
            while (portalResponse.statusCode() == 302) {
                String location = portalResponse.header("Location");
                if (location == null) {
                    response.errorParse("Redirect location not found");
                    return null;
                }
                Connection redirect = HttpConnection.connect(location)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .followRedirects(false)
                        .proxy(location.startsWith(courseNckuOrg) ? proxyManager.getProxy() : null)
                        .header("Referer", loginUrl);
                portalResponse = redirect.execute();
            }

            return portalResponse;
        } catch (UnsupportedEncodingException e) {
            logger.errTrace(e);
            response.errorParse("Unsupported encoding");
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
        return null;
    }

    private void packUserLoginResponse(String result, int infoStart, CookieStore cookie, ApiResponse response) {
        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("login", true);
        int start, end = infoStart;
        // dept/grade
        String deptGradeInfo = null;
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1) {
            builder.append("dept", deptGradeInfo = result.substring(start, end++).trim());
        } else
            response.errorParse("Student dept/grade info not found");

        // name
        String name = null;
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1)
            builder.append("name", name = result.substring(start, end++));
        else
            response.errorParse("Student name not found");

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
            response.errorParse("Student id not found");

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
        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, cookie);
        if (PHPSESSID == null)
            response.errorParse("PHPSESSID id not found");
        loginDataEdit(studentID, name, deptGradeInfo, PHPSESSID);

        loginUserCookie.put(PHPSESSID, cookie);
        response.setData(builder.toString());
    }
}
