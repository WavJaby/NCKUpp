package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.search.RobotCheck;
import com.wavjaby.api.search.Search;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Cookie;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class AllDept implements Module {
    private static final String TAG = "AllDept";
    private static final Logger logger = new Logger(TAG);
    private static final String ALLDEPT_FILE_PATH = "./api_file/alldept.json";
    private final RobotCheck robotCheck;
    private final ProxyManager proxyManager;
    private File allDeptFile;
    private String deptGroup;

    public AllDept(RobotCheck robotCheck, ProxyManager proxyManager) {
        this.robotCheck = robotCheck;
        this.proxyManager = proxyManager;
    }

    @Override
    public void start() {
        // Read cache
        allDeptFile = getFileFromPath(ALLDEPT_FILE_PATH, true, true);
        if (allDeptFile.exists()) {
            deptGroup = readFileToString(allDeptFile, false, StandardCharsets.UTF_8);
        }

        // Get new dept data
        CookieStore cookieStore = new CookieManager().getCookieStore();
        Cookie.addCookie("PHPSESSID", "NCKUpp", courseNckuOrgUri, cookieStore);
        Search.AllDeptGroupData allDept = getAllDeptGroupData(cookieStore);
        if (allDept != null && allDept.getDeptCount() > 0) {
            logger.log("Get " + allDept.getDeptCount() + " dept");
            deptGroup = allDept.toString();
            try {
                FileWriter fileWriter = new FileWriter(allDeptFile);
                fileWriter.write(deptGroup);
                fileWriter.close();
            } catch (IOException e) {
                logger.errTrace(e);
            }
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @SuppressWarnings("unused")
    @RequestMapping("/alldept")
    public RestApiResponse getAlldept(HttpExchange req) {
        long startTime = System.currentTimeMillis();
//        CookieStore cookieStore = new CookieManager().getCookieStore();
//        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setData(deptGroup);

//        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return apiResponse;
    }

    private String getAllDeptPage(CookieStore cookieStore) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy())
                .userAgent(Main.USER_AGENT)
                .timeout(5000);
        HttpResponseData httpResponseData = robotCheck.sendRequest(courseNckuOrg, request, cookieStore);
        if (!httpResponseData.isSuccess())
            return null;
        String body = httpResponseData.data;

        cosPreCheck(courseNckuOrg, body, cookieStore, null, proxyManager);

        return body;
    }

    public Search.AllDeptData getAllDeptData(CookieStore cookieStore, Search.SearchResult result) {
        String allDeptPage = getAllDeptPage(cookieStore);
        if (allDeptPage == null) {
            result.errorFetch("Failed to fetch all dept data");
            return null;
        }

        Set<String> allDept = new HashSet<>();
        for (Element element : Jsoup.parse(allDeptPage).body().getElementsByClass("pnl_dept"))
            allDept.addAll(element.getElementsByAttribute("data-dept").eachAttr("data-dept"));

        String crypt = findStringBetween(allDeptPage, "'crypt'", "'", "'");
        if (crypt == null) {
            result.errorParse("Get all dept 'crypt' data not found");
            return null;
        }
        return new Search.AllDeptData(crypt, allDept, cookieStore);
    }

    public Search.AllDeptGroupData getAllDeptGroupData(CookieStore cookieStore) {
        String allDeptPage = getAllDeptPage(cookieStore);
        if (allDeptPage == null)
            return null;

        int total = 0;
        List<Search.AllDeptGroupData.Group> groups = new ArrayList<>();
        for (Element deptGroup : Jsoup.parse(allDeptPage).getElementsByClass("pnl_dept")) {
            List<Search.AllDeptGroupData.DeptData> dept = new ArrayList<>();
            for (Element deptEle : deptGroup.getElementsByAttribute("data-dept")) {
                String deptName = deptEle.text();
                dept.add(new Search.AllDeptGroupData.DeptData(
                        deptEle.attr("data-dept"),
                        deptName.substring(deptName.indexOf(')') + 1)
                ));
                total++;
            }
            String groupName = deptGroup.getElementsByClass("panel-heading").text();
            groups.add(new Search.AllDeptGroupData.Group(groupName, dept));
        }

        return new Search.AllDeptGroupData(groups, total);
    }
}
