package com.wavjaby.api;

import com.sun.istack.internal.Nullable;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Cookie;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.ProgressBar;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wavjaby.Main.*;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.HttpResponseData.ResponseState;
import static com.wavjaby.lib.Lib.*;

public class Search implements EndpointModule {
    private static final String TAG = "[Search]";
    private static final Logger logger = new Logger(TAG);

    private static final int MAX_ROBOT_CHECK_TRY = 5;
    private static final int COS_PRE_CHECK_COOKIE_LOCK = 2;
    private final ThreadPoolExecutor cosPreCheckPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8, new ThreadFactory("CosPreT-"));
    private final Semaphore cosPreCheckPoolLock = new Semaphore(cosPreCheckPool.getMaximumPoolSize(), true);
    private final Map<String, Semaphore> cosPreCheckCookieLock = new HashMap<>();
    private static final Pattern displayRegex = Pattern.compile("[\\r\\n]+\\.(\\w+) *\\{[\\r\\n]* *(?:/\\* *\\w+ *: *\\w+ *;? *\\*/ *)?display *: *(\\w+) *;? *");

    private static final Map<String, Character> tagColormap = new HashMap<String, Character>() {{
        put("default", '0');
        put("success", '1');
        put("info", '2');
        put("primary", '3');
        put("warning", '4');
        put("danger", '5');
    }};

    private final UrSchool urSchool;
    private final RobotCode robotCode;
    private final ProxyManager proxyManager;

    public Search(UrSchool urSchool, RobotCode robotCode, ProxyManager proxyManager) {
        this.urSchool = urSchool;
        this.robotCode = robotCode;
        this.proxyManager = proxyManager;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        cosPreCheckPool.shutdown();
        try {
            if (!cosPreCheckPool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                logger.warn("CosPreCheck pool close timeout");
                cosPreCheckPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.warn("CosPreCheck pool close error");
            cosPreCheckPool.shutdownNow();
        }
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();

        try {
            // unpack cookie
            String[] cookieIn = splitCookie(requestHeaders);
            String loginState = unpackCourseLoginStateCookie(cookieIn, cookieStore);

            // search
            SearchQuery searchQuery = new SearchQuery(req.getRequestURI().getRawQuery(), cookieIn);
            ApiResponse apiResponse = new ApiResponse();
            if (searchQuery.serialNumberEmpty()) {
                apiResponse.addError(TAG + "Empty serial number query");
            } else if (searchQuery.serialNumberInvalid()) {
                apiResponse.addError(TAG + "Invalid serial number query");
            } else if (searchQuery.historySearchInvalid()) {
                apiResponse.addError(TAG + "Invalid history search query");
            } else if (searchQuery.noQuery()) {
                apiResponse.addError(TAG + "No query found");
            } else
                search(searchQuery, apiResponse, cookieStore);

            // set cookie
            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
            if (apiResponse.isSuccess())
                responseHeader.add("Set-Cookie", setSearchIdCookie(searchQuery) +
                        "; Path=/api/search" + setCookieDomain());
            else
                responseHeader.add("Set-Cookie", removeCookie("searchID") +
                        "; Path=/api/search" + setCookieDomain());
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.isSuccess() ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            e.printStackTrace();
            req.close();
        }
        logger.log("Search " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    public static class SearchQuery {
        String searchID, historySearchID;

        String deptNo;                  // 系所 A...
        final String courseName;        // 課程名稱
        final String instructor;        // 教師姓名
        final String grade;             // 年級 1 ~ 4
        final String dayOfWeek;         // 星期 1 ~ 7
        final String sectionOfDay;      // 節次 [1 ~ 16]

        final boolean getAll;

        final boolean getSerial;
        final Map<String, Set<String>> serialIdNumber;     // 系號=序號&系號2=序號,序號

        final boolean historySearch;
        final int yearBegin, semBegin, yearEnd, semEnd;

        public SearchQuery(CourseData courseData) {
            this.deptNo = courseData.serialNumber == null
                    ? null
                    : courseData.serialNumber.substring(0, courseData.serialNumber.indexOf('-'));
            this.courseName = courseData.courseName;
            this.instructor = courseData.instructors == null ? null : courseData.instructors[0];
            this.grade = courseData.forGrade == null ? null : String.valueOf(courseData.forGrade);
            String dayOfWeek = null, sectionOfDay = null;
            if (courseData.timeList != null) {
                for (CourseData.TimeData time : courseData.timeList) {
                    if (time.section == null) continue;
                    dayOfWeek = String.valueOf(time.dayOfWeek);
                    sectionOfDay = String.valueOf(time.getSectionAsInt());
                    break;
                }
                // if no section
                if (dayOfWeek == null)
                    dayOfWeek = String.valueOf(courseData.timeList[0].dayOfWeek);
            }
            this.dayOfWeek = dayOfWeek;
            this.sectionOfDay = sectionOfDay;

            this.getAll = false;
            this.getSerial = false;
            this.serialIdNumber = null;
            this.historySearch = false;
            this.yearBegin = this.semBegin = this.yearEnd = this.semEnd = -1;
        }

        private SearchQuery(String queryString, String[] cookieIn) {
            if (cookieIn != null) for (String i : cookieIn)
                if (i.startsWith("searchID=")) {
                    int split = i.indexOf('|', 9);
                    if (split == -1) {
                        this.searchID = i.length() == 9 ? null : i.substring(9);
                        this.historySearchID = null;
                    } else {
                        this.searchID = split == 9 ? null : i.substring(9, split);
                        this.historySearchID = split == i.length() - 1 ? null : i.substring(split + 1);
                    }
                    break;
                }

            Map<String, String> query = parseUrlEncodedForm(queryString);

            this.getAll = "ALL".equals(query.get("dept"));
            String serialNum;
            this.getSerial = (serialNum = query.get("serial")) != null;
            String queryTime = query.get("queryTime");
            this.historySearch = queryTime != null;
            if (this.historySearch) {
                String[] cache = queryTime.split(",");
                if (cache.length != 4) {
                    this.yearBegin = this.semBegin = this.yearEnd = this.semEnd = -1;
                } else {
                    this.yearBegin = Integer.parseInt(cache[0]);
                    this.semBegin = Integer.parseInt(cache[1]);
                    this.yearEnd = Integer.parseInt(cache[2]);
                    this.semEnd = Integer.parseInt(cache[3]);
                }
            } else
                this.yearBegin = this.semBegin = this.yearEnd = this.semEnd = -1;

            Map<String, Set<String>> serialIdNumber = null;
            String deptNo = null, courseName = null, instructor = null, grade = null, dayOfWeek = null, sectionOfDay = null;
            if (!this.getAll) {
                if (this.getSerial) {
                    try {
                        serialIdNumber = new HashMap<>();
                        for (Map.Entry<String, String> deptSerial : parseUrlEncodedForm(URLDecoder.decode(serialNum, "UTF-8")).entrySet()) {
                            Set<String> serialNums = new HashSet<>();
                            serialIdNumber.put(deptSerial.getKey(), serialNums);

                            String serialNumsStr = deptSerial.getValue();
                            for (int index0 = 0, index1; index0 < serialNumsStr.length(); ) {
                                if ((index1 = serialNumsStr.indexOf(',', index0)) == -1) index1 = serialNumsStr.length();
                                serialNums.add(serialNumsStr.substring(index0, index1));
                                index0 = index1 + 1;
                            }
                        }
                    } catch (UnsupportedEncodingException ignore) {
                        serialIdNumber = null;
                    }
                } else {
                    deptNo = query.get("dept");             // 系所 A...
                    courseName = query.get("courseName");   // 課程名稱
                    instructor = query.get("instructor");   // 教師姓名
                    grade = query.get("grade");             // 年級 1 ~ 4
                    dayOfWeek = query.get("dayOfWeek");     // 星期 1 ~ 7
                    sectionOfDay = query.get("section");    // 節次 [1 ~ 16]
                }
            }
            this.serialIdNumber = serialIdNumber;
            this.deptNo = deptNo;
            this.courseName = courseName;
            this.instructor = instructor;
            this.grade = grade;
            this.dayOfWeek = dayOfWeek;
            this.sectionOfDay = sectionOfDay;
        }

        boolean noQuery() {
            return !getAll && !getSerial && courseName == null && instructor == null && dayOfWeek == null && deptNo == null && grade == null && sectionOfDay == null;
        }

        public boolean serialNumberEmpty() {
            return getSerial && serialIdNumber != null && serialIdNumber.size() == 0;
        }

        public boolean serialNumberInvalid() {
            return getSerial && serialIdNumber == null;
        }

        public boolean historySearchInvalid() {
            return historySearch && yearBegin == -1;
        }
    }

    public static class CourseData {
        private final String semester;
        private final String departmentName; // Can be null
        private final String serialNumber; // Can be null
        private final String courseAttributeCode;
        private final String courseSystemNumber;
        private final Integer forGrade;  // Can be null
        private final String forClass; // Can be null
        private final String group;  // Can be null
        private final String courseType;
        private final String courseName;
        private final String courseNote; // Can be null
        private final String courseLimit; // Can be null
        private final TagData[] tags; // Can be null
        private final Float credits; // Can be null
        private final Boolean required; // Can be null
        private final String[] instructors; // Can be null
        private final Integer selected; // Can be null
        private final Integer available; // Can be null
        private final TimeData[] timeList; // Can be null
        private final String moodle; // Can be null
        private final String btnPreferenceEnter; // Can be null
        private final String btnAddCourse; // Can be null
        private final String btnPreRegister; // Can be null
        private final String btnAddRequest; // Can be null

        public CourseData(String semester,
                          String departmentName,
                          String serialNumber, String courseAttributeCode, String courseSystemNumber,
                          Integer forGrade, String forClass, String group,
                          String courseType,
                          String courseName, String courseNote, String courseLimit, TagData[] tags,
                          Float credits, Boolean required,
                          String[] instructors,
                          Integer selected, Integer available,
                          TimeData[] timeList,
                          String moodle,
                          String btnPreferenceEnter, String btnAddCourse, String btnPreRegister, String btnAddRequest) {
            this.semester = semester;
            this.departmentName = departmentName;
            this.serialNumber = serialNumber;
            this.courseAttributeCode = courseAttributeCode;
            this.courseSystemNumber = courseSystemNumber;
            this.forGrade = forGrade;
            this.forClass = forClass;
            this.group = group;
            this.courseType = courseType;
            this.courseName = courseName;
            this.courseNote = courseNote;
            this.courseLimit = courseLimit;
            this.tags = tags;
            this.credits = credits;
            this.required = required;
            this.instructors = instructors;
            this.selected = selected;
            this.available = available;
            this.timeList = timeList;
            this.moodle = moodle;
            this.btnPreferenceEnter = btnPreferenceEnter;
            this.btnAddCourse = btnAddCourse;
            this.btnPreRegister = btnPreRegister;
            this.btnAddRequest = btnAddRequest;
        }

        private static class TagData {
            final String tag;
            final String url; // Can be null
            final String colorID;

            public TagData(String tag, String colorID, String url) {
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
            final Byte dayOfWeek; // Can be null
            final Character section; // Can be null
            final Character sectionTo; // Can be null
            final String mapLocation; // Can be null
            final String mapRoomNo; // Can be null
            final String mapRoomName; // Can be null
            // Detailed time data
            final String detailedTimeData; // Can be null

            public TimeData(Byte dayOfWeek, Character section, Character sectionTo,
                            String mapLocation, String mapRoomNo, String mapRoomName) {
                this.dayOfWeek = dayOfWeek;
                this.section = section;
                this.sectionTo = sectionTo;
                this.mapLocation = mapLocation;
                this.mapRoomNo = mapRoomNo;
                this.mapRoomName = mapRoomName;
                this.detailedTimeData = null;
            }

            public TimeData(String detailedTimeData) {
                this.dayOfWeek = null;
                this.section = null;
                this.sectionTo = null;
                this.mapLocation = null;
                this.mapRoomNo = null;
                this.mapRoomName = null;
                this.detailedTimeData = detailedTimeData;
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
                if (detailedTimeData != null)
                    return detailedTimeData;

                StringBuilder builder = new StringBuilder();
                if (dayOfWeek != null) builder.append(dayOfWeek);
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
            jsonBuilder.append("y", semester);
            jsonBuilder.append("dn", departmentName);

            jsonBuilder.append("sn", serialNumber);
            jsonBuilder.append("ca", courseAttributeCode);
            jsonBuilder.append("cs", courseSystemNumber);

            if (forGrade == null) jsonBuilder.append("g");
            else jsonBuilder.append("g", forGrade);
            jsonBuilder.append("co", forClass);
            jsonBuilder.append("cg", group);

            jsonBuilder.append("ct", courseType);

            jsonBuilder.append("cn", courseName);
            jsonBuilder.append("ci", courseNote);
            jsonBuilder.append("cl", courseLimit);
            jsonBuilder.append("tg", toJsonArray(tags));

            if (credits == null) jsonBuilder.append("c");
            else jsonBuilder.append("c", credits);
            if (required == null) jsonBuilder.append("r");
            else jsonBuilder.append("r", required);

            jsonBuilder.append("i", toJsonArray(instructors));

            if (selected == null) jsonBuilder.append("s");
            else jsonBuilder.append("s", selected);
            if (available == null) jsonBuilder.append("a");
            else jsonBuilder.append("a", available);

            jsonBuilder.append("t", toJsonArray(timeList));
            jsonBuilder.append("m", moodle);
            jsonBuilder.append("pe", btnPreferenceEnter);
            jsonBuilder.append("ac", btnAddCourse);
            jsonBuilder.append("pr", btnPreRegister);
            jsonBuilder.append("ar", btnAddRequest);
            return jsonBuilder.toString();
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public String getGroup() {
            return group;
        }

        public String getForClass() {
            return forClass;
        }

        public String getTimeString() {
            if (timeList == null)
                return null;

            StringBuilder builder = new StringBuilder();
            for (TimeData i : timeList) {
                if (i.detailedTimeData != null) continue;
                if (builder.length() > 0)
                    builder.append(',');

                builder.append('[').append(i.dayOfWeek).append(']');
                if (i.section != null) {
                    if (i.sectionTo != null)
                        builder.append(i.section).append('~').append(i.sectionTo);
                    else
                        builder.append(i.section);
                }
            }
            return builder.toString();
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
        private final String urlOrigin, search;
        private final CookieStore cookieStore;

        public SaveQueryToken(String urlOrigin, String search, CookieStore cookieStore) {
            this.urlOrigin = urlOrigin;
            this.search = search;
            this.cookieStore = cookieStore;
        }

        public String getUrl() {
            return urlOrigin + "/index.php?c=qry11215" + search;
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

    public static class DeptToken {
        private final String error;
        private final String urlEncodedId;
        private final CookieStore cookieStore;
        private final String deptNo;

        private DeptToken(String error, String deptNo, String urlEncodedId, CookieStore cookieStore) {
            this.error = error;
            this.urlEncodedId = urlEncodedId;
            this.cookieStore = cookieStore;
            this.deptNo = deptNo;
        }

        public String getID() {
            return urlEncodedId;
        }

        public String getError() {
            return error;
        }

        public CookieStore getCookieStore() {
            return cookieStore;
        }

        public String getUrl() {
            return courseNckuOrg + "/index.php?c=qry_all&m=result&i=" + urlEncodedId;
        }
    }

    private void search(SearchQuery searchQuery, ApiResponse response, CookieStore cookieStore) {
        try {
            boolean success = true;
            // get all course
            if (searchQuery.getAll) {
                ProgressBar progressBar = new ProgressBar(TAG + "Get All ");
                Logger.addProgressBar(progressBar);
                progressBar.setProgress(0f);
                AllDeptData allDeptData = getAllDeptData(cookieStore);
                if (allDeptData == null) {
                    response.addError(TAG + "Can not get crypt");
                    success = false;
                }
                // start getting dept
                else {
                    CountDownLatch taskLeft = new CountDownLatch(allDeptData.deptCount);
                    final int poolSize = 4;
                    ThreadPoolExecutor fetchPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize, new ThreadFactory("SearchT-"));
                    // Get cookie fragments
                    AllDeptData[] fragments = new AllDeptData[poolSize];
                    CountDownLatch fragmentsLeft = new CountDownLatch(poolSize);
                    for (int i = 0; i < poolSize; i++) {
                        int finalI = i;
                        fetchPool.submit(() -> {
                            fragments[finalI] = getAllDeptData(createCookieStore());
                            fragmentsLeft.countDown();
                        });
                    }
                    fragmentsLeft.await();

                    // Fetch data
                    int i = 0;
                    List<CourseData> courseDataList = new ArrayList<>();
                    Semaphore fetchPoolLock = new Semaphore(poolSize, true);
                    AtomicBoolean allSuccess = new AtomicBoolean(true);
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
                                if (allSuccess.get() && !getDeptCourseData(dept, fragment, false, response, courseDataList))
                                    allSuccess.set(false);
                                logger.log(Thread.currentThread().getName() + " " + dept + " done, " + (System.currentTimeMillis() - start) + "ms");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            fetchPoolLock.release();
                            taskLeft.countDown();
                            progressBar.setProgress((float) (allDeptData.deptCount - taskLeft.getCount()) / allDeptData.deptCount * 100f);
                        });
                    }
                    taskLeft.await();
                    fetchPool.shutdown();
                    if (!fetchPool.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                        fetchPool.shutdownNow();
                        logger.warn("FetchPool shutdown timeout");
                    }
                    success = allSuccess.get();
                }
                progressBar.setProgress(100f);
                Logger.removeProgressBar(progressBar);
            }
            // get listed serial
            else if (searchQuery.getSerial) {
                assert searchQuery.serialIdNumber != null;

                final int poolSize = 4;
                ThreadPoolExecutor fetchPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize, new ThreadFactory("SearchT-"));
                CountDownLatch taskLeft = new CountDownLatch(searchQuery.serialIdNumber.size());

                JsonObjectStringBuilder result = new JsonObjectStringBuilder();
                Semaphore fetchPoolLock = new Semaphore(poolSize, true);
                AtomicBoolean allSuccess = new AtomicBoolean(true);
                for (Map.Entry<String, Set<String>> i : searchQuery.serialIdNumber.entrySet()) {
                    fetchPoolLock.acquire();
                    fetchPool.submit(() -> {
                        List<CourseData> courseDataList = new ArrayList<>();
                        if (allSuccess.get() && !getQueryCourseData(searchQuery, i, cookieStore, response, courseDataList)) {
                            allSuccess.set(false);
                        }
                        result.appendRaw(i.getKey(), courseDataList.toString());
                        fetchPoolLock.release();
                        taskLeft.countDown();
                    });
                }
                taskLeft.await();
                fetchPool.shutdown();
                if (!fetchPool.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                    fetchPool.shutdownNow();
                    logger.warn("FetchPool shutdown timeout");
                }
                success = allSuccess.get();
                response.setData(result.toString());
            } else {
                List<CourseData> courseDataList = new ArrayList<>();
                success = getQueryCourseData(searchQuery, null, cookieStore, response, courseDataList);
                response.setData(courseDataList.toString());
            }

            response.setSuccess(success);
        } catch (Exception e) {
            e.printStackTrace();
            response.addError(TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
        }
    }

    public CookieStore createCookieStore() {
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        for (int i = 0; i < 3; i++) {
            try {
                HttpConnection.connect(courseNckuOrg + "/index.php")
                        .header("Connection", "keep-alive")
//                    .header("Referer", "https://course.ncku.edu.tw/index.php")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .execute();
                break;
            } catch (IOException e) {
                logger.errTrace(e);
            }
        }

//        logger.log(cookieStore.getCookies().toString());
        return cookieStore;
    }

    public static String setSearchIdCookie(SearchQuery searchQuery) {
        if (searchQuery.searchID == null && searchQuery.historySearchID == null)
            return "searchID=|";
        if (searchQuery.searchID == null)
            return "searchID=|" + searchQuery.historySearchID;
        if (searchQuery.historySearchID == null)
            return "searchID=" + searchQuery.searchID + '|';
        return "searchID=" + searchQuery.searchID + '|' + searchQuery.historySearchID;
    }

    public AllDeptData getAllDeptData(CookieStore cookieStore) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy());
        HttpResponseData httpResponseData = checkRobot(courseNckuOrg, request, cookieStore);
        if (httpResponseData.state != ResponseState.SUCCESS)
            return null;
        String body = httpResponseData.data;

        cosPreCheck(courseNckuOrg, body, cookieStore, null, proxyManager);

        Set<String> allDept = new HashSet<>();
        for (Element element : Jsoup.parse(body).getElementsByClass("pnl_dept"))
            allDept.addAll(element.getElementsByAttribute("data-dept").eachAttr("data-dept"));

        int cryptStart, cryptEnd;
        if ((cryptStart = body.indexOf("'crypt'")) == -1 ||
                (cryptStart = body.indexOf('\'', cryptStart + 7)) == -1 ||
                (cryptEnd = body.indexOf('\'', ++cryptStart)) == -1
        )
            return null;
        return new AllDeptData(body.substring(cryptStart, cryptEnd), allDept, cookieStore);
    }

    public AllDeptGroupData getAllDeptGroupData(CookieStore cookieStore) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy());
        HttpResponseData httpResponseData = checkRobot(courseNckuOrg, request, cookieStore);
        if (httpResponseData.state != ResponseState.SUCCESS)
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

    public boolean getDeptCourseData(String deptNo, AllDeptData allDeptData, boolean addUrSchoolCache,
                                     ApiResponse response, List<CourseData> courseDataList) {
        DeptToken deptToken = createDeptToken(deptNo, allDeptData);
        if (deptToken == null) {
            response.addError(TAG + "Can not create dept token");
            return false;
        }
        if (deptToken.error != null) {
            response.addError(TAG + "Can not create dept token: " + deptToken.error);
            return false;
        }

        return getDeptCourseData(deptToken, addUrSchoolCache,
                response, courseDataList);
    }

    public boolean getDeptCourseData(DeptToken deptToken, boolean addUrSchoolCache,
                                     @Nullable ApiResponse response, List<CourseData> courseDataList) {
        String searchResultBody = getDeptNCKU(deptToken);
        if (searchResultBody == null) {
            if (response != null)
                response.addWarn(TAG + "Dept.No " + deptToken.deptNo + " not found");
            return true;
        }

        Element table = findCourseTable(searchResultBody, "Dept.No " + deptToken.deptNo, response);
        if (table == null) {
            if (response != null)
                response.addError(TAG + "Can not find course table");
            return false;
        }

        parseCourseTable(
                table,
                searchResultBody,
                null,
                addUrSchoolCache,
                false,
                courseDataList
        );
        return true;
    }

    public DeptToken createDeptToken(String deptNo, AllDeptData allDeptData) {
        try {
            Connection.Response id = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
                    .header("Connection", "keep-alive")
                    .cookieStore(allDeptData.cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .method(Connection.Method.POST)
                    .requestBody("dept_no=" + deptNo + "&crypt=" + URLEncoder.encode(allDeptData.crypt, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            JsonObject idData = new JsonObject(id.body());
            String error = idData.getString("err");
            return new DeptToken(
                    error.length() > 0 ? error : null,
                    deptNo,
                    URLEncoder.encode(idData.getString("id"), "UTF-8"),
                    allDeptData.cookieStore
            );
        } catch (IOException e) {
            return null;
        }
    }

    private String getDeptNCKU(DeptToken deptToken) {
        Connection request = HttpConnection.connect(deptToken.getUrl())
                .header("Connection", "keep-alive")
                .header("Referer", "https://course.ncku.edu.tw/index.php?c=qry_all")
                .cookieStore(deptToken.cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .timeout(10000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = checkRobot(courseNckuOrg, request, deptToken.cookieStore);
        if (httpResponseData.state != ResponseState.SUCCESS)
            return null;

        cosPreCheckCookie(httpResponseData.data, deptToken.cookieStore);

        return httpResponseData.data;
    }

    private boolean getQueryCourseData(SearchQuery searchQuery, Map.Entry<String, Set<String>> getSerialNum,
                                       CookieStore cookieStore, ApiResponse response, List<CourseData> courseDataList) {
        // If getSerialNum is given, set query dept to get courses
        if (getSerialNum != null)
            searchQuery.deptNo = getSerialNum.getKey();

        // Create save query token
        SaveQueryToken saveQueryToken = createSaveQueryToken(searchQuery, cookieStore, response);
        if (saveQueryToken == null) {
            response.addError(TAG + "Can not create query token");
            return false;
        }

        return getQueryCourseData(searchQuery, saveQueryToken, getSerialNum,
                response, courseDataList);
    }

    public boolean getQueryCourseData(SearchQuery searchQuery, SaveQueryToken saveQueryToken, Map.Entry<String, Set<String>> getSerialNum,
                                      @Nullable ApiResponse response, List<CourseData> courseDataList) {
        // Get search result
        String searchResultBody = getCourseNCKU(saveQueryToken);
        if (searchResultBody == null) {
            if (response != null)
                response.addError(TAG + "Can not get result body");
            return false;
        }

        // Get searchID
        String searchID = getSearchID(searchResultBody, response);
        if (searchID == null) {
            if (response != null)
                response.addError(TAG + "Search result body not found");
            return false;
        }

        // Renew searchID
        if (searchQuery.historySearch) {
            String PHPSESSID = Cookie.getCookie("PHPSESSID", courseNckuOrgUri, saveQueryToken.cookieStore);
            if (PHPSESSID == null) {
                if (response != null)
                    response.addError(TAG + "Course query PHPSESSID cookie not found");
                return false;
            }
            searchQuery.historySearchID = searchID + ',' + PHPSESSID;
        } else
            searchQuery.searchID = searchID;

        Element table = findCourseTable(searchResultBody, "Query", response);
        if (table == null) {
            if (response != null)
                response.addError(TAG + "Can not find course table");
            return false;
        }

        parseCourseTable(
                table,
                searchResultBody,
                getSerialNum == null ? null : getSerialNum.getValue(),
                true,
                searchQuery.historySearch,
                courseDataList
        );

        return true;
    }

    public SaveQueryToken createSaveQueryToken(SearchQuery searchQuery, CookieStore cookieStore, ApiResponse response) {
        StringBuilder postData = new StringBuilder();
        String urlOrigin;
        CookieStore postCookieStore;
        // Build query
        try {
            if (searchQuery.historySearch) {
                urlOrigin = courseQueryNckuOrg;
                postCookieStore = new CookieManager().getCookieStore();

                if (searchQuery.historySearchID == null) {
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
//                        response.addError(TAG + "Course query PHPSESSID cookie not found");
//                        return null;
//                    }
//                    // Add searchID
//                    postData.append("id=").append(searchID);
//                    searchQuery.histroySearchID = searchID + ',' + PHPSESSID;
                    postData.append("id=");
                } else {
                    // searchID: "searchID,PHPSESSID"
                    int split = searchQuery.historySearchID.indexOf(',');
                    if (split == -1) {
                        response.addError(TAG + "Search id format error");
                        return null;
                    }
                    postData.append("id=").append(searchQuery.historySearchID, 0, split);
                    postCookieStore.add(courseQueryNckuUri, createHttpCookie("PHPSESSID", searchQuery.historySearchID.substring(split + 1), courseQueryNcku));
                }

                // Write post data
                postData.append("&syear_b=").append(searchQuery.yearBegin);
                postData.append("&syear_e=").append(searchQuery.yearEnd);
                postData.append("&sem_b=").append(searchQuery.semBegin);
                postData.append("&sem_e=").append(searchQuery.semEnd);
                if (searchQuery.courseName != null) postData.append("&cosname=").append(URLEncoder.encode(searchQuery.courseName, "UTF-8"));
                if (searchQuery.instructor != null) postData.append("&teaname=").append(URLEncoder.encode(searchQuery.instructor, "UTF-8"));
                if (searchQuery.deptNo != null) postData.append("&dept_no=").append(searchQuery.deptNo);

                // Required
                String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
                String[] requests = new String[]{
                        urlOrigin + "/js/bootstrap-select/css/bootstrap-select.min.css?" + date,
                        urlOrigin + "/js/jquery-ui-1.11.4.custom/jquery-ui.min.css?" + date,
                        urlOrigin + "/js/fontawesome/css/solid.min.css?" + date,
                        urlOrigin + "/js/fontawesome/css/regular.min.css?" + date, // 20230624
                        urlOrigin + "/js/fontawesome/css/fontawesome.min.css?" + date,
                        urlOrigin + "/js/fontawesome/css/fontawesome.min.css?" + date,
                        urlOrigin + "/js/epack/css/font-awesome.min.css?" + date,
                        urlOrigin + "/js/epack/css/elements/list.css?" + date,
                        urlOrigin + "/js/epack/css/elements/note.css?" + date,  // 20230625

                        urlOrigin + "/js/modernizr-custom.js?" + date,
                        urlOrigin + "/js/bootstrap-select/js/bootstrap-select.min.js?" + date,
                        urlOrigin + "/js/jquery.cookie.js?" + date,
                        urlOrigin + "/js/common.js?" + date,
                        urlOrigin + "/js/mis_grid.js?" + date,
                        urlOrigin + "/js/jquery-ui-1.11.4.custom/jquery-ui.min.js?" + date,
                        urlOrigin + "/js/performance.now-polyfill.js?" + date,
                        urlOrigin + "/js/mdb-sortable/js/addons/jquery-ui-touch-punch.min.js?" + date,
                        urlOrigin + "/js/jquery.taphold.js?" + date, // 20230625
                        urlOrigin + "/js/jquery.patch.js?" + date,
                };
                for (String url : requests) {
//                    logger.log("get require: " + url.substring(urlOrigin.length()));
                    try {
                        HttpConnection.connect(url)
                                .header("Connection", "keep-alive")
                                .cookieStore(postCookieStore)
                                .ignoreContentType(true)
                                .proxy(proxyManager.getProxy())
                                .execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                urlOrigin = courseNckuOrg;
                postCookieStore = cookieStore;
//                // Get searchID if it's null
//                if (searchQuery.searchID == null) {
//                    logger.log("Renew search id");
//
//                    Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
//                            .header("Connection", "keep-alive")
//                            .cookieStore(cookieStore)
//                            .ignoreContentType(true)
//                            .proxy(proxyManager.getProxy());
//                    ResponseData responseData = new ResponseData();
//                    HttpResponseData httpResponseData = checkRobot(courseNckuOrg, request, cookieStore, responseData);
//                    if (httpResponseData.state != ResponseState.SUCCESS)
//                        return null;
//
//                    cosPreCheck(courseNckuOrg, responseData.data, cookieStore, response, proxyManager);
//                    if ((searchQuery.searchID = getSearchID(responseData.data, response)) == null)
//                        return null;
//                }
//                postData.append("id=").append(URLEncoder.encode(searchQuery.searchID, "UTF-8"));

                // Write post data
                postData.append("id=");
                if (searchQuery.searchID != null) postData.append(URLEncoder.encode(searchQuery.searchID, "UTF-8"));
                if (searchQuery.courseName != null) postData.append("&cosname=").append(URLEncoder.encode(searchQuery.courseName, "UTF-8"));
                if (searchQuery.instructor != null) postData.append("&teaname=").append(URLEncoder.encode(searchQuery.instructor, "UTF-8"));
                if (searchQuery.dayOfWeek != null) postData.append("&wk=").append(searchQuery.dayOfWeek);
                if (searchQuery.deptNo != null) postData.append("&dept_no=").append(searchQuery.deptNo);
                if (searchQuery.grade != null) postData.append("&degree=").append(searchQuery.grade);
                if (searchQuery.sectionOfDay != null) postData.append("&cl=").append(searchQuery.sectionOfDay);
            }
        } catch (UnsupportedEncodingException e) {
            logger.errTrace(e);
            if (response != null)
                response.addError(TAG + "Unsupported encoding");
            return null;
        }

        // Post save query
        Connection request = HttpConnection.connect(urlOrigin + "/index.php?c=qry11215&m=save_qry")
                .header("Connection", "keep-alive")
                .cookieStore(postCookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .method(Connection.Method.POST)
                .requestBody(postData.toString())
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest");
        String body;
        try {
            body = request.execute().body();
            logger.log(body);

        } catch (IOException e) {
            logger.errTrace(e);
            if (response != null)
                response.addError(TAG + "Network error");
            return null;
        }

        if (body.equals("0")) {
            if (response != null)
                response.addError(TAG + "Condition not set");
            return null;
        }
        if (body.equals("1")) {
            if (response != null)
                response.addError(TAG + "Wrong condition format");
            return null;
        }
        if (body.equals("&m=en_query")) {
            if (response != null)
                response.addError(TAG + "Can not create save query");
            return null;
        }
        return new SaveQueryToken(urlOrigin, body, postCookieStore);
    }

    private String getCourseNCKU(SaveQueryToken saveQueryToken) {
//            logger.log(TAG + "Get search result");
        Connection request = HttpConnection.connect(saveQueryToken.getUrl())
                .header("Connection", "keep-alive")
                .cookieStore(saveQueryToken.cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .timeout(5000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = checkRobot(saveQueryToken.urlOrigin, request, saveQueryToken.cookieStore);
        if (httpResponseData.state != ResponseState.SUCCESS)
            return null;

        cosPreCheckCookie(httpResponseData.data, saveQueryToken.cookieStore);

        return httpResponseData.data;
    }

    private void cosPreCheckCookie(String pageBody, CookieStore cookieStore) {
        // Get cookie lock
        String PHPSESSID = Cookie.getCookie("PHPSESSID", courseNckuOrgUri, cookieStore);
        final Semaphore cookieLock;
        synchronized (cosPreCheckCookieLock) {
            // Remove free cookie lock
            cosPreCheckCookieLock.entrySet().removeIf(i -> i.getValue().availablePermits() == COS_PRE_CHECK_COOKIE_LOCK);
            // Get cookie lock
            Semaphore cookieLock0 = cosPreCheckCookieLock.get(PHPSESSID);
            if (cookieLock0 == null)
                cosPreCheckCookieLock.put(PHPSESSID, cookieLock0 = new Semaphore(COS_PRE_CHECK_COOKIE_LOCK, true));
            cookieLock = cookieLock0;
        }
        // Wait cookie and pool available
        try {
//            if (cookieLock.availablePermits() == 0)
//                logger.log("CosPreCheckCookie waiting");
            cookieLock.acquire();
            if (cosPreCheckPoolLock.availablePermits() == 0)
                logger.log("CosPreCheck waiting");
            cosPreCheckPoolLock.acquire();
        } catch (InterruptedException e) {
            logger.log(e);
        }
        // Submit cos pre check
        cosPreCheckPool.submit(() -> {
            cosPreCheck(courseNckuOrg, pageBody, cookieStore, null, proxyManager);
            cookieLock.release();
            cosPreCheckPoolLock.release();
        });
    }

    private Element findCourseTable(String html, String errorMessage, @Nullable ApiResponse response) {
        int resultTableStart;
        if ((resultTableStart = html.indexOf("<table")) == -1) {
            if (response != null)
                response.addError(TAG + errorMessage + " result table not found");
            return null;
        }
        // get table body
        int resultTableBodyStart, resultTableBodyEnd;
        if ((resultTableBodyStart = html.indexOf("<tbody>", resultTableStart + 7)) == -1 ||
                (resultTableBodyEnd = html.indexOf("</tbody>", resultTableBodyStart + 7)) == -1
        ) {
            if (response != null)
                response.addError(TAG + errorMessage + " result table body not found");
            return null;
        }

        // parse table
//            logger.log(TAG + "Parse course table");
        String resultBody = html.substring(resultTableBodyStart, resultTableBodyEnd + 8);
        return (Element) Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);
    }

    /**
     * @param tbody            Input: Course data table
     * @param searchResultBody Input: Full document for finding style
     * @param getSerialNumber  Serial number filter
     * @param addUrSchoolCache True for adding UrSchool cache
     * @param historySearch    Have to add offset if search course history
     * @param courseDataList   Output: To course data list
     */
    private void parseCourseTable(Element tbody, String searchResultBody, Set<String> getSerialNumber, boolean addUrSchoolCache, boolean historySearch, List<CourseData> courseDataList) {
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

        List<String> urSchoolCache = addUrSchoolCache ? new ArrayList<>() : null;
        int sectionOffset = historySearch ? 1 : 0;

        // get course list
        Elements courseList = tbody.getElementsByTag("tr");
        for (Element element : courseList) {
            Elements section = element.getElementsByTag("td");

            // Parse semester if history search
            String courseData_semester = null;
            if (historySearch) {
                String semester = section.get(0).text().trim();
                int split = semester.indexOf('-');
                if (split != -1)
                    courseData_semester = semester.substring(0, split) +
                            (semester.charAt(semester.length() - 1) == '1' ? '0' : '1');
            } else
                courseData_semester = allSemester;

            // get serial number
            List<Node> section1 = section.get(sectionOffset + 1).childNodes();
            String serialNumber = ((Element) section1.get(0)).text().trim();
            if (getSerialNumber != null) {
                String serialNumberStr = serialNumber.substring(serialNumber.indexOf('-') + 1);
                // Skip if we don't want
                if (!getSerialNumber.contains(serialNumberStr))
                    continue;
            }
            // Get serial number
            String courseData_serialNumber = serialNumber.length() == 0 ? null : serialNumber;

            // Get system number
            String courseData_courseSystemNumber = ((TextNode) section1.get(1)).text().trim();

            // Get attribute code
            String courseData_courseAttributeCode = null;
            String courseAttributeCode = ((TextNode) section1.get(3)).text().trim();
            if (courseAttributeCode.length() > 0) {
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
            if (courseData_departmentName.length() == 0) courseData_departmentName = null;

            // Get course type
            String courseData_courseType = section.get(sectionOffset + 3).text();

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
                    if (cache.length() == 0) continue;
                    if (section2c == 0)
                        courseData_forGrade = Integer.parseInt(cache);
                    else if (section2c == 1)
                        courseData_forClass = cache;
                    else
                        courseData_group = cache;
                }
            }

            // Get course name
            String courseData_courseName = section.get(sectionOffset + 4).getElementsByClass("course_name").get(0).text();

            // Get course note
            String courseData_courseNote = section.get(sectionOffset + 4).ownText().trim().replace("\"", "\\\"");
            if (courseData_courseNote.length() == 0) courseData_courseNote = null;

            // Get course limit
            String courseData_courseLimit = section.get(sectionOffset + 4).getElementsByClass("cond").text().trim().replace("\"", "\\\"");
            if (courseData_courseLimit.length() == 0) courseData_courseLimit = null;

            // Get course tags
            CourseData.TagData[] courseData_tags = null;
            Elements tagElements = section.get(sectionOffset + 4).getElementsByClass("label");
            if (tagElements.size() > 0) {
                courseData_tags = new CourseData.TagData[tagElements.size()];
                for (int i = 0; i < courseData_tags.length; i++) {
                    Element tagElement = tagElements.get(i);
                    // Get tag Color
                    String tagColor;
                    String styleOverride = tagElement.attr("style");
                    if (styleOverride.length() > 0) {
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
                            logger.log("Unknown tag color: " + tagType);
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
            if (section5.size() > 0 && (section5Str = section5.get(0).text().trim()).length() > 0) {
                courseData_credits = Float.parseFloat(section5Str);
                String cache = section5.get(1).text().trim();
                courseData_required = cache.equals("必修") || cache.equals("Required");
            }

            // Get instructor name
            String[] courseData_instructors = null;
            String instructors = section.get(sectionOffset + 6).text();
            if (instructors.length() > 0 && !instructors.equals("未定")) {
                instructors = instructors.replace("*", "");
                courseData_instructors = instructors.split(" ");
                // Add urSchool cache
                if (addUrSchoolCache && urSchool != null) {
                    urSchoolCache.addAll(Arrays.asList(courseData_instructors));
                    // Flush to add urSchool cache
                    if (urSchoolCache.size() > 10) {
                        urSchool.addInstructorCache(urSchoolCache.toArray(new String[0]));
                        urSchoolCache.clear();
                    }
                }
            }
            // Add urSchool cache
            if (addUrSchoolCache && urSchool != null) {
                // Flush to add urSchool cache
                if (urSchoolCache.size() > 0)
                    urSchool.addInstructorCache(urSchoolCache.toArray(new String[0]));
            }

            // Get time list
            CourseData.TimeData[] courseData_timeList = null;
            List<CourseData.TimeData> timeDataList = new ArrayList<>();
            Byte timeCacheDayOfWeek = null;
            Character timeCacheSection = null, timeCacheSectionTo = null;
            String timeCacheMapLocation = null, timeCacheMapRoomNo = null, timeCacheMapRoomName = null;
            int timeParseState = 0;
            for (Node node : section.get(sectionOffset + 8).childNodes()) {
                // Parse state 0, find day of week and section
                if (timeParseState == 0) {
                    timeParseState++;
                    String text;
                    // Check if data exist
                    if (node instanceof TextNode && (text = ((TextNode) node).text().trim()).length() > 0) {
                        // Get dayOfWeek, format: [1]2~3
                        if (text.length() > 2 && text.charAt(2) == ']') {
                            timeCacheDayOfWeek = (byte) ((text.charAt(1) - '0') - 1);
                            // Get section
                            if (text.length() > 3) {
                                timeCacheSection = text.charAt(3);
                                // Get section end
                                if (text.length() > 5 && text.charAt(4) == '~') {
                                    timeCacheSectionTo = text.charAt(5);
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
                    if ((attribute = node.attr("href")).length() > 0) {
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
                            timeCacheMapRoomName = ((Element) node).text().trim();
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
            if (timeDataList.size() > 0)
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
            Integer courseData_selected = count[0].length() == 0 ? null : Integer.parseInt(count[0]);
            Integer courseData_available = count.length < 2 ? null :
                    (count[1].equals("額滿") || count[1].equals("full")) ? 0 :
                            (count[1].equals("不限") || count[1].equals("unlimited")) ? -1 :
                                    (count[1].startsWith("洽") || count[1].startsWith("please connect")) ? -2 :
                                            Integer.parseInt(count[1]);

            // Get moodle, outline
            String courseData_moodle = null;
            Elements linkEle = section.get(sectionOffset + 9).getElementsByAttribute("href");
            if (linkEle.size() > 0) {
                for (Element ele : linkEle) {
                    String href = ele.attr("href");
                    if (href.startsWith("javascript"))
                        // moodle link
                        courseData_moodle = href.substring(19, href.length() - 3).replace("','", ",");
//                    else
//                        // outline link
//                        courseData_courseOutline = href.substring(href.indexOf("?") + 1);
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
                    courseData_courseType,
                    courseData_courseName, courseData_courseNote, courseData_courseLimit, courseData_tags,
                    courseData_credits, courseData_required,
                    courseData_instructors,
                    courseData_selected, courseData_available,
                    courseData_timeList,
                    courseData_moodle,
                    courseData_btnPreferenceEnter, courseData_btnAddCourse, courseData_btnPreRegister, courseData_btnAddRequest);
            courseDataList.add(courseData);
        }
    }

    private String getSearchID(String body, ApiResponse response) {
        // get entry function
        int searchFunctionStart = body.indexOf("function setdata()");
        if (searchFunctionStart == -1) {
            if (response != null)
                response.addError(TAG + "Search function not found");
            return null;
        } else searchFunctionStart += 18;

        int idStart, idEnd;
        if ((idStart = body.indexOf("'id'", searchFunctionStart)) == -1 ||
                (idStart = body.indexOf('\'', idStart + 4)) == -1 ||
                (idEnd = body.indexOf('\'', idStart + 1)) == -1
        ) {
            if (response != null)
                response.addError(TAG + "Search id not found");
            return null;
        }
        return body.substring(idStart + 1, idEnd);
    }

    public HttpResponseData checkRobot(String urlOrigin, Connection request, CookieStore cookieStore) {
        boolean networkError = false;
        for (int i = 0; i < MAX_ROBOT_CHECK_TRY; i++) {
            String response;
            try {
                response = request.execute().body();
                networkError = false;
            } catch (IOException e) {
                logger.errTrace(e);
                networkError = true;
                // Retry
                continue;
//                return new HttpResponseData(ResponseState.NETWORK_ERROR);
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
            for (; i < MAX_ROBOT_CHECK_TRY; i++) {
                String code = robotCode.getCode(urlOrigin + "/index.php?c=portal&m=robot", cookieStore, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA);
                logger.warn("Crack code: " + code);
                try {
                    String result = HttpConnection.connect(urlOrigin + "/index.php?c=portal&m=robot")
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
                    if (new JsonObject(result).getBoolean("status"))
                        break;
//            System.out.println(new JsonObject(allDeptRes.body()).toStringBeauty());
                } catch (IOException | JsonException e) {
                    logger.errTrace(e);
                }
            }
        }
        if (networkError)
            return new HttpResponseData(ResponseState.NETWORK_ERROR);
        else
            return new HttpResponseData(ResponseState.ROBOT_CODE_CRACK_ERROR);
    }
}
