package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.Module;
import com.wavjaby.SimpleThreadFactory;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.ProgressBar;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.courseQueryNckuOrg;

public class Search implements Module {
    private static final String TAG = "[Search] ";
    private final ExecutorService cosPreCheckPool = Executors.newFixedThreadPool(4);
    private final Semaphore cosPreCheckPoolLock = new Semaphore(4);
    private static final Pattern displayRegex = Pattern.compile("[\\r\\n]+\\.(\\w+) *\\{[\\r\\n]* *(?:/\\* *\\w+ *: *\\w+ *;? *\\*/ *)?display *: *(\\w+) *;? *");

    private static final Map<String, Integer> tagColormap = new HashMap<String, Integer>() {{
        put("default", 0);
        put("success", 1);
        put("info", 2);
        put("primary", 3);
        put("warning", 4);
        put("danger", 5);
    }};

    private final UrSchool urSchool;

    public Search(UrSchool urSchool) {
        this.urSchool = urSchool;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        cosPreCheckPool.shutdown();
        try {
            if (!cosPreCheckPool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                Logger.warn(TAG, "CosPreCheck pool close timeout.");
                cosPreCheckPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Logger.warn(TAG, "CosPreCheck pool close timeout.");
            cosPreCheckPool.shutdownNow();
        }
        Logger.log(TAG, "Stopped");
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String refererUrl = getRefererUrl(requestHeaders);

        try {
            // unpack cookie
            String[] cookieIn = requestHeaders.containsKey("Cookie")
                    ? requestHeaders.get("Cookie").get(0).split(",")
                    : null;
            String loginState = unpackLoginStateCookie(cookieIn, cookieStore);

            // search
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();
            SearchQuery searchQuery = new SearchQuery(req.getRequestURI().getQuery(), cookieIn);
            boolean success = true;
            if (searchQuery.invalidSerialNumber()) {
                data.append("err", TAG + "Invalid serial number");
                success = false;
            }
            if (searchQuery.noQuery()) {
                data.append("err", TAG + "No query string found");
                success = false;
            }
            if (success)
                success = search(searchQuery, data, cookieStore);
            data.append("success", success);

            // set cookie
            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
            if (success) {
                String searchID = (searchQuery.searchID == null ? "" : searchQuery.searchID) +
                        '|' +
                        (searchQuery.timeSearchID == null ? "" : searchQuery.timeSearchID);
                responseHeader.add("Set-Cookie", "searchID=" + searchID +
                        "; Path=/api/search" + getCookieInfoData(refererUrl));
            } else
                responseHeader.add("Set-Cookie", removeCookie("searchID") +
                        "; Path=/api/search" + getCookieInfoData(refererUrl));

            byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            e.printStackTrace();
            req.close();
        }
        Logger.log(TAG, "Search " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    public static class SearchQuery {
        String searchID, timeSearchID;

        String courseName;      // 課程名稱
        String instructor;      // 教師姓名
        String dayOfWeak;       // 星期 1 ~ 7
        String deptNo;          // 系所 A...
        String grade;           // 年級 1 ~ 7
        String cl;              // 節次 1 ~ 16 []

        boolean getAll;

        boolean getSerial;
        Map<String, String> serialIdNumber;     // 系號=序號&系號2=序號

        boolean getTime;
        int yearBegin, semBegin, yearEnd, semEnd;

        public SearchQuery(String dept) {
            this.deptNo = dept;
        }

        public SearchQuery(CourseData courseData) {
            this.deptNo = courseData.serialNumber == null
                    ? null
                    : courseData.serialNumber.substring(0, courseData.serialNumber.indexOf('-'));
            this.instructor = courseData.instructors == null ? null : courseData.instructors[0];
            if (courseData.timeList != null) {
                for (CourseData.TimeData time : courseData.timeList) {
                    if (time.section == null) continue;
                    this.dayOfWeak = String.valueOf(time.dayOfWeak);
                    this.cl = String.valueOf(time.getSectionAsInt());
                    break;
                }
                // if no section
                if (this.cl == null)
                    this.dayOfWeak = String.valueOf(courseData.timeList[0].dayOfWeak);
            }
        }

        private SearchQuery(String queryString, String[] cookieIn) {
            if (cookieIn != null) for (String i : cookieIn)
                if (i.startsWith("searchID")) {
                    String searchIDs = i.substring(16);
                    int split = searchIDs.indexOf('|');
                    if (split == -1)
                        searchID = searchIDs;
                    else {
                        searchID = searchIDs.substring(0, split);
                        timeSearchID = searchIDs.substring(split + 1);
                    }
                    break;
                }

            Map<String, String> query = parseUrlEncodedForm(queryString);

            getAll = query.containsKey("ALL");
            String serialNum;
            getSerial = (serialNum = query.get("serial")) != null;
            String queryTime;
            getTime = (queryTime = query.get("queryTime")) != null;
            if (getTime) {
                String[] cache = queryTime.split(",");
                yearBegin = Integer.parseInt(cache[0]);
                semBegin = Integer.parseInt(cache[1]);
                yearEnd = Integer.parseInt(cache[2]);
                semEnd = Integer.parseInt(cache[3]);
            }

            if (!getAll) {
                if (getSerial) {
                    try {
                        serialIdNumber = parseUrlEncodedForm(URLDecoder.decode(serialNum, "UTF-8"));
                    } catch (UnsupportedEncodingException ignore) {
                    }
                } else {
                    courseName = query.get("courseName");   // 課程名稱
                    instructor = query.get("instructor");   // 教師姓名
                    dayOfWeak = query.get("dayOfWeak");     // 星期 1 ~ 7
                    deptNo = query.get("dept");             // 系所 A...
                    grade = query.get("grade");             // 年級 1 ~ 7
                    cl = query.get("section");              // 節次 1 ~ 16 []
                }
            }
        }

        boolean noQuery() {
            return !getAll && !getSerial && courseName == null && instructor == null && dayOfWeak == null && deptNo == null && grade == null && cl == null;
        }

        public boolean invalidSerialNumber() {
            return getSerial && serialIdNumber.size() == 0;
        }
    }

    public static class CourseData {
        private String departmentName; // Can be null
        private String serialNumber; // Can be null
        private String courseAttributeCode;
        private String courseSystemNumber;
        private String courseName;
        private String courseNote; // Can be null
        private String courseLimit; // Can be null
        private String courseType;
        private Integer forGrade;  // Can be null
        private String forClass; // Can be null
        private String group;  // Can be null
        private String[] instructors; // Can be null
        private TagData[] tags; // Can be null
        private Float credits; // Can be null
        private Boolean required; // Can be null
        private Integer selected; // Can be null
        private Integer available; // Can be null
        private TimeData[] timeList; // Can be null
        private String moodle; // Can be null
        private String outline; // Can be null
        private String btnPreferenceEnter; // Can be null
        private String btnAddCourse; // Can be null
        private String btnPreRegister; // Can be null
        private String btnAddRequest; // Can be null

        private static class TagData {
            String tag;
            String url; // Can be null
            int colorID;

            public TagData(String tag, int colorID, String url) {
                this.tag = tag;
                this.url = url;
                this.colorID = colorID;
            }

            @Override
            public String toString() {
                if (url == null)
                    return tag + ',' + colorID + ',';
                return tag + ',' + colorID + ',' + url;
            }
        }

        private static class TimeData {
            Integer dayOfWeak; // Can be null
            Character section; // Can be null
            Character sectionTo; // Can be null
            String mapLocation; // Can be null
            String mapRoomNo; // Can be null
            String mapRoomName; // Can be null
            // Detailed time data
            String extraTimeDataKey; // Can be null

            public TimeData() {
                dayOfWeak = null;
                section = null;
                sectionTo = null;
                mapLocation = null;
                mapRoomNo = null;
                mapRoomName = null;
                extraTimeDataKey = null;
            }

            public boolean isNotEmpty() {
                return dayOfWeak != null ||
                        section != null ||
                        sectionTo != null ||
                        mapLocation != null ||
                        mapRoomNo != null ||
                        mapRoomName != null ||
                        extraTimeDataKey != null;
            }

            public Integer getSectionAsInt() {
                if (section == null) return null;
                if (section <= '4') return section - '0' + 1;
                if (section == 'N') return 6;
                if (section <= '9') return section - '5' + 7;
                if (section >= 'A' && section <= 'E') return section - 'A' + 12;
                if (section >= 'a' && section <= 'e') return section - 'a' + 12;
                throw new RuntimeException(new NumberFormatException());
            }

            @Override
            public String toString() {
                if (extraTimeDataKey != null) return extraTimeDataKey;
                StringBuilder builder = new StringBuilder();
                if (dayOfWeak != null) builder.append(dayOfWeak);
                builder.append(',');
                if (section != null) builder.append(section);
                builder.append(',');
                if (sectionTo != null) builder.append(sectionTo);
                builder.append(',');
                if (mapLocation != null) builder.append(mapLocation);
                builder.append(',');
                if (mapRoomNo != null) builder.append(mapRoomNo);
                builder.append(',');
                if (mapRoomName != null) builder.append(mapRoomName);
                return builder.toString();
            }
        }

        private JsonArrayStringBuilder toJsonArray(Object[] array) {
            if (array == null) return null;
            JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
            for (Object i : array)
                builder.append(i.toString());
            return builder;
        }

        @Override
        public String toString() {
            // output
            JsonObjectStringBuilder jsonBuilder = new JsonObjectStringBuilder();
            jsonBuilder.append("dn", departmentName);
            jsonBuilder.append("sn", serialNumber);
            jsonBuilder.append("ca", courseAttributeCode);
            jsonBuilder.append("cs", courseSystemNumber);
            jsonBuilder.append("cn", courseName);
            jsonBuilder.append("ci", courseNote);
            jsonBuilder.append("cl", courseLimit);
            jsonBuilder.append("ct", courseType);
            if (forGrade == null) jsonBuilder.append("g");
            else jsonBuilder.append("g", forGrade);
            jsonBuilder.append("co", forClass);
            jsonBuilder.append("cg", group);
            jsonBuilder.append("i", toJsonArray(instructors));
            jsonBuilder.append("tg", toJsonArray(tags));
            if (credits == null) jsonBuilder.append("c");
            else jsonBuilder.append("c", credits);
            if (required == null) jsonBuilder.append("r");
            else jsonBuilder.append("r", required);
            if (selected == null) jsonBuilder.append("s");
            else jsonBuilder.append("s", selected);
            if (available == null) jsonBuilder.append("a");
            else jsonBuilder.append("a", available);
            jsonBuilder.append("t", toJsonArray(timeList));
            jsonBuilder.append("m", moodle);
            jsonBuilder.append("o", outline);
            jsonBuilder.append("pe", btnPreferenceEnter);
            jsonBuilder.append("ac", btnAddCourse);
            jsonBuilder.append("pr", btnPreRegister);
            jsonBuilder.append("ar", btnAddRequest);
            return jsonBuilder.toString();
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public String getCourseName() {
            return courseName;
        }

        public Integer getSelected() {
            return selected;
        }

        public Integer getAvailable() {
            return available;
        }
    }

    public static class SaveQueryToken {
        private final String host, search;
        private final CookieStore cookieStore;

        public SaveQueryToken(String host, String search, CookieStore cookieStore) {
            this.host = host;
            this.search = search;
            this.cookieStore = cookieStore;
        }

        public String getUrl() {
            return host + "/index.php?c=qry11215" + search;
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

    public static class DeptToken {
        private final String error;
        private final String id;
        private final CookieStore cookieStore;

        CountDownLatch cosPreCheckLock;

        private DeptToken(String error, String id, CookieStore cookieStore) {
            this.error = error;
            this.id = id;
            this.cookieStore = cookieStore;
        }

        public String getID() {
            return id;
        }

        public String getError() {
            return error;
        }

        void lockCosPreCheck() {
            cosPreCheckLock = new CountDownLatch(1);
        }

        void unlockCosPreCheckLock() {
            cosPreCheckLock.countDown();
        }

        public void awaitCosPreCheck() {
            if (cosPreCheckLock != null)
                try {
                    cosPreCheckLock.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
    }

    private boolean search(SearchQuery searchQuery, JsonObjectStringBuilder outData, CookieStore cookieStore) {
        try {
            List<CourseData> courseDataList = new ArrayList<>();

            boolean success = true;
            // get all course
            if (searchQuery.getAll) {
                AllDeptData allDeptData = getAllDeptData(cookieStore);
                if (allDeptData == null) {
                    outData.append("err", TAG + "Can not get crypt");
                    success = false;
                }
                // start getting dept
                else {
                    CountDownLatch countDownLatch = new CountDownLatch(allDeptData.deptCount);
                    final int poolSize = 4;
                    ThreadPoolExecutor fetchPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize, new SimpleThreadFactory("Fetch-Thread-"));
                    Semaphore fetchPoolLock = new Semaphore(poolSize);
                    AtomicBoolean allSuccess = new AtomicBoolean(true);
                    ProgressBar progressBar = new ProgressBar(TAG + "Get All ");
                    Logger.addProgressBar(progressBar);
                    progressBar.setProgress(0f);
                    for (String dept : allDeptData.allDept) {
                        fetchPoolLock.acquire();
                        // If one failed, stop all
                        if (!allSuccess.get()) {
                            while (countDownLatch.getCount() > 0)
                                countDownLatch.countDown();
                            break;
                        }
                        fetchPool.submit(() -> {
                            try {
                                long start = System.currentTimeMillis();
                                if (allSuccess.get() && !getDeptCourseData(dept, allDeptData, false, outData, courseDataList))
                                    allSuccess.set(false);
                                Logger.log(TAG, Thread.currentThread().getName() + " Get dept " + dept + " done, " + (System.currentTimeMillis() - start) + "ms");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            fetchPoolLock.release();
                            countDownLatch.countDown();
                            progressBar.setProgress((float) (allDeptData.deptCount - countDownLatch.getCount()) / allDeptData.deptCount * 100f);
                        });
                    }
                    countDownLatch.await();
                    fetchPool.shutdown();
                    if (!fetchPool.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                        fetchPool.shutdownNow();
                        Logger.warn(TAG, "FetchPool shutdown timeout");
                    }
                    progressBar.setProgress(100f);
                    Logger.removeProgressBar(progressBar);
                    success = allSuccess.get();
                }
            }
            // get listed serial
            else if (searchQuery.getSerial) {
                JsonObjectStringBuilder result = new JsonObjectStringBuilder();
                for (Map.Entry<String, String> i : searchQuery.serialIdNumber.entrySet()) {
                    courseDataList.clear();
                    if (!getQueryCourseData(searchQuery, i, cookieStore, outData, courseDataList)) {
                        success = false;
                        break;
                    }
                    result.appendRaw(i.getKey(), courseDataList.toString());
                }
                outData.appendRaw("data", result.toString());
            } else
                success = getQueryCourseData(searchQuery, null, cookieStore, outData, courseDataList);

            if (success && !searchQuery.getSerial)
                outData.appendRaw("data", courseDataList.toString());
            return success;
        } catch (Exception e) {
            outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    public AllDeptData getAllDeptData(CookieStore cookieStore) throws IOException {
        Connection.Response allDeptRes = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                .cookieStore(cookieStore)
                .execute();
        String allDeptBody = allDeptRes.body();
        cosPreCheck(allDeptBody, cookieStore, null);

        Set<String> allDept = new HashSet<>();
        for (Element element : allDeptRes.parse().getElementsByClass("pnl_dept"))
            allDept.addAll(element.getElementsByAttribute("data-dept").eachAttr("data-dept"));

        int cryptStart, cryptEnd;
        if ((cryptStart = allDeptBody.indexOf("'crypt'")) == -1 ||
                (cryptStart = allDeptBody.indexOf('\'', cryptStart + 7)) == -1 ||
                (cryptEnd = allDeptBody.indexOf('\'', ++cryptStart)) == -1
        )
            return null;
        return new AllDeptData(allDeptBody.substring(cryptStart, cryptEnd), allDept, cookieStore);
    }

    public DeptToken createDeptToken(String deptNo, AllDeptData allDeptData) throws IOException {
//        Connection.Response id = null;
//        final int retryCount = 10;
//        for (int i = 0; i < retryCount; i++) {
////            Logger.log(TAG, Thread.currentThread().getName() + " Get dept " + deptNo + ", " + i);
//            try {
//                id = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
//                        .cookieStore(allDeptData.cookieStore)
//                        .ignoreContentType(true)
//                        .method(Connection.Method.POST)
//                        .requestBody("dept_no=" + deptNo + "&crypt=" + URLEncoder.encode(allDeptData.crypt, "UTF-8"))
//                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
//                        .header("X-Requested-With", "XMLHttpRequest")
//                        .timeout(5000)
//                        .execute();
//                break;
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//                return null;
//            } catch (IOException networkErr) {
//                if (i == retryCount - 1) {
//                    Logger.err(TAG, "Get dept " + deptNo + " failed");
//                    networkErr.printStackTrace();
//                    return null;
//                }
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
////                Logger.warn(TAG, "Get dept " + deptNo + " timeout, retry");
//            }
//        }

        Connection.Response id = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
                .cookieStore(allDeptData.cookieStore)
                .ignoreContentType(true)
                .method(Connection.Method.POST)
                .requestBody("dept_no=" + deptNo + "&crypt=" + URLEncoder.encode(allDeptData.crypt, "UTF-8"))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .execute();
        JsonObject idData = new JsonObject(id.body());
        String error = idData.getString("err");
        return new DeptToken(error.length() > 0 ? error : null, idData.getString("id"), allDeptData.cookieStore);
    }

    public boolean getDeptCourseData(DeptToken deptToken, List<CourseData> courseDataList, boolean addUrSchoolCache) throws IOException {
        String searchResultBody = getDeptNCKU(deptToken);
        if (searchResultBody == null)
            return true;

        Element table = findCourseTable(searchResultBody, null, null);
        if (table == null)
            return false;

        parseCourseTable(
                table,
                null,
                searchResultBody,
                courseDataList,
                addUrSchoolCache);
        return true;
    }

    public boolean getDeptCourseData(String deptNo, AllDeptData allDeptData, boolean addUrSchoolCache, JsonObjectStringBuilder outData, List<CourseData> courseDataList) throws IOException {
        DeptToken deptToken = createDeptToken(deptNo, allDeptData);
        if (deptToken == null) {
            outData.append("err", TAG + "Network error");
            return false;
        }
        if (deptToken.error != null) {
            outData.append("err", TAG + "Error from NCKU course: " + deptToken.error);
            return false;
        }

        String searchResultBody = getDeptNCKU(deptToken);
        if (searchResultBody == null) {
            outData.append("warn", TAG + "Dept.No " + deptNo + " not found");
            return true;
        }

        Element table = findCourseTable(searchResultBody, "Dept.No " + deptNo, outData);
        if (table == null)
            return false;

        parseCourseTable(
                table,
                null,
                searchResultBody,
                courseDataList,
                addUrSchoolCache);
        return true;
    }

    private String getDeptNCKU(DeptToken deptToken) throws IOException {
        Connection.Response result = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result&i=" +
                        URLEncoder.encode(deptToken.id, "UTF-8"))
                .cookieStore(deptToken.cookieStore)
                .ignoreContentType(true)
                .header("Referer", courseNckuOrg + "/index.php?c=qry_all")
                .maxBodySize(20 * 1024 * 1024)
                .execute();
        if (result.url().getQuery().equals("c=qry_all"))
            return null;
        String searchResultBody = result.body();
//            cosPreCheck(searchResultBody, deptToken.cookieStore, null);
//            Logger.log(TAG, deptToken.cookieStore.getCookies().toString());
        deptToken.awaitCosPreCheck();
        deptToken.lockCosPreCheck();
        try {
            cosPreCheckPoolLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        cosPreCheckPool.submit(() -> {
            cosPreCheck(searchResultBody, deptToken.cookieStore, null);
            deptToken.unlockCosPreCheckLock();
            cosPreCheckPoolLock.release();
        });
        return searchResultBody;
    }

    private boolean getQueryCourseData(SearchQuery searchQuery, Map.Entry<String, String> getSerialNum,
                                       CookieStore cookieStore, JsonObjectStringBuilder outData, List<CourseData> courseDataList) throws IOException {
        // If getSerialNum is given, set query dept to get courses
        if (getSerialNum != null)
            searchQuery.deptNo = getSerialNum.getKey();

        SaveQueryToken saveQueryToken = createSaveQueryToken(searchQuery, cookieStore, outData);
        if (saveQueryToken == null) {
            outData.append("err", TAG + "Failed to save query");
            return false;
        }

        String searchResultBody = getCourseNCKU(saveQueryToken);

        if ((searchQuery.searchID = getSearchID(searchResultBody, outData)) == null)
            return false;

        Element table = findCourseTable(searchResultBody, "Query " + searchQuery, outData);
        if (table == null)
            return false;

        parseCourseTable(
                table,
                getSerialNum == null ? null : new HashSet<>(Arrays.asList(getSerialNum.getValue().split(","))),
                searchResultBody,
                courseDataList,
                true);

        return true;
    }

    public boolean getQueryCourseData(SearchQuery searchQuery, SaveQueryToken saveQueryToken, List<CourseData> courseDataList, boolean addUrSchoolCache) throws IOException {
        String searchResultBody = getCourseNCKU(saveQueryToken);

        if ((searchQuery.searchID = getSearchID(searchResultBody, null)) == null)
            return false;

        Element table = findCourseTable(searchResultBody, "Query " + searchQuery, null);
        if (table == null)
            return false;

        parseCourseTable(
                table,
                null,
                searchResultBody,
                courseDataList,
                addUrSchoolCache);

        return true;
    }

    public SaveQueryToken createSaveQueryToken(SearchQuery searchQuery, CookieStore cookieStore, JsonObjectStringBuilder outData) {
        try {
            StringBuilder postData = new StringBuilder();
            String host;
            if (searchQuery.getTime) {
                host = courseQueryNckuOrg;
                postData.append("id=").append(URLEncoder.encode(searchQuery.timeSearchID, "UTF-8"));
            } else {
                host = courseNckuOrg;
                // Get searchID if it's null
                if (searchQuery.searchID == null) {
                    final String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .execute().body();
                    cosPreCheck(body, cookieStore, outData);
                    if ((searchQuery.searchID = getSearchID(body, outData)) == null)
                        return null;
                }
                postData.append("id=").append(URLEncoder.encode(searchQuery.searchID, "UTF-8"));
                if (searchQuery.courseName != null)
                    postData.append("&cosname=").append(URLEncoder.encode(searchQuery.courseName, "UTF-8"));
                if (searchQuery.instructor != null)
                    postData.append("&teaname=").append(URLEncoder.encode(searchQuery.instructor, "UTF-8"));
                if (searchQuery.dayOfWeak != null) postData.append("&wk=").append(searchQuery.dayOfWeak);
                if (searchQuery.deptNo != null) postData.append("&dept_no=").append(searchQuery.deptNo);
                if (searchQuery.grade != null) postData.append("&degree=").append(searchQuery.grade);
                if (searchQuery.cl != null) postData.append("&cl=").append(searchQuery.cl);
            }

//            Logger.log(TAG, TAG + "Post search query");
            Connection.Response search = HttpConnection.connect(host + "/index.php?c=qry11215&m=save_qry")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody(postData.toString())
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            String searchBody = search.body();
            if (searchBody.equals("0")) {
                if (outData != null)
                    outData.append("err", TAG + "Condition not set");
                return null;
            }
            if (searchBody.equals("1")) {
                if (outData != null)
                    outData.append("err", TAG + "Wrong condition format");
                return null;
            }
            return new SaveQueryToken(host, searchBody, cookieStore);
        } catch (UnsupportedEncodingException encodingException) {
            encodingException.printStackTrace();
            if (outData != null)
                outData.append("err", TAG + "Unsupported encoding");
        } catch (IOException e) {
            e.printStackTrace();
            if (outData != null)
                outData.append("err", TAG + "Unknown error" + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    private String getCourseNCKU(SaveQueryToken saveQueryToken) throws IOException {
//            Logger.log(TAG, TAG + "Get search result");
        Connection.Response searchResult = HttpConnection.connect(saveQueryToken.host + "/index.php?c=qry11215" + saveQueryToken.search)
                .cookieStore(saveQueryToken.cookieStore)
                .ignoreContentType(true)
                .maxBodySize(20 * 1024 * 1024)
                .execute();

        String searchResultBody = searchResult.body();
        try {
            cosPreCheckPoolLock.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        cosPreCheckPool.submit(() -> {
            cosPreCheck(searchResultBody, saveQueryToken.cookieStore, null);
            cosPreCheckPoolLock.release();
        });
        return searchResultBody;
    }

    private Element findCourseTable(String html, String errorMessage, JsonObjectStringBuilder outData) {
        int resultTableStart;
        if ((resultTableStart = html.indexOf("<table")) == -1) {
            if (outData != null)
                outData.append("err", TAG + errorMessage + " result table not found");
            return null;
        }
        // get table body
        int resultTableBodyStart, resultTableBodyEnd;
        if ((resultTableBodyStart = html.indexOf("<tbody>", resultTableStart + 7)) == -1 ||
                (resultTableBodyEnd = html.indexOf("</tbody>", resultTableBodyStart + 7)) == -1
        ) {
            if (outData != null)
                outData.append("err", TAG + errorMessage + " result table body not found");
            return null;
        }

        // parse table
//            Logger.log(TAG, TAG + "Parse course table");
        String resultBody = html.substring(resultTableBodyStart, resultTableBodyEnd + 8);
        return (Element) Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);
    }

    private void parseCourseTable(Element tbody, Set<String> getSerialNumber, String searchResultBody, List<CourseData> courseDataList, boolean addUrSchoolCache) {
        // find style
        List<String> styleList = new ArrayList<>();
        int styleStart, styleEnd = 0;
        while ((styleStart = searchResultBody.indexOf("<style", styleEnd)) != -1) {
            styleStart = searchResultBody.indexOf(">", styleStart + 6);
            if (styleStart == -1) continue;
            styleStart += 1;
            styleEnd = searchResultBody.indexOf("</style>", styleStart);
            if (styleEnd == -1) continue;
            styleList.add(searchResultBody.substring(styleStart, styleEnd));
            styleEnd += 8;
        }
        // style stuff
        List<Map.Entry<String, Boolean>> styles = new ArrayList<>();
        for (String style : styleList) {
            Matcher matcher = displayRegex.matcher(style);
            while (matcher.find()) {
                if (matcher.groupCount() < 2) continue;
                styles.add(new AbstractMap.SimpleEntry<>(matcher.group(1), !matcher.group(2).equals("none")));
            }
        }

        List<String> urSchoolCache = null;
        if (addUrSchoolCache) urSchoolCache = new ArrayList<>();

        // get course list
        Elements courseList = tbody.getElementsByTag("tr");
        for (Element element : courseList) {
            Elements section = element.getElementsByTag("td");

            List<Node> section1 = section.get(1).childNodes();
            // get serial number
            String serialNumber = ((Element) section1.get(0)).text().trim();
            if (getSerialNumber != null) {
                String serialNumberStr = serialNumber.substring(serialNumber.indexOf('-') + 1);
                // Skip if we don't want
                if (!getSerialNumber.contains(serialNumberStr))
                    continue;
            }

            CourseData courseData = new CourseData();

            // Get department name
            courseData.departmentName = section.get(0).ownText();
            if (courseData.departmentName.length() == 0) courseData.departmentName = null;

            // Get serial number
            courseData.serialNumber = serialNumber.length() == 0 ? null : serialNumber;

            // Get system number
            courseData.courseSystemNumber = ((TextNode) section1.get(1)).text().trim();

            // Get attribute code
            String courseAttributeCode = ((TextNode) section1.get(3)).text().trim();
            if (courseAttributeCode.length() > 0) {
                int courseAttributeCodeStart, courseAttributeCodeEnd;
                if ((courseAttributeCodeStart = courseAttributeCode.indexOf('[')) != -1 &&
                        (courseAttributeCodeEnd = courseAttributeCode.lastIndexOf(']')) != -1)
                    courseData.courseAttributeCode = courseAttributeCode.substring(courseAttributeCodeStart + 1, courseAttributeCodeEnd);
                else
                    courseData.courseAttributeCode = courseAttributeCode;
            } else
                Logger.log(TAG, "AttributeCode not found");

            // Get course name
            courseData.courseName = section.get(4).getElementsByClass("course_name").get(0).text();

            // Get course note
            courseData.courseNote = section.get(4).ownText().trim().replace("\"", "\\\"");
            if (courseData.courseNote.length() == 0) courseData.courseNote = null;

            // Get course limit
            courseData.courseLimit = section.get(4).getElementsByClass("cond").text().trim().replace("\"", "\\\"");
            if (courseData.courseLimit.length() == 0) courseData.courseLimit = null;

            // Get course type
            courseData.courseType = section.get(3).text();

            // Get instructor name
            String instructors = section.get(6).text();
            if (instructors.length() > 0 && !instructors.equals("未定")) {
                instructors = instructors.replace("*", "");
                courseData.instructors = instructors.split(" ");
                // Add urSchool cache
                if (addUrSchoolCache && urSchool != null) {
                    urSchoolCache.addAll(Arrays.asList(courseData.instructors));
                    // Flush to add urSchool cache
                    if (urSchoolCache.size() > 10) {
                        urSchool.addInstructorCache(urSchoolCache.toArray(new String[0]));
                        urSchoolCache.clear();
                    }
                }
            } else courseData.instructors = null;

            // Get course tags
            Elements tagElements = section.get(4).getElementsByClass("label");
            if (tagElements.size() > 0) {
                CourseData.TagData[] tags = new CourseData.TagData[tagElements.size()];
                for (int i = 0; i < tags.length; i++) {
                    Element tagElement = tagElements.get(i);
                    // Get tag Color
                    String tagType = null;
                    for (String j : tagElement.classNames())
                        if (j.length() > 6 && j.startsWith("label")) {
                            tagType = j.substring(6);
                            break;
                        }
                    Integer tagColor = tagColormap.get(tagType);
                    if (tagColor == null) {
                        Logger.log(TAG, "Unknown tag color: " + tagType);
                        tagColor = -1;
                    }

                    Element j = tagElement.firstElementChild();
                    String link = j == null ? null : j.attr("href");

                    tags[i] = new CourseData.TagData(tagElement.text(), tagColor, link);
                }
                courseData.tags = tags;
            } else courseData.tags = null;

            // Get forGrade & classInfo & group
            courseData.forGrade = null;
            courseData.forClass = null;
            courseData.group = null;
            List<Node> section2 = section.get(2).childNodes();
            int section2c = 0;
            for (Node node : section2) {
                if (!(node instanceof TextNode))
                    // <br>
                    section2c++;
                else {
                    // text
                    String cache = ((TextNode) node).text().trim();
                    if (cache.length() == 0) continue;
                    if (section2c == 0)
                        courseData.forGrade = Integer.parseInt(cache);
                    else if (section2c == 1)
                        courseData.forClass = cache;
                    else
                        courseData.group = cache;
                }
            }

            // get credits & required
            List<TextNode> section5 = section.get(5).textNodes();
            String section5Str;
            if (section5.size() > 0 && (section5Str = section5.get(0).text().trim()).length() > 0) {
                courseData.credits = Float.parseFloat(section5Str);
                String cache = section5.get(1).text().trim();
                courseData.required = cache.equals("必修") || cache.equals("Required");
            } else {
                courseData.credits = null;
                courseData.required = null;
            }

            // Get time list
            List<CourseData.TimeData> timeDataList = new ArrayList<>();
            CourseData.TimeData timeDataCache = new CourseData.TimeData();
            int timeParseState = 0;
            for (Node node : section.get(8).childNodes()) {
                // time
                if (timeParseState == 0) {
                    timeParseState++;
                    String text;
                    if (node instanceof TextNode && (text = ((TextNode) node).text().trim()).length() > 0) {
                        // Get dayOfWeak
                        int dayOfWeakEnd = text.indexOf(']', 1);
                        if (dayOfWeakEnd != -1) {
                            timeDataCache.dayOfWeak = Integer.parseInt(text.substring(1, dayOfWeakEnd));
                            // Get section
                            if (text.length() > dayOfWeakEnd + 1) {
                                timeDataCache.section = text.charAt(dayOfWeakEnd + 1);
                                // Get section end
                                int split = text.indexOf('~', dayOfWeakEnd + 1);
                                if (split != -1 && text.length() > split + 1) {
                                    timeDataCache.sectionTo = text.charAt(split + 1);
                                }
                            }
                            continue;
                        }
                    }
                }
                if (timeParseState == 1) {
                    timeParseState++;
                    // Location link
                    String attribute;
                    if ((attribute = node.attr("href")).length() > 0) {
                        int locEnd = attribute.indexOf('\'', 17);
                        if (locEnd != -1) {
                            timeDataCache.mapLocation = attribute.substring(17, locEnd);
                            int roomNoEnd = attribute.indexOf('\'', locEnd + 3);
                            if (roomNoEnd != -1)
                                timeDataCache.mapRoomNo = attribute.substring(locEnd + 3, roomNoEnd);
                        }
                        if (node instanceof Element)
                            timeDataCache.mapRoomName = ((Element) node).text().trim();
                        continue;
                    }
                }
                if (timeParseState == 2 && node instanceof Element) {
                    String tagName = ((Element) node).tagName();
                    if (tagName.equals("br")) {
                        if (timeDataCache.isNotEmpty()) {
                            timeDataList.add(timeDataCache);
                            timeDataCache = new CourseData.TimeData();
                        }
                    } else if (((Element) node).tagName().equals("div")) {
                        // Detailed time data
                        timeDataCache.extraTimeDataKey = node.attr("data-mkey");
                        timeDataList.add(timeDataCache);
                    }
                    timeParseState = 0;
                }
            }
            if (timeDataCache.isNotEmpty())
                timeDataList.add(timeDataCache);

            if (timeDataList.size() > 0)
                courseData.timeList = timeDataList.toArray(new CourseData.TimeData[0]);
            else
                courseData.timeList = null;

            // Get selected & available
            String[] count;
            StringBuilder countBuilder = new StringBuilder();
            for (Node n : section.get(7).childNodes()) {
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
            courseData.selected = count[0].length() == 0 ? null : Integer.parseInt(count[0]);
            courseData.available = count.length < 2 ? null :
                    (count[1].equals("額滿") || count[1].equals("full")) ? 0 :
                            (count[1].equals("不限") || count[1].equals("unlimited")) ? -1 :
                                    (count[1].startsWith("洽") || count[1].startsWith("please connect")) ? -2 :
                                            Integer.parseInt(count[1]);

            // Get moodle, outline
            courseData.moodle = null;
            courseData.outline = null;
            Elements linkEle = section.get(9).getElementsByAttribute("href");
            if (linkEle.size() > 0) {
                for (Element ele : linkEle) {
                    String href = ele.attr("href");
                    if (href.startsWith("javascript"))
                        // moodle link
                        courseData.moodle = href.substring(19, href.length() - 3).replace("','", ",");
                    else
                        // outline link
                        courseData.outline = href.substring(href.indexOf("?") + 1);
                }
            }

            // Get function buttons
            courseData.btnPreferenceEnter = null;
            courseData.btnAddCourse = null;
            courseData.btnPreRegister = null;
            courseData.btnAddRequest = null;
            for (int i = 10; i < section.size(); i++) {
                Element button = section.get(i).firstElementChild();
                if (button == null) continue;
                String buttonText = button.ownText().replace(" ", "");
                switch (buttonText) {
                    case "PreferenceEnter":
                    case "志願登記":
                        courseData.btnPreferenceEnter = button.attr("data-key");
                        break;
                    case "AddCourse":
                    case "單科加選":
                        courseData.btnAddCourse = button.attr("data-key");
                        break;
                    case "Pre-register":
                    case "加入預排":
                        courseData.btnPreRegister = button.attr("data-prekey");
                        break;
                    case "AddRequest":
                    case "加入徵詢":
                        courseData.btnAddRequest = button.attr("data-prekey");
                        break;
                }
            }

            courseDataList.add(courseData);
        }

        // Add urSchool cache
        if (addUrSchoolCache && urSchool != null) {
            // Flush to add urSchool cache
            if (urSchoolCache.size() > 0)
                urSchool.addInstructorCache(urSchoolCache.toArray(new String[0]));
        }
    }

    private String getSearchID(String body, JsonObjectStringBuilder outData) {
        // get entry function
        int searchFunctionStart = body.indexOf("function setdata()");
        if (searchFunctionStart == -1) {
            if (outData != null)
                outData.append("err", TAG + "Search function not found");
            return null;
        } else searchFunctionStart += 18;

        int idStart, idEnd;
        if ((idStart = body.indexOf("'id'", searchFunctionStart)) == -1 ||
                (idStart = body.indexOf('\'', idStart + 4)) == -1 ||
                (idEnd = body.indexOf('\'', idStart + 1)) == -1
        ) {
            if (outData != null)
                outData.append("err", TAG + "Search id not found");
            return null;
        }
        return body.substring(idStart + 1, idEnd);
    }
}
