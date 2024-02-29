package com.wavjaby;

import com.wavjaby.api.search.CourseData;
import com.wavjaby.api.search.Search;
import com.wavjaby.api.search.SearchQuery;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.Lib;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.lib.Lib.executorShutdown;
import static com.wavjaby.lib.Lib.readFileToString;

public class CourseEnrollmentTracker implements Runnable, Module {
    private static final String TAG = "EnrollmentTrack";
    private static final Logger logger = new Logger(TAG);
    private static final String ENROLLMENT_TRACKER_FOLDER = "./api_file/CourseEnrollmentTracker";
    private static final String ALL_COURSE_FILE_NAME = "allCourse.json";
    private final boolean courseEnrollmentTracker;
    private final Search search;
    private CookieStore baseCookieStore;
    private ScheduledExecutorService scheduler;
    private ThreadPoolExecutor messageSendPool;
    private File folder;

    private final long updateInterval = 6 * 50 * 1000;
    private long lastScheduleStart = 0;
    private long lastUpdateStart = System.currentTimeMillis();
    private int count = 0;
    private int taskSkipped = 0;
    private long taskSkippedStartTime = 0;

    private List<CourseData> lastCourseDataList;
    // Serial, index
    private Map<String, Integer> tableIndex;
    private Map<String, SharedCourse> systemCodeMap;

    public static class SharedCourse {
        public final String[] serialIds;
        public final CourseData courseData;

        public SharedCourse(String[] serialIds, CourseData courseData) {
            this.serialIds = serialIds;
            this.courseData = courseData;
        }
    }

    public CourseEnrollmentTracker(Search search, PropertiesReader serverSettings) {
        courseEnrollmentTracker = serverSettings.getPropertyBoolean("courseEnrollmentTracker", false);
        this.search = search;
    }

    @Override
    public void start() {
        this.folder = Lib.getDirectoryFromPath(ENROLLMENT_TRACKER_FOLDER, true);
        String allCourseData = readFileToString(new File(folder, ALL_COURSE_FILE_NAME), true, StandardCharsets.UTF_8);

        lastCourseDataList = new ArrayList<>();
        // Read all course
        if (allCourseData != null && !allCourseData.isEmpty()) {
            JsonObject cache = new JsonObject(allCourseData);

            for (Object o : cache.getArray("data")) {
                CourseData courseData = new CourseData((JsonObject) o);
                lastCourseDataList.add(courseData);
            }

//            systemCodeMap = new HashMap<>();
//            // Parse all course
//            Map<String, List<String>> systemCodeMapBuilder = new HashMap<>();
//            Map<String, CourseData> systemCodeMapBuilderCourse = new HashMap<>();
//            for (Object o : cache.getArray("data")) {
//                CourseData courseData = new CourseData((JsonObject) o);
//                lastCourseDataList.add(courseData);
//                // Add course, check shared system number
//                systemCodeMapBuilder.computeIfAbsent(courseData.getsystemCode(), (i) -> new ArrayList<>())
//                        .add(courseData.getSerialNumber());
//                systemCodeMapBuilderCourse.putIfAbsent(courseData.getsystemCode(), courseData);
//            }
//            // Add to systemCodeMap
//            for (Map.Entry<String, List<String>> entry : systemCodeMapBuilder.entrySet()) {
//                systemCodeMap.put(entry.getKey(), new SharedCourse(
//                        entry.getValue().toArray(new String[0]),
//                        systemCodeMapBuilderCourse.get(entry.getKey())
//                ));
//            }
        }

        if (courseEnrollmentTracker) {
            messageSendPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4, new ThreadFactory(TAG + "-Msg"));
            scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory(TAG + "-Schedule"));
            scheduler.scheduleAtFixedRate(this, 10000, updateInterval, TimeUnit.MILLISECONDS);
        }
    }

