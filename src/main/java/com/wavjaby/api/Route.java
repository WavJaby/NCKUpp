package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.Main;
import com.wavjaby.json.JsonObject;
import com.wavjaby.logger.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@SuppressWarnings("ALL")
public class Route implements EndpointModule {
    private static final String TAG = "[Route]";
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
        String method = req.getRequestMethod();
        String path = req.getRequestURI().getPath().substring(11);
        path = path.replace('$', '%');
        if (path.startsWith("https:"))
            path = "https:/" + path.substring(6);
        else
            path = null;
        try {
            if (path == null) {
                req.sendResponseHeaders(404, 0);
                req.close();
            } else {
                if (method.equalsIgnoreCase("GET")) {
                    String cacheFileName = path.replace(':', ';').replace('/', '%');
                    File cacheFile = new File(Main.cacheFolder, cacheFileName);
                    File headerDataFilepath = new File(Main.cacheFolder, cacheFileName + ".json");
                    JsonObject headerData;
                    byte[] fileBytes;

                    if (cacheFile.exists() && headerDataFilepath.exists()) {
                        headerData = new JsonObject(Files.newInputStream(headerDataFilepath.toPath()));
                        fileBytes = Files.readAllBytes(cacheFile.toPath());
                    } else {
                        // Get request
                        HttpURLConnection conn = (HttpURLConnection) new URL(path).openConnection();
                        conn.setRequestProperty("User-Agent", req.getRequestHeaders().getFirst("User-Agent"));

//                        for (Map.Entry<String, List<String>> i : req.getRequestHeaders().entrySet())
//                            conn.setRequestProperty(i.getKey(), i.getValue().toString());

                        if (conn.getResponseCode() > 399) {
                            req.sendResponseHeaders(404, 0);
                            req.close();
                            return;
                        }

                        InputStream in = conn.getInputStream();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        int len;
                        byte[] buff = new byte[1024];
                        while ((len = in.read(buff, 0, buff.length)) > 0)
                            out.write(buff, 0, len);
                        in.close();

                        String contrentType = conn.getHeaderField("Content-Type");
                        if (contrentType.startsWith("application/javascript"))
                            fileBytes = out.toString("UTF-8")
                                    .replace("\"/_next/", "\"/api/route/https:/quizlet.com/_next/")
                                    .replace("el.qzlt.io", "/api/route/")
                                    .replace("el.quizlet.com", "/api/route/")
                                    .getBytes(StandardCharsets.UTF_8);
                        else if (contrentType.startsWith("text/css"))
                            fileBytes = out.toString("UTF-8")
                                    .replace("url(/_next/", "url(/api/route/https:/quizlet.com/_next/")
                                    .getBytes(StandardCharsets.UTF_8);
                        else
                            fileBytes = out.toByteArray();

                        headerData = new JsonObject();
                        headerData.put("Content-Type", contrentType);

                        String cacheControl = conn.getHeaderField("Cache-Control");
                        if (cacheControl != null) {
                            headerData.put("Cache-Control", cacheControl);

                            try {
                                Files.copy(new ByteArrayInputStream(fileBytes),
                                        cacheFile.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                                Files.copy(new ByteArrayInputStream(headerData.toString().getBytes(StandardCharsets.UTF_8)),
                                        headerDataFilepath.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (Exception e) {
                                logger.errTrace(e);
                            }
                        }
                    }

                    Headers responseHeaders = req.getResponseHeaders();
                    for (Map.Entry<String, Object> i : headerData) {
                        responseHeaders.set(i.getKey(), (String) i.getValue());
                    }

                    // send response
                    req.sendResponseHeaders(200, fileBytes.length);
                    OutputStream response = req.getResponseBody();
                    response.write(fileBytes);
                    response.flush();
                    req.close();
                } else if (method.equalsIgnoreCase("POST")) {
                    if (req.getRequestURI().getPath().equals("/api/route/save")) {
                        InputStream in = req.getRequestBody();
                        JsonObject data = new JsonObject(in);
                        FileOutputStream fileOut = new FileOutputStream("../res/quizlet/" + data.getString("fileName"));
                        fileOut.write(data.getString("data").getBytes(StandardCharsets.UTF_8));
                        fileOut.close();
                    }

                    req.sendResponseHeaders(200, 0);
                    req.close();
                }
            }
        } catch (Exception e) {
            req.close();
            logger.errTrace(e);
        }
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
