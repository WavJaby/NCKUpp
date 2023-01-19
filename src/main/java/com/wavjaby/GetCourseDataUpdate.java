package com.wavjaby;

import com.wavjaby.api.Search;
import com.wavjaby.json.JsonObject;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.Cookie.createHttpCookie;
import static com.wavjaby.Main.courseNcku;
import static com.wavjaby.Main.courseNckuOrg;

public class GetCourseDataUpdate {
    private final static String TAG = "[GetCourseDataUpdate] ";
    private final CookieManager cookieManager = new CookieManager();
    private final CookieStore cookieStore = cookieManager.getCookieStore();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Search search;

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

    public GetCourseDataUpdate(Search search) {
        this.search = search;
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
            cookieStore.add(uri, createHttpCookie("PHPSESSID", "F741147606d7a265fafcbd943052fbfb03d0f6ee7", courseNcku));
            cookieStore.add(uri, createHttpCookie("COURSE_WEB", "ffffffff8f7cbb1f45525d5f4f58455e445a4a423660", courseNcku));
            cookieStore.add(uri, createHttpCookie("COURSE_CDN", "ffffffff8f7ce72345525d5f4f58455e445a4a42cbda", courseNcku));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        long interval = 1500;
        String dept = "A9";

        scheduler.scheduleAtFixedRate(new Runnable() {
            Search.DeptToken deptToken = null;
            long last = System.currentTimeMillis();
            int count = 0;
            List<Search.CourseData> lastCourseDataList = null;

            @Override
            public void run() {
                long lastInterval = System.currentTimeMillis() - last;
                last = System.currentTimeMillis();
                // Skip if last task take too long
                if (lastInterval < interval - 100) {
                    if (lastInterval > 0)
                        Logger.log(TAG, "Skip " + lastInterval + "ms");
                    return;
                }
                Logger.log(TAG, "Start " + lastInterval + "ms");


                long start = System.currentTimeMillis();
                List<Search.CourseData> courseDataList = new ArrayList<>();
                try {
                    if (deptToken == null)
                        deptToken = renewDeptToken(dept, cookieStore);
                    if (deptToken == null)
                        return;
                    // Get dept course data
                    if (!search.getDeptCourseData(deptToken, courseDataList)) {
                        deptToken = null;
                        return;
                    }
                    // Get different
                    List<CourseDataDifference> diff = getDifferent(lastCourseDataList, courseDataList);
                    lastCourseDataList = courseDataList;

                    // Print result
                    if (diff != null) {
                        for (CourseDataDifference i : diff) {
                            switch (i.type) {
                                case CREATE:
                                    Logger.log(TAG, "CREATE: " +
                                            new JsonObject(i.courseData.toString()).toStringBeauty());
                                    break;
                                case UPDATE:
                                    Search.CourseData c = i.courseData;
                                    Logger.log(TAG, "UPDATE " + c.getSerialNumber() + " " + c.getCourseName() + "\n" +
                                                "available: " + i.availableDiff + ", select: " + i.selectDiff);
                                    break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    deptToken = null;
                    count = 0;
                    Logger.log(TAG, "Retry");
                    try {
                        Thread.sleep(6000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    return;
                }
                Logger.log(TAG, count++ + ", " +
                        courseDataList.size() + ", " +
                        (System.currentTimeMillis() - start) + "ms");
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    private List<CourseDataDifference> getDifferent(List<Search.CourseData> last, List<Search.CourseData> now) {
        if (last == null) return null;

        HashMap<String, Search.CourseData> lastCourseDataMap = new HashMap<>();
        for (Search.CourseData i : last)
            lastCourseDataMap.put(i.getSerialNumber(), i);

        List<CourseDataDifference> diff = new ArrayList<>();

        for (Search.CourseData nowData : now) {
            Search.CourseData lastData = lastCourseDataMap.get(nowData.getSerialNumber());

            if (lastData == null) {
                diff.add(new CourseDataDifference(nowData));
            } else if (!Objects.equals(lastData.getAvailable(), nowData.getAvailable()) ||
                    !Objects.equals(lastData.getSelected(), nowData.getSelected())
            ) {
                int availableDiff = nowData.getAvailable() == null ? -lastData.getAvailable()
                        : lastData.getAvailable() == null ? nowData.getAvailable()
                        : nowData.getAvailable() - lastData.getAvailable();
                int selectDiff = nowData.getSelected() == null ? -lastData.getSelected()
                        : lastData.getSelected() == null ? nowData.getSelected()
                        : nowData.getSelected() - lastData.getSelected();
                diff.add(new CourseDataDifference(nowData, availableDiff, selectDiff));
            }
        }

        return diff;
    }

    private Search.DeptToken renewDeptToken(String deptNo, CookieStore cookieStore) throws IOException {
        Logger.log(TAG, "Renew dept token");
        Search.AllDeptData allDeptData = search.getAllDeptData(cookieStore);
        if (allDeptData == null) {
            Logger.err(TAG, "Can not get crypt");
            return null;
        }
        Search.DeptToken deptToken = search.getDeptToken(deptNo, allDeptData);
        if (deptToken.getError() != null) {
            Logger.err(TAG, deptToken.getError());
            return null;
        }
        Logger.log(TAG, deptToken.getID());
        return deptToken;
    }
}