//    public SharedCourse getCourseDataBysystemCode(String systemCode) {
//        return systemCodeMap.get(systemCode);
//    }

    @Override
    public void stop() {
        if (scheduler != null)
            executorShutdown(scheduler, 5000, TAG);
        if (messageSendPool != null)
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
        Search.SearchResult searchResult = null;
        for (int i = 0; i < 4; i++) {
            start = System.currentTimeMillis();
            try {
                searchResult = search.querySearch(searchQuery, baseCookieStore);
            } catch (Exception e) {
                logger.errTrace(e);
                continue;
            }
            if (searchResult != null && searchResult.isSuccess())
                break;
            else {
                logger.warn(count + " Failed " + (System.currentTimeMillis() - start) + "ms, Retry");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignore) {
                }
            }
        }
        long now = System.currentTimeMillis();
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
                // Create table index
                if (tableIndex == null) {
                    tableIndex = new HashMap<>();
                    for (CourseData courseData : newCourseDataList) {
                        if (courseData.getSerialNumber() == null)
                            continue;
                        tableIndex.put(courseData.getDeptWithSerial(), 0);
                    }
                    int i = 0;
                    for (Map.Entry<String, Integer> entry : tableIndex.entrySet())
                        entry.setValue(i++);
                }
                // Create file
                File cacheFile = new File(folder, ALL_COURSE_FILE_NAME);
                JsonObjectStringBuilder out = new JsonObjectStringBuilder();
                out.append("time", now);
                JsonArrayStringBuilder courseJsonArray = new JsonArrayStringBuilder();
                int[] selected = new int[tableIndex.size()];
                for (CourseData courseData : newCourseDataList) {
                    if (courseData.getSerialNumber() == null || courseData.getSelected() == null)
                        continue;
                    courseJsonArray.appendRaw(courseData.toString());

                    Integer index = tableIndex.get(courseData.getDeptWithSerial());
                    if (index == null) {
                        logger.err("Unknown SerialNumber: " + courseData.getDeptWithSerial());
                        return;
                    }
                    selected[index] = courseData.getSelected();
                }
                out.append("data", courseJsonArray);

                try {
                    // Store history Data
                    if (folder.exists() && folder.isDirectory()) {
                        String date = OffsetDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
                                .format(DateTimeFormatter.ISO_LOCAL_DATE);
                        File historyFile = new File(folder, date + ".csv");
                        StringBuilder csvOut = new StringBuilder();
                        if (!historyFile.exists()) {
                            if (!historyFile.createNewFile()) {
                                logger.err("Failed to create file: " + historyFile.getAbsolutePath());
                                return;
                            }
                            for (Map.Entry<String, Integer> entry : tableIndex.entrySet())
                                csvOut.append(',').append(entry.getKey());
                        }
                        // Build row
                        StringBuilder builder = new StringBuilder();
                        for (int j = 0; j < selected.length; j++) {
                            if (j > 0) builder.append(',');
                            builder.append(selected[j]);
                        }
                        csvOut.append('\n').append(now).append(',').append(builder);
                        // Write history
                        FileWriter writer = new FileWriter(historyFile, true);
                        writer.write(csvOut.toString());
                        writer.close();
                    }
                    // Write new data to cache
                    FileWriter fileWriter = new FileWriter(cacheFile);
                    fileWriter.write(out.toString());
                    fileWriter.close();
                } catch (IOException e) {
                    logger.errTrace(e);
                }
            }
            logger.log(++count + ", " + newCourseDataList.size() + ", " + (System.currentTimeMillis() - start) + "ms");
        } else {
            logger.err(count + " Failed " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    private void buildAndSendNotification(List<CourseWatcher.CourseDataDifference> diff) {
        // Print result
        for (CourseWatcher.CourseDataDifference i : diff) {
            switch (i.type) {
                case CREATE:
                    logger.log("CREATE: " + i.courseData.getCourseName());
                    break;
                case DELETE:
                    logger.log("DELETE: " + i.courseData.getCourseName());
                    break;
                case UPDATE:
                    CourseData cosData = i.courseData;
                    logger.log("UPDATE " + cosData.getSerialNumber() + " " + cosData.getCourseName() +
                            " available: " + CourseWatcher.intToString(i.availableDiff) +
                            " select: " + CourseWatcher.intToString(i.selectDiff));
                    break;
            }
        }
    }
}
