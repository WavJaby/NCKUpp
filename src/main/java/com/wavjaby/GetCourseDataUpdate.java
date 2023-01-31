package com.wavjaby;

import com.wavjaby.api.Search;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wavjaby.Cookie.createHttpCookie;
import static com.wavjaby.Main.courseNcku;
import static com.wavjaby.Main.courseNckuOrg;

public class GetCourseDataUpdate implements Runnable {
    private final static String TAG = "[CourseListener] ";
    private final CookieManager cookieManager = new CookieManager();
    private final CookieStore cookieStore = cookieManager.getCookieStore();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Search search;

    private final long updateInterval = 1700;
    private long lastUpdateTime = 0;
    private int count = 0;
    private int taskSkipped = 0;
    private long taskSkippedStartTime = 0;

    private final List<String> listenDept = new ArrayList<>();
    private final Map<String, Search.DeptToken> deptTokenMap = new HashMap<>();
    private List<Search.CourseData> lastCourseDataList = null;
    private final String botToken;


    private static class CourseDataDifference {
        private enum Type {
            CREATE,
            UPDATE,
        }

        private final Type type;
        private final Search.CourseData courseData;
        private final int availableDiff;
        private final int selectDiff;

        public CourseDataDifference(Search.CourseData courseData, int availableDiff, int selectDiff) {
            this.type = Type.UPDATE;
            this.courseData = courseData;
            this.availableDiff = availableDiff;
            this.selectDiff = selectDiff;
        }

        public CourseDataDifference(Search.CourseData courseData) {
            this.type = Type.CREATE;
            this.courseData = courseData;
            this.availableDiff = courseData.getAvailable();
            this.selectDiff = courseData.getSelected();
        }
    }

