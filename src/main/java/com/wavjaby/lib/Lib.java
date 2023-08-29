package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.ProxyManager;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.CookieStore;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.Main.accessControlAllowOrigin;

public class Lib {
    private static final String TAG = "[CosPreCheck]";
    private static final Logger logger = new Logger(TAG);

    public static void cosPreCheck(String urlOrigin, String body, CookieStore cookieStore, ApiResponse response, ProxyManager proxyManager) {
        String cosPreCheckKey = null;
        int cosPreCheckStart = body.indexOf("m=cosprecheck");
        if (cosPreCheckStart == -1) {
            if (response != null)
                response.addWarn(TAG + "CosPreCheck not found");
            logger.warn("CosPreCheck not found");
            return;
        }
        if ((cosPreCheckStart = body.indexOf("&ref=", cosPreCheckStart + 13)) != -1) {
            cosPreCheckStart += 5;
            int cosPreCheckEnd = body.indexOf('"', cosPreCheckStart);
            if (cosPreCheckEnd != -1)
                cosPreCheckKey = body.substring(cosPreCheckStart, cosPreCheckEnd);
        }
//        logger.log("Make CosPreCheck " + cookieStore.getCookies().toString());
        long now = System.currentTimeMillis() / 1000;
        String postData = "time=" + now;
        if (cosPreCheckKey != null) {
            try {
                postData += "&ref=" + URLEncoder.encode(cosPreCheckKey, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                logger.errTrace(e);
            }
        }

        // 3 try
        for (int i = 0; i < 2; i++) {
            try {
                Connection.Response res = HttpConnection.connect(urlOrigin + "/index.php?c=portal&m=cosprecheck&time=" + now)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .method(Connection.Method.POST)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .requestBody(postData)
                        .timeout(10000)
                        .execute();
//                logger.log(res.body());
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

    public static Element checkCourseNckuLoginRequiredPage(Connection connection, ApiResponse response, boolean useWarn) {
        try {
            Connection.Response res = connection.execute();
            if (res.statusCode() == 301) {
                String location = res.header("location");
                if (location != null && location.endsWith("index.php?auth")) {
                    if (useWarn) {
                        response.addWarn("Not login");
                        return res.parse().body();
                    } else
                        response.errorLoginRequire();
                } else
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

    public static void executorShutdown(ExecutorService service, long timeoutMillis, String name) {
        service.shutdown();
        try {
            if (!service.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                logger.warn("[Executor] " + name + " shutdown timeout");
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.errTrace(e);
            logger.warn("[Executor] " + name + " shutdown error");
            service.shutdownNow();
        }
    }

    public static File getFileFromPath(String filePath, boolean mkdir) {
        File file = new File(filePath);

        // Check parent folder
        File folder = file.getParentFile();
        if (folder.exists() && !folder.isDirectory()) {
            logger.err(folder.getAbsolutePath() + " is file not directory");
            return file;
        }

        if (!mkdir)
            return file;

        // Create parent folder if not exist
        if (!folder.exists()) {
            if (!folder.mkdirs())
                logger.err("Failed to create directory: " + folder.getAbsolutePath());
        }
        return file;
    }

    public static boolean createFileIfNotExist(File file) {
        if (!file.exists()) {
            try {
                return file.createNewFile();
            } catch (IOException e) {
                logger.errTrace(e);
                return false;
            }
        } else if (file.isDirectory()) {
            logger.err(file.getAbsolutePath() + " is a directory");
            return false;
        }
        return true;
    }

    public static String readFileToString(File file, boolean createIfNotExist, Charset charset) {
        // Check parent folder
        File folder = file.getParentFile();
        if (folder.exists() && !folder.isDirectory()) {
            logger.err(folder.getAbsolutePath() + " is file not directory");
            return null;
        }

        if (createIfNotExist) {
            if (!createFileIfNotExist(file))
                return null;
        } else if (!file.exists()) {
            return null;
        } else if (file.isDirectory()) {
            logger.err(file.getAbsolutePath() + " is directory");
            return null;
        }

        try {
            return readInputStreamToString(Files.newInputStream(file.toPath()), charset);
        } catch (IOException e) {
            logger.err(e);
            return null;
        }
    }

    public static String readRequestBody(HttpExchange req, Charset charset) throws IOException {
        InputStream in = req.getRequestBody();
        return readInputStreamToString(in, charset);
    }

    public static String readInputStreamToString(InputStream in, Charset charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        int len;
        while ((len = in.read(buff, 0, buff.length)) > 0)
            out.write(buff, 0, len);
        in.close();
        return out.toString(charset.name());
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

    public static String[] simpleSplit(String input, char splitter) {
        ArrayList<String> arr = new ArrayList<>();
        int off = 0, next;
        while ((next = input.indexOf(splitter, off)) != -1) {
            arr.add(input.substring(off, next));
            off = next + 1;
        }
        if (input.length() > off)
            arr.add(input.substring(off));
        return arr.toArray(new String[0]);
    }

    public static Byte sectionCharToByte(char section) {
        if (section <= '4') return (byte) (section - '0');
        if (section == 'N') return 5;
        if (section <= '9') return (byte) (section - '5' + 6);
        if (section >= 'A' && section <= 'E') return (byte) (section - 'A' + 11);
        if (section >= 'a' && section <= 'e') return (byte) (section - 'a' + 11);
        throw new NumberFormatException();
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
