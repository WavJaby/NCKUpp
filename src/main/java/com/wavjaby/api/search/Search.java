package com.wavjaby.api.search;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.AllDept;
import com.wavjaby.api.UrSchool;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.Progressbar;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wavjaby.Main.*;
import static com.wavjaby.api.IP.getClientIP;
import static com.wavjaby.lib.ApiThrottle.*;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class Search implements Module {
    private static final String TAG = "Search";
    private static final Logger logger = new Logger(TAG);
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
    private final AllDept allDept;
    private final RobotCheck robotCheck;
    private final ProxyManager proxyManager;

    private static class CookieLock {
        final Semaphore semaphore;
//        final ReentrantLock lock;

        public CookieLock(Semaphore semaphore) {
            this.semaphore = semaphore;
//            this.lock = new ReentrantLock(true);
        }
    }

    public Search(UrSchool urSchool, AllDept allDept, RobotCheck robotCheck, ProxyManager proxyManager) {
        this.urSchool = urSchool;
        this.allDept = allDept;
        this.robotCheck = robotCheck;
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
        ClientSearchQuery clientSearchQuery = ClientSearchQuery.fromRequest(query, cookieIn, response);
        if (clientSearchQuery == null)
            return response;
        else {
            // Check throttle
            if (!checkIpThrottle(ip)) {
//                logger.log(getClientIP(req) + " Throttle");
                response.errorTooManyRequests();
                return response;
            }

            SearchResult searchResult = querySearch(clientSearchQuery, cookieStore);
            searchResult.passDataTo(response);

            // set cookie
            packCourseLoginStateCookie(req, loginState, cookieStore);
            if (response.isSuccess()) {
                if (searchResult.getSearchID() != null)
                    addCookieToHeader("searchID", searchResult.getSearchID(), "/", req);
            } else
                addRemoveCookieToHeader("searchID", "/", req);
        }

        int used = doneIpThrottle(ip);
        if (used < FETCH_PER_MIN) {
            logger.log(ip + ' ' + used + ' ' + response.isSuccess() + ' ' + (System.currentTimeMillis() - startTime) + "ms");
        }

        return response;
    }

    public SearchResult querySearch(ClientSearchQuery clientSearchQuery, CookieStore cookieStore) {
        // TODO: Support history when get all and serial
        boolean success;
        // get all course
        if (clientSearchQuery.getAll()) {
            SearchResult result = new SearchResult();
            AllDeptData allDeptData = allDept.getAllDeptData(cookieStore, result);
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
                            fragments[finalI] = allDept.getAllDeptData(createCookieStore(), allDeptResult);
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
                            SearchResult deptCourseData = getDeptCourseData(dept, fragment);
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
        else if (clientSearchQuery.getSerial() || clientSearchQuery.multipleTime()) {
            SearchResult result = new SearchResult();
            List<CourseData> courseDataList = new ArrayList<>();
            ThreadPoolExecutor fetchPool = (ThreadPoolExecutor)
                    Executors.newFixedThreadPool(MULTI_QUERY_SEARCH_THREAD_COUNT, new ThreadFactory(TAG + "-Multi"));
            Semaphore fetchPoolLock = new Semaphore(MULTI_QUERY_SEARCH_THREAD_COUNT, true);
            AtomicBoolean allSuccess = new AtomicBoolean(true);
            CourseSearchQuery[] courseSearchQuery = clientSearchQuery.getSerial()
                    ? clientSearchQuery.toCourseQueriesSerial()
                    : clientSearchQuery.toCourseQueriesMultiTime();
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
            return getQueryCourseData(clientSearchQuery.toCourseQuery(), cookieStore);
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
            public final String name;
            public final List<DeptData> dept;

            public Group(String name, List<DeptData> dept) {
                this.name = name;
                this.dept = dept;
            }
        }

        public static class DeptData {
            public final String id, name;

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

        public List<Group> getGroups() {
            return deptGroup;
        }
    }

    public static class SaveQueryToken {
        private final URI urlOrigin;
        private final String url;
        private final CookieStore cookieStore;

        public SaveQueryToken(URI urlOrigin, String url, CookieStore cookieStore) {
            this.urlOrigin = urlOrigin;
            this.url = url;
            this.cookieStore = cookieStore;
        }

        public String getUrl() {
            return url;
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
        private String searchID;
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

        public void setSearchID(String searchID) {
            this.searchID = searchID;
        }

        public String getSearchID() {
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
                    .userAgent(USER_AGENT)
                    .execute();
        } catch (IOException e) {
            logger.errTrace(e);
            byte[] array = new byte[16];
            new Random().nextBytes(array);
            addCookie("PHPSESSID", Base64.getEncoder().encodeToString(array), courseNckuOrgUri, cookieStore);
        }

//        logger.log(cookieStore.getCookies().toString());
        return cookieStore;
    }

    public SearchResult getDeptCourseData(String deptNo, AllDeptData allDeptData) {
        SearchResult result = new SearchResult();
        DeptToken deptToken = createDeptToken(deptNo, allDeptData, result);
        if (deptToken == null) {
            result.setSuccess(false);
            return result;
        }
        getDeptCourseData(deptToken, result);
        return result;
    }

    public void getDeptCourseData(DeptToken deptToken, SearchResult result) {
        HttpResponseData searchResult = getDeptNCKU(deptToken);
        if (!searchResult.isSuccess()) {
            result.errorFetch("Failed to fetch dept search result: " + deptToken.deptNo);
            return;
        }
        parseCourseTable(
                searchResult.data,
                null,
                false,
                result
        );
    }

    public DeptToken createDeptToken(String deptNo, AllDeptData allDeptData, SearchResult result) {
        try {
            Connection.Response res = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
                    .header("Connection", "keep-alive")
                    .cookieStore(allDeptData.cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(USER_AGENT)
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
                .userAgent(USER_AGENT)
                .timeout(9000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = robotCheck.sendRequest(courseNckuOrg, request, deptToken.cookieStore);

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
        getQueryCourseData(searchQuery, saveQueryToken, result);
        return result;
    }

    public void getQueryCourseData(CourseSearchQuery searchQuery, SaveQueryToken saveQueryToken, SearchResult result) {
        // Get search result
        HttpResponseData searchResult = getCourseNCKU(saveQueryToken);
        if (!searchResult.isSuccess()) {
            result.errorFetch("Failed to fetch course search result");
            return;
        }
        String searchResultBody = searchResult.data;

        // Get searchID
        String searchID = getSearchID(searchResultBody, result);
        if (searchID == null) {
            return;
        }

        result.setSearchID(searchID);

        parseCourseTable(
                searchResultBody,
                searchQuery.serialFilter,
                searchQuery.historySearch(),
                result
        );

        // Add UrSchool cache
        if (result.success && urSchool != null) {
            Set<String> urSchoolCache = new HashSet<>();
            for (CourseData courseData : result.courseDataList) {
                if (courseData.instructors == null) continue;
                for (String instructor : courseData.instructors) {
                    urSchoolCache.add(instructor.replace("*", ""));
                }
            }

            // Flush to add urSchool cache
            if (!urSchoolCache.isEmpty())
                urSchool.addInstructorCache(urSchoolCache.toArray(new String[0]));
        }
    }

    public SaveQueryToken createSaveQueryToken(CourseSearchQuery searchQuery, CookieStore cookieStore, SearchResult result) {
        StringBuilder postData = new StringBuilder();
        String baseUrl = courseNckuOrg;
        CookieStore postCookieStore;
        // Build query
        try {
            postCookieStore = cookieStore;
            // Get searchID if it's null
//                if (searchQuery.searchID == null) {
//                    logger.log("Renew search id");
//
//                    Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
//                            .header("Connection", "keep-alive")
//                            .cookieStore(cookieStore)
//                            .ignoreContentType(true)
//                            .proxy(proxyManager.getProxy());
//                    HttpResponseData httpResponseData = robotCheck.sendRequest(courseNckuOrgUri, request, cookieStore);
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

//            // Maybe temporary
//            Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
//                    .header("Connection", "keep-alive")
//                    .cookieStore(cookieStore)
//                    .ignoreContentType(true)
//                    .userAgent(USER_AGENT)
//                    .proxy(proxyManager.getProxy());
//            HttpResponseData httpResponseData = robotCheck.sendRequest(courseNckuOrg, request, cookieStore);
//            if (httpResponseData.state != ResponseState.SUCCESS) {
//                result.errorFetch("Failed to renew search id");
//                return null;
//            }
//            String resultHtml = processIframe(httpResponseData.data, cookieStore, proxyManager, robotCheck);
//            if (resultHtml == null) {
//                result.errorFetch("Failed to get renew cookie");
//                return null;
//            }
//            baseUrl = findStringBetween(resultHtml, "<base", "href=\"", "\"");
//            if (baseUrl == null) {
//                result.errorFetch("Base url not found");
//                return null;
//            }
//            cosPreCheck(baseUrl, resultHtml, cookieStore, null, proxyManager);
//            baseUrl = courseNckuOrg;

            // Write post data
            if (searchQuery.searchID != null)
                postData.append("&id=").append(URLEncoder.encode(searchQuery.searchID, "UTF-8"));
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
        } catch (UnsupportedEncodingException e) {
            logger.errTrace(e);
            return null;
        }


        // Post save query
        Connection request = HttpConnection.connect(baseUrl + "/index.php?c=qry11215&m=save_qry")
                .header("Connection", "keep-alive")
                .cookieStore(postCookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(USER_AGENT)
                .method(Connection.Method.POST)
                .requestBody(postData.substring(1))
                .timeout(9000)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest");
        String body;
        try {
            body = request.execute().body();
        } catch (IOException e) {
            logger.errTrace(e);
            result.errorFetch("Network error when creating query token");
            return null;
        }


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
        if (body.contains("id=\"loader\"")) {
            String url = findStringBetween(body, "id=\"loader\"", "src=\"", "\"");
            try {
                return new SaveQueryToken(new URI(baseUrl), url, postCookieStore);
            } catch (URISyntaxException e) {
                logger.errTrace(e);
                result.errorParse("Can not parse target url");
                return null;
            }
        }
        if (!body.startsWith("&m=en_query")) {
            result.errorFetch("Unknown error");
            return null;
        }
        try {
            return new SaveQueryToken(new URI(baseUrl), baseUrl + "/index.php?c=qry11215" + body, postCookieStore);
        } catch (URISyntaxException e) {
            logger.errTrace(e);
            result.errorParse("Can not parse base url");
            return null;
        }
    }

    private HttpResponseData getCourseNCKU(SaveQueryToken saveQueryToken) {
//            logger.log(TAG + "Get search result");
        Connection request = HttpConnection.connect(saveQueryToken.getUrl())
                .header("Connection", "keep-alive")
                .cookieStore(saveQueryToken.cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(USER_AGENT)
                .timeout(9000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = robotCheck.sendRequest(saveQueryToken.urlOrigin.toString(), request, saveQueryToken.cookieStore);

        if (httpResponseData.isSuccess())
            cosPreCheckCookie(saveQueryToken.urlOrigin, httpResponseData.data, saveQueryToken.cookieStore);

        return httpResponseData;
    }

    private void cosPreCheckCookie(URI originUrl, String pageBody, CookieStore cookieStore) {
        // Get cookie lock
        String PHPSESSID = getCookie("PHPSESSID", originUrl, cookieStore);

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

    private static Element findCourseTable(String html, boolean smallTable) {
        String resultBody = smallTable
                ? findStringBetween(html, "<div class=\"visible-sm\"", "<tbody>", "</tbody>", true)
                : findStringBetween(html, "<div class=\"hidden-xs hidden-sm\"", "<tbody>", "</tbody>", true);
        if (resultBody == null)
            return null;
        return (Element) Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);
    }

    /**
     * @param searchResultBody   Input: Full document for finding style
     * @param serialNumberFilter Serial number filter
     * @param historySearch      Add col offset if search course history
     * @param result             Output: Search result
     */
    public static void parseCourseTable(String searchResultBody,
                                        Set<String> serialNumberFilter, boolean historySearch, SearchResult result) {

        Element tbody = findCourseTable(searchResultBody, false);
        if (tbody == null) {
            result.errorParse("Query result table not found");
            return;
        }
        Element smallTbody = null;

        List<Map.Entry<String, Boolean>> styles = getStylesheet(searchResultBody);

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

        int sectionOffset = historySearch ? 1 : 0;
        // get course list
        Elements courseList = tbody.getElementsByTag("tr");
        for (int courseIndex = 0; courseIndex < courseList.size(); courseIndex++) {
            Element element = courseList.get(courseIndex);
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

            List<Node> section1 = section.get(sectionOffset + 1).childNodes();
            // Get serial number
            String serialNumberRaw = ((Element) section1.get(0)).ownText().trim();
            Integer courseData_serialNumber = null;
            if (!serialNumberRaw.isEmpty()) {
                int start = serialNumberRaw.indexOf('-') + 1;
                for (int i = start; i < serialNumberRaw.length(); i++) {
                    if (serialNumberRaw.charAt(i) != '0') {
                        start = i;
                        break;
                    }
                }
                try {
                    courseData_serialNumber = Integer.parseInt(serialNumberRaw.substring(start));
                } catch (NumberFormatException e) {
                    logger.err("SerialNumber parse error, " + e.getMessage());
                }
            }
            if (serialNumberFilter != null) {
                // Skip if we don't want
                if (courseData_serialNumber == null ||
                        !serialNumberFilter.contains(leftPad(courseData_serialNumber.toString(), 3, '0')))
                    continue;
            }

            // Get system number
            String courseData_systemCode = ((TextNode) section1.get(section1.size() - 3)).text().trim();
            if (courseData_systemCode.isEmpty()) courseData_systemCode = null;

            // Get attribute code
            String courseData_attributeCode = ((TextNode) section1.get(section1.size() - 1)).text().trim();
            if (!courseData_attributeCode.isEmpty()) {
                int start, end;
                if ((start = courseData_attributeCode.indexOf('[')) != -1 &&
                        (end = courseData_attributeCode.lastIndexOf(']')) != -1)
                    courseData_attributeCode = courseData_attributeCode.substring(start + 1, end);
            } else courseData_attributeCode = null;

            // Get department id
            String departmentName = section.get(sectionOffset).ownText();
            String courseData_departmentId = null;
            if (serialNumberRaw.length() > 2)
                courseData_departmentId = serialNumberRaw.substring(0, 2);
            else if (!departmentName.isEmpty())
                courseData_departmentId = AllDept.deptIdMap.get(departmentName);
            else {
                // Use department name from small table
                if (smallTbody == null)
                    smallTbody = findCourseTable(searchResultBody, true);
                if (smallTbody == null) {
                    result.errorParse("Failed to get small tbody");
                    return;
                }
                Element detailRoot = smallTbody.child(courseIndex * 4 + 3).firstElementChild();
                Elements detailElement;
                if (detailRoot != null && (detailElement = detailRoot.getElementsByTag("tr")).size() > 1) {
                    Element departmentNameElement = detailElement.get(1).firstElementChild();
                    if (departmentNameElement != null) {
                        departmentName = departmentNameElement.ownText();
                        courseData_departmentId = AllDept.deptIdMap.get(departmentName);
                    }
                }
            }
            if (courseData_departmentId == null) {
                if (courseData_systemCode != null && courseData_systemCode.length() > 2) {
                    logger.warn("Failed get dept id: '" + departmentName + "', " + courseData_systemCode);
                    courseData_departmentId = courseData_systemCode.substring(0, 2);
                } else {
                    result.errorParse("Failed get dept id: '" + departmentName + "'");
                    courseData_departmentId = "";
                }
            }


            // Get course type
            String courseData_category = section.get(sectionOffset + 3).text();

            // Get forGrade & forClass & group
            Integer courseData_forGrade = null;
            String courseData_forClass = null;
            String courseData_forClassGroup = null;
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
                        courseData_forClassGroup = cache;
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
            List<Node> instructorNodes = section.get(sectionOffset + 6).childNodes();
            if (!instructorNodes.isEmpty() &&
                    (instructorNodes.size() > 1 || !(instructorNodes.get(0) instanceof TextNode) || !"未定".equals(((TextNode) instructorNodes.get(0)).text()))) {
                List<String> instructors = new ArrayList<>();
                for (Node node : instructorNodes) {
                    if (node instanceof TextNode) {
                        String instructorName = ((TextNode) node).text().trim();
                        // Main instructor
                        if (instructorName.endsWith("*"))
                            instructors.add(0, instructorName.substring(0, instructorName.length() - 1));
                        else
                            instructors.add(instructorName);
                    }
                }
                if (!instructors.isEmpty())
                    courseData_instructors = instructors.toArray(new String[0]);
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
                                // Missing section start
                                if (text.charAt(3) == '~') {
                                    logger.err("Course " + courseData_semester + " " + courseData_serialNumber + " " + courseData_courseName +
                                            " missing time section start: '" + text + "', using section end");
                                    if (text.length() > 4) {
                                        text = text.substring(0, 3) + text.charAt(4) + "~" + text.charAt(4);
                                    } else
                                        text = text.substring(0, 3);
                                }

                                if (text.length() > 3)
                                    timeCacheSection = sectionCharToByte(text.charAt(3));
                                // Get section end
                                if (text.length() > 5) {
                                    timeCacheSectionTo = sectionCharToByte(text.charAt(5));
                                    if (text.length() > 6 || text.charAt(4) != '~')
                                        logger.err("Course " + courseData_semester + " " + courseData_serialNumber + " " + courseData_courseName +
                                                " wrong format: '" + text + "'");
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
            String[] count = getAvailableCount(section.get(sectionOffset + 7).childNodes(), styles);
            Integer courseData_selected = count[0].isEmpty() ? null : Integer.parseInt(count[0]);
            Integer courseData_available;
            if (count.length < 2) courseData_available = null;
            else switch (count[1]) {
                case "額滿":
                case "full":
                    courseData_available = 0;
                    break;
                case "不限":
                case "unlimited":
                    courseData_available = -1;
                    break;
                default:
                    courseData_available = (count[1].startsWith("洽") || count[1].startsWith("please connect"))
                            ? -2 : Integer.parseInt(count[1]);
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
                    courseData_departmentId,
                    courseData_serialNumber, courseData_systemCode, courseData_attributeCode,
                    courseData_forGrade, courseData_forClass, courseData_forClassGroup,
                    courseData_category,
                    courseData_courseName, courseData_courseNote, courseData_courseLimit, courseData_tags,
                    courseData_credits, courseData_required,
                    courseData_instructors,
                    courseData_selected, courseData_available,
                    courseData_timeList,
                    courseData_btnPreferenceEnter, courseData_btnAddCourse, courseData_btnPreRegister, courseData_btnAddRequest);
            result.courseDataList.add(courseData);
        }
    }

    private static List<Map.Entry<String, Boolean>> getStylesheet(String body) {
        List<Map.Entry<String, Boolean>> styles = new ArrayList<>();
        // Find style section
        int styleStart, styleEnd = 0;
        while ((styleStart = body.indexOf("<style", styleEnd)) != -1) {
            styleStart = body.indexOf(">", styleStart + 6);
            if (styleStart == -1) continue;
            styleStart += 1;
            styleEnd = body.indexOf("</style>", styleStart);
            if (styleEnd == -1) continue;
            // Get style section inner text
            String style = body.substring(styleStart, styleEnd);
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
        return styles;
    }

    private static String[] getAvailableCount(List<Node> nodes, List<Map.Entry<String, Boolean>> styles) {
        StringBuilder countBuilder = new StringBuilder();
        for (Node n : nodes) {
            // Check style
            if (n instanceof Element) {
                String className = ((Element) n).className();
                boolean display = true;
                for (Map.Entry<String, Boolean> style : styles) {
                    if (className.equals(style.getKey()))
                        display = style.getValue();
                }
                if (display)
                    countBuilder.append(((Element) n).text());
            } else if (n instanceof TextNode)
                countBuilder.append(((TextNode) n).text());
        }
        return countBuilder.toString().split("/");
    }

    public static String getSearchID(String body, SearchResult result) {
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
}
