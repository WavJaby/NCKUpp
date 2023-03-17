package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.ProgressBar;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.CookieManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Lib.*;

public class UrSchool implements EndpointModule {
    private static final String TAG = "[UrSchool] ";
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final long updateInterval = 60 * 60 * 1000;
    private static final long cacheUpdateInterval = 5 * 60 * 1000;

    private String urSchoolDataJson;
    private JsonArray urSchoolData;
    private long lastFileUpdateTime;

    private final Map<String, Object[]> instructorCache = new HashMap<>();


    @Override
    public void start() {
        File file = new File("./urschool.json");
        if (file.exists()) {
            try {
                InputStream reader = Files.newInputStream(file.toPath());
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                int len;
                byte[] buff = new byte[1024];
                while ((len = reader.read(buff)) != -1)
                    out.write(buff, 0, len);
                reader.close();

                urSchoolDataJson = out.toString("UTF-8");
                urSchoolData = new JsonArray(urSchoolDataJson);
            } catch (IOException e) {
                e.printStackTrace();
            }
            lastFileUpdateTime = file.lastModified();
        }

        if (updateUrSchoolData())
            Logger.log(TAG, "Up to date");
    }

    @Override
    public void stop() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                Logger.warn(TAG, "Data update pool close timeout");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Logger.warn(TAG, "Data update pool close error");
            pool.shutdownNow();
        }
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        Headers requestHeaders = req.getRequestHeaders();
        getDefaultCookie(requestHeaders, cookieManager.getCookieStore());

        try {
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();

            String queryString = req.getRequestURI().getRawQuery();
            boolean success = true;
            if (queryString == null) {
                updateUrSchoolData();
                data.appendRaw("data", urSchoolDataJson);
            } else {
                Map<String, String> query = parseUrlEncodedForm(queryString);
                String instructorID = query.get("id");
                String getMode = query.get("mode");
                if (instructorID == null || getMode == null) {
                    if (instructorID == null)
                        data.append("err", TAG + "Query id not found");
                    if (getMode == null)
                        data.append("err", TAG + "Query mode not found");
                } else
                    success = getInstructorInfo(instructorID, getMode, data);
            }
            data.append("success", success);

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
        }
        Logger.log(TAG, "Get UrSchool " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean getInstructorInfo(String id, String mode, JsonObjectStringBuilder outData) {
        // check if in cache
        Object[] cacheData = instructorCache.get(id + '-' + mode);
        if (cacheData != null && (System.currentTimeMillis() - ((long) cacheData[0])) < cacheUpdateInterval) {
//            Logger.log(TAG, id + " use cache");
            if (outData != null)
                outData.appendRaw("data", (String) cacheData[1]);
            return true;
        }
//        Logger.log(TAG, id + " fetch data");

        try {
            Connection.Response result;
            while (true) {
                try {
                    result = HttpConnection.connect("https://urschool.org/ajax/modal/" + id + "?mode=" + mode)
                            .header("Connection", "keep-alive")
                            .ignoreContentType(true)
                            .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.75 Safari/537.36")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .timeout(10 * 1000)
                            .execute();
                    break;
                } catch (IOException ignore) {
                }
            }
            String resultBody = result.body();

            // Get tags range
            int tagsStart, tagsEnd;
            if ((tagsStart = resultBody.indexOf("<div class='col-md-12'>")) == -1 ||
                    (tagsEnd = resultBody.indexOf("</div>", tagsStart += 23)) == -1
            ) {
                if (outData != null) outData.append("err", TAG + "Tags not found");
                return false;
            }

            // Get tags
            JsonArrayStringBuilder tags = new JsonArrayStringBuilder();
            int urlStart, urlEnd = tagsStart;
            while (true) {
                // Get tag url
                if ((urlStart = resultBody.indexOf("href='", urlEnd + 1)) == -1 ||
                        (urlEnd = resultBody.indexOf('\'', urlStart += 6)) == -1
                ) {
                    if (outData != null) outData.append("err", TAG + "Tag url not found");
                    return false;
                }
                String url = resultBody.substring(urlStart, urlEnd);
                if (url.startsWith("/shop") || urlStart > tagsEnd) break;

                // Get tag name
                int tagNameStart, tagNameEnd;
                if ((tagNameStart = resultBody.indexOf('>', urlEnd + 1)) == -1 ||
                        (tagNameEnd = resultBody.indexOf('<', tagNameStart += 1)) == -1
                ) {
                    if (outData != null) outData.append("err", TAG + "Tag name not found");
                    return false;
                }
                String tagName = resultBody.substring(tagNameStart, tagNameEnd).trim()
                        .replace("&amp;", "&");

                JsonArrayStringBuilder tagData = new JsonArrayStringBuilder();
                tagData.append(url).append(tagName);
                tags.append(tagData);
            }

            // Get reviewer count
            int reviewerCount;
            int reviewerCountStart, reviewerCountEnd;
            if ((reviewerCountStart = resultBody.indexOf("/reviewers/", urlEnd + 1)) == -1 ||
                    (reviewerCountStart = resultBody.indexOf('>', reviewerCountStart)) == -1 ||
                    (reviewerCountEnd = resultBody.indexOf(' ', reviewerCountStart += 1)) == -1
            )
                reviewerCount = 0;
            else
                reviewerCount = Integer.parseInt(resultBody.substring(reviewerCountStart, reviewerCountEnd));

            // Get visitors
            int countStart, countEnd;
            if ((countStart = resultBody.indexOf("store.count")) == -1 ||
                    (countStart = resultBody.indexOf('=', countStart + 11)) == -1 ||
                    (countEnd = resultBody.indexOf(';', countStart += 1)) == -1
            ) {
                if (outData != null) outData.append("err", TAG + "Visitors not found");
                return false;
            }
            JsonArrayStringBuilder visitors = new JsonArrayStringBuilder();
            int takeCourseCount = Integer.parseInt(resultBody.substring(countStart, countEnd).trim());
            int visitorStart, visitorEnd = countEnd;
            while (true) {
                // Get profile url
                if ((visitorStart = resultBody.indexOf("store.visits.push(", visitorEnd + 1)) == -1
                ) {
                    if (outData != null) outData.append("err", TAG + "Profile url not found");
                    return false;
                }
                if (resultBody.startsWith("store", visitorStart += 18)) break;
                if ((visitorStart = resultBody.indexOf('\'', visitorStart)) == -1 ||
                        (visitorEnd = resultBody.indexOf('\'', visitorStart += 1)) == -1) {
                    if (outData != null) outData.append("err", TAG + "Profile url string not found");
                    return false;
                }
                String url = resultBody.substring(visitorStart, visitorEnd);

                int subStart, subEnd;
                if ((subEnd = url.lastIndexOf('/')) == -1 ||
                        (subStart = url.lastIndexOf('/', subEnd -= 1)) == -1) {
                    if (outData != null) outData.append("err", TAG + "Profile url parse error");
                    return false;
                }
                url = url.substring(subStart + 1, subEnd + 1);

                visitors.append(url);
            }

            // Get comment
            JsonArrayStringBuilder comments = new JsonArrayStringBuilder();
            int commentStart, commentEnd = visitorEnd;
            // Get comment data
            while ((commentStart = resultBody.indexOf("var obj", commentEnd + 1)) != -1 &&
                    (commentStart = resultBody.indexOf('{', commentStart + 7)) != -1) {

                if ((commentEnd = resultBody.indexOf('}', commentStart + 1)) == -1) {
                    if (outData != null) outData.append("err", TAG + "Comment data not found");
                    return false;
                }
                JsonObject commentData = new JsonObject(resultBody.substring(commentStart, commentEnd + 1));
                commentData.remove("cafe_id");
                commentData.put("body", parseUnicode(commentData.getString("body")));

                // Get avatar
                int limit = resultBody.indexOf('}', commentEnd + 1);
                int avatarStart, avatarEnd;
                if ((avatarStart = resultBody.indexOf("avatar", commentEnd + 1)) == -1 ||
                        (avatarStart = resultBody.indexOf('\'', avatarStart + 6)) == -1 ||
                        (avatarEnd = resultBody.indexOf('\'', avatarStart += 1)) == -1 ||
                        avatarEnd > limit) {
                    if (outData != null) outData.append("err", TAG + "Profile url string not found");
                    return false;
                }
                String url = resultBody.substring(avatarStart, avatarEnd);
                if (url.contains("anonymous"))
                    url = null;
                else {
                    int subStart, subEnd;
                    if ((subEnd = url.lastIndexOf('/')) == -1 ||
                            (subStart = url.lastIndexOf('/', subEnd -= 1)) == -1) {
                        if (outData != null) outData.append("err", TAG + "Profile url parse error");
                        return false;
                    }
                    url = url.substring(subStart + 1, subEnd + 1);
                }
                commentData.put("profile", url);

                // Get timestamp
                int timestampStart, timestampEnd;
                if ((timestampStart = resultBody.indexOf("timestamp", commentEnd + 1)) == -1 ||
                        (timestampStart = resultBody.indexOf('\'', timestampStart + 9)) == -1 ||
                        (timestampEnd = resultBody.indexOf('\'', timestampStart += 1)) == -1 ||
                        timestampEnd > limit) {
                    if (outData != null) outData.append("err", TAG + "Profile url string not found");
                    return false;
                }
                String timestamp = resultBody.substring(timestampStart, timestampEnd);
                commentData.put("timestamp", Long.parseLong(timestamp));

                comments.append(commentData);
            }

            // Write result
            JsonObjectStringBuilder jsonBuilder = new JsonObjectStringBuilder();
            jsonBuilder.append("id", id);
            jsonBuilder.append("tags", tags);
            jsonBuilder.append("reviewerCount", reviewerCount);
            jsonBuilder.append("takeCourseCount", takeCourseCount);
            jsonBuilder.append("takeCourseUser", visitors);
            jsonBuilder.append("comments", comments);
            if (outData != null)
                outData.append("data", jsonBuilder);
            instructorCache.put(id + '-' + mode, new Object[]{System.currentTimeMillis(), jsonBuilder.toString()});
//            Logger.log(TAG, id + " done");
            return true;
        } catch (Exception e) {
            if (outData != null)
                outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
            return false;
        }
    }

    private synchronized boolean updateUrSchoolData() {
        final long start = System.currentTimeMillis();
        if (start - lastFileUpdateTime <= updateInterval) return true;
        lastFileUpdateTime = start;
        pool.submit(() -> {
            int[] maxPage = new int[1];
            StringBuilder result = new StringBuilder();

            // Get first page
            ProgressBar progressBar = new ProgressBar(TAG + "Update data ");
            Logger.addProgressBar(progressBar);
            progressBar.setProgress(0f);
            String firstPage = fetchUrSchoolData(1, maxPage);
            progressBar.setProgress((float) 1 / maxPage[0] * 100);
            if (firstPage == null)
                return;
            result.append(firstPage);

            // Get the rest of the page
            ThreadPoolExecutor fetchPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
            Semaphore fetchPoolLock = new Semaphore(8);
            CountDownLatch fetchLeft = new CountDownLatch(maxPage[0] - 1);
            for (int i = 1; i < maxPage[0]; i++) {
                try {
                    fetchPoolLock.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int finalI = i + 1;
                fetchPool.submit(() -> {
                    String page = fetchUrSchoolData(finalI, null);
                    synchronized (result) {
                        result.append(page);
                    }
                    fetchPoolLock.release();
                    fetchLeft.countDown();
                    progressBar.setProgress(((float) (maxPage[0] - fetchLeft.getCount() + 1) / maxPage[0]) * 100);
                });
            }
            // Wait result
            try {
                fetchLeft.await();
                fetchPool.shutdown();
                if (!fetchPool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    fetchPool.shutdownNow();
                    Logger.warn(TAG, "FetchPool shutdown timeout");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            progressBar.setProgress(100f);
            Logger.removeProgressBar(progressBar);
            if (result.length() > 0) result.setCharAt(0, '[');
            else result.append('[');
            result.append(']');
            String resultString = result.toString();
            urSchoolDataJson = resultString;
            urSchoolData = new JsonArray(urSchoolDataJson);
            try {
                File file = new File("./urschool.json");
                FileWriter fileWriter = new FileWriter(file);
                fileWriter.write(resultString);
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Logger.log(TAG, "Used " + (System.currentTimeMillis() - start) + "ms");
        });
        return false;
    }

    private String fetchUrSchoolData(int page, int[] maxPage) {
        try {
            Connection pageFetch = HttpConnection.connect("https://urschool.org/ncku/list?page=" + page)
                    .header("Connection", "keep-alive")
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.75 Safari/537.36")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(10 * 1000);
            String resultBody;
            while (true) {
                try {
                    resultBody = pageFetch.execute().body();
                    break;
                } catch (Exception ignore) {
                }
            }

            if (maxPage != null) {
                int maxPageStart, maxPageEnd;
                if ((maxPageStart = resultBody.lastIndexOf("https://urschool.org/ncku/list?page=")) == -1 ||
                        (maxPageStart = resultBody.lastIndexOf("https://urschool.org/ncku/list?page=", maxPageStart - 36)) == -1 ||
                        (maxPageEnd = resultBody.indexOf('"', maxPageStart + 36)) == -1) {
                    Logger.log(TAG, "Max page number not found");
                    return null;
                }
                maxPage[0] = Integer.parseInt(resultBody.substring(maxPageStart + 36, maxPageEnd));
            }

            int resultTableStart;
            if ((resultTableStart = resultBody.indexOf("<table")) == -1) {
                Logger.log(TAG, "Result table not found");
                return null;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = resultBody.indexOf("<tbody", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = resultBody.indexOf("</tbody>", resultTableBodyStart + 6)) == -1
            ) {
                Logger.log(TAG, "Result table body not found");
                return null;
            }

            // parse table
            String bodyStr = resultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(bodyStr, new Element("tbody"), "").get(0);

            StringBuilder out = new StringBuilder();
            for (Element i : ((Element) tbody).getElementsByTag("tr")) {
                String id = i.attr("onclick");
                int idStart, idEnd;
                if ((idStart = id.indexOf('\'')) == -1 ||
                        (idEnd = id.indexOf('\'', idStart + 1)) == -1
                ) {
                    Logger.log(TAG, "Instructor ID not found");
                    return null;
                }
                int modeStart, modeEnd;
                if ((modeStart = id.indexOf('\'', idEnd + 1)) == -1 ||
                        (modeEnd = id.indexOf('\'', modeStart + 1)) == -1
                ) {
                    Logger.log(TAG, "Open mode not found");
                    return null;
                }
                String mode = id.substring(modeStart + 1, modeEnd);
                id = id.substring(idStart + 1, idEnd);

                StringBuilder builder = new StringBuilder();
                builder.append(',').append('"').append(id).append('"');
                builder.append(',').append('"').append(mode).append('"');

                // table data
                Elements elements = i.getElementsByTag("td");
                for (int j = 0; j < elements.size(); j++) {
                    Element element = elements.get(j);
                    String text = element.text();
                    // rating
                    if (j > 2 && j < 8) {
                        int end = text.lastIndexOf(' ');
                        if (end != -1)
                            text = text.substring(0, end);
                        else
                            text = "-1";
                        builder.append(',').append(text);
                    }
                    // info text
                    else
                        builder.append(',').append('"').append(text.replace("\\", "\\\\")).append('"');
                }
                builder.setCharAt(0, '[');
                builder.append(']');
                out.append(',').append(builder);
            }
            return out.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addInstructorCache(String[] instructors) {
//        Logger.log(TAG, Arrays.toString(instructors));
        pool.submit(() -> {
            for (String name : instructors) {
                List<JsonArray> results = new ArrayList<>();
                for (Object i : urSchoolData) {
                    JsonArray array = (JsonArray) i;
                    if (array.getString(2).equals(name))
                        results.add(array);
                }
                String id = null, mode = null;
                // TODO: Determine if the same name
                for (JsonArray array : results) {
                    if (array.getFloat(5) != -1) {
                        id = array.getString(0);
                        mode = array.getString(1);
                        break;
                    }
                }

                if (id != null && mode != null) {
//                    Logger.log(TAG, "Add cache " + id);
                    getInstructorInfo(id, mode, null);
                }
            }
        });
    }
}
