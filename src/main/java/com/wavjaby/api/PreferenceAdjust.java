package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RequestMethod;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class PreferenceAdjust implements Module {
    private static final String TAG = "PreferenceAdjust";
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

    @RequestMapping(value = "/preferenceAdjust", method = RequestMethod.GET)
    public RestApiResponse getPreferenceAdjust(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse response = new ApiResponse();
        getPreferenceAdjustList(response, cookieStore);

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    @RequestMapping(value = "/preferenceAdjust", method = RequestMethod.POST)
    public RestApiResponse postPreferenceAdjust(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse response = new ApiResponse();
        try {
            updatePreferenceAdjustList(readRequestBody(req, StandardCharsets.UTF_8), response, cookieStore);
        } catch (IOException e) {
            response.errorBadPayload("Read payload failed");
            logger.err(e);
        }

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    private void updatePreferenceAdjustList(String postData, ApiResponse response, CookieStore cookieStore) {
        Map<String, String> form = parseUrlEncodedForm(postData);
        String mode = form.get("mode");
        String type = form.get("type");
        String expectA9RegVal = form.get("expectA9RegVal");
        if (mode == null || mode.isEmpty()) {
            response.errorBadPayload("Payload form require \"mode\"");
            return;
        }
        if ((type == null || type.isEmpty()) && (expectA9RegVal == null || expectA9RegVal.isEmpty())) {
            response.errorBadPayload("Payload form require least one of \"type\" or \"expectA9RegVal\"");
            return;
        }

        StringBuilder payload = new StringBuilder();
        String modifyItems, removeItem;
        if ((modifyItems = form.get("modifyItems")) != null && !modifyItems.isEmpty()) {
            for (String itemId : modifyItems.split(",")) {
                if (payload.length() > 0)
                    payload.append('&');
                payload.append("list_data%5B%5D=").append(itemId);
            }
        } else if ((removeItem = form.get("removeItem")) != null && !removeItem.isEmpty()) {
            payload.append("time=").append(System.currentTimeMillis() / 1000)
                    .append("&type=").append(type)
                    .append("&item=").append(removeItem);
        } else if (expectA9RegVal != null && !expectA9RegVal.isEmpty()) {
            payload.append("time=").append(System.currentTimeMillis() / 1000)
                    .append("&desire=").append(expectA9RegVal);
        } else {
            response.errorBadPayload("Payload form require \"modifyItems\" or \"removeItem\"");
            return;
        }

        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21342&time=" + (System.currentTimeMillis() / 1000) +
                        "&m=" + mode + "&type=" + type)
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy())
                .method(Connection.Method.POST)
                .requestBody(payload.toString());
        try {
            JsonObject result = new JsonObject(conn.execute().body());
            if (!result.getBoolean("status"))
                response.errorCourseNCKU();
            response.setMessageDisplay(result.getString("msg"));
        } catch (JsonException e) {
            logger.errTrace(e);
            response.errorParse("Response Json parse error: " + e.getMessage());
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    private void getPreferenceAdjustList(ApiResponse response, CookieStore cookieStore) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21342")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy());
        Element body = checkCourseNckuLoginRequiredPage(conn, response, false);
        if (body == null)
            return;

        String pageError = checkCourseNckuPageError(body);
        if (pageError != null) {
            response.setMessageDisplay(pageError);
            response.errorCourseNCKU();
            return;
        }

        // Get action key
        Element action = body.getElementById("cos21342_action");
        Element remove = body.getElementById("cos21342_remove");
        Element expectA9Reg = body.getElementById("cos21342_a9will3rd");
        Element expectA9RegVal = body.getElementById("a9will3rd_val");
//        Element group2action = body.getElementById("cos_group2_action");
        if (action == null) {
            response.errorParse("Action key not found");
            return;
        }
        if (remove == null) {
            response.errorParse("Action remove key not found");
            return;
        }
        if (expectA9Reg == null || expectA9RegVal == null) {
            response.addWarn("Action expectA9Reg key not found");
        }

        // All data need adjust
        Element allTabs = body.getElementsByClass("tab-content").first();
        if (allTabs == null) {
            response.errorParse("No adjust list found");
            return;
        }

        JsonObjectStringBuilder result = new JsonObjectStringBuilder();
        result.append("action", action.ownText().trim());
        result.append("remove", remove.ownText().trim());
        JsonArrayStringBuilder tabs = new JsonArrayStringBuilder();

        // Get list item
        for (Element adjLists : allTabs.children()) {
            String id = adjLists.id();

            Element adjList = adjLists.getElementById("list_" + id);
            if (adjList == null) {
                response.errorParse("Adjust list not found");
                return;
            }

            Element adjListNameElement = body.getElementById("tc-tabs_" + id);
            if (adjListNameElement == null) {
                response.errorParse("Adjust list name type not found");
                return;
            }
            String adjListName = adjListNameElement.text();

            // Get type
            if (!adjList.hasAttr("data_type")) {
                response.errorParse("Adjust list type not found");
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
                    response.errorParse("Adjust list item key not found");
                    return;
                }

                // Parse course name
                Element courseNameElement = item.getElementsByClass("course_name").first();
                if (courseNameElement == null) {
                    response.errorParse("Adjust list item course name not found");
                    return;
                }
                String rawCourseName = courseNameElement.ownText().trim();
                int split = rawCourseName.indexOf('】');
                if (split == -1) {
                    response.errorParse("Adjust list item wrong format: " + rawCourseName);
                    return;
                }

                // Parse course info
                Node nextSibling = courseNameElement.nextSibling();
                if (!(nextSibling instanceof TextNode)) {
                    response.errorParse("Adjust list item course info not found: " + rawCourseName);
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
                        response.errorParse("Adjust list item time format error: " + courseInfo);
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
                    response.errorParse("Adjust list item content not found: " + courseInfo);
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
            if (id.startsWith("A9") && expectA9Reg != null && expectA9RegVal != null) {
                adjustList.append("expectA9Reg", expectA9Reg.ownText().trim());
                adjustList.append("expectA9RegVal", expectA9RegVal.ownText().trim());
            }
            tabs.append(adjustList);
        }
        result.append("tabs", tabs);
        response.setData(result.toString());
    }


}
