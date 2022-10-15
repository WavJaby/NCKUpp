package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.json.JsonObject;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.*;
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
                String queryString = req.getRequestURI().getQuery();
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
                responseHeader.set("Content-Type", "application/json; charset=UTF-8");

                // send response
                setAllowOrigin(requestHeaders, responseHeader);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                OutputStream response = req.getResponseBody();
                response.write(dataByte);
                response.flush();
                req.close();
            } catch (IOException e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("[Search] Search " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    public boolean search(Map<String, String> query, JsonBuilder outData, String[] searchID, CookieStore cookieStore) {
        try {
            String getSerialNumber;
            boolean getAll = query.containsKey("ALL"),
                    getSerial = (getSerialNumber = query.get("serial")) != null;

            StringBuilder searchResult = new StringBuilder();
            boolean success = true;
            // get all course
            if (getAll) {
                Connection.Response allDeptRes = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                        .cookieStore(cookieStore)
                        .execute();
                String allDeptBody = allDeptRes.body();
                cosPreCheck(allDeptBody, cookieStore, outData);

                List<String> allDept = new ArrayList<>();
                for (Element element : allDeptRes.parse().getElementsByClass("pnl_dept"))
                    allDept.addAll(element.getElementsByAttribute("data-dept").eachAttr("data-dept"));

                int cryptStart, cryptEnd;
                if ((cryptStart = allDeptBody.indexOf("'crypt'")) == -1 ||
                        (cryptStart = allDeptBody.indexOf('\'', cryptStart + 7)) == -1 ||
                        (cryptEnd = allDeptBody.indexOf('\'', ++cryptStart)) == -1
                ) {
                    outData.append("err", "[Search] can not get crypt");
                    success = false;
                }
                // success getting crypt
                // start get dept
                else {
                    String crypt = allDeptBody.substring(cryptStart, cryptEnd);
//                    for (int i = 0; i < 7; i++) {
//                        if (!postSearchData(
//                                null, null, String.valueOf(i + 1), null, null, null, null,
//                                searchID, cookieStore, outData, searchResult
//                        )) {
//                            success = false;
//                            break;
//                        }
//                    }

//                    CountDownLatch countDownLatch = new CountDownLatch(allDept.size());
//                    AtomicBoolean allSuccess = new AtomicBoolean(true);
//                    for (String deptNo : allDept) {
//                        pool.submit(() -> {
//                            if (!getDept(deptNo, crypt, cookieStore, outData, searchResult))
//                                allSuccess.set(false);
//                            countDownLatch.countDown();
//                        });
//                        if (!postSearchData(
//                                null, null, null, deptNo, null, null, null,
//                                searchID, cookieStore, outData, searchResult
//                        )) {
//                            success = false;
//                            break;
//                        }
//                    }
//                    countDownLatch.await();
//                    if (!allSuccess.get())
//                        success = false;
                    for (String deptNo : allDept) {
                        if (!getDept(deptNo, crypt, cookieStore, outData, searchResult)) {
                            success = false;
                            break;
                        }
                    }
                }
            }
            // get listed serial
            else if (getSerial) {
                Map<String, String> serialIdNumber = parseUrlEncodedForm(URLDecoder.decode(getSerialNumber, "UTF-8"));
                if (serialIdNumber.size() == 0) {
                    outData.append("err", "[Search] invalid serial number");
                    return false;
                }
                for (Map.Entry<String, String> i : serialIdNumber.entrySet()) {
                    searchResult.setLength(0);
                    if (!postSearchData(
                            null, null, null, i.getKey(), null, null,
                            Arrays.stream(i.getValue().split(",")).collect(Collectors.toSet()),
                            searchID, cookieStore, outData, searchResult
                    )) {
                        success = false;
                        break;
                    }
                    if (searchResult.length() > 0) searchResult.setCharAt(0, '[');
                    else searchResult.append('[');
                    searchResult.append(']');
                    outData.append(i.getKey(), searchResult.toString(), true);
                }
            } else {
                String cosname = query.get("course");       // 課程名稱
                String teaname = query.get("teacher");      // 教師姓名
                String wk = query.get("day");               // 星期 1 ~ 7
                String dept_no = query.get("dept");         // 系所 A...
                String degree = query.get("grade");         // 年級 1 ~ 7
                String cl = query.get("section");           // 節次 1 ~ 16 []

                if (cosname == null && teaname == null && wk == null && dept_no == null && degree == null && cl == null) {
                    outData.append("err", "[Search] no query string found");
                    return false;
                }
                success = postSearchData(
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

    public boolean getDept(String deptNo, String crypt, CookieStore cookieStore, JsonBuilder outData, StringBuilder searchResultBuilder) {
        try {
            Connection.Response id = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody("dept_no=" + deptNo + "&crypt=" + URLEncoder.encode(crypt, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(1000 * 60 * 60)
                    .execute();

            JsonObject idData = new JsonObject(id.body());
            if (idData.getString("err").length() > 0) {
                outData.append("err", "[Search] error from NCKU course: " + idData.getString("err"));
                return false;
            }

            Connection.Response result = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result&i=" +
                            URLEncoder.encode(idData.getString("id"), "UTF-8"))
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .maxBodySize(20 * 1024 * 1024)
                    .timeout(1000 * 60 * 60)
                    .header("Referer", courseNckuOrg + "/index.php?c=qry_all")
                    .execute();
            if (result.url().getQuery().equals("c=qry_all")) {
                outData.append("warn", "[Search] dept.No " + deptNo + " not found");
                return true;
            }

            String searchResultBody = result.body();
//            cosPreCheck(searchResultBody, cookieStore, outData);

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
                    null,
                    searchResultBody,
                    searchResultBuilder
            );

            return true;
        } catch (IOException e) {
            outData.append("err", "[Search] Unknown error" + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    public boolean postSearchData(
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
//                System.out.println("[Search] Get searchID");
                final String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
                        .ignoreContentType(true)
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

//            System.out.println("[Search] Post search query");
            Connection.Response search = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=save_qry")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
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
//            System.out.println("[Search] Get search result");
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
//            System.out.println("[Search] Parse course table");
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
            outData.append("err", "[Search] Unknown error" + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
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

            String departmentName = section.get(0).ownText();

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

            // get system number
            String systemNumber = ((TextNode) section1.get(1)).text().trim();

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
            String teachers = section.get(6).text();

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
            int courseGrade = -1;
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
                        courseGrade = Integer.parseInt(cache);
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
            StringBuilder timeSectionBuilder = new StringBuilder();
            timeSectionBuilder.append('[');
            StringBuilder builder = new StringBuilder();
            int timeParseState = 0;
            for (Node node : section.get(8).childNodes()) {
                // time
                if (timeParseState == 0) {
                    if (node instanceof TextNode) {
                        String text = ((TextNode) node).text().trim();
                        int split = text.indexOf(']', 1);
                        if (split == -1)
                            continue;
                        else
                            builder.append(text, 1, split).append(',')
                                    .append(text, split + 1, text.length()).append(',');
                        timeParseState++;
                        continue;
                    }
                    // if no time
                    else {
                        builder.append(',').append(',');
                        timeParseState++;
                    }
                }
                if (timeParseState == 1) {
                    Attributes attributes = node.attributes();
                    // location link
                    if (attributes.size() > 0) {
                        String attribute = attributes.get("href");
                        if (attribute.length() == 0) continue;
                        String[] j = attribute.substring(17, attribute.length() - 3).split("','");
                        builder.append(j[0]).append(',').append(j[1]).append(',').append(((Element) node).text().trim());
                        timeParseState++;
                        continue;
                    }
                    // if no location
                    else {
                        builder.append(',').append(',');
                        timeParseState++;
                    }
                }
                if (timeParseState == 2 && ((Element) node).tagName().equals("br")) {
                    timeSectionBuilder.append('"').append(builder).append('"').append(',');
                    timeParseState = 0;
                    builder.setLength(0);
                }
            }
            if (builder.length() > 0) {
                if (timeParseState != 2)
                    builder.append(',').append(',');
                timeSectionBuilder.append('"').append(builder).append('"');
            }
            timeSectionBuilder.append(']');
            time = timeSectionBuilder.toString();

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
            jsonBuilder.append("dn", departmentName);
            jsonBuilder.append("sn", serialNumber);
            jsonBuilder.append("ca", attributeCode);
            jsonBuilder.append("cs", systemNumber);
            jsonBuilder.append("cn", courseName);
            jsonBuilder.append("ci", courseNote);
            jsonBuilder.append("cl", courseLimit);
            jsonBuilder.append("ct", courseType);
            jsonBuilder.append("g", courseGrade);
            jsonBuilder.append("co", classInfo);
            jsonBuilder.append("cg", classGroup);
            jsonBuilder.append("ts", teachers);
            jsonBuilder.append("tg", tags, true);
            jsonBuilder.append("c", floatFormat.format(credits), true);
            jsonBuilder.append("r", required);
            jsonBuilder.append("s", selected);
            jsonBuilder.append("a", available);
            jsonBuilder.append("t", time, true);
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
