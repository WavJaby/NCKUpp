package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class UsefulWebsite implements EndpointModule {
    private static final String TAG = "[UsefulWebsite]";
    private static final Logger logger = new Logger(TAG);

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
        Headers requestHeaders = req.getRequestHeaders();

        try {
            ApiResponse apiResponse = new ApiResponse();
            String method = req.getRequestMethod();
            if (method.equalsIgnoreCase("GET"))
                getLinks(apiResponse);
            else
                apiResponse.errorUnsupportedHttpMethod(method);

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private static class Website {
        private final String url;
        private final String iconUrl;
        private final String logoUrl;
        private final String name;
        private final String description;
        private final String logoSlice;
        private final String iconSlice;

        private Website(String url, String iconUrl, String logoUrl, String name, String description) {
            this(url, iconUrl, logoUrl, name, description, null, null);
        }

        private Website(String url, String iconUrl, String logoUrl, String name, String description, String logoSlice, String iconSlice) {
            this.url = url;
            this.iconUrl = iconUrl;
            this.logoUrl = logoUrl;
            this.name = name;
            this.description = description;
            this.logoSlice = logoSlice;
            this.iconSlice = iconSlice;
        }

        public JsonObjectStringBuilder toJsonObjectBuilder() {
            return new JsonObjectStringBuilder()
                    .append("url", url)
                    .append("iconUrl", iconUrl)
                    .append("logoUrl", logoUrl)
                    .append("name", name)
                    .append("description", description)
                    .append("logoSlice", logoSlice)
                    .append("iconSlice", iconSlice);
        }
    }

    private void getLinks(ApiResponse response) {
        response.setData(new JsonArrayStringBuilder()
                .append(new Website(
                        "https://nckucsie-pastexam.owenowenisme.com/",
                        "https://nckucsie-pastexam.owenowenisme.com/favicon.ico",
                        null,
                        "成大資工考古題系統",
                        "正在開發的考古題系統，內有資工系必修課程的考古題，歡迎大家上傳提供"
                ).toJsonObjectBuilder())
                .append(new Website(
                        "https://web.ncku.edu.tw/p/412-1000-6149.php",
                        "https://web.ncku.edu.tw/var/file/0/1000/plugin/mobile/title/hln_4480_3767086_48566.png",
                        null,
                        "成功大學 行事曆",
                        "國立成功大學全校行事曆",
                        null, "142,85,0,28.5,0,28.5"
                ).toJsonObjectBuilder())
                .append(new Website(
                        "https://urschool.org/ncku",
                        "https://urschool.org/favicon-32x32.png?v=3",
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/5/57/Urschool_logo.jpg/1200px-Urschool_logo.jpg",
                        "成大系所教授評價",
                        "國立成功大學什麼科系教授好？如何選課? 高中生、大學生如何選填科系、研究所？來這裡查看評價",
                        "1200,959,300,150,300,150",null
                ).toJsonObjectBuilder())
                .append(new Website(
                        "https://nckuhub.com/",
                        "https://nckuhub.com/dist/images/favicon/favicon-32x32.png",
                        "https://nckuhub.com/dist/images/table/nav_logo.svg",
                        "NCKU HUB｜資訊改善校園",
                        "NCKU HUB 是由成大生自主發起的課程資訊平台。和我們一起尋找課程心得、模擬未來課表，讓選課之路更加便利吧！"
                ).toJsonObjectBuilder())
                .append(new Website(
                        "https://nckustudy.com/",
                        "https://nckustudy.com/favicon.ico",
                        "https://i.imgur.com/GJawE7A.jpg",
                        "NCKU STUDY・成大學業分享",
                        "NCKU STUDY | 一個讓成大學生匿名分享轉系、輔修、雙主修...等各式學業心得的平台。希望以更透明的資訊幫助成大學生規劃自己學業的方向。(原NCKUTRANS)",
                        "4000,2417,700,1200,800,1200", null
                ).toJsonObjectBuilder())
                .toString()
        );
    }
}
