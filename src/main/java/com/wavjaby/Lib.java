package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.json.JsonBuilder;
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
import java.util.List;
import java.util.Map;

import static com.wavjaby.Main.accessControlAllowOrigin;
import static com.wavjaby.Main.courseNckuOrg;

public class Lib {
    private static final String TAG = "[CosPreCheck] ";

    public static void cosPreCheck(String body, CookieStore cookieStore, JsonBuilder data) {
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
            if (data != null)
                data.append("warn", TAG + "CosPreCheck key not found");
            return;
        }
//        Logger.log(TAG, "Make CosPreCheck " + cookieStore.getCookies().toString());

        long now = System.currentTimeMillis() / 1000;
        try {
            String postData = cosPreCheckKey == null ? ("time=" + now) : ("time=" + now + "&ref=" + URLEncoder.encode(cosPreCheckKey, "UTF-8"));
            HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=cosprecheck&time=" + now)
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .requestBody(postData)
                    .execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        String[] pairs = data.split("&");
        try {
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx == -1) {
                    query.put(
                            URLDecoder.decode(pair, "UTF-8"),
                            null
                    );
                } else
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

    public static void setAllowOrigin(Headers requestHeaders, Headers responseHeader) {
        String refererUrl;
        List<String> clientUrl = requestHeaders.get("Referer");
//        Logger.log(TAG, "Referer: " + clientUrl);
        if (clientUrl != null && clientUrl.size() > 0)
            refererUrl = clientUrl.get(0);
        else return;

        for (String i : accessControlAllowOrigin)
            if (refererUrl.startsWith(i)) {
                responseHeader.set("Access-Control-Allow-Origin", i);
                responseHeader.set("Access-Control-Allow-Credentials", "true");
                return;
            }

        responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin[0]);
        responseHeader.set("Access-Control-Allow-Credentials", "true");
    }

    public static String getRefererUrl(Headers requestHeaders) {
        List<String> clientUrl = requestHeaders.get("Referer");
        if (clientUrl != null && clientUrl.size() > 0)
            return clientUrl.get(0);
        return null;
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
