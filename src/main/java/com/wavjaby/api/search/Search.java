package com.wavjaby.api.search;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.RobotCode;
import com.wavjaby.api.UrSchool;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Cookie;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.Progressbar;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URI;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wavjaby.Main.*;
import static com.wavjaby.api.IP.getClientIP;
import static com.wavjaby.lib.ApiThrottle.*;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.HttpResponseData.ResponseState;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class Search implements Module {
    private static final String TAG = "Search";
    private static final Logger logger = new Logger(TAG);
    private static final int MAX_ROBOT_CHECK_REQUEST_TRY = 3;
    private static final int MAX_ROBOT_CHECK_TRY = 2;
    private static final int COS_PRE_CHECK_COOKIE_LOCK = 2;
    private static final int MULTITHREADING_SEARCH_THREAD_COUNT = 4;
    private static final int MULTI_QUERY_SEARCH_THREAD_COUNT = 4;
    private static final Pattern displayRegex = Pattern.compile("[\\r\\n]+\\.(\\w+) *\\{[\\r\\n]* *(?:/\\* *\\w+ *: *\\w+ *;? *\\*/ *)?display *: *(\\w+) *;? *");
    private static final Map<String, Character> tagColormap = new HashMap<String, Character>() {{
        put("default", '0');
        put("success", '1');
        put("info", '2');
        put("primary", '3');
        put("warning", '4');
        put("danger", '5');
    }};

    private final ThreadPoolExecutor cosPreCheckPool;
    private final Semaphore cosPreCheckPoolLock;
    private final Map<String, CookieLock> cosPreCheckCookieLock = new ConcurrentHashMap<>();

    private final UrSchool urSchool;
    private final RobotCode robotCode;
    private final ProxyManager proxyManager;

    private static class CookieLock {
        final Semaphore semaphore;
//        final ReentrantLock lock;

        public CookieLock(Semaphore semaphore) {
            this.semaphore = semaphore;
//            this.lock = new ReentrantLock(true);
        }
    }

    public Search(UrSchool urSchool, RobotCode robotCode, ProxyManager proxyManager) {
        this.urSchool = urSchool;
        this.robotCode = robotCode;
        this.proxyManager = proxyManager;
        this.cosPreCheckPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8, new ThreadFactory(TAG + "-Cos-Pre"));
        this.cosPreCheckPoolLock = new Semaphore(cosPreCheckPool.getMaximumPoolSize(), true);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        executorShutdown(cosPreCheckPool, 1000, "CosPreCheck");
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @SuppressWarnings("unused")
    @RequestMapping("/search")
    public RestApiResponse search(HttpExchange req) {
        String ip = getClientIP(req);
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();

        // unpack cookie
        String[] cookieIn = splitCookie(req);
        String loginState = unpackCourseLoginStateCookie(cookieIn, cookieStore);

        // search
        String rawQuery = req.getRequestURI().getRawQuery();
        // Parse query
        Map<String, String> query = parseUrlEncodedForm(rawQuery);
        ApiResponse response = new ApiResponse();
        SearchQuery searchQuery = getSearchQueryFromRequest(query, cookieIn, response);
        String errorMessage = searchQuery.getError();
        if (errorMessage != null)
            response.errorBadQuery(errorMessage);
        else if (searchQuery.noQuery())
            response.errorBadQuery("Empty query");
        else {
            // Check throttle
            if (!checkIpThrottle(ip)) {
//                logger.log(getClientIP(req) + " Throttle");
                response.errorTooManyRequests();
                return response;
            }

            SearchResult searchResult = querySearch(searchQuery, cookieStore);
            searchResult.passDataTo(response);

            // set cookie
            packCourseLoginStateCookie(req, loginState, cookieStore);
            if (response.isSuccess()) {
                if (searchResult.getSearchID() != null)
                    addCookieToHeader("searchID", searchResult.getSearchID().getAsCookieValue(), "/", req);
            } else
                addRemoveCookieToHeader("searchID", "/", req);
        }

        int used = doneIpThrottle(ip);
        if (used < FETCH_PER_MIN) {
            logger.log(ip + ' ' + used + ' ' + response.isSuccess() + ' ' + (System.currentTimeMillis() - startTime) + "ms");
        }

        return response;
    }

    public SearchQuery getSearchQueryFromRequest(Map<String, String> query, String[] cookieIn, ApiResponse response) {
        // Get search ID
        String searchIDs = Cookie.getCookie("searchID", cookieIn);
        CourseSearchID courseSearchID = null;
        if (searchIDs != null) {
            int split = searchIDs.indexOf('|');
            String searchID = searchIDs.isEmpty() || split == 0 ? null
                    : searchIDs.substring(0, split);
            // "searchID,PHPSESSID"
            String historySearchIDAndCookie = split == -1 || split + 1 == searchIDs.length() ? null
                    : searchIDs.substring(split + 1);
            String historySearchID = null, historySearchPHPSESSID = null;
            if (historySearchIDAndCookie != null) {
                int idSplit = historySearchIDAndCookie.indexOf(',');
                if (idSplit == -1 || idSplit == 0 || idSplit + 1 == historySearchIDAndCookie.length()) {
                    if (response != null)
                        response.addWarn("'searchID' Format error, Not provide correct 'searchID' will impact response time, please use correct searchID from response cookie.");
                } else {
                    historySearchID = historySearchIDAndCookie.substring(0, idSplit);
                    historySearchPHPSESSID = historySearchIDAndCookie.substring(idSplit + 1);
                }
            }
            courseSearchID = new CourseSearchID(searchID, historySearchID, historySearchPHPSESSID);
        }

        // Get with serial
        String rawSerial = query.get("serial");
        Map<String, Set<String>> serialIdNumber = null;
        if (rawSerial != null) {
            serialIdNumber = new HashMap<>();
            // Text type
            String[] deptSerialArr = simpleSplit(rawSerial, ',');
            for (String i : deptSerialArr) {
                int index = i.indexOf('-');
                if (index == -1) {
                    return SearchQuery.newError("'serial' Format error: '" + i + "', use '-' to separate dept id and serial number, EX: F7-001.");
                }
                Set<String> serial = serialIdNumber.computeIfAbsent(i.substring(0, index), k -> new HashSet<>());
                String serialId = i.substring(index + 1);
                // TODO: Check dept id and serial number
                serial.add(serialId);
            }
        }

        // History search
        String queryTimeBegin = query.get("semBegin"), queryTimeEnd = query.get("semEnd");
        CourseHistorySearch historySearch = null;
        if (queryTimeBegin != null || queryTimeEnd != null) {
            int yearBegin = -1, semBegin = -1, yearEnd = -1, semEnd = -1;
            if (queryTimeBegin == null) {
                return SearchQuery.newError("History search missing 'timeBegin' key in query.");
            } else if (queryTimeEnd == null) {
                return SearchQuery.newError("History search missing 'timeEnd' key in query.");
            } else {
                int startSplit = queryTimeBegin.indexOf('-');
                if (startSplit == -1) {
                    return SearchQuery.newError("'semBegin' Format error: use '-' to separate year and semester, EX: 111-1.");
                } else {
                    try {
                        yearBegin = Integer.parseInt(queryTimeBegin.substring(0, startSplit));
                        semBegin = Integer.parseInt(queryTimeBegin.substring(startSplit + 1));
                    } catch (NumberFormatException e) {
                        return SearchQuery.newError("'semBegin' Format error: " + e.getMessage() + '.');
                    }
                }

                int endSplit = queryTimeEnd.indexOf('-');
                if (endSplit == -1) {
                    return SearchQuery.newError("'semEnd' Format error: use '-' to separate year and semester, EX: 111-1.");
                } else {
                    try {
                        yearEnd = Integer.parseInt(queryTimeEnd.substring(0, endSplit));
                        semEnd = Integer.parseInt(queryTimeEnd.substring(endSplit + 1));
                    } catch (NumberFormatException e) {
                        return SearchQuery.newError("'semEnd' Format error: " + e.getMessage() + '.');
                    }
                }
            }
            historySearch = new CourseHistorySearch(yearBegin, semBegin, yearEnd, semEnd);
        }

        // Parse time query
        String timeRaw = query.get("time");                   // 時間 [星期)節次~節次_節次_節次...] (星期: 0~6, 節次: 0~15)
        CourseSearchQuery.TimeQuery[] timeQueries = null;
        if (timeRaw != null) {
            List<CourseSearchQuery.TimeQuery> timeQuerieList = new ArrayList<>();
            boolean[][] timeTable = new boolean[7][];
            String[] timeArr = simpleSplit(timeRaw, ',');
            for (String time : timeArr) {
                int dayOfWeekIndex = time.indexOf(')');

                byte dayOfWeek;
                boolean allDay = false;
                boolean noSection = false;
                if (dayOfWeekIndex != -1 || (noSection = time.indexOf('~') == -1 && time.indexOf('_') == -1)) {
                    if (noSection)
                        dayOfWeekIndex = time.length();
                    try {
                        int d = Integer.parseInt(time.substring(0, dayOfWeekIndex));
                        if (d < 0 || d > 6) {
                            return SearchQuery.newError("'time' Format error: day of week should >= 0 and <= 6.");
                        }
                        dayOfWeek = (byte) d;
                    } catch (NumberFormatException e) {
                        return SearchQuery.newError("'time' Format error: day of week '" + time.substring(0, dayOfWeekIndex) + "' is not a number.");
                    }
                    if (noSection)
                        Arrays.fill(timeTable[dayOfWeek] = new boolean[16], true);
                } else {
                    dayOfWeek = 6;
                    allDay = true;
                }
                if (!noSection) {
                    // Parse section
                    String sectionOfDayRaw = time.substring(dayOfWeekIndex + 1);
                    for (int i = allDay ? 0 : dayOfWeek; i <= dayOfWeek; i++) {
                        boolean[] sections = timeTable[i];
                        if (sections == null)
                            sections = timeTable[i] = new boolean[16];
                        String[] sectionOfDays = simpleSplit(sectionOfDayRaw, '_');
                        // [section~section, section, section]
                        for (String section : sectionOfDays) {
                            int index = section.indexOf('~');
                            byte sectionStart, sectionEnd;
                            // Parse section end
                            try {
                                int s = Integer.parseInt(section.substring(index + 1));
                                if (s < 0 || s > 15) {
                                    return SearchQuery.newError("'time' Format error: section start should >= 0 and <= 15.");
                                }
                                sectionEnd = (byte) s;
                            } catch (NumberFormatException e) {
                                return SearchQuery.newError("'time' Format error: section start '" + section + "' is not a number.");
                            }
                            // Parse section start
                            if (index == -1) {
                                sectionStart = sectionEnd;
                            } else {
                                try {
                                    int s = Integer.parseInt(section.substring(0, index));
                                    if (s < 0 || s > 15) {
                                        return SearchQuery.newError("'time' Format error: section end should >= 0 and <= 15.");
                                    }
                                    sectionStart = (byte) s;
                                } catch (NumberFormatException e) {
                                    return SearchQuery.newError("'time' Format error: section end '" + section + "' is not a number.");
                                }
                            }
                            for (int j = sectionStart; j <= sectionEnd; j++)
                                sections[j] = true;
                        }
                    }
                }
            }
            List<Byte> selectedSections = new ArrayList<>();
            for (byte i = 0; i < timeTable.length; i++) {
                boolean[] sections = timeTable[i];
                if (sections == null)
                    continue;
                selectedSections.clear();
                for (byte j = 0; j < sections.length; j++) {
                    if (!sections[j])
                        continue;
                    selectedSections.add(j);
                }
                byte[] b = new byte[selectedSections.size()];
                for (int j = 0; j < selectedSections.size(); j++)
                    b[j] = selectedSections.get(j);
                timeQuerieList.add(new CourseSearchQuery.TimeQuery(i, b));
            }
            if (!timeQuerieList.isEmpty())
                timeQueries = timeQuerieList.toArray(new CourseSearchQuery.TimeQuery[0]);
        }

        // Normal parameters
        String courseName = query.get("courseName");          // 課程名稱
        String instructor = query.get("instructor");          // 教師姓名
        String grade = query.get("grade");                    // 年級 1 ~ 4
        // TODO: Check grade valid
        String dept = query.get("dept");

        return new SearchQuery(courseSearchID,
                serialIdNumber,
                historySearch,
                dept, timeQueries,
                courseName,
                instructor,
                grade
        );
    }

    public SearchResult querySearch(SearchQuery searchQuery, CookieStore cookieStore) {
        // TODO: Support history when get all and serial
        boolean success;
        // get all course
        if (searchQuery.getAll()) {
            SearchResult result = new SearchResult();
            AllDeptData allDeptData = getAllDeptData(cookieStore, result);
            if (!result.success)
                return result;
            // start getting dept
            Progressbar progressbar = Logger.addProgressbar(TAG + " get all");
            progressbar.setProgress(0);
            CountDownLatch taskLeft = new CountDownLatch(allDeptData.deptCount);
            ThreadPoolExecutor fetchPool = (ThreadPoolExecutor)
                    Executors.newFixedThreadPool(MULTITHREADING_SEARCH_THREAD_COUNT, new ThreadFactory(TAG + "-All"));
            Semaphore fetchPoolLock = new Semaphore(MULTITHREADING_SEARCH_THREAD_COUNT, true);
            // Get cookie fragments
            AllDeptData[] fragments = new AllDeptData[MULTITHREADING_SEARCH_THREAD_COUNT];
            CountDownLatch fragmentsLeft = new CountDownLatch(MULTITHREADING_SEARCH_THREAD_COUNT);
            AtomicBoolean allSuccess = new AtomicBoolean(true);
            List<CourseData> courseDataList = new ArrayList<>();
            try {
                // Get fragments
                for (int i = 0; i < MULTITHREADING_SEARCH_THREAD_COUNT; i++) {
                    int finalI = i;
                    fetchPool.submit(() -> {
                        try {
                            SearchResult allDeptResult = new SearchResult();
                            fragments[finalI] = getAllDeptData(createCookieStore(), allDeptResult);
                            if (!allDeptResult.success && result.success)
                                synchronized (result) {
                                    allDeptResult.passDataTo(result);
                                }
                        } catch (Exception e) {
                            logger.errTrace(e);
                            result.setSuccess(false);
                        }
                        fragmentsLeft.countDown();
                    });
                }
                fragmentsLeft.await();
                // Failed to create fragment
                if (!result.success) {
                    progressbar.setProgress(100f);
                    executorShutdown(fetchPool, 5000, "SearchFetchPool");
                    return result;
                }

                // Fetch data
                int i = 0;
                for (String dept : allDeptData.allDept) {
                    fetchPoolLock.acquire();
                    // If one failed, stop all
                    if (!allSuccess.get()) {
                        while (taskLeft.getCount() > 0)
                            taskLeft.countDown();
                        break;
                    }
                    // Switch fragment
                    AllDeptData fragment = fragments[i++];
                    if (i == fragments.length)
                        i = 0;
                    fetchPool.submit(() -> {
                        try {
                            long start = System.currentTimeMillis();
                            SearchResult deptCourseData = getDeptCourseData(dept, fragment, false);
                            if (allSuccess.get() && deptCourseData.isSuccess()) {
                                synchronized (courseDataList) {
                                    courseDataList.addAll(deptCourseData.courseDataList);
                                }
                                progressbar.setProgress(
                                        Thread.currentThread().getName() + " " + dept + " " + (System.currentTimeMillis() - start) + "ms",
                                        (float) (allDeptData.deptCount - taskLeft.getCount()) / allDeptData.deptCount * 100f);
                            }
                            // If search error
                            else if (!deptCourseData.isSuccess()) {
                                allSuccess.set(false);
                                logger.err(Thread.currentThread().getName() + " " + dept + " failed");
                                synchronized (result) {
                                    deptCourseData.passDataTo(result);
                                }
                            }
                        } catch (Exception e) {
                            logger.errTrace(e);
                            allSuccess.set(false);
                        }
                        fetchPoolLock.release();
                        taskLeft.countDown();
                    });
                }
                taskLeft.await();
            } catch (InterruptedException e) {
                allSuccess.set(false);
            }
            executorShutdown(fetchPool, 5000, "SearchFetchPool");
            progressbar.setProgress(100f);
            success = allSuccess.get();
            result.setSuccess(success);
            if (success)
                result.setCourseData(courseDataList);
            return result;
        }
        // Get listed serial or multiple time
        else if (searchQuery.getSerial() || searchQuery.multipleTime()) {
            SearchResult result = new SearchResult();
            List<CourseData> courseDataList = new ArrayList<>();
            ThreadPoolExecutor fetchPool = (ThreadPoolExecutor)
                    Executors.newFixedThreadPool(MULTI_QUERY_SEARCH_THREAD_COUNT, new ThreadFactory(TAG + "-Multi"));
            Semaphore fetchPoolLock = new Semaphore(MULTI_QUERY_SEARCH_THREAD_COUNT, true);
            AtomicBoolean allSuccess = new AtomicBoolean(true);
            CourseSearchQuery[] courseSearchQuery = searchQuery.getSerial()
                    ? searchQuery.toCourseQueriesSerial()
                    : searchQuery.toCourseQueriesMultiTime();
            CountDownLatch taskLeft = new CountDownLatch(courseSearchQuery.length);
            try {
                for (CourseSearchQuery query : courseSearchQuery) {
                    fetchPoolLock.acquire();
                    // If one failed, stop all
                    if (!allSuccess.get()) {
                        while (taskLeft.getCount() > 0)
                            taskLeft.countDown();
                        break;
                    }
                    fetchPool.submit(() -> {
                        try {
                            SearchResult deptCourseData = getQueryCourseData(query, cookieStore);
                            if (allSuccess.get() && deptCourseData.isSuccess())
                                synchronized (courseDataList) {
                                    courseDataList.addAll(deptCourseData.courseDataList);
                                }
                            else {
                                allSuccess.set(false);
                                // Pass error data
                                synchronized (result) {
                                    deptCourseData.passDataTo(result);
                                }
                            }
                        } catch (Exception e) {
                            logger.errTrace(e);
                            allSuccess.set(false);
                        }
                        fetchPoolLock.release();
                        taskLeft.countDown();
                    });
                }
                taskLeft.await();
                executorShutdown(fetchPool, 5000, "SearchFetchPool");
            } catch (InterruptedException e) {
                logger.errTrace(e);
                allSuccess.set(false);
            }
            success = allSuccess.get();
            result.setSuccess(success);
            if (success)
                result.setCourseData(courseDataList);
            return result;
        }
        // Normal Search
        else {
            return getQueryCourseData(searchQuery.toCourseQuery(), cookieStore);
        }
    }

    public static class AllDeptData {
        private final String crypt;
        private final Set<String> allDept;
        private final int deptCount;
        private final CookieStore cookieStore;

        public AllDeptData(String crypt, Set<String> allDept, CookieStore cookieStore) {
            this.crypt = crypt;
            this.allDept = allDept;
            this.deptCount = allDept.size();
            this.cookieStore = cookieStore;
        }
    }

    public static class AllDeptGroupData {
        public static class Group {
            final String name;
            final List<DeptData> dept;

            public Group(String name, List<DeptData> dept) {
                this.name = name;
                this.dept = dept;
            }
        }

        public static class DeptData {
            final String id, name;

            public DeptData(String id, String name) {
                this.id = id;
                this.name = name;
            }
        }

        private final List<Group> deptGroup;
        private final int deptCount;

        public AllDeptGroupData(List<Group> allDept, int total) {
            this.deptGroup = allDept;
            this.deptCount = total;
        }

        public int getDeptCount() {
            return deptCount;
        }

        @Override
        public String toString() {
            JsonObjectStringBuilder outJson = new JsonObjectStringBuilder();
            JsonArrayStringBuilder outDeptGroup = new JsonArrayStringBuilder();
            for (Group group : deptGroup) {
                JsonObjectStringBuilder outGroup = new JsonObjectStringBuilder();
                outGroup.append("name", group.name);
                JsonArrayStringBuilder outDeptData = new JsonArrayStringBuilder();
                for (DeptData deptData : group.dept) {
                    outDeptData.append(new JsonArrayStringBuilder()
                            .append(deptData.id).append(deptData.name));
                }
                outGroup.append("dept", outDeptData);
                outDeptGroup.append(outGroup);
            }

            outJson.append("deptGroup", outDeptGroup);
            outJson.append("deptCount", deptCount);
            return outJson.toString();
        }
    }

    public static class SaveQueryToken {
        private final URI urlOrigin;
        private final String search;
        private final CookieStore cookieStore;

        public SaveQueryToken(URI urlOrigin, String search, CookieStore cookieStore) {
            this.urlOrigin = urlOrigin;
            this.search = search;
            this.cookieStore = cookieStore;
        }

        public String getUrl() {
            return urlOrigin + "/index.php?c=qry11215" + search;
        }
    }

    public static class DeptToken {
        private final String urlEncodedId;
        private final CookieStore cookieStore;
        private final String deptNo;

        private DeptToken(String deptNo, String urlEncodedId, CookieStore cookieStore) {
            this.urlEncodedId = urlEncodedId;
            this.cookieStore = cookieStore;
            this.deptNo = deptNo;
        }

        public String getID() {
            return urlEncodedId;
        }

        public CookieStore getCookieStore() {
            return cookieStore;
        }

        public String getUrl() {
            return courseNckuOrg + "/index.php?c=qry_all&m=result&i=" + urlEncodedId;
        }
    }

    public static class SearchResult {
        private List<CourseData> courseDataList = new ArrayList<>();
        private boolean success = true;
        private CourseSearchID searchID;
        private String parseError, fetchError;

        public void setCourseData(List<CourseData> courseDataList) {
            this.courseDataList = courseDataList;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSearchID(CourseSearchID searchID) {
            this.searchID = searchID;
        }

        public CourseSearchID getSearchID() {
            return searchID;
        }

        public void errorParse(String message) {
            if (this.parseError != null)
                return;
            this.parseError = message;
            this.success = false;
        }

        public void errorFetch(String message) {
            if (this.fetchError != null)
                return;
            this.fetchError = message;
            this.success = false;
        }

        public void passDataTo(ApiResponse apiResponse) {
            if (this.parseError != null)
                apiResponse.errorParse(this.parseError);
            if (this.fetchError != null)
                apiResponse.errorFetch(this.fetchError);
            if (this.success)
                apiResponse.setData(courseDataList.toString());
        }

        public void passDataTo(SearchResult result) {
            if (result.parseError == null)
                result.parseError = this.parseError;
            if (result.fetchError == null)
                result.fetchError = this.fetchError;
            result.success = this.success;
            if (result.success) {
                result.courseDataList = this.courseDataList;
            }
        }

        public List<CourseData> getCourseDataList() {
            return courseDataList;
        }

        public String getErrorString() {
            if (parseError != null || fetchError != null) {
                if (parseError == null)
                    return fetchError;
                else if (fetchError == null)
                    return parseError;
                else
                    return fetchError + ", " + parseError;
            }
            return null;
        }
    }

    public CookieStore createCookieStore() {
        CookieStore cookieStore = new CookieManager().getCookieStore();
        try {
            HttpConnection.connect(courseNckuOrg + "/index.php")
                    .header("Connection", "keep-alive")
//                    .header("Referer", "https://course.ncku.edu.tw/index.php")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(Main.USER_AGENT)
                    .execute();
        } catch (IOException e) {
            logger.errTrace(e);
            byte[] array = new byte[16];
            new Random().nextBytes(array);
            cookieStore.add(courseNckuOrgUri, Cookie.createHttpCookie("PHPSESSID", Base64.getEncoder().encodeToString(array), courseNcku));
        }

//        logger.log(cookieStore.getCookies().toString());
        return cookieStore;
    }

    public AllDeptData getAllDeptData(CookieStore cookieStore, SearchResult result) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .timeout(5000);
        HttpResponseData httpResponseData = sendRequestAndCheckRobot(courseNckuOrgUri, request, cookieStore);
        if (!httpResponseData.isSuccess()) {
            result.errorFetch("Failed to fetch all dept data");
            return null;
        }
        String body = httpResponseData.data;

        cosPreCheck(courseNckuOrg, body, cookieStore, null, proxyManager);

        Set<String> allDept = new HashSet<>();
        for (Element element : Jsoup.parse(body).getElementsByClass("pnl_dept"))
            allDept.addAll(element.getElementsByAttribute("data-dept").eachAttr("data-dept"));

        int cryptStart, cryptEnd;
        if ((cryptStart = body.indexOf("'crypt'")) == -1 ||
                (cryptStart = body.indexOf('\'', cryptStart + 7)) == -1 ||
                (cryptEnd = body.indexOf('\'', ++cryptStart)) == -1
        ) {
            result.errorParse("Get all dept 'crypt' data not found");
            return null;
        }
        return new AllDeptData(body.substring(cryptStart, cryptEnd), allDept, cookieStore);
    }

    public AllDeptGroupData getAllDeptGroupData(CookieStore cookieStore) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(USER_AGENT)
                .timeout(5000);
        HttpResponseData httpResponseData = sendRequestAndCheckRobot(courseNckuOrgUri, request, cookieStore);
        if (!httpResponseData.isSuccess())
            return null;
        String body = httpResponseData.data;

        cosPreCheck(courseNckuOrg, body, cookieStore, null, proxyManager);

        int total = 0;
        List<AllDeptGroupData.Group> groups = new ArrayList<>();
        for (Element deptGroup : Jsoup.parse(body).getElementsByClass("pnl_dept")) {
            List<AllDeptGroupData.DeptData> dept = new ArrayList<>();
            for (Element deptEle : deptGroup.getElementsByAttribute("data-dept")) {
                String deptName = deptEle.text();
                dept.add(new AllDeptGroupData.DeptData(
                        deptEle.attr("data-dept"),
                        deptName.substring(deptName.indexOf(')') + 1)
                ));
                total++;
            }
            String groupName = deptGroup.getElementsByClass("panel-heading").text();
            groups.add(new AllDeptGroupData.Group(groupName, dept));
        }

        return new AllDeptGroupData(groups, total);
    }

    public SearchResult getDeptCourseData(String deptNo, AllDeptData allDeptData, boolean addUrSchoolCache) {
        SearchResult result = new SearchResult();
        DeptToken deptToken = createDeptToken(deptNo, allDeptData, result);
        if (deptToken == null) {
            result.setSuccess(false);
            return result;
        }
        result.setSuccess(getDeptCourseData(deptToken, addUrSchoolCache, result));
        return result;
    }

    public boolean getDeptCourseData(DeptToken deptToken, boolean addUrSchoolCache, SearchResult result) {
        HttpResponseData searchResult = getDeptNCKU(deptToken);
        if (!searchResult.isSuccess()) {
            result.errorFetch("Failed to fetch dept search result: " + deptToken.deptNo);
            return false;
        }
        String searchResultBody = searchResult.data;

        Element table = findCourseTable(searchResultBody, "Dept " + deptToken.deptNo, result);
        if (table == null) {
            return false;
        }

        parseCourseTable(
                table,
                searchResultBody,
                null,
                addUrSchoolCache,
                false,
                result
        );
        return true;
    }

    public DeptToken createDeptToken(String deptNo, AllDeptData allDeptData, SearchResult result) {
        try {
            Connection.Response res = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
                    .header("Connection", "keep-alive")
                    .cookieStore(allDeptData.cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(Main.USER_AGENT)
                    .method(Connection.Method.POST)
                    .requestBody("dept_no=" + deptNo + "&crypt=" + URLEncoder.encode(allDeptData.crypt, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            JsonObject idData = new JsonObject(res.body());
            String error = idData.getString("err");

            if (!error.isEmpty()) {
                result.errorFetch("Create dept token failed: " + error);
                return null;
            }
            String id = idData.getString("id");
            if (id == null || id.isEmpty()) {
                result.errorFetch("Create dept token failed: id not found");
                return null;
            }

            return new DeptToken(
                    deptNo,
                    URLEncoder.encode(id, "UTF-8"),
                    allDeptData.cookieStore
            );
        } catch (JsonException e) {
            logger.errTrace(e);
            result.errorParse("Response Json parse error: " + e.getMessage());
            return null;
        } catch (UnsupportedEncodingException e) {
            logger.errTrace(e);
            return null;
        } catch (IOException e) {
            logger.errTrace(e);
            result.errorFetch("Network error when creating dept token");
            return null;
        }
    }

    private HttpResponseData getDeptNCKU(DeptToken deptToken) {
        Connection request = HttpConnection.connect(deptToken.getUrl())
                .header("Connection", "keep-alive")
                .header("Referer", "https://course.ncku.edu.tw/index.php?c=qry_all")
                .cookieStore(deptToken.cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .timeout(9000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = sendRequestAndCheckRobot(courseNckuOrgUri, request, deptToken.cookieStore);

        if (httpResponseData.isSuccess())
            cosPreCheckCookie(courseNckuOrgUri, httpResponseData.data, deptToken.cookieStore);

        return httpResponseData;
    }

    private SearchResult getQueryCourseData(CourseSearchQuery searchQuery, CookieStore cookieStore) {
        SearchResult result = new SearchResult();
        // Create save query token
        SaveQueryToken saveQueryToken = createSaveQueryToken(searchQuery, cookieStore, result);
        if (saveQueryToken == null) {
            result.setSuccess(false);
            return result;
        }
        result.setSuccess(getQueryCourseData(searchQuery, saveQueryToken, result));
        return result;
    }

    public boolean getQueryCourseData(CourseSearchQuery searchQuery, SaveQueryToken saveQueryToken,
                                      SearchResult result) {
        // Get search result
        HttpResponseData searchResult = getCourseNCKU(saveQueryToken);
        if (!searchResult.isSuccess()) {
            result.errorFetch("Failed to fetch course search result");
            return false;
        }
        String searchResultBody = searchResult.data;

        // Get searchID
        String searchID = getSearchID(searchResultBody, result);
        if (searchID == null) {
            return false;
        }

        // Renew searchID
        if (searchQuery.historySearch()) {
            String PHPSESSID = Cookie.getCookie("PHPSESSID", saveQueryToken.urlOrigin, saveQueryToken.cookieStore);
            if (PHPSESSID == null) {
                result.errorParse("Cookie historySearch 'PHPSESSID' not found");
                return false;
            }
            result.setSearchID(new CourseSearchID(searchID, PHPSESSID));
        } else
            result.setSearchID(new CourseSearchID(searchID));

        Element table = findCourseTable(searchResultBody, "Query", result);
        if (table == null) {
            return false;
        }

        parseCourseTable(
                table,
                searchResultBody,
                searchQuery.serialFilter,
                true,
                searchQuery.historySearch(),
                result
        );

        return true;
    }

    public SaveQueryToken createSaveQueryToken(CourseSearchQuery searchQuery, CookieStore cookieStore, SearchResult result) {
        StringBuilder postData = new StringBuilder();
        URI urlOrigin;
        CookieStore postCookieStore;
        // Build query
        try {
            if (searchQuery.historySearch()) {
                urlOrigin = courseQueryNckuOrgUri;
                postCookieStore = new CookieManager().getCookieStore();

                if (searchQuery.searchID.historySearchID == null) {
//                    logger.log("Renew search id");
//                    Connection request = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=en_query")
//                            .header("Connection", "keep-alive")
//                            .cookieStore(postCookieStore)
//                            .ignoreContentType(true)
//                            .proxy(proxyManager.getProxy());
//                    HttpResponseData httpResponseData = checkRobot(courseQueryNckuOrg, request, postCookieStore);
//                    if (httpResponseData.state != ResponseState.SUCCESS)
//                        return null;
//
//                    cosPreCheck(courseQueryNckuOrg, httpResponseData.data, postCookieStore, response, proxyManager);
//                    String searchID = getSearchID(httpResponseData.data, response);
//                    if (searchID == null)
//                        return null;
//
//                    // Get PHPSESSID cookie
//                    String PHPSESSID = getCourseQueryNckuCookiePHPSESSID(postCookieStore);
//                    if (PHPSESSID == null) {
//                        response.errorFetch(TAG + "Course query PHPSESSID cookie not found");
//                        return null;
//                    }
//                    // Add searchID
//                    postData.append("id=").append(searchID);
//                    searchQuery.histroySearchID = searchID + ',' + PHPSESSID;
                    postData.append("id=");
                } else {
                    postData.append("id=").append(searchQuery.searchID.historySearchID);
                    postCookieStore.add(courseQueryNckuOrgUri, createHttpCookie("PHPSESSID", searchQuery.searchID.historySearchPHPSESSID, courseQueryNcku));
                }

                // Write post data
                postData.append("&syear_b=").append(searchQuery.historySearch.yearBegin);
                postData.append("&syear_e=").append(searchQuery.historySearch.yearEnd);
                postData.append("&sem_b=").append(searchQuery.historySearch.semBegin);
                postData.append("&sem_e=").append(searchQuery.historySearch.semEnd);

                if (searchQuery.courseName != null)
                    postData.append("&cosname=").append(URLEncoder.encode(searchQuery.courseName, "UTF-8"));
                if (searchQuery.instructor != null)
                    postData.append("&teaname=").append(URLEncoder.encode(searchQuery.instructor, "UTF-8"));
                if (searchQuery.deptNo != null) postData.append("&dept_no=").append(searchQuery.deptNo);

//                Connection request = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=en_query")
//                        .header("Connection", "keep-alive")
//                        .cookieStore(postCookieStore)
//                        .ignoreContentType(true)
//                        .proxy(proxyManager.getProxy());
//                HttpResponseData httpResponseData = checkRobot(courseQueryNckuOrg, request, postCookieStore);
//                if (httpResponseData.state != ResponseState.SUCCESS)
//                    return null;
//                cosPreCheck(courseQueryNckuOrg, httpResponseData.data, postCookieStore, response, proxyManager);

                // Required
                String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
                String[] requests = new String[]{
//                        urlOrigin + "/js/modernizr-custom.js?" + date,
//                        urlOrigin + "/js/bootstrap-select/css/bootstrap-select.min.css?" + date,
//                        urlOrigin + "/js/bootstrap-select/js/bootstrap-select.min.js?" + date,
//                        urlOrigin + "/js/jquery.cookie.js?" + date,
                        urlOrigin + "/js/common.js?" + date, // 20230912
//                        urlOrigin + "/js/mis_grid.js?" + date,
//                        urlOrigin + "/js/jquery-ui/jquery-ui.min.css?" + date,
//                        urlOrigin + "/js/jquery-ui/jquery-ui.min.js?" + date,
//                        urlOrigin + "/js/fontawesome/css/solid.min.css?" + date,
//                        urlOrigin + "/js/fontawesome/css/regular.min.css?" + date, // 20230624
//                        urlOrigin + "/js/fontawesome/css/fontawesome.min.css?" + date,
//                        urlOrigin + "/js/epack/css/font-awesome.min.css?" + date,
//                        urlOrigin + "/js/epack/css/elements/list.css?" + date,
//                        urlOrigin + "/js/epack/css/elements/note.css?" + date, // 20230625
//                        urlOrigin + "/js/performance.now-polyfill.js?" + date,
//                        urlOrigin + "/js/mdb-sortable/js/addons/jquery-ui-touch-punch.min.js?" + date,
                        urlOrigin + "/js/jquery.taphold.js?" + date, // 20230625, 20230912
//                        urlOrigin + "/js/jquery.patch.js?" + date,
                };
                for (String url : requests) {
//                    logger.log("get require: " + url.substring(urlOrigin.length()));
                    try {
                        HttpConnection.connect(url)
                                .header("Connection", "keep-alive")
                                .cookieStore(postCookieStore)
                                .ignoreContentType(true)
                                .proxy(proxyManager.getProxy())
                                .userAgent(Main.USER_AGENT)
                                .execute();
                    } catch (IOException e) {
                        logger.errTrace(e);
                    }
                }
            } else {
                urlOrigin = courseNckuOrgUri;
                postCookieStore = cookieStore;
                // Get searchID if it's null
//                if (searchQuery.searchID == null || searchQuery.searchID.searchID == null) {
//                    logger.log("Renew search id");
//
//                    Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
//                            .header("Connection", "keep-alive")
//                            .cookieStore(cookieStore)
//                            .ignoreContentType(true)
//                            .proxy(proxyManager.getProxy());
//                    HttpResponseData httpResponseData = sendRequestAndCheckRobot(courseNckuOrgUri, request, cookieStore);
//                    if (httpResponseData.state != ResponseState.SUCCESS) {
//                        result.errorFetch("Failed to renew search id");
//                        return null;
//                    }
//
//                    cosPreCheck(courseNckuOrg, httpResponseData.data, cookieStore, null, proxyManager);
//                    String searchID = getSearchID(httpResponseData.data, result);
//                    if (searchID == null) {
//                        result.errorFetch("Failed to renew search id");
//                        return null;
//                    }
//                    postData.append("id=").append(URLEncoder.encode(searchID, "UTF-8"));
//                }


                // Write post data
                if (searchQuery.searchID != null && searchQuery.searchID.searchID != null)
                    postData.append(URLEncoder.encode(searchQuery.searchID.searchID, "UTF-8"));
                if (searchQuery.courseName != null)
                    postData.append("&cosname=").append(URLEncoder.encode(searchQuery.courseName, "UTF-8"));
                if (searchQuery.instructor != null)
                    postData.append("&teaname=").append(URLEncoder.encode(searchQuery.instructor, "UTF-8"));
                if (searchQuery.time != null) {
                    postData.append("&wk=").append(searchQuery.time.dayOfWeek + 1);
                    if (searchQuery.time.sectionOfDay != null) {
                        StringBuilder b = new StringBuilder();
                        for (byte i : searchQuery.time.sectionOfDay) {
                            if (b.length() > 0) b.append("%2C");
                            b.append(i + 1);
                        }
                        postData.append("&cl=").append(b);
                    }
                }
                if (searchQuery.deptNo != null) postData.append("&dept_no=").append(searchQuery.deptNo);
                if (searchQuery.grade != null) postData.append("&degree=").append(searchQuery.grade);
            }
        } catch (UnsupportedEncodingException e) {
            logger.errTrace(e);
            return null;
        }

        // Post save query
        Connection request = HttpConnection.connect(urlOrigin + "/index.php?c=qry11215&m=save_qry")
                .header("Connection", "keep-alive")
                .cookieStore(postCookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .method(Connection.Method.POST)
                .requestBody(postData.toString())
                .timeout(9000)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest");
        boolean enQuery;
        String body;
        try {
            body = request.execute().body();
        } catch (IOException e) {
            logger.errTrace(e);
            result.errorFetch("Network error when creating query token");
            return null;
        }

        enQuery = body.startsWith("&m=en_query");
//        if (enQuery)
//            logger.log(urlOrigin + "/index.php?c=qry11215" + body);

        if (body.equals("0")) {
            result.errorParse("Condition not set");
            return null;
        }
        if (body.equals("1")) {
            result.errorParse("Wrong condition format");
            return null;
        }
        if (body.equals("&m=en_query")) {
            result.errorParse("Can not create save query");
            return null;
        }
        if (!enQuery) {
            result.errorFetch("Unknown error");
            return null;
        }
        return new SaveQueryToken(urlOrigin, body, postCookieStore);
    }

    private HttpResponseData getCourseNCKU(SaveQueryToken saveQueryToken) {
//            logger.log(TAG + "Get search result");
        Connection request = HttpConnection.connect(saveQueryToken.getUrl())
                .header("Connection", "keep-alive")
                .cookieStore(saveQueryToken.cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .timeout(9000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = sendRequestAndCheckRobot(saveQueryToken.urlOrigin, request, saveQueryToken.cookieStore);

        if (httpResponseData.state == ResponseState.SUCCESS)
            cosPreCheckCookie(saveQueryToken.urlOrigin, httpResponseData.data, saveQueryToken.cookieStore);

        return httpResponseData;
    }

    private void cosPreCheckCookie(URI originUrl, String pageBody, CookieStore cookieStore) {
        // Get cookie lock
        String PHPSESSID = Cookie.getCookie("PHPSESSID", originUrl, cookieStore);

        // Get cookie lock
        CookieLock cookieLock = cosPreCheckCookieLock.get(PHPSESSID);
        if (cookieLock == null) {
            cookieLock = new CookieLock(new Semaphore(COS_PRE_CHECK_COOKIE_LOCK, true));
            cosPreCheckCookieLock.put(PHPSESSID, cookieLock);
        }
        final CookieLock finalCookieLock = cookieLock;

        // Wait cookie and pool available
        try {
//            if (cookieLock.availablePermits() == 0)
//                logger.log("CosPreCheckCookie waiting");
            finalCookieLock.semaphore.acquire();
            if (cosPreCheckPoolLock.availablePermits() == 0)
                logger.log("CosPreCheck waiting");
            cosPreCheckPoolLock.acquire();
        } catch (InterruptedException e) {
            return;
        }
        // Submit cos pre checkPass
        if (cosPreCheckPool.isShutdown())
            return;
        cosPreCheckPool.submit(() -> {
            try {
                cosPreCheck(originUrl.toString(), pageBody, cookieStore, null, proxyManager);
            } catch (Exception e) {
                logger.errTrace(e);
            } finally {
                // TODO: Find problem
                finalCookieLock.semaphore.release();
//                logger.log(finalCookieLock.semaphore);
                if (finalCookieLock.semaphore.availablePermits() == COS_PRE_CHECK_COOKIE_LOCK)
                    cosPreCheckCookieLock.remove(PHPSESSID);
                cosPreCheckPoolLock.release();
            }
        });
    }

    private Element findCourseTable(String html, String errorPrefix, SearchResult result) {
        int resultTableStart;
        if ((resultTableStart = html.indexOf("<table")) == -1) {
            result.errorParse(errorPrefix + " result table not found");
            return null;
        }
        // get table body
        int resultTableBodyStart, resultTableBodyEnd;
        if ((resultTableBodyStart = html.indexOf("<tbody>", resultTableStart + 7)) == -1 ||
                (resultTableBodyEnd = html.indexOf("</tbody>", resultTableBodyStart + 7)) == -1
        ) {
            result.errorParse(errorPrefix + " result table body not found");
            return null;
        }

        // parse table
//            logger.log(TAG + "Parse course table");
        String resultBody = html.substring(resultTableBodyStart, resultTableBodyEnd + 8);
        return (Element) Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);
    }

    /**
     * @param tbody              Input: Course data table
     * @param searchResultBody   Input: Full document for finding style
     * @param serialNumberFilter Serial number filter
     * @param addUrSchoolCache   True for adding UrSchool cache
     * @param historySearch      Add col offset if search course history
     * @param result             Output: Search result
     */
    private void parseCourseTable(Element tbody, String searchResultBody,
                                  Set<String> serialNumberFilter, boolean addUrSchoolCache, boolean historySearch, SearchResult result) {
        List<Map.Entry<String, Boolean>> styles = new ArrayList<>();
        // Find style section
        int styleStart, styleEnd = 0;
        while ((styleStart = searchResultBody.indexOf("<style", styleEnd)) != -1) {
            styleStart = searchResultBody.indexOf(">", styleStart + 6);
            if (styleStart == -1) continue;
            styleStart += 1;
            styleEnd = searchResultBody.indexOf("</style>", styleStart);
            if (styleEnd == -1) continue;
            // Get style section inner text
            String style = searchResultBody.substring(styleStart, styleEnd);
            styleEnd += 8;

            // Parse style display state
            Matcher matcher = displayRegex.matcher(style);
            while (matcher.find()) {
                if (matcher.groupCount() < 2) continue;
                // Set selector display state
                // key: selector, value: show or not
                styles.add(new AbstractMap.SimpleEntry<>(matcher.group(1), !matcher.group(2).equals("none")));
            }
        }

        // Parse semester
        String allSemester = null;
        if (!historySearch) {
            int start = searchResultBody.indexOf("cookie_name"), end;
            if (start != -1 && (start = searchResultBody.indexOf('\'', start + 11)) != -1
                    && (end = searchResultBody.indexOf('_', start)) != -1) {
                // Skip 0
                for (int i = start + 1; i < end; i++)
                    if (searchResultBody.charAt(i) != '0') {
                        start = i;
                        break;
                    }

                char c = searchResultBody.charAt(end - 1);
                allSemester = searchResultBody.substring(start, end - 1) + ((c == '1') ? '0' : '1');
            }
        }

        Set<String> urSchoolCache = addUrSchoolCache ? new HashSet<>() : null;
        int sectionOffset = historySearch ? 1 : 0;

        // get course list
        Elements courseList = tbody.getElementsByTag("tr");
        for (Element element : courseList) {
            Elements section = element.getElementsByTag("td");

            // Parse semester if history search
            String courseData_semester = null;
            if (historySearch) {
                String semester = section.get(0).ownText().trim();
                int split = semester.indexOf('-');
                if (split != -1)
                    courseData_semester = semester.substring(0, split) +
                            (semester.charAt(semester.length() - 1) == '1' ? '0' : '1');
            } else
                courseData_semester = allSemester;

            // get serial number
            List<Node> section1 = section.get(sectionOffset + 1).childNodes();
            String serialNumber = ((Element) section1.get(0)).ownText().trim();
            if (serialNumberFilter != null) {
                String serialNumberStr = serialNumber.substring(serialNumber.indexOf('-') + 1);
                // Skip if we don't want
                if (!serialNumberFilter.contains(serialNumberStr))
                    continue;
            }
            // Get serial number
            String courseData_serialNumber = serialNumber.isEmpty() ? null : serialNumber;

            // Get system number
            String courseData_courseSystemNumber = ((TextNode) section1.get(1)).text().trim();

            // Get attribute code
            String courseData_courseAttributeCode = null;
            String courseAttributeCode = ((TextNode) section1.get(3)).text().trim();
            if (!courseAttributeCode.isEmpty()) {
                int courseAttributeCodeStart, courseAttributeCodeEnd;
                if ((courseAttributeCodeStart = courseAttributeCode.indexOf('[')) != -1 &&
                        (courseAttributeCodeEnd = courseAttributeCode.lastIndexOf(']')) != -1)
                    courseData_courseAttributeCode = courseAttributeCode.substring(courseAttributeCodeStart + 1, courseAttributeCodeEnd);
                else
                    courseData_courseAttributeCode = courseAttributeCode;
            } else
                logger.log(serialNumber + "AttributeCode not found");

            // Get department name
            String courseData_departmentName = section.get(sectionOffset).ownText();
            if (courseData_departmentName.isEmpty()) courseData_departmentName = null;

            // Get course type
            String courseData_category = section.get(sectionOffset + 3).text();

            // Get forGrade & classInfo & group
            Integer courseData_forGrade = null;
            String courseData_forClass = null;
            String courseData_group = null;
            List<Node> section2 = section.get(sectionOffset + 2).childNodes();
            int section2c = 0;
            for (Node node : section2) {
                if (!(node instanceof TextNode))
                    // <br>
                    section2c++;
                else {
                    // text
                    String cache = ((TextNode) node).text().trim();
                    if (cache.isEmpty()) continue;
                    if (section2c == 0)
                        courseData_forGrade = Integer.parseInt(cache);
                    else if (section2c == 1)
                        courseData_forClass = cache.replace("　", "");
                    else
                        courseData_group = cache;
                }
            }

            // Get course name
            String courseData_courseName = section.get(sectionOffset + 4).getElementsByClass("course_name").get(0).text();

            // Get course note
            String courseData_courseNote = section.get(sectionOffset + 4).ownText().trim().replace("\"", "\\\"");
            if (courseData_courseNote.isEmpty()) courseData_courseNote = null;

            // Get course limit
            String courseData_courseLimit = section.get(sectionOffset + 4).getElementsByClass("cond").text().trim().replace("\"", "\\\"");
            if (courseData_courseLimit.isEmpty()) courseData_courseLimit = null;

            // Get course tags
            CourseData.TagData[] courseData_tags = null;
            Elements tagElements = section.get(sectionOffset + 4).getElementsByClass("label");
            if (!tagElements.isEmpty()) {
                courseData_tags = new CourseData.TagData[tagElements.size()];
                for (int i = 0; i < courseData_tags.length; i++) {
                    Element tagElement = tagElements.get(i);
                    // Get tag Color
                    String tagColor;
                    String styleOverride = tagElement.attr("style");
                    if (!styleOverride.isEmpty()) {
                        int backgroundColorStart, backgroundColorEnd;
                        if ((backgroundColorStart = styleOverride.indexOf("background-color:")) != -1 &&
                                (backgroundColorEnd = styleOverride.indexOf(';', backgroundColorStart + 17)) != -1)
                            tagColor = styleOverride.substring(backgroundColorStart + 17, backgroundColorEnd).trim();
                        else
                            tagColor = "0";
                    } else {
                        String tagType = null;
                        for (String j : tagElement.classNames())
                            if (j.length() > 6 && j.startsWith("label")) {
                                tagType = j.substring(6);
                                break;
                            }
                        tagColor = String.valueOf(tagColormap.get(tagType));
                        if (tagColor == null) {
                            logger.warn("Unknown tag color: " + tagType);
                            tagColor = "0";
                        }
                    }

                    Element j = tagElement.firstElementChild();
                    String link = j == null ? null : j.attr("href");

                    courseData_tags[i] = new CourseData.TagData(tagElement.text(), tagColor, link);
                }
            }

            // get credits & required
            Float courseData_credits = null;
            Boolean courseData_required = null;
            List<TextNode> section5 = section.get(sectionOffset + 5).textNodes();
            String section5Str;
            if (!section5.isEmpty() && !(section5Str = section5.get(0).text().trim()).isEmpty()) {
                courseData_credits = Float.parseFloat(section5Str);
                String cache = section5.get(1).text().trim();
                courseData_required = cache.equals("必修") || cache.equals("Required");
            }

            // Get instructor name
            String[] courseData_instructors = null;
            String instructors = section.get(sectionOffset + 6).text();
            if (!instructors.isEmpty() && !instructors.equals("未定")) {
                instructors = instructors.replace("*", "");
                courseData_instructors = simpleSplit(instructors, ' ');
                // Add urSchool cache
                if (addUrSchoolCache && urSchool != null)
                    urSchoolCache.addAll(Arrays.asList(courseData_instructors));
            }

            // Get time list
            CourseData.TimeData[] courseData_timeList = null;
            List<CourseData.TimeData> timeDataList = new ArrayList<>();
            Byte timeCacheDayOfWeek = null, timeCacheSection = null, timeCacheSectionTo = null;
            String timeCacheMapLocation = null, timeCacheMapRoomNo = null, timeCacheMapRoomName = null;
            int timeParseState = 0;
            for (Node node : section.get(sectionOffset + 8).childNodes()) {
                // Parse state 0, find day of week and section
                if (timeParseState == 0) {
                    timeParseState++;
                    String text;
                    // Check if data exist
                    if (node instanceof TextNode && !(text = ((TextNode) node).text().trim()).isEmpty()) {
                        // Get dayOfWeek, format: [1]2~3
                        if (text.length() > 2 && text.charAt(2) == ']') {
                            timeCacheDayOfWeek = (byte) (text.charAt(1) - '1');
                            // Get section
                            if (text.length() > 3) {
                                timeCacheSection = sectionCharToByte(text.charAt(3));
                                // Get section end
                                if (text.length() > 5 && text.charAt(4) == '~') {
                                    timeCacheSectionTo = sectionCharToByte(text.charAt(5));
                                }
                            }
                            continue;
                        }
                    }
                }
                // Parse state 1, classroom data
                if (timeParseState == 1) {
                    timeParseState++;
                    // Location link, format: javascript:maps('12','789AB');
                    String attribute;
                    if (!(attribute = node.attr("href")).isEmpty()) {
                        // Get location
                        int locStart, locEnd;
                        if ((locStart = attribute.indexOf('\'')) != -1 &&
                                (locEnd = attribute.indexOf('\'', locStart + 1)) != -1) {
                            timeCacheMapLocation = attribute.substring(locStart + 1, locEnd);
                            int roomNoStart, roomNoEnd;
                            if ((roomNoStart = attribute.indexOf('\'', locEnd + 1)) != -1 &&
                                    (roomNoEnd = attribute.indexOf('\'', roomNoStart + 1)) != -1)
                                timeCacheMapRoomNo = attribute.substring(roomNoStart + 1, roomNoEnd);
                        }
                        if (node instanceof Element)
                            timeCacheMapRoomName = ((Element) node).ownText().trim();
                        continue;
                    }
                }
                // Parse state 2, save time data and reset
                if (timeParseState == 2 && node instanceof Element) {
                    boolean detailedTime = ((Element) node).tagName().equals("div");
                    // Save time data
                    if (timeCacheDayOfWeek != null || timeCacheSection != null || timeCacheSectionTo != null ||
                            timeCacheMapLocation != null || timeCacheMapRoomNo != null || timeCacheMapRoomName != null)
                        timeDataList.add(new CourseData.TimeData(timeCacheDayOfWeek, timeCacheSection, timeCacheSectionTo,
                                timeCacheMapLocation, timeCacheMapRoomNo, timeCacheMapRoomName));
                    // Add detailed time data
                    if (detailedTime)
                        timeDataList.add(new CourseData.TimeData(node.attr("data-mkey")));
                    // Reset state
                    timeCacheDayOfWeek = null;
                    timeCacheSection = null;
                    timeCacheSectionTo = null;
                    timeCacheMapLocation = null;
                    timeCacheMapRoomNo = null;
                    timeCacheMapRoomName = null;
                    timeParseState = 0;
                }
            }
            // Add last time data
            if (timeCacheDayOfWeek != null || timeCacheSection != null || timeCacheSectionTo != null ||
                    timeCacheMapLocation != null || timeCacheMapRoomNo != null || timeCacheMapRoomName != null)
                timeDataList.add(new CourseData.TimeData(timeCacheDayOfWeek, timeCacheSection, timeCacheSectionTo,
                        timeCacheMapLocation, timeCacheMapRoomNo, timeCacheMapRoomName));
            if (!timeDataList.isEmpty())
                courseData_timeList = timeDataList.toArray(new CourseData.TimeData[0]);

            // Get selected & available
            String[] count;
            StringBuilder countBuilder = new StringBuilder();
            for (Node n : section.get(sectionOffset + 7).childNodes()) {
                // Check style
                if (n instanceof Element) {
                    String nc = ((Element) n).className();
                    boolean display = true;
                    for (Map.Entry<String, Boolean> style : styles) {
                        if (nc.equals(style.getKey()))
                            display = style.getValue();
                    }
                    if (display)
                        countBuilder.append(((Element) n).text());
                } else if (n instanceof TextNode)
                    countBuilder.append(((TextNode) n).text());
            }
            count = countBuilder.toString().split("/");
            Integer courseData_selected = count[0].isEmpty() ? null : Integer.parseInt(count[0]);
            Integer courseData_available = count.length < 2 ? null :
                    (count[1].equals("額滿") || count[1].equals("full")) ? 0 :
                            (count[1].equals("不限") || count[1].equals("unlimited")) ? -1 :
                                    (count[1].startsWith("洽") || count[1].startsWith("please connect")) ? -2 :
                                            Integer.parseInt(count[1]);

            // Get moodle, outline
            String courseData_moodle = null;
            Elements linkEle = section.get(sectionOffset + 9).getElementsByAttribute("href");
            if (!linkEle.isEmpty()) {
                for (Element ele : linkEle) {
                    String href = ele.attr("href");
                    if (href.startsWith("javascript"))
                        // moodle link
                        courseData_moodle = href.substring(19, href.length() - 3).replace("','", ",");
                }
            }

            // Get function buttons
            String courseData_btnPreferenceEnter = null;
            String courseData_btnAddCourse = null;
            String courseData_btnPreRegister = null;
            String courseData_btnAddRequest = null;
            for (int i = sectionOffset + 10; i < section.size(); i++) {
                Element button = section.get(i).firstElementChild();
                if (button == null) continue;
                String buttonText = button.ownText().replace(" ", "");
                switch (buttonText) {
                    case "PreferenceEnter":
                    case "志願登記":
                        courseData_btnPreferenceEnter = button.attr("data-key");
                        break;
                    case "AddCourse":
                    case "單科加選":
                        courseData_btnAddCourse = button.attr("data-key");
                        break;
                    case "Pre-register":
                    case "加入預排":
                        courseData_btnPreRegister = button.attr("data-prekey");
                        break;
                    case "AddRequest":
                    case "加入徵詢":
                        courseData_btnAddRequest = button.attr("data-prekey");
                        break;
                }
            }

            CourseData courseData = new CourseData(courseData_semester,
                    courseData_departmentName,
                    courseData_serialNumber, courseData_courseAttributeCode, courseData_courseSystemNumber,
                    courseData_forGrade, courseData_forClass, courseData_group,
                    courseData_category,
                    courseData_courseName, courseData_courseNote, courseData_courseLimit, courseData_tags,
                    courseData_credits, courseData_required,
                    courseData_instructors,
                    courseData_selected, courseData_available,
                    courseData_timeList,
                    courseData_moodle,
                    courseData_btnPreferenceEnter, courseData_btnAddCourse, courseData_btnPreRegister, courseData_btnAddRequest);
            result.courseDataList.add(courseData);
        }

        // Add urSchool cache
        if (addUrSchoolCache && urSchool != null) {
            // Flush to add urSchool cache
            if (!urSchoolCache.isEmpty())
                urSchool.addInstructorCache(urSchoolCache.toArray(new String[0]));
        }
    }

    private String getSearchID(String body, SearchResult result) {
        // get entry function
        int searchFunctionStart = body.indexOf("function setdata()");
        if (searchFunctionStart == -1) {
            result.errorParse("Search id function not found");
            return null;
        } else searchFunctionStart += 18;

        int idStart, idEnd;
        if ((idStart = body.indexOf("'id'", searchFunctionStart)) == -1 ||
                (idStart = body.indexOf('\'', idStart + 4)) == -1 ||
                (idEnd = body.indexOf('\'', idStart + 1)) == -1
        ) {
            result.errorParse("Search id not found");
            return null;
        }
        return body.substring(idStart + 1, idEnd);
    }

    public HttpResponseData sendRequestAndCheckRobot(URI urlOriginUri, Connection request, CookieStore cookieStore) {
        boolean networkError = false;
        for (int i = 0; i < MAX_ROBOT_CHECK_REQUEST_TRY; i++) {
            String response;
            try {
                response = request.execute().body();
                networkError = false;
            } catch (UncheckedIOException | IOException e) {
                networkError = true;
                // Last try failed
                if (i + 1 == MAX_ROBOT_CHECK_REQUEST_TRY)
                    logger.errTrace(e);
                else
                    logger.warn("Fetch page failed(" + (i + 1) + "): " + e.getMessage() + ", Retry...");
                continue;
            }

            // Check if no robot
            int codeTicketStart, codeTicketEnd;
            if ((codeTicketStart = response.indexOf("index.php?c=portal&m=robot")) == -1 ||
                    (codeTicketStart = response.indexOf("code_ticket=", codeTicketStart)) == -1 ||
                    (codeTicketEnd = response.indexOf("&", codeTicketStart)) == -1
            ) {
                return new HttpResponseData(ResponseState.SUCCESS, response);
            }
            String codeTicket = response.substring(codeTicketStart + 12, codeTicketEnd);

            // Crack robot
            logger.warn("Crack robot code");
            for (int j = 0; j < MAX_ROBOT_CHECK_TRY; j++) {
                String code = robotCode.getCode(urlOriginUri + "/index.php?c=portal&m=robot", cookieStore, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA);
                if (code == null || code.isEmpty())
                    continue;
                try {
                    String result = HttpConnection.connect(urlOriginUri + "/index.php?c=portal&m=robot")
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .method(Connection.Method.POST)
                            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .requestBody("time=" + (System.currentTimeMillis() / 1000) +
                                    "&code_ticket=" + URLEncoder.encode(codeTicket, "UTF-8") +
                                    "&code=" + code)
                            .execute().body();
//                    logger.log(result);
                    boolean success = new JsonObject(result).getBoolean("status");
                    logger.warn("Crack code: " + code + ", " + (success ? "success" : "retry"));
                    if (success)
                        break;
                } catch (JsonException e) {
                    logger.errTrace(e);
                } catch (IOException e) {
                    logger.errTrace(e);
                    networkError = true;
                }
            }
        }
        if (networkError)
            return new HttpResponseData(ResponseState.NETWORK_ERROR);
        else
            return new HttpResponseData(ResponseState.ROBOT_CODE_CRACK_ERROR);
    }
}
