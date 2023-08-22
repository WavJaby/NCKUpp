package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.ProxyManager;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieStore;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.wavjaby.Main.accessControlAllowOrigin;

public class Lib {
    private static final String TAG = "[CosPreCheck]";
    private static final Logger logger = new Logger(TAG);

    public static void cosPreCheck(String urlOrigin, String body, CookieStore cookieStore, ApiResponse response, ProxyManager proxyManager) {
        String cosPreCheckKey = null;
        int cosPreCheckStart;
        if ((cosPreCheckStart = body.indexOf("m=cosprecheck")) != -1 &&
                (cosPreCheckStart = body.indexOf("&ref=", cosPreCheckStart + 13)) != -1) {
            cosPreCheckStart += 5;
            int cosPreCheckEnd = body.indexOf('"', cosPreCheckStart);
            if (cosPreCheckEnd != -1)
                cosPreCheckKey = body.substring(cosPreCheckStart, cosPreCheckEnd);
        }
        if (cosPreCheckKey == null) {
            if (response != null)
                response.addWarn(TAG + "CosPreCheck key not found");
            logger.warn("CosPreCheck key not found");
            return;
        }
//        logger.log("Make CosPreCheck " + cookieStore.getCookies().toString());

        // 3 try
        for (int i = 0; i < 2; i++) {
            long now = System.currentTimeMillis() / 1000;
            try {
                String postData = "time=" + now + "&ref=" + URLEncoder.encode(cosPreCheckKey, "UTF-8");
                HttpConnection.connect(urlOrigin + "/index.php?c=portal&m=cosprecheck&time=" + now)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .method(Connection.Method.POST)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .requestBody(postData)
                        .timeout(5000)
                        .execute();
                return;
            } catch (IOException e) {
//                logger.errTrace(e);
                logger.warn("CosPreCheck timeout");
            }
        }
        // Failed
        if (response != null)
            response.addWarn(TAG + "Network error");
    }

    public static Element checkCourseNckuLoginRequiredPage(Connection connection, ApiResponse response) {
        try {
            Connection.Response res = connection.execute();
            if (res.statusCode() == 301) {
                String location = res.header("location");
                if (location != null && location.endsWith("index.php?auth"))
                    response.errorLoginRequire();
                else
                    response.errorParse("Redirect but unknown location");
                return null;
            }
            return res.parse().body();
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return null;
        }
    }

    public static String readRequestBody(HttpExchange req) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = req.getRequestBody();
        byte[] buff = new byte[1024];
        int len;
        while ((len = in.read(buff, 0, buff.length)) > 0)
            out.write(buff, 0, len);
        in.close();
        return out.toString("UTF-8");
    }

    public static Map<String, String> parseUrlEncodedForm(String data) {
        Map<String, String> query = new HashMap<>();
        if (data == null)
            return query;

        char[] arr = data.toCharArray();
        int start = 0;
        String key = null;
        try {
            for (int i = 0; i < arr.length; i++) {
                if (key == null && arr[i] == '=') {
                    key = data.substring(start, i);
                    start = i + 1;
                }

                if (arr[i] == '&') {
                    if (key != null) {
                        query.put(URLDecoder.decode(key, "UTF-8"),
                                start == i ? "" : URLDecoder.decode(data.substring(start, i), "UTF-8")
                        );
                        key = null;
                    }
                    start = i + 1;
                }
            }
            // Last key
            if (key != null)
                query.put(URLDecoder.decode(key, "UTF-8"),
                        start == arr.length ? "" : URLDecoder.decode(data.substring(start), "UTF-8")
                );
            else if (start != arr.length)
                query.put(URLDecoder.decode(data.substring(start), "UTF-8"), null);
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            logger.errTrace(e);
        }
        return query;
    }

    public static void setAllowOrigin(Headers requestHeaders, Headers responseHeader) {
        String originUrl = requestHeaders.getFirst("Origin");
        if (originUrl == null)
            return;

        responseHeader.set("Access-Control-Allow-Credentials", "true");
        for (String i : accessControlAllowOrigin)
            if (originUrl.equals(i)) {
                responseHeader.set("Access-Control-Allow-Origin", i);
                return;
            }
        if (originUrl.startsWith("http://localhost") || originUrl.startsWith("https://localhost"))
            responseHeader.set("Access-Control-Allow-Origin", originUrl);
        else
            responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin[0]);
    }

    public static String parseUnicode(String input) {
        int lastIndex = 0, index;
        int length = input.length();
        index = input.indexOf("\\u");
        StringBuilder builder = new StringBuilder();
        while (index > -1) {
            if (index > (length - 6)) break;
            int nuiCodeStart = index + 2;
            int nuiCodeEnd = nuiCodeStart + 4;
            String substring = input.substring(nuiCodeStart, nuiCodeEnd);
            int number = Integer.parseInt(substring, 16);

            builder.append(input, lastIndex, index);
            builder.append((char) number);

            lastIndex = nuiCodeEnd;
            index = input.indexOf("\\u", nuiCodeEnd);
        }
        builder.append(input, lastIndex, length);
        return builder.toString();
    }

    public static String leftPad(String input, int length, char chr) {
        StringBuilder builder = new StringBuilder();
        for (int i = input.length(); i < length; i++)
            builder.append(chr);
        return builder + input;
    }
}
