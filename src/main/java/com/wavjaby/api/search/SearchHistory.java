package com.wavjaby.api.search;

import com.wavjaby.Main;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.RobotCode;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.Cookie;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.lib.Lib;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wavjaby.Main.*;
import static com.wavjaby.lib.Lib.cosPreCheck;
import static com.wavjaby.lib.Lib.findStringBetween;

public class SearchHistory {
    private static final String FILE_PATH = "api_file/CourseHistory";
    private static final Logger logger = new Logger("SearchHistory");
    private final ProxyManager proxyManager;
    private final RobotCheck robotCheck;

    private enum Language {
        TW("cht"),
        EN("eng"),
        ;

        public final String code;

        Language(String code) {
            this.code = code;
        }
    }

    public SearchHistory(ProxyManager proxyManager, RobotCheck robotCheck) {
        this.proxyManager = proxyManager;
        this.robotCheck = robotCheck;
    }

    public static void main(String[] args) {
        PropertiesReader serverSettings = new PropertiesReader("./server.properties");
        ProxyManager proxyManager = new ProxyManager(serverSettings);
        proxyManager.start();
        RobotCode robotCode = new RobotCode(serverSettings, proxyManager);
        robotCode.start();
        RobotCheck robotCheck = new RobotCheck(robotCode, proxyManager);
        new SearchHistory(proxyManager, robotCheck).search();
        robotCode.stop();
        proxyManager.stop();
    }

