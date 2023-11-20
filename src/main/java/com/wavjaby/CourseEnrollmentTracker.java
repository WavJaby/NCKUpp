package com.wavjaby;

import com.wavjaby.api.search.CourseData;
import com.wavjaby.api.search.Search;
import com.wavjaby.api.search.SearchQuery;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.lib.Lib.executorShutdown;
import static com.wavjaby.lib.Lib.setFilePermission;

public class CourseEnrollmentTracker implements Runnable, Module {
    private static final String TAG = "EnrollmentTrack";
    private static final Logger logger = new Logger(TAG);
    private static final String ENROLLMENT_TRACKER_FOLDER = "./api_file/CourseEnrollmentTracker";
    private static final String CACHE_FILE_NAME = "cache.json";
    private final Search search;
    private CookieStore baseCookieStore;
    private ScheduledExecutorService scheduler;
    private ThreadPoolExecutor messageSendPool;
    private File folder;

    private final long updateInterval = 5 * 50 * 1000;
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
        File folder = new File(ENROLLMENT_TRACKER_FOLDER);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                logger.err("EnrollmentTracker folder failed to create");
                folder = null;
            }
        }
        if (folder != null && !folder.isDirectory()) {
            logger.err("EnrollmentTracker folder is not Directory");
            folder = null;
        }
        if (folder != null)
            setFilePermission(folder, Main.userPrincipal, Main.groupPrincipal, Main.folderPermission);
        this.folder = folder;

        baseCookieStore = search.createCookieStore();
        lastCourseDataList = new ArrayList<>();
        // Read cache
        File cacheFile;
        if (folder != null && (cacheFile = new File(folder, CACHE_FILE_NAME)).isFile()) {
            try {
                JsonArray jsonArray = new JsonArray(Files.newInputStream(cacheFile.toPath()));
                for (Object o : jsonArray) {
                    lastCourseDataList.add(new CourseData((JsonObject) o));
                }
            } catch (IOException e) {
                logger.errTrace(e);
            }
        }

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
        if (count % 2 == 0) {
            baseCookieStore = search.createCookieStore();
        }

        // Start fetch course update
        Map<String, String> map = new HashMap<>();
        map.put("dept", "ALL");
        SearchQuery searchQuery = search.getSearchQueryFromRequest(map, new String[0], null);
        Search.SearchResult searchResult = search.querySearch(searchQuery, baseCookieStore);
        // Check result
        if (searchResult.isSuccess()) {
            List<CourseData> newCourseDataList = searchResult.getCourseDataList();
            // Get diff
            List<CourseWatcher.CourseDataDifference> diff = new ArrayList<>();
            if (!lastCourseDataList.isEmpty())
                diff.addAll(CourseWatcher.getDifferent(lastCourseDataList, newCourseDataList));
            lastCourseDataList = newCourseDataList;
            if (!diff.isEmpty()) {
                // Build notification
                messageSendPool.submit(() -> buildAndSendNotification(diff));
                // Create file
                File cacheFile = new File(folder, CACHE_FILE_NAME);
                JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
                for (CourseData courseData : newCourseDataList) {
                    if (courseData.getSerialNumber() != null && courseData.getSelected() != null)
                        builder.appendRaw(courseData.toStringShort());
                }
                try {
                    // Store history Data
                    if (folder != null && cacheFile.isFile()) {
                        File historyFile = new File(folder, cacheFile.lastModified() + ".json");
                        Files.copy(cacheFile.toPath(), historyFile.toPath(),
                                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    }
                    // Write bew data to cache
                    FileWriter fileWriter = new FileWriter(cacheFile);
                    fileWriter.write(builder.toString());
                    fileWriter.close();
                } catch (IOException e) {
                    logger.errTrace(e);
                }
            }
            logger.log(++count + ", " + newCourseDataList.size() + ", " + (System.currentTimeMillis() - start) + "ms");
        } else {
            logger.warn(count + " Failed " + (System.currentTimeMillis() - start) + "ms");
        }
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
                            "available: " + CourseWatcher.intToString(i.availableDiff) +
                            ", select: " + CourseWatcher.intToString(i.selectDiff));
                    break;
            }
        }
    }
}
