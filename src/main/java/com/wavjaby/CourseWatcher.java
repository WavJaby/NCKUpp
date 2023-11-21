package com.wavjaby;

import com.sun.istack.internal.NotNull;
import com.wavjaby.api.DeptWatchdog;
import com.wavjaby.api.search.CourseData;
import com.wavjaby.api.search.Search;
import com.wavjaby.api.search.SearchQuery;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.Cookie;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.net.CookieStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wavjaby.Main.courseNcku;
import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Lib.executorShutdown;

public class CourseWatcher implements Runnable, Module {
    private static final String TAG = "CourseWatcher";
    private static final Logger logger = new Logger(TAG);
    private static final String apiUrl = "https://discord.com/api/v10";
    private final Search search;
    private final DeptWatchdog watchDog;
    private CookieStore baseCookieStore;
    private ScheduledExecutorService scheduler;
    private ThreadPoolExecutor fetchCoursePool, messageSendPool;

    private final long updateInterval = 1600;
    private long lastScheduleStart = 0;
    private long lastUpdateStart = System.currentTimeMillis();
    private int count = 0;
    private int taskSkipped = 0;
    private long taskSkippedStartTime = 0;

    private final Map<String, CookieStore> listenDept = new HashMap<>();
    private final Map<String, Search.DeptToken> deptTokenMap = new HashMap<>();
    private final Map<String, List<CourseData>> deptCourseDataList = new HashMap<>();
    private final String botToken;
    private final HashMap<String, String> userDmChannelCache = new HashMap<>();

    public static class CourseDataDifference {
        public enum Type {
            CREATE,
            DELETE,
            UPDATE,
        }

        public final Type type;
        public final CourseData courseData;
        public final int availableDiff;
        public final int selectDiff;

        public CourseDataDifference(CourseData courseData, int availableDiff, int selectDiff) {
            this.type = Type.UPDATE;
            this.courseData = courseData;
            this.availableDiff = availableDiff;
            this.selectDiff = selectDiff;
        }

        public CourseDataDifference(CourseData courseData) {
            this.type = Type.CREATE;
            this.courseData = courseData;
            this.availableDiff = courseData.getAvailable();
            this.selectDiff = courseData.getSelected();
        }
    }

    public CourseWatcher(Search search, DeptWatchdog watchDog, Properties serverSettings) {
        this.search = search;
        this.watchDog = watchDog;
        botToken = serverSettings.getProperty("botToken");
    }

    @Override
    public void start() {
        baseCookieStore = search.createCookieStore();
//        addListenDept("A1");
//        addListenDept("A2");
//        addListenDept("A6");
//        addListenDept("A7");
//        addListenDept("A9");
//        addListenDept("AF");
//        addListenDept("F7");
//        addListenDept("J0");
//        addListenDept("M0");
        addListenDept("F7");
        addListenDept("A9");
        scheduler.scheduleAtFixedRate(this, 0, updateInterval, TimeUnit.MILLISECONDS);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        fetchCoursePool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        messageSendPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
    }

    @Override
    public void stop() {
        executorShutdown(scheduler, 5000, TAG);
        executorShutdown(fetchCoursePool, 5000, TAG + "Fetch Pool");
        executorShutdown(messageSendPool, 5000, TAG + "Message Pool");
    }

    @Override
    public String getTag() {
        return TAG;
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
            logger.errTrace(e);
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
//            logger.log(new JsonObject(result).toStringBeauty());
        } catch (IOException e) {
            logger.errTrace(e);
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
            baseCookieStore = search.createCookieStore();
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
                    deptTokenMap.put(dept, deptToken);
                }
                // Have dept token
                if (deptToken != null) {
                    // Get dept course data
                    Search.SearchResult searchResult = new Search.SearchResult();
                    if (!search.getDeptCourseData(deptToken, false, searchResult)) {
                        logger.log("Dept " + dept + " failed");
                        if (!done.get())
                            deptTokenMap.put(dept, null);
                    } else if (!done.get()) {
                        List<CourseData> lastCourseDataList = deptCourseDataList.get(dept);
                        List<CourseData> newCourseDataList = searchResult.getCourseDataList();
                        total[0] += newCourseDataList.size();
                        if (lastCourseDataList != null)
                            diff.addAll(getDifferent(lastCourseDataList, newCourseDataList));
                        deptCourseDataList.put(dept, newCourseDataList);
//                        logger.log("Dept " + dept + " done");
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
        if (!diff.isEmpty())
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
                    CourseData cosData = i.courseData;
                    logger.log("UPDATE " + cosData.getSerialNumber() + " " + cosData.getCourseName());
//                            "available: " + i.availableDiff + ", select: " + i.selectDiff);

                    String url = null;
                    try {
                        SearchQuery searchQuery = new SearchQuery(cosData);
                        Search.SaveQueryToken token = search.createSaveQueryToken(searchQuery.toCourseQuery(), baseCookieStore, null);
                        if (token != null)
                            url = token.getUrl();
                    } catch (Exception e) {
                        logger.errTrace(e);
                    }

                    int courseAvailable = cosData.getAvailable() == null ? -1 : cosData.getAvailable();
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
                                            .put("value", cosData.getTimeString() == null ? "未定" : String.valueOf(cosData.getTimeString()))
                                            .put("inline", true)
                                    )
                                    .add(new JsonObject()
                                            .put("name", "組別")
                                            .put("value", cosData.getForClass() == null ? "無" : String.valueOf(cosData.getForClass()))
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
//        if (mainChannelNotificationEmbeds.length > 0) {
//            logger.log("Post to channel");
//            messageSendPool.submit(() -> postToChannel("1018756061911601224", mainChannelNotification));
//        }
    }

    public static String intToString(int integer) {
        return integer > 0 ? "+" + integer : String.valueOf(integer);
    }

    public static List<CourseDataDifference> getDifferent(@NotNull List<CourseData> last, @NotNull List<CourseData> now) {
        HashMap<String, CourseData> lastCourseDataMap = new HashMap<>();
        for (CourseData i : last)
            if (i.getSerialNumber() != null)
                lastCourseDataMap.put(i.getSerialNumber(), i);

        List<CourseDataDifference> diff = new ArrayList<>();

        for (CourseData nowData : now) {
            if (nowData.getSerialNumber() == null) continue;
            CourseData lastData = lastCourseDataMap.get(nowData.getSerialNumber());

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

    private static int getIntegerDiff(Integer oldInt, Integer newInt) {
        return newInt == null ? -oldInt
                : oldInt == null ? newInt
                : newInt - oldInt;
    }

    private Search.DeptToken renewDeptToken(String deptNo, CookieStore cookieStore) {
        logger.log("Renew dept token " + deptNo);
        cookieStore.add(courseNckuOrgUri, Cookie.createHttpCookie("PHPSESSID", "ID", courseNcku));
        Search.AllDeptData allDeptData = search.getAllDeptData(cookieStore, null);
        if (allDeptData == null) {
            logger.err("Can not get allDeptData");
            return null;
        }
        Search.DeptToken deptToken = search.createDeptToken(deptNo, allDeptData, null);
        if (deptToken == null)
            return null;
        logger.log(deptToken.getID());
        return deptToken;
    }
}
