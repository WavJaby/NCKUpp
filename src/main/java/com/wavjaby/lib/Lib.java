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
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.Main.accessControlAllowOrigin;

public class Lib {
    private static final String TAG = "Lib";
    private static final Logger logger = new Logger(TAG);
    public final static UserPrincipal userPrincipal;
    public final static GroupPrincipal groupPrincipal;
    public final static Set<PosixFilePermission> filePermission;
    public final static Set<PosixFilePermission> directoryPermission;

    static {
        // Get permission
        filePermission = PosixFilePermissions.fromString("rw-rw-r--");
        directoryPermission = PosixFilePermissions.fromString("rwxrw-r--");
        UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
        UserPrincipal userPrincipal_ = null;
        GroupPrincipal groupPrincipal_ = null;
        try {
            String userHostName = InetAddress.getLocalHost().getHostName();
            userPrincipal_ = lookupService.lookupPrincipalByName(userHostName);
            groupPrincipal_ = lookupService.lookupPrincipalByGroupName(userHostName);
        } catch (IOException e) {
            logger.warn(e);
        }
        userPrincipal = userPrincipal_;
        groupPrincipal = groupPrincipal_;
    }

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
            }
        }
        // Failed
        if (response != null) {
            response.addWarn(TAG + "CosPreCheck Network error");
            logger.warn("CosPreCheck Network error");
        }
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

    public static String checkCourseNckuLoginRequiredPageStr(Connection connection, ApiResponse response, boolean useWarn) {
        try {
            Connection.Response res = connection.execute();
            if (res.statusCode() == 301) {
                String location = res.header("location");
                if (location != null && location.endsWith("index.php?auth")) {
                    if (useWarn) {
                        response.addWarn("Not login");
                        return res.body();
                    } else
                        response.errorLoginRequire();
                } else
                    response.errorParse("Redirect but unknown location");
                return null;
            }
            return res.body();
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return null;
        }
    }

    public static String checkCourseNckuPageError(Element body) {
        // Get if error
        Element error = body.getElementById("error");
        if (error == null || error.parent() == null || error.parent().attr("style").equals("display:none;"))
            return null;

        Element errorText;
        if ((errorText = error.getElementsByClass("note-desc").first()) != null) {
            return errorText.text().trim();
        }
        return null;
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

    public static File getDirectoryFromPath(String filePath, boolean mkdir) {
        return getDirectory(new File(filePath), mkdir, null, null, null);
    }

    public static File getDirectory(File folder, boolean mkdir,
                                    UserPrincipal user, GroupPrincipal group, Set<PosixFilePermission> permission) {
        // Exist but not folder
        if (folder.exists() && !folder.isDirectory()) {
            logger.err(folder.getAbsolutePath() + " is file not directory");
            return folder;
        }

        if (!mkdir)
            return folder;

        // Create parent folder if not exist
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                logger.err("Failed to create directory: " + folder.getAbsolutePath());
                return folder;
            }
            setFilePermission(folder, user, group, permission);
        }
        return folder;
    }

    public static File getFileFromPath(String filePath, boolean mkdir, boolean createIfNotExist) {
        return getFile(new File(filePath), mkdir, createIfNotExist, null, null, null, null);
    }

    public static File getFile(File file, boolean mkdir, boolean createIfNotExist,
                               UserPrincipal user, GroupPrincipal group, Set<PosixFilePermission> directoryPermission, Set<PosixFilePermission> filePermission) {
        // Check parent folder
        File folder = getDirectory(file.getParentFile(), mkdir, user, group, directoryPermission);
        if (!folder.exists() || !folder.isDirectory()) {
            return file;
        }

        // Exist but not file
        if (file.exists() && !file.isFile()) {
            logger.err(file.getAbsolutePath() + " is directory not file");
            return file;
        }

        // Create file if not exist
        if (!file.exists() && createIfNotExist) {
            try {
                if (!file.createNewFile()) {
                    logger.err("Failed to create directory: " + folder.getAbsolutePath());
                    return file;
                }
            } catch (IOException e) {
                logger.errTrace(e);
                return file;
            }
            setFilePermission(folder, user, group, filePermission);
        }
        return file;
    }

    public static String readFileToString(File file, boolean createIfNotExist, Charset charset) {
        getFile(file, createIfNotExist, createIfNotExist, null, null, null, null);
        // Check parent folder
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        try {
            return readInputStreamToString(Files.newInputStream(file.toPath()), charset);
        } catch (IOException e) {
            logger.errTrace(e);
            return null;
        }
    }

    public static String readRequestBody(HttpExchange req, Charset charset) throws IOException {
        return readInputStreamToString(req.getRequestBody(), charset);
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
        if (originUrl.startsWith("http://localhost") || originUrl.startsWith("https://localhost") || originUrl.startsWith("https://192.168."))
            responseHeader.set("Access-Control-Allow-Origin", originUrl);
        else
            responseHeader.set("Access-Control-Allow-Origin", accessControlAllowOrigin[0]);
    }

    public static String[] simpleSplit(String input, char splitter) {
        return simpleSplitToArray(input, splitter).toArray(new String[0]);
    }

    public static List<String> simpleSplitToArray(String input, char splitter) {
        ArrayList<String> arr = new ArrayList<>();
        int off = 0, next;
        while ((next = input.indexOf(splitter, off)) != -1) {
            arr.add(input.substring(off, next));
            off = next + 1;
        }
        if (input.length() > off)
            arr.add(input.substring(off));
        return arr;
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

    public static void setFilePermission(File file, UserPrincipal user, GroupPrincipal group, Set<PosixFilePermission> permission) {
        PosixFileAttributeView attributeView = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributeView == null)
            return;
        try {
            if (user != null)
                attributeView.setOwner(user);
            else if (userPrincipal != null)
                attributeView.setOwner(userPrincipal);
            if (group != null)
                attributeView.setGroup(group);
            else if (groupPrincipal != null)
                attributeView.setGroup(groupPrincipal);
            if (permission != null)
                attributeView.setPermissions(permission);
            else if (file.isFile()) {
                if (filePermission != null)
                    attributeView.setPermissions(filePermission);
            } else if (file.isDirectory()) {
                if (directoryPermission != null)
                    attributeView.setPermissions(directoryPermission);
            }
        } catch (IOException e) {
            logger.errTrace(e);
        }
    }

    public static String findStringBetween(String input, String where, String from, String end) {
        int startIndex = input.indexOf(where), endIndex = -1;
        if (startIndex != -1) startIndex = input.indexOf(from, startIndex + where.length());
        if (startIndex != -1) endIndex = input.indexOf(end, startIndex + from.length());
        return startIndex == -1 || endIndex == -1 ? null : input.substring(startIndex + from.length(), endIndex);
    }

    public static String findStringBetween(String input, int beginIndex, String from, String end) {
        int startIndex = beginIndex, endIndex = -1;
        if (startIndex != -1) startIndex = input.indexOf(from, startIndex);
        if (startIndex != -1) endIndex = input.indexOf(end, startIndex + from.length());
        return startIndex == -1 || endIndex == -1 ? null : input.substring(startIndex + from.length(), endIndex);
    }
}
