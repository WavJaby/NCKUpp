package com.wavjaby;

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

    public static void unpackAuthCookie(String[] cookieIn, CookieStore cookieStore) {
        if (cookieIn == null) return;
        try {
            URI uri = new URI(portalNckuOrg);
            for (String cookie : cookieIn) {
                int startIndex;
                if ((startIndex = cookie.indexOf("authData=")) != -1) {
                    cookie = cookie.substring(startIndex + 9);
                    int start = 0, end;
                    if ((end = cookie.indexOf("|")) == -1) continue;
                    cookieStore.add(uri, createHttpCookie("MSISAuth", cookie.substring(start, end), portalNcku));
                    start = end + 1;
                    if ((end = cookie.indexOf("|", start)) == -1) continue;
                    cookieStore.add(uri, createHttpCookie("MSISAuthenticated", cookie.substring(start, end), portalNcku));
                    start = end + 1;
                    cookieStore.add(uri, createHttpCookie("MSISLoopDetectionCookie", cookie.substring(start), portalNcku));
                    break;
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void packAuthCookie(Headers headers, String refererUrl, CookieStore cookieStore) {
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
            outCookie.append("; Path=/api/login").append(getCookieInfoData(refererUrl));
            headers.add("Set-Cookie", outCookie.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String unpackLoginStateCookie(String[] cookieIn, CookieStore cookieStore) {
        if (cookieIn == null) return null;
        String originalCookie = null;
        try {
            URI uri = new URI(courseNckuOrg);
            for (String cookie : cookieIn) {
                int startIndex;
                if ((startIndex = cookie.indexOf("loginData=")) != -1) {
                    cookie = cookie.substring(startIndex + 10);
                    int start = 0, end;
                    if ((end = cookie.indexOf("|")) == -1) break;
                    cookieStore.add(uri, createHttpCookie("PHPSESSID", cookie.substring(start, end), courseNcku));
                    start = end + 1;
                    if ((end = cookie.indexOf("|", start)) == -1) break;
                    cookieStore.add(uri, createHttpCookie("COURSE_WEB", cookie.substring(start, end), courseNcku));
                    start = end + 1;
                    if ((end = cookie.indexOf("|", start)) == -1) break;
                    cookieStore.add(uri, createHttpCookie("COURSE_CDN", cookie.substring(start, end), courseNcku));
                    start = end + 1;
                    cookieStore.add(uri, createHttpCookie("SSO", cookie.substring(start), courseNcku));
                    originalCookie = cookie;
                    break;
                }
            }
            return originalCookie;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void packLoginStateCookie(Headers headers, String orgCookie, String refererUrl, CookieStore cookieStore) {
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
            outCookie.append("; Path=/").append(getCookieInfoData(refererUrl));
            if (orgCookie == null || !out.endsWith(orgCookie))
                headers.add("Set-Cookie", outCookie.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getDefaultCookie(Headers requestHeaders, CookieStore cookieStore) {
        // unpack cookie
        String[] cookieIn = requestHeaders.containsKey("Cookie")
                ? requestHeaders.get("Cookie").get(0).split(";")
                : null;
        return unpackLoginStateCookie(cookieIn, cookieStore);
    }

    public static String getDefaultLoginCookie(Headers requestHeaders, CookieStore cookieStore) {
        // unpack cookie
        String[] cookieIn = requestHeaders.containsKey("Cookie")
                ? requestHeaders.get("Cookie").get(0).split(";")
                : null;
        unpackAuthCookie(cookieIn, cookieStore);
        return unpackLoginStateCookie(cookieIn, cookieStore);
    }

    public static String getCookieInfoData(String refererUrl) {
        return "; SameSite=None; Secure; Domain=" + getCookieDomain(refererUrl);
    }

    public static String removeCookie(String key) {
        return key + "=; Max-Age=0";
    }

    public static String getCookieDomain(String refererUrl) {
        if (refererUrl == null || refererUrl.contains("localhost"))
            return "localhost";
        return cookieDomain;
    }
}
