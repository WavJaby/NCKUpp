package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Cookie;
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

public class AllDept implements EndpointModule {
    private static final String TAG = "[AllDept]";
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
        logger.log("Get " + allDept.getDeptCount() + " dept");
        if (allDept.getDeptCount() > 0) {
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

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setData(deptGroup);

        packCourseLoginStateCookie(req, loginState, cookieStore);
        apiResponse.sendResponse(req);

        logger.log("Get all dept " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