    public void search() {
        Language language = Language.EN;
        CourseHistorySearchQuery historySearch = new CourseHistorySearchQuery(
                112, 2, 112, 2
        );

        CookieStore cookieStore = new CookieManager().getCookieStore();
        Cookie.addCookie("cos_lang", language.code, courseQueryNckuOrgUri, cookieStore);

        long start = System.currentTimeMillis();
        // Get all dept
        String searchId;
        logger.log("Getting all department");
        List<String> allDeptNo;
        try {
            String page = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=en_query")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .execute().body();
            String allDeptStr = findStringBetween(page, "var collist", "=", "<");
            if (allDeptStr == null) {
                logger.err("Department data not found");
                return;
            }
            cosPreCheck(courseQueryNckuOrg, page, cookieStore, null, proxyManager);
            allDeptNo = new ArrayList<>();
            JsonObject data = new JsonObject(allDeptStr);
            for (Object object : data.getMap().values()) {
                JsonObject i = (JsonObject) object;
                for (Object deptList : i.getArray("deptlist")) {
                    JsonObject j = (JsonObject) deptList;
                    allDeptNo.add(j.getString("dept_no"));
                }
            }
//            logger.log(allDeptNo);
            searchId = Search.getSearchID(page, new Search.SearchResult());
            if (searchId == null) {
                logger.err("Search id not found");
                return;
            }
            logger.log(allDeptNo.size() + " department find");
        } catch (IOException e) {
            logger.errTrace(e);
            return;
        }

        logger.log("Getting resource");
        getResources(cookieStore);


        ThreadPoolExecutor resultFetchPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        Semaphore semaphore = new Semaphore(resultFetchPool.getMaximumPoolSize());
        CountDownLatch tasks = new CountDownLatch(allDeptNo.size());
        JsonArray courseDataList = new JsonArray();
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int i = 0; i < allDeptNo.size(); ) {
            // Abort remaining task if any error
            if (failed.get()) {
                tasks.countDown();
                i++;
                continue;
            }

            // Create save query
            String deptNo = allDeptNo.get(i);
            long queryStart = System.currentTimeMillis();
            String query = createSearchQuery(deptNo, historySearch, searchId, cookieStore);
            logger.log("Searching dept: " + deptNo + ", " + (System.currentTimeMillis() - queryStart) + "ms");
            // Save query error
            if (query == null) {
                failed.set(true);
                continue;
            }
            // Simulate page reload
            if (query.equals("&m=en_query")) {
                logger.warn("Can not create save query");
                getResources(cookieStore);
                continue;
            }

            // Fetch result
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                logger.errTrace(e);
                failed.set(true);
                continue;
            }
            resultFetchPool.execute(() -> {
                long start1 = System.currentTimeMillis();
                Search.SearchResult result = getSearchResult(deptNo, query, cookieStore);
                logger.log("Fetching result " + (System.currentTimeMillis() - start1) + "ms");
                semaphore.release();
                if (!result.isSuccess()) {
                    logger.err(result.getErrorString());
                    failed.set(true);
                } else if (failed.get()) {
                    logger.warn("Skip dept: " + deptNo + ", due to previous error");
                } else {
                    synchronized (courseDataList) {
                        courseDataList.add(result.getCourseDataList());
                    }
                }
                tasks.countDown();
            });
            i++;
        }

        try {
            tasks.await();
            resultFetchPool.shutdown();
            if (!resultFetchPool.awaitTermination(1000, TimeUnit.MILLISECONDS))
                resultFetchPool.shutdownNow();
        } catch (InterruptedException e) {
            logger.errTrace(e);
            return;
        }
        if (failed.get()) {
            return;
        }

        logger.log("Get " + courseDataList.length + " course, use " + ((System.currentTimeMillis() - start) / 1000) + "s");

        File rawHistoryFileFolder = Lib.getDirectoryFromPath(FILE_PATH, true);
        if (!rawHistoryFileFolder.isDirectory())
            return;

        try {
            FileWriter rawHistoryFile = new FileWriter(new File(rawHistoryFileFolder,
                    historySearch + "_" + language.name() + ".json"
            ));
            rawHistoryFile.write(courseDataList.toString());
            rawHistoryFile.close();
        } catch (IOException e) {
            logger.errTrace(e);
        }

    }

    private String createSearchQuery(String deptNo, CourseHistorySearchQuery historySearch, String searchId, CookieStore cookieStore) {
        String postData = "id=" + searchId +
                "&syear_b=" + historySearch.yearBegin +
                "&syear_e=" + historySearch.yearEnd +
                "&sem_b=" + historySearch.semBegin +
                "&sem_e=" + historySearch.semEnd +
                "&dept_no=" + deptNo;

        Connection request = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215&m=save_qry")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .method(Connection.Method.POST)
                .requestBody(postData)
                .timeout(10000)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest");
        String body;
        try {
            body = request.execute().body();
        } catch (IOException e) {
            logger.errTrace(e);
            return null;
        }
        if (body.equals("0")) {
            logger.err("Condition not set");
            return null;
        }
        if (body.equals("1")) {
            logger.err("Wrong condition format");
            return null;
        }
        return body;
    }

    private Search.SearchResult getSearchResult(String deptNo, String query, CookieStore cookieStore) {
        Search.SearchResult result = new Search.SearchResult();

        Connection request = HttpConnection.connect(courseQueryNckuOrg + "/index.php?c=qry11215" + query)
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .timeout(9000)
                .maxBodySize(20 * 1024 * 1024);
        HttpResponseData httpResponseData = robotCheck.sendRequest(courseQueryNckuOrg, request, cookieStore);
        String searchResultBody = httpResponseData.data;
        if (!httpResponseData.isSuccess()) {
            result.errorFetch("Failed fetch result");
            return result;
        }

        cosPreCheck(courseQueryNckuOrg, searchResultBody, cookieStore, null, proxyManager);

        Element table = Search.findCourseTable(searchResultBody, "Dept " + deptNo, result);
        if (table == null)
            return result;

        String searchId = Search.getSearchID(searchResultBody, result);
        if (searchId == null)
            return result;

        result.setSearchID(searchId);

        Search.parseCourseTable(
                table,
                searchResultBody,
                null,
                true,
                result
        );

        return result;
    }

    private void getResources(CookieStore cookieStore) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String[] requests = new String[]{
                courseQueryNckuOrg + "/js/modernizr-custom.js?" + date,
                courseQueryNckuOrg + "/js/bootstrap-select/css/bootstrap-select.min.css?" + date,
                courseQueryNckuOrg + "/js/bootstrap-select/js/bootstrap-select.min.js?" + date,
                courseQueryNckuOrg + "/js/jquery.cookie.js?" + date,
                courseQueryNckuOrg + "/js/common.js?" + date,
                courseQueryNckuOrg + "/js/mis_grid.js?" + date,
                courseQueryNckuOrg + "/js/jquery-ui/jquery-ui.min.css?" + date,
                courseQueryNckuOrg + "/js/jquery-ui/jquery-ui.min.js?" + date,
                courseQueryNckuOrg + "/js/fontawesome/css/solid.min.css?" + date,
                courseQueryNckuOrg + "/js/fontawesome/css/regular.min.css?" + date,
                courseQueryNckuOrg + "/js/fontawesome/css/fontawesome.min.css?" + date,
                courseQueryNckuOrg + "/js/epack/css/font-awesome.min.css?" + date,
                courseQueryNckuOrg + "/js/epack/css/elements/list.css?" + date,
                courseQueryNckuOrg + "/js/epack/css/elements/note.css?" + date,
                courseQueryNckuOrg + "/js/performance.now-polyfill.js?" + date,
                courseQueryNckuOrg + "/js/mdb-sortable/js/addons/jquery-ui-touch-punch.min.js?" + date,
                courseQueryNckuOrg + "/js/jquery.taphold.js?" + date,
                courseQueryNckuOrg + "/js/jquery.patch.js?" + date,
        };
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        for (String url : requests) {
            executor.execute(() -> {
                try {
                    HttpConnection.connect(url)
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .userAgent(Main.USER_AGENT)
                            .execute();
                } catch (IOException e) {
                    logger.errTrace(e);
                }
//                logger.log("get: " + url.substring(courseQueryNckuOrg.length()));
            });
        }
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                executor.shutdownNow();
        } catch (InterruptedException e) {
            logger.errTrace(e);
        }
    }

    private synchronized void stopAlTask(CountDownLatch countDownLatch) {
        while (countDownLatch.getCount() > 0)
            countDownLatch.countDown();
    }
}