    public GetCourseDataUpdate(Search search, Properties serverSettings) {
        this.search = search;
        botToken = serverSettings.getProperty("botToken");
//        Search.SearchQuery searchQuery = new Search.SearchQuery("F7");
//        Search.SaveQueryToken saveQueryToken = search.getSaveQueryToken(searchQuery, cookieManager.getCookieStore(), null);
//        List<Search.CourseData> courseDataList = new ArrayList<>();
//
//        for (int i = 0; i < 10; i++) {
//            long start = System.currentTimeMillis();
//            search.getQueryCourseData(searchQuery, saveQueryToken, courseDataList);
//            Logger.log(TAG, new JsonArray(courseDataList.toString()).toStringBeauty());
//            Logger.log(TAG, (System.currentTimeMillis() - start) + "ms");
//            courseDataList.clear();
//        }

//        Search.AllDeptData allDeptData = search.getAllDeptData(cookieManager.getCookieStore());
//        Search.DeptToken deptToken = search.getDeptToken("F7", allDeptData);
//        Logger.log(TAG, (System.currentTimeMillis() - start) + "ms");


        try {
            URI uri = new URI(courseNckuOrg);
            cookieStore.add(uri, createHttpCookie("PHPSESSID", "F741147602f1fa9139804c750869ea09d1aa70a9c", courseNcku));
            cookieStore.add(uri, createHttpCookie("COURSE_WEB", "ffffffff8f7cbb0345525d5f4f58455e445a4a423660", courseNcku));
            cookieStore.add(uri, createHttpCookie("COURSE_CDN", "ffffffff8f7ce72345525d5f4f58455e445a4a42cbd9", courseNcku));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        listenDept.add("A9");
        listenDept.add("A2");
        listenDept.add("F7");
        scheduler.scheduleAtFixedRate(this, 0, updateInterval, TimeUnit.MILLISECONDS);
    }

    private void postToChannel(String channelID, JsonObject jsonObject) {
        final String apiUrl = "https://discord.com/api/v10";
        try {
//            Logger.log(TAG, jsonObject.toString());
            String result = HttpConnection.connect(apiUrl + "/channels/" + channelID + "/messages")
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .userAgent("DiscordBot (https://github.com/WavJaby/NCKUpp, 1.0)")
                    .method(Connection.Method.POST)
                    .requestBody(jsonObject.toString())
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute().body();
//            Logger.log(TAG, new JsonObject(result).toStringBeauty());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        long lastInterval = System.currentTimeMillis() - lastUpdateTime;
        lastUpdateTime = System.currentTimeMillis();
        // Skip if last task take too long
        if (taskSkipped > 0 && lastInterval > updateInterval - 100) {
            Logger.log(TAG, "Skipped " + taskSkipped + ", " + (System.currentTimeMillis() - taskSkippedStartTime));
            taskSkipped = 0;
        } else if (lastInterval < updateInterval - 100) {
            if (taskSkipped++ == 0)
                taskSkippedStartTime = lastUpdateTime;
            return;
        }
//        Logger.log(TAG, "Start " + lastInterval + "ms");


        long start = System.currentTimeMillis();
        List<Search.CourseData> courseDataList = new ArrayList<>();
        CountDownLatch taskLeft = new CountDownLatch(listenDept.size());
        AtomicBoolean allSuccess = new AtomicBoolean(true);
        for (String dept : listenDept) {
            pool.submit(() -> {
                try {
                    Search.DeptToken deptToken = deptTokenMap.get(dept);
                    if (deptToken == null)
                        deptTokenMap.put(dept, (deptToken = renewDeptToken(dept, cookieStore)));
                    if (deptToken == null)
                        return;
                    // Get dept course data
                    if (!search.getDeptCourseData(deptToken, courseDataList, false)) {
                        deptTokenMap.put(dept, null);
                        return;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    allSuccess.set(false);
                    deptTokenMap.put(dept, null);
                }
                taskLeft.countDown();
            });
        }
        try {
            taskLeft.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
        if (!allSuccess.get())
            return;

        try {
            // Get different
            List<CourseDataDifference> diff = getDifferent(lastCourseDataList, courseDataList);
            lastCourseDataList = courseDataList;

            JsonObject jsonObject = new JsonObject();
            JsonArray embeds = new JsonArray();
            jsonObject.put("embeds", embeds);

            // Print result
            if (diff != null) {
                for (CourseDataDifference i : diff) {
                    switch (i.type) {
                        case CREATE:
                            Logger.log(TAG, "CREATE: " +
                                    new JsonObject(i.courseData.toString()).toStringBeauty());
                            break;
                        case UPDATE:
                            Search.SearchQuery searchQuery = new Search.SearchQuery(courseDataList.get(0));
                            Search.SaveQueryToken token = search.createSaveQueryToken(searchQuery, cookieStore, null);
                            embeds.add(new JsonObject()
                                            .put("type", "rich")
                                            .put("color", 0x00FFFF)
                                            .put("title", i.courseData.getSerialNumber() + " " + i.courseData.getCourseName())
//                        .put("description", "0w0")
                                            .put("url", token.getUrl())
                                            .put("fields", new JsonArray()
                                                            .add(new JsonObject()
                                                                    .put("name", "餘額 " + intToString(i.availableDiff))
                                                                    .put("value", "總餘額: " + i.courseData.getAvailable())
                                                                    .put("inline", true)
                                                            )
                                                            .add(new JsonObject()
                                                                    .put("name", "已選人數 " + intToString(i.selectDiff))
                                                                    .put("value", "總已選人數: " + i.courseData.getSelected())
                                                                    .put("inline", true)
                                                            )
//                                                .add(new JsonObject()
//                                                        .put("name", "")
//                                                        .put("value", "")
//                                                        .put("inline", false)
//                                                )
//                                                .add(new JsonObject()
//                                                        .put("name", "w")
//                                                        .put("value", "w")
//                                                        .put("inline", true)
//                                                )
                                            )
                            );
                            Search.CourseData c = i.courseData;
                            Logger.log(TAG, "UPDATE " + c.getSerialNumber() + " " + c.getCourseName() + "\n" +
                                    "available: " + i.availableDiff + ", select: " + i.selectDiff);
                            break;
                    }
                }
            }
            if (embeds.length > 0)
                postToChannel("1018756061911601224", jsonObject);

            Logger.log(TAG, count++ + ", " +
                    courseDataList.size() + ", " +
                    (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String intToString(int integer) {
        return integer > 0 ? "+" + integer : String.valueOf(integer);
    }

    private List<CourseDataDifference> getDifferent(List<Search.CourseData> last, List<Search.CourseData> now) {
        if (last == null) return null;

        HashMap<String, Search.CourseData> lastCourseDataMap = new HashMap<>();
        for (Search.CourseData i : last)
            if (i.getSerialNumber() != null)
                lastCourseDataMap.put(i.getSerialNumber(), i);

        List<CourseDataDifference> diff = new ArrayList<>();

        for (Search.CourseData nowData : now) {
            if (nowData.getSerialNumber() == null) continue;
            Search.CourseData lastData = lastCourseDataMap.get(nowData.getSerialNumber());

            if (lastData == null) {
                diff.add(new CourseDataDifference(nowData));
            } else if (!Objects.equals(lastData.getAvailable(), nowData.getAvailable()) ||
                    !Objects.equals(lastData.getSelected(), nowData.getSelected())
            ) {
                int availableDiff = getIntegerDiff(lastData.getAvailable(), nowData.getAvailable());
                int selectDiff = getIntegerDiff(lastData.getSelected(), nowData.getSelected());
                diff.add(new CourseDataDifference(nowData, availableDiff, selectDiff));
            }
        }

        return diff;
    }

    private int getIntegerDiff(Integer oldInt, Integer newInt) {
        return newInt == null ? -oldInt
                : oldInt == null ? newInt
                : newInt - oldInt;
    }

    private Search.DeptToken renewDeptToken(String deptNo, CookieStore cookieStore) throws IOException {
        Logger.log(TAG, "Renew dept token");
        Search.AllDeptData allDeptData = search.getAllDeptData(cookieStore);
        if (allDeptData == null) {
            Logger.err(TAG, "Can not get crypt");
            return null;
        }
        Search.DeptToken deptToken = search.createDeptToken(deptNo, allDeptData);
        if (deptToken.getError() != null) {
            Logger.err(TAG, deptToken.getError());
            return null;
        }
        Logger.log(TAG, deptToken.getID());
        return deptToken;
    }
}
