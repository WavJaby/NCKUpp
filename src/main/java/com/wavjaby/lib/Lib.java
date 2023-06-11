package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.ProxyManager;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

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
import static com.wavjaby.Main.courseNckuOrg;

public class Lib {
    private static final String TAG = "[CosPreCheck]";
    private static final Logger logger = new Logger(TAG);

    public static void cosPreCheck(String body, CookieStore cookieStore, ApiResponse response, ProxyManager proxyManager) {
        String cosPreCheckKey = null;
        int cosPreCheckStart = body.indexOf("m=cosprecheck");
        if (cosPreCheckStart != -1) {
            cosPreCheckStart = body.indexOf("&ref=", cosPreCheckStart + 13);
            if (cosPreCheckStart != -1) {
                cosPreCheckStart += 5;
                int cosPreCheckEnd = body.indexOf('"', cosPreCheckStart);
                if (cosPreCheckEnd != -1)
                    cosPreCheckKey = body.substring(cosPreCheckStart, cosPreCheckEnd);
            }
        } else {
            if (response != null)
                response.addWarn(TAG + "CosPreCheck key not found");
            return;
        }
//        logger.log("Make CosPreCheck " + cookieStore.getCookies().toString());

        long now = System.currentTimeMillis() / 1000;
        try {
            String postData = cosPreCheckKey == null ? ("time=" + now) : ("time=" + now + "&ref=" + URLEncoder.encode(cosPreCheckKey, "UTF-8"));
            HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=cosprecheck&time=" + now)
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .method(Connection.Method.POST)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .requestBody(postData)
                    .timeout(5000)
                    .execute();
        } catch (IOException e) {
            logger.errTrace(e);
            if (response != null)
                response.addWarn(TAG + "Network error");
        }
    }

    public static String readResponse(HttpExchange req) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = req.getRequestBody();
            byte[] buff = new byte[1024];
            int len;
            while ((len = in.read(buff, 0, buff.length)) > 0)
                out.write(buff, 0, len);
            in.close();
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                if (arr[i] == '=') {
                    key = data.substring(start, i);
                    start = i + 1;
                } else if (arr[i] == '&') {
                    if (key != null)
                        query.put(
                                URLDecoder.decode(key, "UTF-8"),
                                start == i ? null : URLDecoder.decode(data.substring(start, i), "UTF-8")
                        );
                    start = i + 1;
                }
                // Last
                if (i + 1 == arr.length)
                    if (key != null)
                        query.put(
                                URLDecoder.decode(key, "UTF-8"),
                                start == i + 1 ? null : URLDecoder.decode(data.substring(start), "UTF-8")
                        );
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return query;
    }

    public static void setAllowOrigin(Headers requestHeaders, Headers responseHeader) {
        String originUrl = requestHeaders.getFirst("Origin");
        if (originUrl == null)
            return;

        for (String i : accessControlAllowOrigin)
            if (originUrl.equals(i)) {
                responseHeader.set("Access-Control-Allow-Origin", i);
                responseHeader.set("Access-Control-Allow-Credentials", "true");
                return;
            }
        if (originUrl.startsWith("http://localhost") || originUrl.startsWith("https://localhost"))
            responseHeader.set("Access-Control-Allow-Origin", originUrl);
        else
            responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin[0]);
        responseHeader.set("Access-Control-Allow-Credentials", "true");
    }

    public static String getOriginUrl(Headers requestHeaders) {
        return requestHeaders.getFirst("Origin");
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
}
