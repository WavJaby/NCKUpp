package com.wavjaby;

import com.wavjaby.api.DeptWatchDog;
import com.wavjaby.api.Search;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.net.CookieStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GetCourseDataUpdate implements Runnable {
    private static final String TAG = "[CourseListener] ";
    private static final String apiUrl = "https://discord.com/api/v10";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Search search;
    private final DeptWatchDog watchDog;
    private final CookieStore baseCookieStore;

    private final long updateInterval = 1600;
    private long lastScheduleStart = 0;
    private long lastUpdateStart = System.currentTimeMillis();
    private int count = 0;
    private int taskSkipped = 0;
    private long taskSkippedStartTime = 0;

    private final Map<String, CookieStore> listenDept = new HashMap<>();
    private final Map<String, Search.DeptToken> deptTokenMap = new HashMap<>();
    private List<Search.CourseData> courseDataList = null;
    private final String botToken;
    private final HashMap<String, String> userDmChannelCache = new HashMap<>();


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

    public GetCourseDataUpdate(Search search, DeptWatchDog watchDog, Properties serverSettings) {
        this.search = search;
        this.watchDog = watchDog;
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
        baseCookieStore = search.createCookieStore();
        watchDog.getAllCourse().forEach(this::addListenDept);
        scheduler.scheduleAtFixedRate(this, 0, updateInterval, TimeUnit.MILLISECONDS);
    }


    private void getWatchDogUpdate() {
        watchDog.getNewDept().forEach(this::addListenDept);
    }

    private void addListenDept(String deptID) {
        Logger.log(TAG, "Add watch: " + deptID);
        listenDept.put(deptID, search.createCookieStore());
    }


    private String getDmChannel(String userID) {
        String dmChannelID = userDmChannelCache.get(userID);
        if (dmChannelID != null) return dmChannelID;
        try {
//            Logger.log(TAG, jsonObject.toString());
            String result = HttpConnection.connect(apiUrl + "/users/@me/channels")
                    .header("Connection", "keep-alive")
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .userAgent("DiscordBot (https://github.com/WavJaby/NCKUpp, 1.0)")
                    .method(Connection.Method.POST)
                    .requestBody(new JsonObject().put("recipient_id", userID).toString())
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute().body();
            userDmChannelCache.put(userID, dmChannelID = new JsonObject(result).getString("id"));
            return dmChannelID;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void postToChannel(String channelID, JsonObject jsonObject) {
        try {
//            Logger.log(TAG, jsonObject.toString());
            String result = HttpConnection.connect(apiUrl + "/channels/" + channelID + "/messages")
                    .header("Connection", "keep-alive")
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
        long start = System.currentTimeMillis();
        long lastInterval = start - lastScheduleStart;
        lastScheduleStart = start;
        // Skip if last task take too long
        if (taskSkipped > 0 && start - lastUpdateStart > updateInterval) {
            Logger.log(TAG, "Skipped " + taskSkipped + ", " + (start - taskSkippedStartTime));
            taskSkipped = 0;
        } else if (lastInterval < updateInterval - 50) {
            if (taskSkipped++ == 0)
                taskSkippedStartTime = lastScheduleStart;
            return;
        }
        Logger.log(TAG, "Interval " + (start - lastUpdateStart) + "ms");
        lastUpdateStart = start;

        getWatchDogUpdate();
        try {
            CountDownLatch taskLeft = new CountDownLatch(listenDept.size());
            AtomicBoolean allSuccess = new AtomicBoolean(true);
            List<Search.CourseData> newCourseDataList = new ArrayList<>();

            if (count >= 3000) {
                for (String dept : listenDept.keySet())
                    deptTokenMap.put(dept, null);
                count = 0;
            }

            for (Map.Entry<String, CookieStore> i : listenDept.entrySet()) {
                String dept = i.getKey();
                CookieStore cookieStore = i.getValue();
                pool.submit(() -> {
                    try {
                        Search.DeptToken deptToken = deptTokenMap.get(dept);
                        if (deptToken == null)
                            deptTokenMap.put(dept, (deptToken = renewDeptToken(dept, cookieStore)));
                        if (deptToken == null)
                            return;
                        // Get dept course data
                        List<Search.CourseData> thisCourseDataList = new ArrayList<>();
                        if (!search.getDeptCourseData(deptToken, thisCourseDataList, false)) {
                            deptTokenMap.put(dept, null);
                            return;
                        }
                        if (thisCourseDataList.size() == 0) {
                            deptTokenMap.put(dept, null);
                            return;
                        }
                        newCourseDataList.addAll(thisCourseDataList);
                    } catch (Exception e) {
                        e.printStackTrace();
                        allSuccess.set(false);
                        deptTokenMap.put(dept, null);
                    }
                    taskLeft.countDown();
                });
            }
            if (!taskLeft.await(10, TimeUnit.SECONDS)) {
                for (String dept : listenDept.keySet())
                    deptTokenMap.put(dept, null);
                count = 0;
                return;
            }

            if (!allSuccess.get())
                return;

            // Get different
            List<CourseDataDifference> diff = getDifferent(courseDataList, newCourseDataList);
            courseDataList = newCourseDataList;

            // Build notification
            if (diff != null && diff.size() > 0)
                pool.submit(() -> buildAndSendNotification(diff));
        } catch (Exception e) {
            e.printStackTrace();
        }

        Logger.log(TAG, count++ + ", " +
                courseDataList.size() + ", " +
                (System.currentTimeMillis() - start) + "ms");
    }

    private void buildAndSendNotification(List<CourseDataDifference> diff) {
        JsonObject mainChannelNotification = new JsonObject();
        JsonArray mainChannelNotificationEmbeds = new JsonArray();
        mainChannelNotification.put("embeds", mainChannelNotificationEmbeds);

        // User notification
        HashMap<String, JsonObject> userNotifications = new HashMap<>();
        HashMap<String, JsonArray> userNotificationEmbeds = new HashMap<>();

        // Print result
        for (CourseDataDifference i : diff) {
            switch (i.type) {
                case CREATE:
                    Logger.log(TAG, "CREATE: " + i.courseData.getCourseName());
                    break;
                case UPDATE:
                    Search.CourseData cosData = i.courseData;
                    Logger.log(TAG, "UPDATE " + cosData.getSerialNumber() + " " + cosData.getCourseName() + "\n" +
                            "available: " + i.availableDiff + ", select: " + i.selectDiff);

                    Search.SearchQuery searchQuery = new Search.SearchQuery(cosData);
                    Search.SaveQueryToken token = search.createSaveQueryToken(searchQuery, baseCookieStore, null);
                    JsonObject deptEmbed = new JsonObject()
                            .put("type", "rich")
                            .put("color", i.availableDiff > 0
                                    ? (cosData.getAvailable() == 1 ? 0x00FF00 : 0x00FFFF)
                                    : cosData.getAvailable() > 0
                                    ? 0xFFFF00
                                    : 0xFF0000)
                            .put("title", cosData.getSerialNumber() + " " + cosData.getCourseName())
                            .put("description", cosData.getGroup())
                            .put("url", token.getUrl())
                            .put("fields", new JsonArray()
                                    .add(new JsonObject()
                                            .put("name", "餘額 " + intToString(i.availableDiff))
                                            .put("value", "總餘額: " + cosData.getAvailable())
                                            .put("inline", true)
                                    )
                                    .add(new JsonObject()
                                            .put("name", "已選人數 " + intToString(i.selectDiff))
                                            .put("value", "總已選人數: " + cosData.getSelected())
                                            .put("inline", true)
                                    )
                                    .add(new JsonObject()
                                            .put("name", "")
                                            .put("value", "")
                                            .put("inline", false)
                                    )
                                    .add(new JsonObject()
                                            .put("name", "時間")
                                            .put("value", cosData.getTimeString())
                                            .put("inline", true)
                                    )
                                    .add(new JsonObject()
                                            .put("name", "組別")
                                            .put("value", cosData.getForClass())
                                            .put("inline", true)
                                    )
                            );
                    mainChannelNotificationEmbeds.add(deptEmbed);

                    // Build user notification embed
                    if (i.courseData.getSerialNumber() == null)
                        break;
                    for (String discordID : watchDog.getWatchedUserDiscordID(i.courseData.getSerialNumber())) {
                        JsonArray userEmbeds = userNotificationEmbeds.get(discordID);
                        if (userEmbeds == null) {
                            userNotificationEmbeds.put(discordID, userEmbeds = new JsonArray());
                            userNotifications.put(discordID, new JsonObject().put("embeds", userEmbeds));
                        }
                        userEmbeds.add(deptEmbed);
                    }
                    break;
            }
        }
        for (Map.Entry<String, JsonObject> userNotificationData : userNotifications.entrySet())
            pool.submit(() -> postToChannel(getDmChannel(userNotificationData.getKey()), userNotificationData.getValue()));
        if (mainChannelNotificationEmbeds.length > 0)
            pool.submit(() -> postToChannel("1018382309197623366", mainChannelNotification));
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
