package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.logger.Logger;

import java.io.UnsupportedEncodingException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static com.wavjaby.Main.*;

public class Cookie {
    private static final Logger logger = new Logger("Cookie");

    public static HttpCookie createHttpCookie(String key, String value, String domain) {
        HttpCookie httpCookie = new HttpCookie(key, value);
        httpCookie.setPath("/");
        httpCookie.setVersion(0);
        httpCookie.setDomain(domain);
        return httpCookie;
    }

    public static String getCookie(String name, URI uri, CookieStore cookieStore) {
        for (HttpCookie httpCookie : cookieStore.get(uri))
            if (httpCookie.getName().equals(name))
                return httpCookie.getValue();
        return null;
    }

    public static String getCookie(String name, String[] cookies) {
        if (cookies == null)
            return null;
        for (String httpCookie : cookies)
            if (httpCookie.startsWith(name + '='))
                return httpCookie.substring(name.length() + 1);
        return null;
    }

    public static String unpackAuthCookie(String[] cookieIn, CookieStore cookieStore) {
        if (cookieIn == null) return null;
        String originalCookie = null;
        for (String cookie : cookieIn) {
            int startIndex;
            if ((startIndex = cookie.indexOf("authData=")) != -1) {
                cookie = cookie.substring(startIndex + 9);
                int start = 0, end;
                if ((end = cookie.indexOf("|")) == -1) continue;
                cookieStore.add(portalNckuOrgUri, createHttpCookie("MSISAuth", cookie.substring(start, end), portalNcku));
                start = end + 1;
                if ((end = cookie.indexOf("|", start)) == -1) continue;
                cookieStore.add(portalNckuOrgUri, createHttpCookie("MSISAuthenticated", cookie.substring(start, end), portalNcku));
                start = end + 1;
                cookieStore.add(portalNckuOrgUri, createHttpCookie("MSISLoopDetectionCookie", cookie.substring(start), portalNcku));
                originalCookie = cookie;
                break;
            }
        }
        return originalCookie;
    }

    public static void packAuthCookie(HttpExchange req, String orgCookie, CookieStore cookieStore) {
        StringBuilder cookieValueBuilder = new StringBuilder();
        Map<String, String> portalNckuCookies = new HashMap<>();
        for (HttpCookie i : cookieStore.get(portalNckuOrgUri))
            portalNckuCookies.put(i.getName(), i.getValue());

        if (portalNckuCookies.containsKey("MSISAuth"))
            cookieValueBuilder.append(portalNckuCookies.get("MSISAuth"));
        cookieValueBuilder.append('|');
        if (portalNckuCookies.containsKey("MSISAuthenticated"))
            cookieValueBuilder.append(portalNckuCookies.get("MSISAuthenticated"));
        cookieValueBuilder.append('|');
        if (portalNckuCookies.containsKey("MSISLoopDetectionCookie"))
            cookieValueBuilder.append(portalNckuCookies.get("MSISLoopDetectionCookie"));

        String cookieValue = cookieValueBuilder.toString();
        if (!cookieValue.equals(orgCookie))
            addCookieToHeader("authData", cookieValue, "/api/login", req);
    }

    public static String unpackCourseLoginStateCookie(String[] cookieIn, CookieStore cookieStore) {
        if (cookieIn == null) return null;
        String originalCookie = null;
        for (String cookie : cookieIn) {
            if (cookie.startsWith("courseLoginData=")) {
                cookie = cookie.substring(16);
                int start = 0, end;
                if ((end = cookie.indexOf("|")) == -1) break;
                cookieStore.add(courseNckuOrgUri, createHttpCookie("PHPSESSID", cookie.substring(start, end), courseNcku));
                start = end + 1;
                if ((end = cookie.indexOf("|", start)) == -1) break;
                cookieStore.add(courseNckuOrgUri, createHttpCookie("COURSE_WEB", cookie.substring(start, end), courseNcku));
                start = end + 1;
                if ((end = cookie.indexOf("|", start)) == -1) break;
                cookieStore.add(courseNckuOrgUri, createHttpCookie("COURSE_CDN", cookie.substring(start, end), courseNcku));
                start = end + 1;
                cookieStore.add(courseNckuOrgUri, createHttpCookie("SSO", cookie.substring(start), courseNcku));
                originalCookie = cookie;
                break;
            }
        }
        return originalCookie;
    }

