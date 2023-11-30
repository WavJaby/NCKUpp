package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.api.search.Search;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Cookie;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Main.courseNcku;
import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.getFileFromPath;
import static com.wavjaby.lib.Lib.readFileToString;

@RequestMapping("/api/v0")
public class AllDept implements Module {
    private static final String TAG = "AllDept";
    private static final Logger logger = new Logger(TAG);
    private static final String ALLDEPT_FILE_PATH = "./api_file/alldept.json";
    private File allDeptFile;
    private final Search search;
    private String deptGroup;

    public AllDept(Search search) {
        this.search = search;
    }

    @Override
    public void start() {
        // Read cache
        allDeptFile = getFileFromPath(ALLDEPT_FILE_PATH, true);
        if (allDeptFile.exists()) {
            deptGroup = readFileToString(allDeptFile, false, StandardCharsets.UTF_8);
        }

        // Get new dept data
        CookieStore cookieStore = new CookieManager().getCookieStore();
        cookieStore.add(courseNckuOrgUri, Cookie.createHttpCookie("PHPSESSID", "ID", courseNcku));
        Search.AllDeptGroupData allDept = search.getAllDeptGroupData(cookieStore);
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
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setData(deptGroup);

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return apiResponse;
    }
}
