package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonBuilder;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Main {
    public static final String courseNcku = "course.ncku.edu.tw";
    public static final String portalNcku = "fs.ncku.edu.tw";

    public static final String courseNckuOrg = "https://" + courseNcku;
    public static final String portalNckuOrg = "https://" + portalNcku;

    public static final String accessControlAllowOrigin = "https://wavjaby.github.io";

    public static final String cookieDomain = "wavjaby.github.io";
    ExecutorService exec = Executors.newCachedThreadPool();

    Main() {
        // server
        HttpsServer server = new HttpsServer("key/key.keystore", "key/key.properties");
        server.start(443);

        server.createContext("/", req -> exec.submit(() -> {
            String path = req.getRequestURI().getPath();
            Headers responseHeader = req.getResponseHeaders();
            OutputStream response = req.getResponseBody();
            System.out.println(path);

            try {
                if (path.equals("/")) {
                    responseHeader.set("Content-Type", "text/html; charset=utf-8");
                    File file = new File("./index.html");

                    InputStream in = Files.newInputStream(file.toPath());
                    req.sendResponseHeaders(200, in.available());
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        response.write(buff, 0, len);

                } else {
                    File file = new File("./", path);
                    if (!file.exists())
                        req.sendResponseHeaders(404, 0);
                    else {
                        if (path.endsWith(".js"))
                            responseHeader.set("Content-Type", "text/javascript; charset=utf-8");
                        else if (path.endsWith(".css"))
                            responseHeader.set("Content-Type", "text/css; charset=utf-8");

                        InputStream in = Files.newInputStream(file.toPath());
                        req.sendResponseHeaders(200, in.available());
                        byte[] buff = new byte[1024];
                        int len;
                        while ((len = in.read(buff, 0, buff.length)) > 0)
                            response.write(buff, 0, len);
                    }
                }
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("Done");
        }));

        server.createContext("/api/login", req -> exec.submit(() -> {
            System.out.println("[Login] Login");
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers headers = req.getRequestHeaders();
            OutputStream response = req.getResponseBody();

            try {
                // unpack cookie
                List<String> cookieIn = headers.containsKey("Cookie")
                        ? Arrays.asList(headers.get("Cookie").get(0).split(","))
                        : null;
                String orgCookie = unpackLoginCookie(cookieIn, cookieManager);
                unpackAuthCookie(cookieIn, cookieManager);

                // login
                JsonBuilder data = new JsonBuilder();
                boolean success = login(req, data, cookieStore);
                if (!success) data.append("login", false);

                Headers responseHeader = req.getResponseHeaders();
                packLoginCookie(responseHeader, orgCookie, cookieStore);
                packAuthCookie(responseHeader, cookieStore);

                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin);
                responseHeader.set("Content-Type", "application/json; charset=utf-8");
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                response.write(dataByte);
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("Done");
        }));

        server.createContext("/api/logout", req -> exec.submit(() -> {
            System.out.println("[Login] Logout");
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers headers = req.getRequestHeaders();
            OutputStream response = req.getResponseBody();

            try {
                // unpack cookie
                List<String> cookieIn = headers.containsKey("Cookie")
                        ? Arrays.asList(headers.get("Cookie").get(0).split(","))
                        : null;
                String orgCookie = unpackLoginCookie(cookieIn, cookieManager);

                // login
                JsonBuilder data = new JsonBuilder();
                boolean success = logout(data, cookieStore);

                Headers responseHeader = req.getResponseHeaders();
                packLoginCookie(responseHeader, orgCookie, cookieStore);

                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin);
                responseHeader.set("Content-Type", "application/json; charset=utf-8");
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                response.write(dataByte);
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("Done");
        }));

        server.createContext("/api/courseSchedule", req -> exec.submit(() -> {
            System.out.println("[Schedule] Get schedule");
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers headers = req.getRequestHeaders();
            OutputStream response = req.getResponseBody();

            try {
                // unpack cookie
                List<String> cookieIn = headers.containsKey("Cookie")
                        ? Arrays.asList(headers.get("Cookie").get(0).split(","))
                        : null;
                String orgCookie = unpackLoginCookie(cookieIn, cookieManager);

                JsonBuilder data = new JsonBuilder();
                boolean success = getCourseSchedule(cookieStore, data);

                Headers responseHeader = req.getResponseHeaders();
                packLoginCookie(responseHeader, orgCookie, cookieStore);

                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Content-Type", "application/json; charset=utf-8");
                responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                response.write(dataByte);
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("Done");
        }));
    }

    public boolean getCourseSchedule(CookieStore cookieStore, JsonBuilder data) {
        Connection conn = HttpConnection.connect("https://course.ncku.edu.tw/index.php?c=cos32315")
                .cookieStore(cookieStore);
        Document root = null;
        try {
            root = conn.get();
        } catch (IOException ignore) {
        }
        if (root == null) {
            data.append("err", "[Schedule] Can not fetch schedule");
            return false;
        }

        Elements tableData = root.getElementsByClass("row visible-xs");
        if (tableData.size() < 1) {
            data.append("err", "[Schedule] Table not found");
            return false;
        }
        Elements tbody = tableData.get(0).getElementsByTag("tbody");
        if (tbody.size() < 7) {
            data.append("err", "[Schedule] Table body not found");
            return false;
        }
        Element ownerInfoEle = tableData.get(0).child(0);
        if (ownerInfoEle.childNodeSize() < 2 || ownerInfoEle.getElementsByClass("clock").size() == 0) {
            data.append("err", "[Schedule] OwnerInfo not found");
            return false;
        }
        ownerInfoEle = ownerInfoEle.child(1);
        String[] ownerInfoArr = ownerInfoEle.textNodes().get(0).toString().split("&nbsp; ");
        if (ownerInfoArr.length != 3) {
            data.append("err", "[Schedule] OwnerInfo parse error");
            return false;
        }
        int creditsStart = -1, creditsEnd = -1;
        char[] creditsParseArr = ownerInfoArr[2].toCharArray();
        for (int i = 0; i < creditsParseArr.length; i++) {
            char ch = creditsParseArr[i];
            if (ch >= '0' && ch <= '9')
                if (creditsStart == -1) creditsStart = i;
                else creditsEnd = i;
            else if (creditsEnd != -1)
                break;
        }
        if (creditsStart == -1 || creditsEnd == -1) {
            data.append("err", "[Schedule] OwnerInfo credits parse error");
            return false;
        }
        ownerInfoArr[2] = ownerInfoArr[2].substring(creditsStart, creditsEnd + 1);

        JsonArray courseScheduleData = new JsonArray();

        for (Element element : tbody) {
            JsonArray array = new JsonArray();
            courseScheduleData.add(array);
            // section times
            Elements eachCourse = element.getElementsByTag("tr");
            if (eachCourse.size() < 18) {
                data.append("err", "[Schedule] Course section not found");
                return false;
            }
            for (int i = 1; i < 18; i++) {
                Elements elements = eachCourse.get(i).getElementsByTag("td");
                if (elements.size() == 0) {
                    data.append("err", "[Schedule] Course info not found");
                    return false;
                }
                List<TextNode> courseDataText = elements.get(0).textNodes();

                JsonArray courseData = new JsonArray();
                array.add(courseData);
                if (courseDataText.size() > 0) {
                    String courseName = courseDataText.get(0).text();
                    int courseIdEnd = courseName.indexOf("】");
                    if (courseIdEnd == -1) {
                        data.append("err", "[Schedule] Course name parse error");
                        return false;
                    }
                    courseData.add(courseName.substring(1, courseIdEnd));
                    courseData.add(courseName.substring(courseIdEnd + 1));
                    if (courseDataText.size() > 1) {
                        String locationText = courseDataText.get(1).text();
                        int locationStart = locationText.indexOf("：");
                        if (locationStart == -1) {
                            data.append("err", "[Schedule] Course location parse error");
                            return false;
                        }
                        courseData.add(locationText.substring(locationStart + 1, locationText.length() - 1));
                    } else
                        courseData.add("");
                }
            }
        }
        data.append("id", ownerInfoArr[0]);
        data.append("name", ownerInfoArr[1]);
        data.append("credits", ownerInfoArr[2]);
        data.append("schedule", courseScheduleData.toString(), true);
        return true;
    }

    public void unpackAuthCookie(List<String> cookieIn, CookieManager cookieManager) {
        if (cookieIn == null) return;
        List<String> portalNckuCookieIn = new ArrayList<>();
        for (String cookie : cookieIn) {
            int startIndex;
            if ((startIndex = cookie.indexOf("authData=")) != -1) {
                cookie = cookie.substring(startIndex + 10);
                int start = 0, end;
                if ((end = cookie.indexOf("|")) == -1) continue;
                portalNckuCookieIn.add("MSISAuth=" + cookie.substring(start, end));
                start = end + 1;
                if ((end = cookie.indexOf("|", start)) == -1) continue;
                portalNckuCookieIn.add("MSISAuthenticated=" + cookie.substring(start, end));
                start = end + 1;
                portalNckuCookieIn.add("MSISLoopDetectionCookie=" + cookie.substring(start));
                break;
            }
        }

        try {
            cookieManager.put(new URI(portalNckuOrg), new HashMap<String, List<String>>() {{
                put("Set-Cookie", portalNckuCookieIn);
            }});
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String unpackLoginCookie(List<String> cookieIn, CookieManager cookieManager) {
        if (cookieIn == null) return null;
        List<String> courseNckuCookieIn = new ArrayList<>();
        String originalCookie = null;
        for (String cookie : cookieIn) {
            int startIndex;
            if ((startIndex = cookie.indexOf("loginData=")) != -1) {
                cookie = cookie.substring(startIndex + 10);
                int start = 0, end;
                if ((end = cookie.indexOf("|")) == -1) continue;
                courseNckuCookieIn.add("PHPSESSID=" + cookie.substring(start, end));
                start = end + 1;
                if ((end = cookie.indexOf("|", start)) == -1) continue;
                courseNckuCookieIn.add("COURSE_WEB=" + cookie.substring(start, end));
                start = end + 1;
                if ((end = cookie.indexOf("|", start)) == -1) continue;
                courseNckuCookieIn.add("COURSE_CDN=" + cookie.substring(start, end));
                start = end + 1;
                courseNckuCookieIn.add("SSO=" + cookie.substring(start));
                originalCookie = cookie;
                break;
            }
        }
        try {
            cookieManager.put(new URI(courseNckuOrg), new HashMap<String, List<String>>() {{
                put("Set-Cookie", courseNckuCookieIn);
            }});
            return originalCookie;
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void packLoginCookie(Headers headers, String orgCookie, CookieStore cookieStore) {
        try {
            StringBuilder outCookie = new StringBuilder();
            outCookie.append("loginData=");
            Map<String, String> courseNckuCookies = new HashMap<>();
            for (HttpCookie i : cookieStore.get(new URI(courseNckuOrg)))
                courseNckuCookies.put(i.getName(), i.getValue());

            if (courseNckuCookies.containsKey("PHPSESSID"))
                outCookie.append(courseNckuCookies.get("PHPSESSID"));
            outCookie.append('|');
            if (courseNckuCookies.containsKey("COURSE_WEB"))
                outCookie.append(courseNckuCookies.get("COURSE_WEB"));
            outCookie.append('|');
            if (courseNckuCookies.containsKey("COURSE_CDN"))
                outCookie.append(courseNckuCookies.get("COURSE_CDN"));
            outCookie.append('|');
            if (courseNckuCookies.containsKey("SSO"))
                outCookie.append(courseNckuCookies.get("SSO"));
            String out = outCookie.toString();
            outCookie.append("; Path=/NCKU; Domain=" + cookieDomain);
            if (orgCookie == null || !out.endsWith(orgCookie))
                headers.add("Set-Cookie", outCookie.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void packAuthCookie(Headers headers, CookieStore cookieStore) {
        try {
            StringBuilder outCookie = new StringBuilder();
            Map<String, String> portalNckuCookies = new HashMap<>();
            outCookie.append("authData=");
            for (HttpCookie i : cookieStore.get(new URI(portalNckuOrg)))
                portalNckuCookies.put(i.getName(), i.getValue());

            if (portalNckuCookies.containsKey("MSISAuth"))
                outCookie.append(portalNckuCookies.get("MSISAuth"));
            outCookie.append('|');
            if (portalNckuCookies.containsKey("MSISAuthenticated"))
                outCookie.append(portalNckuCookies.get("MSISAuthenticated"));
            outCookie.append('|');
            if (portalNckuCookies.containsKey("MSISLoopDetectionCookie"))
                outCookie.append(portalNckuCookies.get("MSISLoopDetectionCookie"));
            outCookie.append("; Path=/NCKU/api/login; Domain=" + cookieDomain);
            headers.add("Set-Cookie", outCookie.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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
                    postLogin.header("Content-Type", "application/x-www-form-urlencoded");
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
            return false;
        }
    }

    public boolean logout(JsonBuilder outData, CookieStore cookieStore) {
        try {
            Connection.Response toLogin = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=logout")
                    .cookieStore(cookieStore)
                    .execute();

            outData.append("login", toLogin.body().contains("/index.php?c=auth&m=logout"));
            return true;
        } catch (Exception e) {
            outData.append("err", "[Login] Unknown error: " + e);
            return false;
        }
    }

    public String readResponse(HttpExchange req) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = req.getRequestBody();
            byte[] buff = new byte[1024];
            int len;
            while ((len = in.read(buff, 0, buff.length)) > 0)
                out.write(buff, 0, len);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> parseUrlEncodedForm(String data) {
        Map<String, String> query = new HashMap<>();
        String[] pairs = data.split("&");
        try {
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx == -1) continue;
                query.put(
                        URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                );
            }
            return query;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Main();
    }
}
