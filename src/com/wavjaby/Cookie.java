package com.wavjaby;

import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wavjaby.Main.*;

public class Cookie {
    public static void unpackAuthCookie(List<String> cookieIn, CookieManager cookieManager) {
        if (cookieIn == null) return;
        List<String> portalNckuCookieIn = new ArrayList<>();
        for (String cookie : cookieIn) {
            int startIndex;
            if ((startIndex = cookie.indexOf("authData=")) != -1) {
                cookie = cookie.substring(startIndex + 9);
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

    public static String unpackLoginCookie(List<String> cookieIn, CookieManager cookieManager) {
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

    public static void packLoginCookie(Headers headers, String orgCookie, String refererUrl, CookieStore cookieStore) {
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
            outCookie.append("; Path=/; SameSite=None; Secure; Domain=").append(getCookieDomain(refererUrl));
            if (orgCookie == null || !out.endsWith(orgCookie))
                headers.add("Set-Cookie", outCookie.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getCookieInfoData(String refererUrl) {
        return "; SameSite=None; Secure; Domain=" + getCookieDomain(refererUrl);
    }

    public static String removeCookie(String key) {
        return key + "=; Max-Age=0";
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
            outCookie.append("; Path=/api/login; SameSite=None; Secure; Domain=").append(getCookieDomain(refererUrl));
            headers.add("Set-Cookie", outCookie.toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getCookieDomain(String refererUrl) {
        if (refererUrl == null || refererUrl.contains("localhost"))
            return "localhost";
        return cookieDomain;
    }
}
