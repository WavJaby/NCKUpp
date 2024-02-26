package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.api.search.RobotCheck;
import com.wavjaby.api.search.Search;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
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
import java.util.*;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class AllDept implements Module {
    private static final String TAG = "AllDept";
    private static final Logger logger = new Logger(TAG);
    private static final String ALL_DEPT_FILE_PATH = "./api_file/allDept.json";
    public static final Map<String, String> deptIdMap = new HashMap<>();
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
        allDeptFile = getFileFromPath(ALL_DEPT_FILE_PATH, true, true);
        if (allDeptFile.exists()) {
            deptGroup = readFileToString(allDeptFile, false, StandardCharsets.UTF_8);
            assert deptGroup != null;
            JsonObject jsonObject = new JsonObject(deptGroup);
            for (Object i : jsonObject.getArray("deptGroup")) {
                for (Object j : ((JsonObject) i).getArray("dept")) {
                    deptIdMap.put(((JsonArray) j).getString(1), ((JsonArray) j).getString(0));
                    deptIdMap.put(((JsonArray) j).getString(2), ((JsonArray) j).getString(0));
                }
            }
        }

        // Get new dept data
        CookieStore cookieStore = new CookieManager().getCookieStore();
        Cookie.addCookie("PHPSESSID", "NCKUpp", courseNckuOrgUri, cookieStore);
        Cookie.addCookie("cos_lang", "cht", courseNckuOrgUri, cookieStore);
        Search.AllDeptGroupData allDeptTW = getAllDeptGroupData(cookieStore);
        Cookie.addCookie("cos_lang", "eng", courseNckuOrgUri, cookieStore);
        Search.AllDeptGroupData allDeptEN = getAllDeptGroupData(cookieStore);
        if (allDeptTW == null || allDeptTW.getDeptCount() == 0 ||
                allDeptEN == null || allDeptEN.getDeptCount() == 0) {
            logger.err("Failed to get all departments data");
            return;
        }
        if (allDeptTW.getDeptCount() != allDeptEN.getDeptCount()) {
            logger.err("Failed to get all departments data, department count not match");
            return;
        }

        int count = allDeptTW.getDeptCount();
        logger.log("Get " + count + " departments");


        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        JsonArrayStringBuilder outDeptGroup = new JsonArrayStringBuilder();
        List<Search.AllDeptGroupData.Group> groupsTW = allDeptTW.getGroups();
        List<Search.AllDeptGroupData.Group> groupsEN = allDeptEN.getGroups();
        List<String> conflictName = new ArrayList<>();
        Map<String, String> newDeptIdMap = new HashMap<>();

        for (int i = 0; i < groupsTW.size(); i++) {
            Search.AllDeptGroupData.Group groupTW = groupsTW.get(i);
            Search.AllDeptGroupData.Group groupEN = groupsEN.get(i);
            JsonObjectStringBuilder groupOut = new JsonObjectStringBuilder();
            groupOut.append("nameTW", groupTW.name);
            boolean emptyNameEN = true;
            for (int j = 0; j < groupEN.name.length(); j++)
                if (groupEN.name.charAt(j) != '*') {
                    emptyNameEN = false;
                    break;
                }
            groupOut.append("nameEN", emptyNameEN ? groupTW.name : groupEN.name);

            JsonArrayStringBuilder outDeptData = new JsonArrayStringBuilder();
            for (int j = 0; j < groupTW.dept.size(); j++) {
                Search.AllDeptGroupData.DeptData deptTW = groupTW.dept.get(j);
                Search.AllDeptGroupData.DeptData deptEN = groupEN.dept.get(j);
                outDeptData.append(new JsonArrayStringBuilder()
                        .append(deptTW.id).append(deptTW.name).append(deptEN.name));
                if (newDeptIdMap.containsKey(deptTW.name)) conflictName.add(deptTW.name);
                else newDeptIdMap.put(deptTW.name, deptTW.id);

                if (newDeptIdMap.containsKey(deptEN.name)) conflictName.add(deptEN.name);
                else newDeptIdMap.put(deptEN.name, deptEN.id);
            }
            groupOut.append("dept", outDeptData);
            outDeptGroup.append(groupOut);
        }
        if (!conflictName.isEmpty()) {
            logger.warn("Conflict name: " + (conflictName.size() / 2));
            for (String s : conflictName)
                newDeptIdMap.remove(s);
        }
        deptIdMap.clear();
        deptIdMap.putAll(newDeptIdMap);

        builder.append("deptGroup", outDeptGroup);
        builder.append("deptCount", count);
        deptGroup = builder.toString();
        try {
            FileWriter fileWriter = new FileWriter(allDeptFile);
            fileWriter.write(deptGroup);
            fileWriter.close();
        } catch (IOException e) {
            logger.errTrace(e);
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
        if (!httpResponseData.isSuccess() || httpResponseData.data == null)
            return null;
        String body = processIframe(httpResponseData.data, cookieStore, proxyManager, robotCheck);
        if (body != null) {
            String baseUrl = findStringBetween(body, "<base", "href=\"", "\"");
            if (baseUrl == null) {
                logger.err("Base url not found");
                return null;
            }
            cosPreCheck(baseUrl, body, cookieStore, null, proxyManager);
        }

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
