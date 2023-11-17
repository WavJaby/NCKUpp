package com.wavjaby;

import com.wavjaby.api.search.CourseData;
import com.wavjaby.api.search.Search;
import com.wavjaby.api.search.SearchQuery;
import com.wavjaby.lib.Cookie;
import com.wavjaby.logger.Logger;

import java.net.CookieStore;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.Main.courseNcku;
import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Lib.executorShutdown;

public class CourseEnrollmentTracker implements Runnable, Module {
    private static final String TAG = "EnrollmentTrack";
    private static final Logger logger = new Logger(TAG);
    private final Search search;
    private CookieStore baseCookieStore;
    private ScheduledExecutorService scheduler;
    private ThreadPoolExecutor messageSendPool;

    private final long updateInterval = 120000;
    private long lastScheduleStart = 0;
    private long lastUpdateStart = System.currentTimeMillis();
    private int count = 0;
    private int taskSkipped = 0;
    private long taskSkippedStartTime = 0;

    private List<CourseData> lastCourseDataList;

    public CourseEnrollmentTracker(Search search, Properties serverSettings) {
        this.search = search;
    }

    @Override
    public void start() {
        baseCookieStore = search.createCookieStore();
        lastCourseDataList = new ArrayList<>();

        messageSendPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this, 1000, updateInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        executorShutdown(scheduler, 5000, TAG);
        executorShutdown(messageSendPool, 5000, TAG + "Message Pool");
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public boolean api() {
        return false;
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

        // Force renew cookie
        if (count >= 1000) {
            baseCookieStore = search.createCookieStore();
            count = 0;
        }

        // Start fetch course update
        List<CourseWatcher.CourseDataDifference> diff = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        map.put("dept", "ALL");
        SearchQuery searchQuery = search.getSearchQueryFromRequest(map, new String[0], null);
        Search.SearchResult searchResult = search.querySearch(searchQuery, baseCookieStore);

        List<CourseData> newCourseDataList = searchResult.getCourseDataList();
        if (!lastCourseDataList.isEmpty())
            diff.addAll(CourseWatcher.getDifferent(lastCourseDataList, newCourseDataList));
        lastCourseDataList = newCourseDataList;

        // Build notification
        if (!diff.isEmpty())
            messageSendPool.submit(() -> buildAndSendNotification(diff));
        logger.log(++count + ", " + newCourseDataList.size() + ", " + (System.currentTimeMillis() - start) + "ms");
    }

    private void buildAndSendNotification(List<CourseWatcher.CourseDataDifference> diff) {
        // Print result
        for (CourseWatcher.CourseDataDifference i : diff) {
            switch (i.type) {
                case CREATE:
                    logger.log("CREATE: " + i.courseData.getCourseName());
                    break;
                case UPDATE:
                    CourseData cosData = i.courseData;
                    logger.log("UPDATE " + cosData.getSerialNumber() + " " + cosData.getCourseName() +
                            "available: " + i.availableDiff + ", select: " + i.selectDiff);

                    break;
            }
        }
    }

    private String intToString(int integer) {
        return integer > 0 ? "+" + integer : String.valueOf(integer);
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
