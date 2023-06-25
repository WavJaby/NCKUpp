package com.wavjaby;

import com.sun.istack.internal.NotNull;
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
    private static final String TAG = "[CourseListener]";
    private static final Logger logger = new Logger(TAG);
    private static final String apiUrl = "https://discord.com/api/v10";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService fetchCoursePool = Executors.newFixedThreadPool(8);
    private final ExecutorService messageSendPool = Executors.newFixedThreadPool(4);
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
    private final Map<String, List<Search.CourseData>> deptCourseDataList = new HashMap<>();
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

        baseCookieStore = search.createCookieStore();
        addListenDept("A1");
        addListenDept("A2");
        addListenDept("A6");
        addListenDept("A7");
        addListenDept("A9");
        addListenDept("AF");
        addListenDept("F7");
        addListenDept("J0");
        addListenDept("M0");
        scheduler.scheduleAtFixedRate(this, 0, updateInterval, TimeUnit.MILLISECONDS);
    }


    private void getWatchDogUpdate() {
        String[] newDept = watchDog.getNewDept();
        if (newDept != null)
            for (String dept : newDept)
                addListenDept(dept);
    }

    private void addListenDept(String deptID) {
        if (listenDept.containsKey(deptID)) return;
        logger.log("Add watch: " + deptID);
        listenDept.put(deptID, search.createCookieStore());
    }


    private String getDmChannel(String userID) {
        String dmChannelID = userDmChannelCache.get(userID);
        if (dmChannelID != null) return dmChannelID;
        try {
//            logger.log(jsonObject.toString());
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
//            logger.log(jsonObject.toString());
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
            logger.log(new JsonObject(result).toStringBeauty());
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
            logger.log("Skipped " + taskSkipped + ", " + (start - taskSkippedStartTime));
            taskSkipped = 0;
        } else if (lastInterval < updateInterval - 50) {
            if (taskSkipped++ == 0)
                taskSkippedStartTime = lastScheduleStart;
            return;
        }
        logger.log("Interval " + (start - lastUpdateStart) + "ms");
        lastUpdateStart = start;

        getWatchDogUpdate();

        // Start fetch course update
        CountDownLatch taskLeft = new CountDownLatch(listenDept.size());
        AtomicBoolean done = new AtomicBoolean(false);
        List<CourseDataDifference> diff = new ArrayList<>();

        // Force renew cookie
        if (count >= 1000) {
            for (String dept : listenDept.keySet())
                deptTokenMap.put(dept, null);
            count = 0;
        }

        int[] total = new int[1];
        // Fetch dept data
        for (Map.Entry<String, CookieStore> i : listenDept.entrySet()) {
            final String dept = i.getKey();
            final CookieStore cookieStore = i.getValue();
            fetchCoursePool.submit(() -> {
                Search.DeptToken deptToken = deptTokenMap.get(dept);
                if (deptToken == null) {
                    deptToken = renewDeptToken(dept, cookieStore);
                    if (!done.get())
                        deptTokenMap.put(dept, deptToken);
                }
                // Have dept token
                if (!done.get() && deptToken != null) {
                    // Get dept course data
                    List<Search.CourseData> newCourseDataList = new ArrayList<>();
                    if (!search.getDeptCourseData(deptToken, false, null, newCourseDataList) ||
                            newCourseDataList.size() == 0) {
                        if (!done.get()) {
                            logger.log("Dept " + dept + " failed");
                            deptTokenMap.put(dept, null);
                        }
                    } else if (!done.get()) {
                        List<Search.CourseData> lastCourseDataList = deptCourseDataList.get(dept);
                        total[0] += newCourseDataList.size();
                        if (lastCourseDataList != null)
                            diff.addAll(getDifferent(lastCourseDataList, newCourseDataList));
                        deptCourseDataList.put(dept, newCourseDataList);
                        logger.log("Dept " + dept + " done");
                    }
                }
                taskLeft.countDown();
            });
        }
        boolean timeout;
        try {
            timeout = !taskLeft.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.errTrace(e);
            return;
        }
        done.set(true);

        // Build notification
        if (diff.size() > 0)
            messageSendPool.submit(() -> buildAndSendNotification(diff));
        if (timeout) {
            logger.log(count + ", Time out, " + (System.currentTimeMillis() - start) + "ms");
        } else {
            logger.log(++count + ", " + total[0] + ", " + (System.currentTimeMillis() - start) + "ms");
        }
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
                    logger.log("CREATE: " + i.courseData.getCourseName());
                    break;
                case UPDATE:
                    Search.CourseData cosData = i.courseData;
                    logger.log("UPDATE " + cosData.getSerialNumber() + " " + cosData.getCourseName() + "\n" +
                            "available: " + i.availableDiff + ", select: " + i.selectDiff);

                    String url = null;
                    try {
                        Search.SearchQuery searchQuery = new Search.SearchQuery(cosData);
                        Search.SaveQueryToken token = search.createSaveQueryToken(searchQuery, baseCookieStore, null);
                        if (token != null)
                            url = token.getUrl();
                    } catch (Exception e) {
                        logger.errTrace(e);
                    }

                    int courseAvailable = cosData.getAvailable() != null ? -1 : cosData.getAvailable();
                    int embedColor = i.availableDiff > 0
                            ? (courseAvailable == 1 ? 0x00FF00 : 0x00FFFF)
                            : courseAvailable > 0 ? 0xFFFF00 : 0xFF0000;

                    JsonObject deptEmbed = new JsonObject()
                            .put("type", "rich")
                            .put("color", embedColor)
                            .put("title", cosData.getSerialNumber() + " " + cosData.getCourseName())
                            .put("description", cosData.getGroup())
                            .put("url", url)
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
                                            .put("value", String.valueOf(cosData.getTimeString()))
                                            .put("inline", true)
                                    )
                                    .add(new JsonObject()
                                            .put("name", "組別")
                                            .put("value", String.valueOf(cosData.getForClass()))
                                            .put("inline", true)
                                    )
                            );
                    mainChannelNotificationEmbeds.add(deptEmbed);

                    // Build user notification embed
