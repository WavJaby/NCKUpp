package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.Module;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.request.CustomResponse;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

@RequestMapping("/api/v0")
public class Route implements Module {
    private static final String TAG = "Route";
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

    @RequestMapping("/quizlet")
    @CustomResponse
    public void quizlet(HttpExchange req) {
        String method = req.getRequestMethod();

        String path = req.getRequestURI().getRawPath().substring(13);
//        path = path.replace('$', '%');
//        if (path.startsWith("https:"))
//            path = "https:/" + path.substring(6);
//        else
//            path = null;

        try {
            if (method.equalsIgnoreCase("GET")) {
                String parsedPath = null;
                if (path.startsWith("assets/")) {
                    parsedPath = "https://assets.quizlet.com/" + path.substring(7);
                } else if (path.startsWith("base/")) {
                    parsedPath = "https://quizlet.com/" + path.substring(5);
                }

                if (parsedPath != null) {
                    String cacheFileName = parsedPath.replace(':', ';').replace('/', '^');
                    File cacheFile = new File(Main.cacheFolder, cacheFileName);
                    File headerDataFilepath = new File(Main.cacheFolder, cacheFileName + ".json");

                    byte[] fileBytes;

                    if (cacheFile.exists() && headerDataFilepath.exists()) {
                        JsonArray headerData = new JsonArray(Files.newInputStream(headerDataFilepath.toPath()));
                        fileBytes = Files.readAllBytes(cacheFile.toPath());

                        Headers responseHeaders = req.getResponseHeaders();
                        for (Object i : headerData) {
                            JsonArray a = (JsonArray) i;
                            responseHeaders.set(a.getString(0), a.getString(1));
                        }
                        // send response
                        req.sendResponseHeaders(200, fileBytes.length);
                        OutputStream response = req.getResponseBody();
                        response.write(fileBytes);
                        response.flush();
                    } else {
                        req.sendResponseHeaders(404, 0);
//
//                        // Get request
//                        HttpURLConnection conn = (HttpURLConnection) new URL(parsedPath).openConnection();
//                        conn.setRequestProperty("User-Agent", req.getRequestHeaders().getFirst("User-Agent"));
//
////                        for (Map.Entry<String, List<String>> i : req.getRequestHeaders().entrySet())
////                            conn.setRequestProperty(i.getKey(), i.getValue().toString());
//
//                        if (conn.getResponseCode() > 399) {
//                            req.sendResponseHeaders(404, 0);
//                            req.close();
//                            return;
//                        }
//
//                        String response = readInputStreamToString(conn.getInputStream(), StandardCharsets.UTF_8);
//
//                        String contrentType = conn.getHeaderField("Content-Type");
//                        if (contrentType.startsWith("application/javascript"))
//                            fileBytes = response
//                                    .replace("\"/_next/", "\"/api/route/https:/quizlet.com/_next/")
//                                    .replace("el.qzlt.io", "/api/route/")
//                                    .replace("el.quizlet.com", "/api/route/")
//                                    .getBytes(StandardCharsets.UTF_8);
//                        else if (contrentType.startsWith("text/css"))
//                            fileBytes = response
//                                    .replace("url(/_next/", "url(/api/route/ssets/_next/")
//                                    .getBytes(StandardCharsets.UTF_8);
//                        else
//                            fileBytes = response.getBytes("UTF-8");
//
//                        headerData = new JsonObject();
//                        headerData.put("Content-Type", contrentType);
//
//                        String cacheControl = conn.getHeaderField("Cache-Control");
//                        if (cacheControl != null) {
//                            headerData.put("Cache-Control", cacheControl);
//
//                            try {
//                                Files.copy(new ByteArrayInputStream(fileBytes),
//                                        cacheFile.toPath(),
//                                        StandardCopyOption.REPLACE_EXISTING);
//                                Files.copy(new ByteArrayInputStream(headerData.toString().getBytes(StandardCharsets.UTF_8)),
//                                        headerDataFilepath.toPath(),
//                                        StandardCopyOption.REPLACE_EXISTING);
//                            } catch (Exception e) {
//                                logger.errTrace(e);
//                            }
//                        }
                    }
                    req.close();
                    return;
                }
            } else if (method.equalsIgnoreCase("POST")) {
                if (path.equals("save")) {
                    InputStream in = req.getRequestBody();
                    JsonObject body = new JsonObject(in);
                    String resourcesUrl = body.getString("resourcesUrl");
                    // Resources file
                    if (resourcesUrl != null) {
                        String cacheFileName = resourcesUrl.replace(':', ';').replace('/', '^');
                        File cacheFile = new File(Main.cacheFolder, cacheFileName);
                        File headerFile = new File(Main.cacheFolder, cacheFileName + ".json");
                        if (!cacheFile.getParentFile().exists())
                            cacheFile.getParentFile().mkdirs();

                        FileOutputStream fileWriter = new FileOutputStream(cacheFile);
                        FileOutputStream headerFileWriter = new FileOutputStream(headerFile);

                        fileWriter.write(Base64.getDecoder().decode(body.getString("data")));
                        fileWriter.close();

                        JsonArray headers = body.getArray("headers");
                        headerFileWriter.write(headers.toString().getBytes(StandardCharsets.UTF_8));
                        headerFileWriter.close();
                    } else {
                        File filePath = new File("./quizlet" + body.getString("filePath"));
                        filePath.getParentFile().mkdirs();
                        FileOutputStream fileOut = new FileOutputStream(filePath);
                        fileOut.write(body.getString("data").getBytes(StandardCharsets.UTF_8));
                        fileOut.close();
                    }

                    req.sendResponseHeaders(200, 0);
                    req.close();
                    return;
                }
            }

            req.sendResponseHeaders(404, 0);
            req.close();
        } catch (Exception e) {
            req.close();
            logger.errTrace(e);
        }
    }


}
