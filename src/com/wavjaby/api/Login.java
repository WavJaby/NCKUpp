package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.*;


public class Login implements HttpHandler {
    @Override
    public void handle(HttpExchange req) {
        pool.execute(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers requestHeaders = req.getRequestHeaders();
            String refererUrl = getRefererUrl(requestHeaders);

            try {
                // unpack cookie
                String loginState = getDefaultLoginCookie(requestHeaders, cookieManager);

                // login
                JsonBuilder data = new JsonBuilder();
                boolean success = login(req, data, cookieStore);
                if (!success) data.append("login", false);

                Headers responseHeader = req.getResponseHeaders();
                packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
                packAuthCookie(responseHeader, refererUrl, cookieStore);
                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Content-Type", "application/json; charset=utf-8");

                // send response
                setAllowOrigin(requestHeaders, responseHeader);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                OutputStream response = req.getResponseBody();
                response.write(dataByte);
                response.flush();
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("[Login] Login " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    public boolean login(HttpExchange req, JsonBuilder outData, CookieStore cookieStore) {
        try {
            if (req.getRequestMethod().equals("GET")) {
                Connection.Response toLogin = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal")
                        .cookieStore(cookieStore)
                        .execute();
                outData.append("login", toLogin.body().contains("/index.php?c=auth&m=logout"));
                return true;
            } else {
                Map<String, String> query = parseUrlEncodedForm(readResponse(req));
                if (!query.containsKey("username") || !query.containsKey("password")) {
                    outData.append("err", "[Login] login data not send");
                    return false;
                }

                Connection.Response toLogin = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth")
                        .cookieStore(cookieStore)
                        .execute();
                if (toLogin.url().toString().endsWith("/index.php?c=portal") &&
                        toLogin.body().contains("/index.php?c=auth&m=logout")) {
                    outData.append("warn", "[Login] Already login");
                    outData.append("login", true);
                    return true;
                }

//                URI uri = new URI(portalNckuOrg);
//                cookieStore.getCookies().stream()
//                        .filter(i -> i != null && (i.getName().equals("MSISAuth") || i.getName().equals("MSISAuthenticated")))
//                        .collect(Collectors.toList())
//                        .forEach(i -> cookieStore.remove(uri, i));

                Connection.Response toPortal = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=oauth&time=" + (System.currentTimeMillis() / 1000))
                        .cookieStore(cookieStore)
                        .execute();
                String data = toPortal.body();
                Connection.Response loginRes;
                if (toPortal.url().getHost().equals(courseNcku)) {
                    // portal auto login
                    loginRes = toPortal;
                } else {
                    // login use portal
                    int loginFormIndex = data.indexOf("id=\"loginForm\"");
                    if (loginFormIndex == -1) {
                        outData.append("err", "[Login] loginForm not found");
                        return false;
                    }
                    int actionLink = data.indexOf("action=\"", loginFormIndex + 14);
                    if (actionLink == -1) {
                        outData.append("err", "[Login] loginForm action link not found");
                        return false;
                    }
                    actionLink += 8;
                    int actionLinkEnd = data.indexOf('"', actionLink);
                    String loginUrl = portalNckuOrg + data.substring(actionLink, actionLinkEnd);

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

                // check if error
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
                    outData.append("msg", errorMessage);
                    return false;
                }

//                System.out.println(loginRes.body());
//                System.out.println(loginRes.url());
                // redirect to home page
                String redirect = loginRes.header("refresh");
                int redirectUrlStart;
                if (redirect == null || (redirectUrlStart = redirect.indexOf("url=")) == -1) {
                    outData.append("err", "[Login] Refresh url not found");
                    return false;
                }
                redirect = redirect.substring(redirectUrlStart + 4);

                String result = HttpConnection.connect(redirect)
                        .cookieStore(cookieStore)
                        .execute().body();

                // check if force login
                if (result.contains("/index.php?c=auth&m=force_login")) {
                    outData.append("warn", "[Login] Force login");
                    result = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=force_login")
                            .cookieStore(cookieStore)
                            .execute().body();
                }

                outData.append("login", result.contains("/index.php?c=auth&m=logout"));
                return true;
            }
        } catch (Exception e) {
            outData.append("err", "[Login] Unknown error: " + e);
            e.printStackTrace();
            return false;
        }
    }
}
