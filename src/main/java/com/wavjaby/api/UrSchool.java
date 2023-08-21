package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.ThreadFactory;
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
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.wavjaby.lib.Lib.*;

public class UrSchool implements EndpointModule {
    private static final String TAG = "[UrSchool]";
    private static final Logger logger = new Logger(TAG);
    private static final long updateInterval = 2 * 60 * 60 * 1000;
    private static final long cacheUpdateInterval = 10 * 60 * 1000;
    private static final int UPDATE_THREAD_COUNT = 8;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, new ThreadFactory("UrSchool-"));

    private final CookieStore urSchoolCookie = new CookieManager().getCookieStore();

    private String urSchoolDataJson;
    private List<ProfessorSummary> urSchoolData;
    private long lastFileUpdateTime;

    private final Map<String, Object[]> instructorCache = new ConcurrentHashMap<>();

    private static class ProfessorSummary {
        final String id, method;

        String name;
        String department;
        String jobTitle;
        String averageScore;
        String highestQualification;
        String note;
        String nickName;
        String rollCallMethod;

        float recommend, reward, articulate, pressure, sweet;

        public ProfessorSummary(String id, String method) {
            this.id = id;
            this.method = method;
        }

        public ProfessorSummary(JsonArray jsonArray) {
            id = jsonArray.getString(0);
            method = jsonArray.getString(1);
            name = jsonArray.getString(2);
            department = jsonArray.getString(3);
            jobTitle = jsonArray.getString(4);
            averageScore = jsonArray.getString(10);
            highestQualification = jsonArray.getString(11);
            note = jsonArray.getString(12);
            nickName = jsonArray.getString(13);
            rollCallMethod = jsonArray.getString(14);
            recommend = jsonArray.getFloat(5);
            reward = jsonArray.getFloat(6);
            articulate = jsonArray.getFloat(7);
            pressure = jsonArray.getFloat(8);
            sweet = jsonArray.getFloat(9);
        }

        @Override
        public String toString() {
            return new JsonArrayStringBuilder()
                    .append(id)
                    .append(method)
                    .append(name)
                    .append(department)
                    .append(jobTitle)
                    .append(recommend)
                    .append(reward)
                    .append(articulate)
                    .append(pressure)
                    .append(sweet)
                    .append(averageScore)
                    .append(highestQualification)
                    .append(note)
                    .append(nickName)
                    .append(rollCallMethod)
                    .toString();

        }
    }

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

                urSchoolData = new ArrayList<>();
                urSchoolDataJson = out.toString("UTF-8");
                for (Object i : new JsonArray(urSchoolDataJson)) {
                    urSchoolData.add(new ProfessorSummary((JsonArray) i));
                }
            } catch (IOException e) {
                logger.errTrace(e);
            }
            lastFileUpdateTime = file.lastModified();
        }

        if (checkTimeUpdateUrSchoolData())
            logger.log("Up to date");
    }

    @Override
    public void stop() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                logger.warn("Data update pool close timeout");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.errTrace(e);
            logger.warn("Data update pool close error");
            pool.shutdownNow();
        }
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        Headers requestHeaders = req.getRequestHeaders();

        try {
            ApiResponse apiResponse = new ApiResponse();

            String queryString = req.getRequestURI().getRawQuery();
            if (queryString == null) {
                checkTimeUpdateUrSchoolData();
                apiResponse.setData(urSchoolDataJson);
            } else {
                Map<String, String> query = parseUrlEncodedForm(queryString);
                String instructorID = query.get("id");
                String getMode = query.get("mode");
                if (instructorID == null || getMode == null) {
                    String err = "Query require";
                    if (instructorID == null) err += " \"id\"";
                    if (getMode == null) err += " \"mode\"";
                    apiResponse.errorBadQuery(err);
                } else
                    getInstructorInfo(instructorID, getMode, apiResponse);
            }

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Get UrSchool " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void getInstructorInfo(String id, String mode, ApiResponse response) {
        // check if in cache
        Object[] cacheData = instructorCache.get(id + '-' + mode);
        if (cacheData != null && (System.currentTimeMillis() - ((long) cacheData[0])) < cacheUpdateInterval) {
//            logger.log(id + " use cache");
            if (response != null)
                response.setData((String) cacheData[1]);
            return;
        }
//        logger.log(id + " fetch data");

        try {
            Connection.Response result;
            while (true) {
                try {
                    result = HttpConnection.connect("https://urschool.org/ajax/modal/" + id + "?mode=" + mode)
                            .header("Connection", "keep-alive")
                            .ignoreContentType(true)
                            .cookieStore(urSchoolCookie)
                            .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.75 Safari/537.36")
                            .timeout(20 * 1000)
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
                if (response != null)
                    response.errorParse("Tags not found");
                return;
            }

            // Get tags
            JsonArrayStringBuilder tags = new JsonArrayStringBuilder();
            int urlStart, urlEnd = tagsStart;
            while (true) {
                // Get tag url
                if ((urlStart = resultBody.indexOf("href='", urlEnd + 1)) == -1 ||
                        (urlEnd = resultBody.indexOf('\'', urlStart += 6)) == -1
                ) {
                    if (response != null)
                        response.errorParse("Tag url not found");
                    return;
                }
                String url = resultBody.substring(urlStart, urlEnd);
                if (url.startsWith("/shop") || urlStart > tagsEnd)
                    break;

                // Get tag name
                int tagNameStart, tagNameEnd;
                if ((tagNameStart = resultBody.indexOf('>', urlEnd + 1)) == -1 ||
                        (tagNameEnd = resultBody.indexOf('<', tagNameStart += 1)) == -1
                ) {
                    if (response != null)
                        response.errorParse("Tag name not found");
                    return;
                }
                String tagName = Parser.unescapeEntities(resultBody.substring(tagNameStart, tagNameEnd).trim(), true);

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
                if (response != null)
                    response.errorParse("Visitors not found");
                return;
            }
            JsonArrayStringBuilder visitors = new JsonArrayStringBuilder();
            int takeCourseCount = Integer.parseInt(resultBody.substring(countStart, countEnd).trim());
            int visitorStart, visitorEnd = countEnd;
            while (true) {
                // Get profile url
                if ((visitorStart = resultBody.indexOf("store.visits.push(", visitorEnd + 1)) == -1
                ) {
                    if (response != null)
                        response.errorParse("Profile url not found");
                    return;
                }
                if (resultBody.startsWith("store", visitorStart += 18)) break;
                if ((visitorStart = resultBody.indexOf('\'', visitorStart)) == -1 ||
                        (visitorEnd = resultBody.indexOf('\'', visitorStart += 1)) == -1) {
                    if (response != null)
                        response.errorParse("Profile url string not found");
                    return;
                }
                String url = resultBody.substring(visitorStart, visitorEnd);

                int subStart, subEnd;
                if ((subEnd = url.lastIndexOf('/')) == -1 ||
                        (subStart = url.lastIndexOf('/', subEnd -= 1)) == -1) {
                    if (response != null)
                        response.errorParse("Profile url parse error");
                    return;
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
                    if (response != null)
                        response.errorParse("Comment data not found");
                    return;
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
                    if (response != null)
                        response.errorParse("Profile url string not found");
                    return;
                }
                String url = resultBody.substring(avatarStart, avatarEnd);
                if (url.contains("anonymous"))
                    url = null;
                else {
                    int subStart, subEnd;
                    if ((subEnd = url.lastIndexOf('/')) == -1 ||
                            (subStart = url.lastIndexOf('/', subEnd -= 1)) == -1) {
                        if (response != null)
                            response.errorParse("Profile url parse error");
                        return;
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
                    if (response != null)
                        response.errorParse("Profile url string not found");
                    return;
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
            String instructorInfo = jsonBuilder.toString();
            if (response != null)
                response.setData(instructorInfo);
            instructorCache.put(id + '-' + mode, new Object[]{System.currentTimeMillis(), instructorInfo});
//            logger.log(id + " done");
        } catch (Exception e) {
            logger.errTrace(e);
            if (response != null)
                response.errorParse(e.getMessage());
        }
    }

    private synchronized boolean checkTimeUpdateUrSchoolData() {
        final long start = System.currentTimeMillis();
        if (start - lastFileUpdateTime <= updateInterval)
            return true;
        lastFileUpdateTime = start;
        pool.submit(() -> {
            // Get first page
            ProgressBar progressBar = new ProgressBar(TAG + "Update data ");
            Logger.addProgressBar(progressBar);
            progressBar.setProgress(0f);
            int[] maxPage = new int[1];
            List<ProfessorSummary> firstPage = fetchUrSchoolData(1, maxPage);
            progressBar.setProgress((float) 1 / maxPage[0] * 100);
            if (firstPage == null)
                return;
            List<ProfessorSummary> result = new ArrayList<>(firstPage);

            // Get the rest of the page
            ThreadPoolExecutor fetchPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                    UPDATE_THREAD_COUNT,
                    new ThreadFactory("UrSchool-", Thread.NORM_PRIORITY - 1)
            );
            Semaphore fetchPoolLock = new Semaphore(UPDATE_THREAD_COUNT, true);
            CountDownLatch fetchLeft = new CountDownLatch(maxPage[0] - 1);
            for (int i = 1; i < maxPage[0]; i++) {
                try {
                    fetchPoolLock.acquire();
                } catch (InterruptedException ignore) {
                    // Stop all
                    shutdownFetchPool(1000, fetchPool);
                    return;
                }
                // Stop all
                if (pool.isShutdown()) {
                    shutdownFetchPool(1000, fetchPool);
                    return;
                }

                int finalI = i + 1;
                fetchPool.submit(() -> {
                    List<ProfessorSummary> page = fetchUrSchoolData(finalI, null);
                    fetchPoolLock.release();
                    if (page != null)
                        result.addAll(page);
                    fetchLeft.countDown();
                    progressBar.setProgress(((float) (maxPage[0] - fetchLeft.getCount() + 1) / maxPage[0]) * 100);
                });
            }
            // Wait result
            try {
                fetchLeft.await();
            } catch (InterruptedException e) {
                logger.errTrace(e);
                return;
            }
            shutdownFetchPool(1000, fetchPool);

            progressBar.setProgress(100f);
            Logger.removeProgressBar(progressBar);

            String resultString = result.toString();
            urSchoolDataJson = resultString;
            urSchoolData = result;
            try {
                File file = new File("./urschool.json");
                FileWriter fileWriter = new FileWriter(file);
                fileWriter.write(resultString);
                fileWriter.close();
            } catch (IOException e) {
                logger.errTrace(e);
            }

            logger.log("Used " + (System.currentTimeMillis() - start) + "ms");
        });
        return false;
    }

    private void shutdownFetchPool(long timeout, ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
                logger.warn("FetchPool shutdown timeout");
            }
        } catch (InterruptedException ignore) {
            logger.warn("FetchPool shutdown interrupted");
            pool.shutdownNow();
        }
    }

    private List<ProfessorSummary> fetchUrSchoolData(int page, int[] maxPage) {
        try {
            Connection pageFetch = HttpConnection.connect("https://urschool.org/ncku/list?page=" + page)
                    .header("Connection", "keep-alive")
                    .ignoreContentType(true)
                    .cookieStore(urSchoolCookie)
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.75 Safari/537.36")
                    .timeout(20 * 1000);
            String resultBody;
            while (true) {
                try {
                    resultBody = pageFetch.execute().body();
                    break;
                } catch (IOException | UncheckedIOException ignore) {
                }
            }

            if (maxPage != null) {
                int maxPageStart, maxPageEnd;
                if ((maxPageStart = resultBody.lastIndexOf("https://urschool.org/ncku/list?page=")) == -1 ||
                        (maxPageStart = resultBody.lastIndexOf("https://urschool.org/ncku/list?page=", maxPageStart - 36)) == -1 ||
                        (maxPageEnd = resultBody.indexOf('"', maxPageStart + 36)) == -1) {
                    logger.log("Max page number not found");
                    return null;
                }
                maxPage[0] = Integer.parseInt(resultBody.substring(maxPageStart + 36, maxPageEnd));
            }

            int resultTableStart;
            if ((resultTableStart = resultBody.indexOf("<table")) == -1) {
                logger.log("Result table not found");
                return null;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = resultBody.indexOf("<tbody", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = resultBody.indexOf("</tbody>", resultTableBodyStart + 6)) == -1
            ) {
                logger.log("Result table body not found");
                return null;
            }

            // parse table
            String bodyStr = resultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(bodyStr, new Element("tbody"), "").get(0);

            List<ProfessorSummary> professorSummaries = new ArrayList<>();
            for (Element i : ((Element) tbody).getElementsByTag("tr")) {
                String id = i.attr("onclick");
                int idStart, idEnd;
                if ((idStart = id.indexOf('\'')) == -1 ||
                        (idEnd = id.indexOf('\'', idStart + 1)) == -1
                ) {
                    logger.log("Instructor ID not found");
                    return null;
                }
                int modeStart, modeEnd;
                if ((modeStart = id.indexOf('\'', idEnd + 1)) == -1 ||
                        (modeEnd = id.indexOf('\'', modeStart + 1)) == -1
                ) {
                    logger.log("Open mode not found");
                    return null;
                }
                String mode = id.substring(modeStart + 1, modeEnd);
                id = id.substring(idStart + 1, idEnd);


                // table data
                Elements elements = i.children();
                if (elements.size() < 13) {
                    logger.log("Professor summary parse error");
                    return null;
                }

                ProfessorSummary professorSummary = new ProfessorSummary(id, mode);
                for (int j = 0; j < elements.size(); j++) {
                    Element element = elements.get(j);
                    String text = element.text();
                    switch (j) {
                        // info
                        case 0:
                            professorSummary.name = text;
                            break;
                        case 1:
                            professorSummary.department = text;
                            break;
                        case 2:
                            professorSummary.jobTitle = text;
                            break;
                        // rating
                        case 3: {
                            int end = text.lastIndexOf(' ');
                            professorSummary.recommend = end == -1 ? -1 : Float.parseFloat(text.substring(0, end));
                            break;
                        }
                        case 4: {
                            int end = text.lastIndexOf(' ');
                            professorSummary.reward = end == -1 ? -1 : Float.parseFloat(text.substring(0, end));
                            break;
                        }
                        case 5: {
                            int end = text.lastIndexOf(' ');
                            professorSummary.articulate = end == -1 ? -1 : Float.parseFloat(text.substring(0, end));
                            break;
                        }
                        case 6: {
                            int end = text.lastIndexOf(' ');
                            professorSummary.pressure = end == -1 ? -1 : Float.parseFloat(text.substring(0, end));
                            break;
                        }
                        case 7: {
                            int end = text.lastIndexOf(' ');
                            professorSummary.sweet = end == -1 ? -1 : Float.parseFloat(text.substring(0, end));
                            break;
                        }
                        // details
                        case 8:
                            professorSummary.averageScore = text;
                            break;
                        case 9:
                            professorSummary.highestQualification = text;
                            break;
                        case 10:
                            professorSummary.note = text;
                            break;
                        case 11:
                            professorSummary.nickName = text;
                            break;
                        case 12:
                            professorSummary.rollCallMethod = text;
                            break;
                    }
                }
                professorSummaries.add(professorSummary);
            }
            return professorSummaries;
        } catch (Exception e) {
            logger.errTrace(e);
            return null;
        }
    }

    public void addInstructorCache(String[] instructors) {
//        logger.log(Arrays.toString(instructors));
        pool.submit(() -> {
            for (String name : instructors) {
                List<ProfessorSummary> results = new ArrayList<>();
                for (ProfessorSummary i : urSchoolData) {
                    if (i.name.equals(name))
                        results.add(i);
                }
                String id = null, mode = null;
                // TODO: Determine if the same name
                for (ProfessorSummary i : results) {
                    if (i.recommend != -1) {
                        id = i.id;
                        mode = i.name;
                        break;
                    }
                }

                if (id != null && mode != null) {
//                    logger.log("Add cache " + id);
                    getInstructorInfo(id, mode, null);
                }
            }
        });
    }
}
