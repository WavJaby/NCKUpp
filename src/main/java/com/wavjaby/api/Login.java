package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.Module;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.*;


public class Login implements Module {
    private static final String TAG = "[Login] ";
    private static final String loginCheckString = "/index.php?c=auth&m=logout";

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String refererUrl = getRefererUrl(requestHeaders);

        try {
            // unpack cookie
            String loginState = getDefaultLoginCookie(requestHeaders, cookieStore);

            // login
            JsonBuilder data = new JsonBuilder();
            boolean success = login(req, data, cookieStore);
            data.append("success", success);

            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
            packAuthCookie(responseHeader, refererUrl, cookieStore);
            byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
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

    private boolean login(HttpExchange req, JsonBuilder outData, CookieStore cookieStore) {
        try {
            boolean get = req.getRequestMethod().equals("GET");
            Connection.Response checkLoginPage;
            if (get) {
                // GET
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .execute();
            } else {
                // POST
                checkLoginPage = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth")
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
                    outData.append("warn", TAG + "Already login");
                packUserLoginInfo(checkResult, loginState + loginCheckString.length(), outData);
                return true;
            }

            // start login
            Connection.Response toPortal = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=oauth&time=" + (System.currentTimeMillis() / 1000))
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
                outData.append("login", false);
                return true;
            } else {
                // POST
                // in portal
                Map<String, String> query = parseUrlEncodedForm(readResponse(req));
                if (!query.containsKey("username") || !query.containsKey("password")) {
                    outData.append("err", TAG + "Login data not send");
                    return false;
                }

                // login use portal
                int loginFormIndex = toPortalBody.indexOf("id=\"loginForm\"");
                if (loginFormIndex == -1) {
                    outData.append("err", TAG + "Login form not found");
                    return false;
                }
                int actionLink = toPortalBody.indexOf("action=\"", loginFormIndex + 14);
                if (actionLink == -1) {
                    outData.append("err", TAG + "Login form action link not found");
                    return false;
                }
                actionLink += 8;
                int actionLinkEnd = toPortalBody.indexOf('"', actionLink);
                String loginUrl = portalNckuOrg + toPortalBody.substring(actionLink, actionLinkEnd);

                // use password login
                Connection postLogin = HttpConnection.connect(loginUrl);
                postLogin.cookieStore(cookieStore);
                postLogin.method(Connection.Method.POST);
                postLogin.header("Referer", loginUrl);
                postLogin.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                postLogin.requestBody("UserName=" + URLEncoder.encode(query.get("username"), "UTF-8") +
                        "&Password=" + URLEncoder.encode(query.get("password"), "UTF-8") +
                        "&AuthMethod=FormsAuthentication");
                loginRes = postLogin.execute();
            }

            // check if portal login error
            if (toPortal.url().getHost().equals(portalNcku)) {
                String loginPage = loginRes.body();
                int errorStart = loginPage.indexOf("id=\"errorText\"");
                if (errorStart != -1) {
                    String errorMessage = null;
                    errorStart = loginPage.indexOf(">", errorStart + 14);
                    if (errorStart != -1) {
                        int errorEnd = loginPage.indexOf("<", errorStart + 1);
                        if (errorEnd != -1)
                            errorMessage = loginPage.substring(errorStart + 1, errorEnd);
                    }
                    outData.append("err", "loginErr");
                    if (errorMessage != null)
                        outData.append("msg", errorMessage
                                .replace("\\", "\\\\")
                                .replace("&quot;", "\\\""));
                    else
                        outData.append("msg", "Unknown error");
                    return false;
                }
            }

            // redirect to home page
            String redirect = loginRes.header("refresh");
            int redirectUrlStart;
            if (redirect == null || (redirectUrlStart = redirect.indexOf("url=")) == -1) {
                outData.append("err", TAG + "Refresh url not found");
                return false;
            }
            redirect = redirect.substring(redirectUrlStart + 4);

            String result = HttpConnection.connect(redirect)
                    .cookieStore(cookieStore)
                    .execute().body();

            // check if force login
            if (result.contains("/index.php?c=auth&m=force_login")) {
                outData.append("warn", TAG + "Force login");
                result = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=force_login")
                        .ignoreContentType(true)
                        .cookieStore(cookieStore)
                        .execute().body();
            }

            // check login state
            if ((loginState = result.indexOf(loginCheckString)) == -1 ||
                    (loginState = result.indexOf(loginCheckString, loginState + loginCheckString.length())) == -1) {
                outData.append("login", false);
            } else {
                cosPreCheck(result, cookieStore, outData);
                packUserLoginInfo(result, loginState + loginCheckString.length(), outData);
            }
            return true;
        } catch (Exception e) {
            outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
            return false;
        }
    }

    private void packUserLoginInfo(String result, int infoStart, JsonBuilder outData) {
        outData.append("login", true);
        int start, end = infoStart;
        // dept
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1) {
            outData.append("dept", result.substring(start, end++).trim());
        }
        // name
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1)
            outData.append("name", result.substring(start, end++));
        // student ID
        if ((start = result.indexOf('>', end)) != -1 &&
                (end = result.indexOf('<', ++start)) != -1) {
            int cache = result.indexOf('（', start);
            if (cache != -1 && cache < end) start = cache + 1;
            cache = result.lastIndexOf('）', end);
            if (cache != -1 && cache > start) end = cache;
            outData.append("studentID", result.substring(start, end));
        }

        // year/semester
        if ((start = result.indexOf("\"apName")) != -1 &&
                (start = result.indexOf("span>", start + 7)) != -1) {
            char c;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            outData.append("year", Integer.parseInt(result.substring(start, end)));

            start = end;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            outData.append("semester", Integer.parseInt(result.substring(start, end)));
        }
    }
}
