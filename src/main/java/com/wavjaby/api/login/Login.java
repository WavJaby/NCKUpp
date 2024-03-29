package com.wavjaby.api.login;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.CourseFuncBtn;
import com.wavjaby.api.CourseSchedule;
import com.wavjaby.api.search.Search;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.LoginVerifyCode;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RequestMethod;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.lib.restapi.request.RequestBody;
import com.wavjaby.logger.Logger;
import com.wavjaby.sql.SQLDriver;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.*;

import static com.wavjaby.Main.*;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;


@RequestMapping("/api/v0")
public class Login implements Module {
    private static final String TAG = "Login";
    private static final Logger logger = new Logger(TAG);
    public static final String loginCheckString = "/index.php?c=auth&m=logout";

    private final SQLDriver sqlDriver;
    private final Search search;
    private final ProxyManager proxyManager;
    private final CourseFuncBtn courseFunctionButton;
    private final CourseSchedule courseSchedule;
    private PreparedStatement addUserLoginData, updateUserLoginData, getUserLoginState;

    private final Map<String, CookieStore> loginUserCookie = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepLoginUpdater = Executors.newSingleThreadScheduledExecutor(new ThreadFactory(TAG + "-Checker"));
    private final ThreadPoolExecutor loginCosPreCheckPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4, new ThreadFactory(TAG + "-Pre-Check"));

    private static class UserShortInfo {
        public final String studentID, name, deptGradeInfo, PHPSESSID;
        public final Integer academicYear, semester;

        private UserShortInfo(String studentID, String name, String deptGradeInfo, Integer academicYear, Integer semester, String PHPSESSID) {
            this.studentID = studentID;
            this.name = name;
            this.deptGradeInfo = deptGradeInfo;
            this.academicYear = academicYear;
            this.semester = semester;
            this.PHPSESSID = PHPSESSID;
        }

        public void setDataToApiResponse(ApiResponse response) {
            if (deptGradeInfo == null)
                response.errorParse("Student dept/grade info not found");
            if (name == null)
                response.errorParse("Student name not found");
            if (studentID == null)
                response.errorParse("Student id not found");
            if (academicYear == null)
                response.errorParse("AcademicYear not found");
            if (semester == null)
                response.errorParse("Semester not found");
            if (PHPSESSID == null)
                response.errorParse("PHPSESSID id not found");

            JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
            builder.append("studentID", studentID);
            builder.append("name", name);
            builder.append("deptGradeInfo", deptGradeInfo);
            builder.append("academicYear", academicYear);
            builder.append("semester", semester);
            builder.append("login", true);
            response.setData(builder.toString());
        }
    }

    public Login(Search search, CourseFuncBtn courseFunctionButton, CourseSchedule courseSchedule, SQLDriver sqlDriver, ProxyManager proxyManager) {
        this.search = search;
        this.courseFunctionButton = courseFunctionButton;
        this.courseSchedule = courseSchedule;
        this.sqlDriver = sqlDriver;
        this.proxyManager = proxyManager;
    }

    @Override
    public void start() {
        try {
            addUserLoginData = sqlDriver.getDatabase().prepareStatement(
                    "INSERT INTO login_data (student_id, name,dept_grade_info, PHPSESSID) VALUES (?, ?, ?, ?)"
            );
            updateUserLoginData = sqlDriver.getDatabase().prepareStatement(
                    "UPDATE login_data SET student_id=?, name=?, dept_grade_info=?, PHPSESSID=? WHERE student_id=?"
            );
            getUserLoginState = sqlDriver.getDatabase().prepareStatement(
                    "SELECT student_id FROM login_data WHERE student_id=? AND PHPSESSID=?"
            );
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }

        keepLoginUpdater.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, CookieStore> entry : loginUserCookie.entrySet()) {
                try {
                    CookieStore cookieStore = entry.getValue();
                    String result;
                    Connection.Response checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal")
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .userAgent(Main.USER_AGENT)
                            .timeout(5000)
                            .execute();
                    result = checkLoginPage.body();
                    UserShortInfo shortInfo = getCourseLoginUserInfo(result, cookieStore);

                    if (shortInfo != null) {
                        if (shortInfo.studentID.equals("F74114760")) {
                            logger.log(shortInfo.studentID + " is login");
                        }
//                        if (shortInfo.studentID.equals("F74114760")) {
////                        logger.log(shortInfo.studentID + " is login");
//
//                            ApiResponse response = new ApiResponse();
//                            List<Search.CourseData> courseDataList = new ArrayList<>();
//                            search.getQueryCourseData(new Search.SearchQuery("dept=A9", new String[0]), null,
//                                    cookieStore, response, courseDataList);
//                            response.setData(null);
//                            logger.log(response.toString());
//                            if (response.isSuccess() && !courseDataList.isEmpty()) {
//                                String preKey = courseDataList.get(0).getBtnPreRegister();
//                                if (preKey != null) {
//                                    response = new ApiResponse();
//                                    courseFunctionButton.postPreKey(preKey, cookieStore, response);
//                                    logger.log(response.toString());
//                                }
//
//                                response = new ApiResponse();
//                                courseSchedule.getPreCourseSchedule(cookieStore, response);
//                                if (response.isSuccess()) {
//                                    for (Object o : new JsonObject(response.getData()).getArray("schedule")) {
//                                        JsonObject i = (JsonObject) o;
//                                        if (!(i.getString("deptID") + '-' + i.getString("sn")).equals("A9-001"))
//                                            continue;
//                                        String delete = i.getString("delete");
//                                        if (delete != null) {
//                                            response = new ApiResponse();
//                                            courseSchedule.postPreCourseSchedule("action=delete&info=" + delete, cookieStore, response);
//                                            logger.log(response);
//                                        }
//
//                                        break;
//                                    }
//                                }
//                            }
//                        }
                    } else {
                        logger.log(entry.getKey() + " is logout");
                        loginUserCookie.remove(entry.getKey());
                    }
                } catch (IOException ignore) {
//                    logger.errTrace(e);
                }
            }
        }, 1000 * 10, 1000 * 60 * 5, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        executorShutdown(keepLoginUpdater, 1000, "LoginUpdater");
        executorShutdown(loginCosPreCheckPool, 1000, "LoginCosPreCheck");
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private void loginDataEdit(String studentID, String studentName, String deptGradeInfo, String PHPSESSID) {
        try {
            updateUserLoginData.setString(1, studentID);
            updateUserLoginData.setString(2, studentName);
            updateUserLoginData.setString(3, deptGradeInfo);
            updateUserLoginData.setString(4, PHPSESSID);
            updateUserLoginData.setString(5, studentID);
            int returnValue = updateUserLoginData.executeUpdate();

            // Value not exist
            if (returnValue == 0) {
                logger.log("New user login: " + studentID);
                loginDataAdd(studentID, studentName, deptGradeInfo, PHPSESSID);
            }
            updateUserLoginData.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    private void loginDataAdd(String studentID, String studentName, String deptGradeInfo, String PHPSESSID) {
        try {
            addUserLoginData.setString(1, studentID);
            addUserLoginData.setString(2, studentName);
            addUserLoginData.setString(3, deptGradeInfo);
            addUserLoginData.setString(4, PHPSESSID);
            addUserLoginData.executeUpdate();
            addUserLoginData.clearParameters();
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
    }

    public boolean getUserLoginState(String studentID, String PHPSESSID) {
        try {
            getUserLoginState.setString(1, studentID);
            getUserLoginState.setString(2, PHPSESSID);
            ResultSet result = getUserLoginState.executeQuery();
            boolean login = result.next() && result.getString("student_id") != null;
            getUserLoginState.clearParameters();
            result.close();
            return login;
        } catch (SQLException e) {
            sqlDriver.printStackTrace(e);
        }
        return false;
    }

    @SuppressWarnings("unused")
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public RestApiResponse getLogin(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        ApiResponse response = new ApiResponse();
        login(req, response, null);
        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    @SuppressWarnings("unused")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public RestApiResponse postLogin(HttpExchange req, @RequestBody LoginData loginData) {
        long startTime = System.currentTimeMillis();
        ApiResponse response = new ApiResponse();
        login(req, response, loginData);
        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    private LoginMode getLoginMode(ApiResponse response, Map<String, String> query) {
        String mode = query.get("mode");
        if (mode == null || mode.isEmpty()) {
            response.errorBadQuery("Login url query require 'mode', value should be one of 'course' or 'stuId'");
            return LoginMode.UNKNOWN;
        }
        switch (mode) {
            case "course":
                return LoginMode.COURSE;
            case "stuId":
                return LoginMode.STUDENT_ID_SYSTEM;
            default:
                response.errorBadQuery("Unknown login mode: " + mode + ", 'mode' should be one of 'course' or 'stuId'");
                return LoginMode.UNKNOWN;
        }
    }

    private void login(HttpExchange req, ApiResponse response, LoginData postLoginData) {
        // Setup cookies
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String[] cookies = splitCookie(req);
        String authState = unpackAuthCookie(cookies, cookieStore);

        Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
        LoginMode loginMode = getLoginMode(response, query);
        if (loginMode == LoginMode.UNKNOWN)
            return;

        // Login course ncku
        if (loginMode == LoginMode.COURSE) {
            String loginState = unpackCourseLoginStateCookie(cookies, cookieStore);
            loginCourseNcku(postLoginData == null, postLoginData, response, cookieStore);
            packCourseLoginStateCookie(req, loginState, cookieStore);
        }
        // Login student identification system
        else if (loginMode == LoginMode.STUDENT_ID_SYSTEM) {
            String loginState = unpackStudentIdSysLoginStateCookie(cookies, cookieStore);
            loginNckuStudentIdSystem(postLoginData == null, postLoginData, response, cookieStore);
            packStudentIdSysLoginStateCookie(req, loginState, cookieStore);
        }

        packAuthCookie(req, authState, "/api/v0/login", cookieStore);
    }

    private void loginCourseNcku(boolean get, LoginData loginData, ApiResponse response, CookieStore cookieStore) {
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
                        .userAgent(Main.USER_AGENT)
                        .execute();
            } else {
                // POST
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .execute();
            }

            // checkPass login state
            String checkResult = checkLoginPage.body();
            UserShortInfo shortInfo;
            if ((get || checkLoginPage.url().toString().endsWith("/index.php?c=portal")) &&
                    (shortInfo = getCourseLoginUserInfo(checkResult, cookieStore)) != null) {
                // POST and already login
                if (!get)
                    response.addWarn("Already login");
                shortInfo.setDataToApiResponse(response);
                return;
            }

//            logger.log("Login use portal");
            // Check of login portal available
            String result;
            if (checkResult.contains("m=oauth")) {
                // Use portal
                Connection.Response portalPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=oauth&time=" + (System.currentTimeMillis() / 1000))
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
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
                        portalPage = portalLogin(loginData, portalPage.body(), cookieStore, response);
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
                result = loginCheck.body();
            }
            // Use regular login
            else {
                // Post login
                if (!get) {
                    String code = LoginVerifyCode.parseCode(cookieStore, proxyManager);
                    if (code == null) {
                        response.errorParse("Login 'code' not found");
                        response.setData("{\"login\":false}");
                        return;
                    }
                    String token = findStringBetween(checkResult, "name=\"csrftoken\"", "value=\"", "\"");
                    if (token == null) {
                        response.errorParse("Login 'csrftoken' not found");
                        response.setData("{\"login\":false}");
                        return;
                    }


                    String username = loginData.username.substring(0, loginData.username.indexOf('@'));
                    Connection.Response portalPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=login")
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .userAgent(Main.USER_AGENT)
                            .method(Connection.Method.POST)
                            .requestBody("user_id=" + URLEncoder.encode(username, "UTF-8") +
                                    "&passwd=" + URLEncoder.encode(loginData.password, "UTF-8") +
                                    "&code=" + code +
                                    "&csrftoken=" + token)
                            .execute();

                    result = portalPage.body();

                    String errorMessage = findStringBetween(result, "$(\"#code\")", "show_note_msg(\"", "\"");
                    if (errorMessage != null) {
                        response.errorBadPayload(errorMessage);
                        return;
                    }
                }
                // Check login
                else
                    result = checkResult;
            }

            // checkPass if force login
            if (result.contains("/index.php?c=auth&m=force_login")) {
                logger.log("Force login");
                response.addWarn("Force login");
                result = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=force_login")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .execute().body();
            }

            // checkPass login state
            logger.log("Check user info");
            shortInfo = getCourseLoginUserInfo(result, cookieStore);
            if (shortInfo == null) {
                response.setData("{\"login\":false}");
                return;
            }
            shortInfo.setDataToApiResponse(response);
            logger.log("Login success");

            String finalResult = result;
            loginCosPreCheckPool.execute(() ->
                    cosPreCheck(courseNckuOrg, finalResult, cookieStore, null, proxyManager)
            );
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    public void loginNckuStudentIdSystem(boolean get, LoginData loginData, ApiResponse response, CookieStore cookieStore) {
        try {
            logger.log("Check login state");
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

            // checkPass login state
            String checkResult = checkLoginPage.body();
            // checkPass if already login
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
                    logger.log("POST portal login data");
                    portalPage = portalLogin(loginData, portalPage.body(), cookieStore, response);
                    if (portalPage == null) {
                        response.setData("{\"login\":false}");
                        return;
                    }
                }
            }

            // Check if login success
            logger.log("Check if login success");
            Connection.Response loginCheck = checkPortalLogin(portalPage, cookieStore, response, null);
            if (loginCheck == null) {
                response.setData("{\"login\":false}");
                return;
            }
            String result = loginCheck.body();
            if (result.lastIndexOf("logouts.asp") != -1) {
                logger.log("Login success");
                response.setData("{\"login\":true}");
            } else
                response.setData("{\"login\":false}");
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    private Connection.Response checkPortalLogin(Connection.Response portalPage, CookieStore cookieStore, ApiResponse response, Proxy proxy) {
        // checkPass if portal login error
        if (portalPage.url().getHost().equals(portalNcku)) {
            String loginPage = portalPage.body();
            int errorStart = loginPage.indexOf("id=\"errorText\""), errorEnd;
            if (errorStart != -1 &&
                    (errorStart = loginPage.indexOf('>', errorStart + 14)) != -1 &&
                    (errorEnd = loginPage.indexOf('<', errorStart + 1)) != -1) {
                response.setMessageDisplay(Parser.unescapeEntities(loginPage.substring(errorStart + 1, errorEnd), true));
                return null;
            }
        }

        // Redirect
        String redirect = portalPage.header("refresh");
        int redirectUrlStart;
        if (redirect != null && (redirectUrlStart = redirect.indexOf("url=")) != -1) {
            redirect = redirect.substring(redirectUrlStart + 4);
            if (redirect.startsWith("http://"))
                redirect = "https://" + redirect.substring(7);
            try {
                return HttpConnection.connect(redirect)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxy)
                        .userAgent(Main.USER_AGENT)
                        .execute();
            } catch (IOException e) {
                logger.errTrace(e);
                response.errorNetwork(e);
                return null;
            }
        }
        return portalPage;
    }

    private Connection.Response portalLogin(LoginData loginData, String portalBody, CookieStore cookieStore, ApiResponse response) {
        // In portal page
        if (loginData.username == null || loginData.password == null) {
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
                    .requestBody("UserName=" + URLEncoder.encode(loginData.username, "UTF-8") +
                            "&Password=" + URLEncoder.encode(loginData.password, "UTF-8") +
                            "&AuthMethod=FormsAuthentication");
            Connection.Response portalResponse = postLogin.execute();

            // Redirect
            while (portalResponse.statusCode() == 302 || portalResponse.statusCode() == 301) {
                String location = portalResponse.header("Location");
                if (location == null) {
                    response.errorParse("Redirect location not found");
                    return null;
                }
                // Check if not start with origin
                if (location.startsWith("./") ||
                        !location.startsWith("http://") && !location.startsWith("https://")) {
                    URL url = portalResponse.url();
                    String origin = url.getProtocol() + "://" + url.getHost();
                    // Get parent path
                    if (url.getPath() != null && !location.startsWith("/")) {
                        int end;
                        if ((end = url.getPath().lastIndexOf('/')) != -1)
                            origin += url.getPath().substring(0, end);
                    }
                    location = origin + '/' + location.substring(location.startsWith("./") ? 2 : location.startsWith("/") ? 1 : 0);
                }
                Connection redirect = HttpConnection.connect(location)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .followRedirects(false)
                        .proxy(location.startsWith(courseNckuOrg) ? proxyManager.getProxy() : null)
                        .userAgent(Main.USER_AGENT)
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

    private UserShortInfo getCourseLoginUserInfo(String result, CookieStore cookie) {
        int infoStart;
        if ((infoStart = result.indexOf(loginCheckString)) == -1 ||
                (infoStart = result.indexOf(loginCheckString, infoStart + loginCheckString.length())) == -1) {
            return null;
        }
        infoStart += loginCheckString.length();

        int start, end = infoStart;
        // dept/grade
        String deptGradeInfo = null;
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1) {
            deptGradeInfo = result.substring(start, end++).trim();
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

        // year/semester
        Integer academicYear = null, semester = null;
        if ((start = result.indexOf("\"apName")) != -1 &&
                (start = result.indexOf("span>", start + 7)) != -1) {
            char c;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            academicYear = Integer.parseInt(result.substring(start, end));

            start = end;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            semester = Integer.parseInt(result.substring(start, end));
        }

        // PHPSESSID
        String PHPSESSID = getCookie("PHPSESSID", courseNckuOrgUri, cookie);
        if (PHPSESSID != null) {
            loginDataEdit(studentID, name, deptGradeInfo, PHPSESSID);
            loginUserCookie.put(studentID, cookie);
        }
        return new UserShortInfo(studentID, name, deptGradeInfo, academicYear, semester, PHPSESSID);
    }
}