    public static void packCourseLoginStateCookie(HttpExchange req, String orgCookie, CookieStore cookieStore) {
        Map<String, String> courseNckuCookies = new HashMap<>();
        for (HttpCookie i : cookieStore.get(courseNckuOrgUri))
            courseNckuCookies.put(i.getName(), i.getValue());

        StringBuilder cookieValueBuilder = new StringBuilder();
        if (courseNckuCookies.containsKey("PHPSESSID"))
            cookieValueBuilder.append(courseNckuCookies.get("PHPSESSID"));
        cookieValueBuilder.append('|');
        if (courseNckuCookies.containsKey("COURSE_WEB"))
            cookieValueBuilder.append(courseNckuCookies.get("COURSE_WEB"));
        cookieValueBuilder.append('|');
        if (courseNckuCookies.containsKey("COURSE_CDN"))
            cookieValueBuilder.append(courseNckuCookies.get("COURSE_CDN"));
        cookieValueBuilder.append('|');
        if (courseNckuCookies.containsKey("SSO"))
            cookieValueBuilder.append(courseNckuCookies.get("SSO"));

        String cookieValue = cookieValueBuilder.toString();
        if (!cookieValue.equals(orgCookie))
            addCookieToHeader("courseLoginData", cookieValue, "/", req);
    }

    public static String unpackStudentIdSysLoginStateCookie(String[] cookieIn, CookieStore cookieStore) {
        if (cookieIn == null) return null;
        String originalCookie = null;
        for (String cookie : cookieIn) {
            if (cookie.startsWith("stuSysLoginData=")) {
                cookie = cookie.substring(16);
                int start = 0, end;
                if ((end = cookie.indexOf("|", start)) == -1) break;
                String key = cookie.substring(start, end);
                start = end + 1;
                cookieStore.add(stuIdSysNckuOrgUri, createHttpCookie(key, cookie.substring(start), stuIdSysNcku));
                originalCookie = cookie;
                break;
            }
        }
        return originalCookie;
    }

    public static void packStudentIdSysLoginStateCookie(HttpExchange req, String orgCookie, CookieStore cookieStore) {
        String cookieValue = null;
        for (HttpCookie i : cookieStore.get(stuIdSysNckuOrgUri)) {
            if (i.getName().startsWith("ASPSESSION")) {
                cookieValue = i.getName() + '|' + i.getValue();
                break;
            }
        }
        if (cookieValue != null && !cookieValue.equals(orgCookie))
            addCookieToHeader("stuSysLoginData", cookieValue, "/", req);
    }

    public static String[] splitCookie(HttpExchange req) {
        Headers headers = req.getRequestHeaders();
        String cookie;
        if (isSafari(headers)) {
            String q = req.getRequestURI().getRawQuery();
            cookie = q.substring(q.lastIndexOf("cookie=") + 7);
            try {
                cookie = URLDecoder.decode(cookie, "UTF-8");
            } catch (IllegalArgumentException | UnsupportedEncodingException e) {
                logger.errTrace(e);
            }
        } else
            cookie = headers.getFirst("Cookie");
        return cookie == null ? null : cookie.split("; ?");
    }

    public static String getDefaultCookie(HttpExchange req, CookieStore cookieStore) {
        // Unpack cookie
        return unpackCourseLoginStateCookie(splitCookie(req), cookieStore);
    }

    public static void addCookieToHeader(String key, String value, String path, HttpExchange req) {
        Headers headers = req.getResponseHeaders();
        headers.add("Set-Cookie", key + '=' + value + "; Path=" + path +
                "; SameSite=None; OnlyHttp; Secure; Domain=" + cookieDomain);
    }

    public static void addRemoveCookieToHeader(String key, String path, HttpExchange req) {
        addCookieToHeader(key, "; Max-Age=0", path, req);
    }

    public static boolean isSafari(Headers headers) {
        String userAgent = headers.getFirst("User-Agent");
//        return userAgent != null &&
//                userAgent.contains("Safari") && !userAgent.contains("Chrome") &&
//                !userAgent.contains("CriOS") &&
//                !userAgent.contains("FxiOS");
        return userAgent != null &&
                userAgent.contains("Safari") && !userAgent.contains("Chrome");
    }
}