//                    if (i.courseData.getSerialNumber() == null)
//                        break;
//                    for (String discordID : watchDog.getWatchedUserDiscordID(i.courseData.getSerialNumber())) {
//                        JsonArray userEmbeds = userNotificationEmbeds.get(discordID);
//                        if (userEmbeds == null) {
//                            userNotificationEmbeds.put(discordID, userEmbeds = new JsonArray());
//                            userNotifications.put(discordID, new JsonObject().put("embeds", userEmbeds));
//                        }
//                        userEmbeds.add(deptEmbed);
//                    }
                    break;
            }
        }
//        for (Map.Entry<String, JsonObject> userNotificationData : userNotifications.entrySet())
//            messageSendPool.submit(() -> postToChannel(getDmChannel(userNotificationData.getKey()), userNotificationData.getValue()));
        if (mainChannelNotificationEmbeds.length > 0) {
            logger.log("Post to channel");
            messageSendPool.submit(() -> postToChannel("1018382309197623366", mainChannelNotification));
        }
    }

    private String intToString(int integer) {
        return integer > 0 ? "+" + integer : String.valueOf(integer);
    }

    private List<CourseDataDifference> getDifferent(@NotNull List<Search.CourseData> last, @NotNull List<Search.CourseData> now) {
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

    private Search.DeptToken renewDeptToken(String deptNo, CookieStore cookieStore) {
        logger.log("Renew dept token " + deptNo);
        Search.AllDeptData allDeptData = search.getAllDeptData(cookieStore);
        if (allDeptData == null) {
            logger.err("Can not get allDeptData");
            return null;
        }
        Search.DeptToken deptToken = search.createDeptToken(deptNo, allDeptData);
        if (deptToken == null)
            return null;
        if (deptToken.getError() != null) {
            logger.err(deptToken.getError());
            return null;
        }
        logger.log(deptToken.getID());
        return deptToken;
    }
}
