package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.lib.Lib.getFileFromPath;
import static com.wavjaby.lib.Lib.readFileToString;

public class UsefulWebsite implements EndpointModule {
    private static final String TAG = "[UsefulWebsite]";
    private static final Logger logger = new Logger(TAG);
    private static final String WEBSITE_FILE_PATH = "./api_file/usefulWebsite.json";
    private long lastFileModify = -1;
    private File file;
    private String websitesJsonString;

    private static class Website {
        private final String url;
        private final String iconUrl;
        private final String logoUrl;
        private final String name;
        private final String description;
        private final String logoSlice;
        private final String iconSlice;

        private Website(JsonObject data) {
            this.url = data.getString("url");
            this.iconUrl = data.getString("iconUrl");
            this.logoUrl = data.getString("logoUrl");
            this.name = data.getString("name");
            this.description = data.getString("description");
            this.logoSlice = data.getString("logoSlice");
            this.iconSlice = data.getString("iconSlice");
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

    @Override
    public void start() {
        file = getFileFromPath(WEBSITE_FILE_PATH, true);
        updateData();
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

        ApiResponse apiResponse = new ApiResponse();
        String method = req.getRequestMethod();
        if (method.equalsIgnoreCase("GET"))
            getLinks(apiResponse);
        else
            apiResponse.errorUnsupportedHttpMethod(method);

        apiResponse.sendResponse(req);

        logger.log("Get useful website " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void getLinks(ApiResponse response) {
        updateData();
        response.setData(websitesJsonString);
    }

    private void updateData() {
        if (file.lastModified() == lastFileModify)
            return;
        String fileStr = readFileToString(file, false, StandardCharsets.UTF_8);
        if (fileStr != null) {
            JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
            JsonArray arr = new JsonArray(fileStr);
            for (Object i : arr) {
                builder.append(new Website((JsonObject) i).toJsonObjectBuilder());
            }
            websitesJsonString = builder.toString();
        }
        lastFileModify = file.lastModified();
    }
}
