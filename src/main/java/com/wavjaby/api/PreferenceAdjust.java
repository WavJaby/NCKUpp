package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class PreferenceAdjust implements EndpointModule {
    private static final String TAG = "[PreferenceAdjust]";
    private static final Logger logger = new Logger(TAG);
    public static final HashMap<String, Integer> dayOfWeekTextToInt = new HashMap<String, Integer>() {{
        put("MON", 0);
        put("TUE", 1);
        put("WED", 2);
        put("THU", 3);
        put("FRI", 4);
        put("SAT", 5);
        put("SUN", 6);
    }};
    private final ProxyManager proxyManager;

    public PreferenceAdjust(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }


    @Override
    public void start() {
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
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();

            preferenceAdjust(apiResponse, cookieStore);
            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
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
            req.close();
            e.printStackTrace();
        }
        logger.log("Preference adjust " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void preferenceAdjust(ApiResponse apiResponse, CookieStore cookieStore) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21342")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true)
                .proxy(proxyManager.getProxy());

        Document document;
        try {
            document = conn.get();
        } catch (IOException e) {
            e.printStackTrace();
            apiResponse.addError(TAG + "Network error");
            return;
        }
        Element body = document.body();

        // Get action key
        Element action = body.getElementById("cos21342_action");
        Element remove = body.getElementById("cos21342_remove");
        Element group2action = body.getElementById("cos_group2_action");
        if (action == null) {
            apiResponse.addError(TAG + "Action key not found");
            return;
        }
        if (remove == null) {
            apiResponse.addError(TAG + "Action remove key not found");
            return;
        }

        // All data need adjust
        Element allTabs = body.getElementsByClass("tab-content").first();
        if (allTabs == null) {
            apiResponse.addError(TAG + "No preference adjust not found");
            return;
        }

        // Get list item
        // TODO: Handle multiple list
        //      for (Element adjLists : allTabs)
        String id = allTabs.firstElementChild().id();

        Element adjList = allTabs.getElementById("list_" + id);
        if (adjList == null) {
            apiResponse.addError(TAG + "Adjust list not found");
            return;
        }

        Element adjListNameElement = body.getElementById("tc-tabs_" + id);
        if (adjListNameElement == null) {
            apiResponse.addError(TAG + "Adjust list name type not found");
            return;
        }
        String adjListName = adjListNameElement.text();

        // Get type
        if (!adjList.hasAttr("data_type")) {
            apiResponse.addError(TAG + "Adjust list type not found");
            return;
        }
        String type = adjList.attr("data_type");
//        logger.log("type: " + type);

        JsonObjectStringBuilder adjustList = new JsonObjectStringBuilder();
        adjustList.append("name", adjListName);
        adjustList.append("type", type);

        JsonArrayStringBuilder items = new JsonArrayStringBuilder();
        for (Element item : adjList.children()) {
            // Check item key exist
            if (!item.hasAttr("data_item")) {
                apiResponse.addError(TAG + "Adjust list item key not found");
                return;
            }

            // Parse course name
            Element courseNameElement = item.getElementsByClass("course_name").first();
            if (courseNameElement == null) {
                apiResponse.addError(TAG + "Adjust list item course name not found");
                return;
            }
            String rawCourseName = courseNameElement.text().trim();
            int split = rawCourseName.indexOf('】');
            if (split == -1) {
                apiResponse.addError(TAG + "Adjust list item wrong format: " + rawCourseName);
                return;
            }

            // Parse course info
            Node nextSibling = courseNameElement.nextSibling();
            if (!(nextSibling instanceof TextNode)) {
                apiResponse.addError(TAG + "Adjust list item course info not found: " + rawCourseName);
                return;
            }
            String courseInfo = ((TextNode) nextSibling).text().trim();
            String requireText = null, creditsText = null, time = null;
            int index = courseInfo.indexOf(' ');
            if (index != -1) {
                requireText = courseInfo.substring(1, index);
                int index2 = courseInfo.indexOf(' ', index + 1);
                if (index2 != -1)
                    creditsText = courseInfo.substring(index + 1, index2);
            }
            if ((index = courseInfo.indexOf(')')) != -1 &&
                    (index = courseInfo.indexOf(' ', index + 1)) != -1 &&
                    index != courseInfo.length() - 1) {
                int index2 = courseInfo.indexOf('.', index + 1);
                String dayOfWeekText = courseInfo.substring(index + 1, index2);
                Integer dayOfWeek = dayOfWeekTextToInt.get(dayOfWeekText);
                if (dayOfWeek == null) {
                    apiResponse.addError(TAG + "Adjust list item time format error: " + courseInfo);
                    return;
                }
                int index3 = courseInfo.indexOf('-', index2 + 1);
                if (index3 != -1) {
                    time = String.valueOf(dayOfWeek) + ',' +
                            courseInfo.substring(index2 + 1, index3).trim() + ',' +
                            courseInfo.substring(index3 + 1).trim()
                    ;
                } else {
                    time = String.valueOf(dayOfWeek) + ',' +
                            courseInfo.substring(index2 + 1).trim() + ',';
                }
            }
            if (requireText == null || creditsText == null) {
                apiResponse.addError(TAG + "Adjust list item content not found: " + courseInfo);
                return;
            }

            String itemKey = item.attr("data_item");
            String serialNumber = rawCourseName.substring(1, split);
            String courseName = rawCourseName.substring(split + 1);
            boolean require = requireText.equals("必修") || requireText.equals("REQUIRED");
            float credits = Float.parseFloat(creditsText);
            items.append(new JsonObjectStringBuilder()
                    .append("key", itemKey)
                    .append("sn", serialNumber)
                    .append("name", courseName)
                    .append("require", require)
                    .append("credits", credits)
                    .append("time", time)
            );
        }
        adjustList.append("items", items);
        apiResponse.setData(adjustList.toString());


//        int tableStart;
//        if ((tableStart = body.indexOf("id=\"list_A9_000_999\"")) == -1) {
//            apiResponse.addError(TAG + "list body not found");
//        }
//        tableStart += 20;
//
//        // Get data type
//        int datatypeStart, datatypeEnd;
//        if ((datatypeStart = body.indexOf("data_type=\"", tableStart)) == -1 ||
//                (datatypeEnd = body.indexOf('"', datatypeStart += 11)) == -1) {
//            apiResponse.addError(TAG + "datatype not found");
//            return;
//        }
//        String datatype = body.substring(datatypeStart, datatypeEnd);
//
//        // Get action key
//        int actionKeyStart, actionKeyEnd;
//        if ((actionKeyStart = body.indexOf("id='cos21342_action'")) == -1 ||
//                (actionKeyStart = body.indexOf('>', actionKeyStart + 20)) == -1 ||
//                (actionKeyEnd = body.indexOf('<', actionKeyStart += 1)) == -1) {
//            apiResponse.addError(TAG + "actionKey not found");
//            return;
//        }
//        String actionKey = body.substring(actionKeyStart, actionKeyEnd);
//
//        // Get list items
//        int itemTagStart, itemTagEnd = datatypeEnd,
//                itemIdStart, itemIdEnd,
//                courseNameStart, courseNameEnd,
//                courseInfoStart, courseInfoEnd;
//        while ((itemTagStart = body.indexOf("class=\"list-group-item", itemTagEnd)) != -1 &&
//                (itemTagEnd = body.indexOf('>', itemTagStart + 22)) != -1) {
//            // Get itemID
//            if ((itemIdStart = body.indexOf("data_item=\"", itemTagStart)) == -1 ||
//                    (itemIdEnd = body.indexOf('"', itemIdStart += 11)) == -1 ||
//                    itemIdStart > itemTagEnd || itemIdEnd > itemTagEnd) {
//                apiResponse.addError(TAG + "item id not found");
//                return;
//            }
//            String itemId = body.substring(itemIdStart, itemIdEnd);
//
//            // Get course name
//            if ((courseNameStart = body.indexOf("class=\"course_name\"", itemTagEnd)) == -1 ||
//                    (courseNameStart = body.indexOf('>', courseNameStart + 19)) == -1 ||
//                    (courseNameEnd = body.indexOf('<', courseNameStart)) == -1) {
//                apiResponse.addError(TAG + "course name not found");
//                return;
//            }
//            String courseIdAndName = body.substring(courseNameStart, courseNameEnd);
//
//            // Get course info
//            if ((courseInfoStart = body.indexOf('>', courseNameEnd)) == -1 ||
//                    (courseInfoEnd = body.indexOf('<', courseInfoStart += 1)) == -1) {
//                apiResponse.addError(TAG + "course info not found");
//                return;
//            }
//            String courseInfo = body.substring(courseInfoStart, courseInfoEnd).trim();
//
//            System.out.println(itemId);
//            System.out.println(courseIdAndName);
//            System.out.println(courseInfo);
//
//            itemTagEnd = courseNameEnd;
//        }
//
//
//        System.out.println(actionKey);
//        System.out.println(datatype);
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
