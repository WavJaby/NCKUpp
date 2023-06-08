package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static com.wavjaby.Main.*;

public class Cookie {
    public static HttpCookie createHttpCookie(String key, String value, String domain) {
        HttpCookie httpCookie = new HttpCookie(key, value);
        httpCookie.setPath("/");
        httpCookie.setVersion(0);
        httpCookie.setDomain(domain);
        return httpCookie;
    }

    public static String getCookie(String name, String url, CookieStore cookieStore) {
        try {
            for (HttpCookie httpCookie : cookieStore.get(new URI(url)))
                if (httpCookie.getName().equalsIgnoreCase(name))
                    return httpCookie.getValue();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
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

    public static void packAuthCookie(Headers headers, String orgCookie, String originUrl, CookieStore cookieStore) {
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
            headers.add("Set-Cookie", "authData=" + cookieValue + "; Path=/api/login" + setCookieDomain(originUrl));
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

    public static void packCourseLoginStateCookie(Headers headers, String orgCookie, String originUrl, CookieStore cookieStore) {
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
            headers.add("Set-Cookie", "courseLoginData=" + cookieValue + "; Path=/" + setCookieDomain(originUrl));
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

    public static void packStudentIdSysLoginStateCookie(Headers headers, String orgCookie, String originUrl, CookieStore cookieStore) {
        String cookieValue = null;
        for (HttpCookie i : cookieStore.get(stuIdSysNckuOrgUri)) {
            String out = i.getName() + '|' + i.getValue();
            if (cookieValue == null || !cookieValue.equals(orgCookie))
                cookieValue = out;
        }
        if (cookieValue != null && !cookieValue.equals(orgCookie))
            headers.add("Set-Cookie", "stuSysLoginData=" + cookieValue + "; Path=/" + setCookieDomain(originUrl));
    }

    public static String[] splitCookie(Headers requestHeaders) {
        String cookie = requestHeaders.getFirst("Cookie");
        return cookie == null ? null : cookie.split("; ?");
    }

    public static String getDefaultCookie(Headers requestHeaders, CookieStore cookieStore) {
        // Unpack cookie
        return unpackCourseLoginStateCookie(splitCookie(requestHeaders), cookieStore);
    }

    public static String setCookieDomain(String originUrl) {
        if (originUrl == null || originUrl.startsWith("http://localhost") || originUrl.startsWith("https://localhost"))
            return "; SameSite=None; Secure; Domain=localhost";
        return "; SameSite=None; Secure; Domain=" + cookieDomain;
    }

    public static String removeCookie(String key) {
        return key + "=; Max-Age=0";
    }
}
