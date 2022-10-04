package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.pool;

public class Search implements HttpHandler {
    private static final Pattern displayRegex = Pattern.compile("[\\r\\n]+\\.(\\w+) *\\{[\\r\\n]* *(?:/\\* *\\w+ *: *\\w+ *;? *\\*/ *)?display *: *(\\w+) *;? *");
    private static final DecimalFormat floatFormat = new DecimalFormat("#.#");

    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
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
                String loginState = unpackLoginStateCookie(cookieIn, cookieManager);
                String queryString = req.getRequestURI().getQuery();
                String[] searchID = {null};
                if (cookieIn != null)
                    for (String i : cookieIn)
                        if (i.startsWith("searchID")) {
                            searchID[0] = i.substring(16);
                            break;
                        }

                // search
                boolean success = false;
                JsonBuilder data = new JsonBuilder();
                if (queryString != null) {
                    Map<String, String> query = parseUrlEncodedForm(queryString);
                    success = search(query, data, searchID, cookieStore);
                } else
                    data.append("err", "[Search] no query string found");

                // set cookie
                Headers responseHeader = req.getResponseHeaders();
                responseHeader.add("Set-Cookie", (searchID[0] != null && success
                        ? "searchID=" + searchID[0]
                        : removeCookie("searchID")) +
                        "; Path=/api/search" + getCookieInfoData(refererUrl));
                packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);

                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Content-Type", "application/json; charset=utf-8");

                // send response
                setAllowOrigin(requestHeaders, responseHeader);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                OutputStream response = req.getResponseBody();
                response.write(dataByte);
                response.flush();
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("[Search] Search " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    public boolean search(Map<String, String> query, JsonBuilder outData, String[] searchID, CookieStore cookieStore) {
        try {
            String
                    cosname = null,    // 課程名稱
                    teaname = null,    // 教師姓名
                    wk = null,         // 星期 1 ~ 7
                    dept_no = null,    // 系所 A...
                    degree = null,     // 年級 1 ~ 7
                    cl = null;         // 節次 1 ~ 16 []

            // get query
            // extend
            String getSerialNumber;
            Map<String, String> serialIdNumber = null;
            boolean getAll = query.containsKey("ALL"),
                    getSerial = (getSerialNumber = query.get("serial")) != null;
            if (getSerial) {
                serialIdNumber = parseUrlEncodedForm(URLDecoder.decode(getSerialNumber, "UTF-8"));
                if (serialIdNumber.size() == 0) {
                    outData.append("err", "[Search] invalid serial number");
                    return false;
                }
            }
            // regular
            else if (!getAll) {
                cosname = query.get("cosname");     // 課程名稱
                teaname = query.get("teaname");     // 教師姓名
                wk = query.get("wk");               // 星期 1 ~ 7
                dept_no = query.get("dept");        // 系所 A...
                degree = query.get("degree");       // 年級 1 ~ 7
                cl = query.get("cl");               // 節次 1 ~ 16 []

                if (cosname == null && teaname == null && wk == null && dept_no == null && degree == null && cl == null) {
                    outData.append("err", "[Search] no query string found");
                    return false;
                }
            }

            StringBuilder searchResult = new StringBuilder();
            boolean success = true;
            if (getAll) {
                for (int i = 0; i < 7; i++)
                    if (!postSearchDataAndGetResponse(
                            null, null, String.valueOf(i + 1), null, null, null,
                            null, searchID, cookieStore, outData, searchResult
                    )) {
                        success = false;
                        break;
                    }
            } else if (getSerial) {
                for (Map.Entry<String, String> i : serialIdNumber.entrySet()) {
                    searchResult.setLength(0);
                    if (!postSearchDataAndGetResponse(
                            null, null, null, i.getKey(), null, null,
                            Arrays.stream(i.getValue().split(",")).collect(Collectors.toSet()),
                            searchID, cookieStore, outData, searchResult
                    )) {
                        success = false;
                        break;
                    }
                    if (searchResult.length() > 0)
                        searchResult.setCharAt(0, '[');
                    else
                        searchResult.append('[');
                    searchResult.append(']');
                    outData.append(i.getKey(), searchResult.toString(), true);
                }
            } else {
                success = postSearchDataAndGetResponse(
                        cosname, teaname, wk, dept_no, degree, cl,
                        null, searchID, cookieStore, outData, searchResult
                );
            }

            if (success && !getSerial) {
                if (searchResult.length() > 0)
                    searchResult.setCharAt(0, '[');
                else
                    searchResult.append('[');
                searchResult.append(']');
                outData.append("data", searchResult.toString(), true);
            }
            return success;
        } catch (Exception e) {
            outData.append("err", "[Login] Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    public boolean postSearchDataAndGetResponse(
            String cosname,
            String teaname,
            String wk,
            String dept_no,
            String degree,
            String cl,
            Set<String> getSerialNumber, String[] searchID, CookieStore cookieStore,
            JsonBuilder outData, StringBuilder searchResultBuilder) {
        try {
            // setup
            if (searchID[0] == null) {
                final String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
                        .cookieStore(cookieStore)
                        .execute().body();
                cosPreCheck(body, cookieStore, outData);
                if ((searchID[0] = getSearchID(body, outData)) == null) {
                    outData.append("err", "[Search] can not get searchID");
                    return false;
                }
            }

            // search
            StringBuilder builder = new StringBuilder();
            builder.append("id=").append(URLEncoder.encode(searchID[0], "UTF-8"));
            if (cosname != null)
                builder.append("&cosname=").append(URLEncoder.encode(cosname, "UTF-8"));
            if (teaname != null)
                builder.append("&teaname=").append(URLEncoder.encode(teaname, "UTF-8"));
            if (wk != null)
                builder.append("&wk=").append(wk);
            if (dept_no != null)
                builder.append("&dept_no=").append(dept_no);
            if (degree != null)
                builder.append("&degree=").append(degree);
            if (cl != null)
                builder.append("&cl=").append(cl);

            Connection.Response search = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=save_qry")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody(builder.toString())
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            String searchBody = search.body();
            if (searchBody.equals("0")) {
                outData.append("err", "[Search] condition not set");
                return false;
            }
            if (searchBody.equals("1")) {
                outData.append("err", "[Search] wrong condition format");
                return false;
            }

            // get result
            Connection.Response searchResult = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215" + searchBody)
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody(builder.toString())
                    .maxBodySize(20 * 1024 * 1024)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();

            final String searchResultBody = searchResult.body();
            cosPreCheck(searchResultBody, cookieStore, outData);

            if ((searchID[0] = getSearchID(searchResultBody, outData)) == null) {
                outData.append("err", "[Search] can not get searchID");
                return false;
            }

            int resultTableStart;
            if ((resultTableStart = searchResultBody.indexOf("<table")) == -1) {
                outData.append("err", "[Search] result table not found");
                return false;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = searchResultBody.indexOf("<tbody>", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = searchResultBody.indexOf("</tbody>", resultTableBodyStart + 7)) == -1
            ) {
                outData.append("err", "[Search] result table body not found");
                return false;
            }

            // parse table
            String resultBody = searchResultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);


            parseCourseTable(
                    (Element) tbody,
                    getSerialNumber,
                    searchResultBody,
                    searchResultBuilder
            );
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void parseCourseTable(Element tbody, Set<String> getSerialNumber, String searchResultBody, StringBuilder result) {
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
                styles.add(new AbstractMap.SimpleEntry<>
                        (matcher.group(1), !matcher.group(2).equals("none")));
            }
        }

        // get course list
        int getCount = 0;
        Elements courseList = tbody.getElementsByTag("tr");
        for (Element element : courseList) {
            Elements section = element.getElementsByTag("td");

//                String departmentName = section.get(0).ownText();

            List<Node> section1 = section.get(1).childNodes();
            // get serial number
            String serialNumber = ((Element) section1.get(0)).text().trim();
            if (getSerialNumber != null) {
                if (getCount == getSerialNumber.size())
                    return;
                if (!getSerialNumber.contains(serialNumber.substring(serialNumber.indexOf('-') + 1)))
                    continue;
                getCount++;
            }

            // get class code
            String classCode = ((TextNode) section1.get(1)).text().trim();

            // get attribute code
            String attributeCode = ((TextNode) section1.get(3)).text().trim();
            int end = attributeCode.lastIndexOf(']');
            if (end != -1) attributeCode = attributeCode.substring(attributeCode.indexOf('[') + 1, end);

            // get course name
            String courseName = section.get(4).getElementsByClass("course_name").get(0).text();

            // get course note
            String courseNote = section.get(4).ownText().replace("\"", "\\\"");

            // get course limit
            String courseLimit = section.get(4).getElementsByClass("cond").text().replace("\"", "\\\"");

            // get course type
            String courseType = section.get(3).text();

            // get teacher name
            String teacherName = section.get(6).text();

            // get course tags
            String tags;
            StringBuilder tagBuilder = new StringBuilder();
            for (Element tag : section.get(4).getElementsByClass("label"))
                tagBuilder.append(',').append('"').append(tag.text()).append('"');
            if (tagBuilder.length() == 0) tags = "[]";
            else {
                tagBuilder.setCharAt(0, '[');
                tags = tagBuilder.append(']').toString();
            }

            // get grade & classInfo & group
            int classGrade = -1;
            String classInfo = "";
            String classGroup = "";
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
                        classGrade = Integer.parseInt(cache);
                    else if (section2c == 1)
                        classInfo = cache;
                    else
                        classGroup = cache;
                }
            }

            // get credits & required
            List<TextNode> section5 = section.get(5).textNodes();
            float credits;
            boolean required;
            String section5Str;
            if (section5.size() > 0 && (section5Str = section5.get(0).text().trim()).length() > 0) {
                credits = Float.parseFloat(section5Str);
                String cache = section5.get(1).text().trim();
                required = cache.equals("必修") || cache.equals("Required");
            } else {
                credits = -1;
                required = false;
            }

            // get time
            String time;
            List<TextNode> section8 = section.get(8).textNodes();
            String section8Str;
            if (section8.size() > 0 && !(section8Str = section8.get(0).text()).contains("未定") && !section8Str.contains("Undecided")) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append('[');
                for (int i = 0; i < section8.size(); i++) {
                    String[] timeStr = section8.get(i).text().trim().split("]");
                    if (i > 0) stringBuilder.append(',');
                    if (timeStr.length == 1)
                        stringBuilder.append('"').append(timeStr[0].substring(1)).append('"');
                    else
                        stringBuilder.append('"').append(timeStr[0].substring(1)).append(timeStr[1]).append('"');
                }
                time = stringBuilder.append(']').toString();
            } else time = "[]";

            // get location
            String location;
            Elements section8a = section.get(8).getElementsByTag("a");
            if (section8a.size() > 0) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append('[');
                for (int i = 0; i < section8a.size(); i++) {
                    String attribute = section8a.get(i).attributes().get("href");
                    String[] j = attribute.substring(17, attribute.length() - 3).split("','");
                    if (i > 0) stringBuilder.append(',');
                    stringBuilder.append('"').append(j[0]).append(',').append(j[1]).append('"');
                }
                location = stringBuilder.append(']').toString();
            } else location = "[]";

            // get selected & available
            String[] count;
            StringBuilder countBuilder = new StringBuilder();
            for (Node n : section.get(7).childNodes()) {
                // need to check style
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
            int selected = count[0].length() > 0 ? Integer.parseInt(count[0]) : -1;
            String available = count.length > 1 ? count[1] : null;

            // get moodle
            String moodle = "";
            Elements moodleEle = section.get(9).getElementsByAttributeValueStarting("href", "javascript:moodle");
            if (moodleEle.size() == 1) {
                String str = moodleEle.get(0).attr("href");
                moodle = str.substring(19, str.length() - 3).replace("','", ",");
            }

            // output
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.append("sn", serialNumber);
            jsonBuilder.append("ac", attributeCode);
            jsonBuilder.append("cc", classCode);
            jsonBuilder.append("cn", courseName);
            jsonBuilder.append("cr", courseNote);
            jsonBuilder.append("cl", courseLimit);
            jsonBuilder.append("ct", courseType);
            jsonBuilder.append("cf", classGrade);
            jsonBuilder.append("ci", classInfo);
            jsonBuilder.append("cg", classGroup);
            jsonBuilder.append("tn", teacherName);
            jsonBuilder.append("tg", tags, true);
            jsonBuilder.append("c", floatFormat.format(credits), true);
            jsonBuilder.append("r", required);
            jsonBuilder.append("s", selected);
            jsonBuilder.append("a", available);
            jsonBuilder.append("t", time, true);
            jsonBuilder.append("l", location, true);
            jsonBuilder.append("m", moodle);
            result.append(',').append(jsonBuilder);
        }
    }

    public String getSearchID(String body, JsonBuilder outData) {
        // get entry function
        int searchFunctionStart = body.indexOf("function setdata()");
        if (searchFunctionStart == -1) {
            outData.append("err", "[Search] search function not found");
            return null;
        } else searchFunctionStart += 18;

        int idStart, idEnd;
        if ((idStart = body.indexOf("'id'", searchFunctionStart)) == -1 ||
                (idStart = body.indexOf('\'', idStart + 4)) == -1 ||
                (idEnd = body.indexOf('\'', idStart + 1)) == -1
        ) {
            outData.append("err", "[Search] search id not found");
            return null;
        }
        return body.substring(idStart + 1, idEnd);
    }
}
