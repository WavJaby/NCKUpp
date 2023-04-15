package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.wavjaby.lib.Lib.parseUrlEncodedForm;

@SuppressWarnings("ALL")
public class Route implements EndpointModule {
    private static final String TAG = "[Route] ";


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
        Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getQuery());
        String path = query.get("path");
        try {
            if (path == null) {
                req.sendResponseHeaders(404, 0);
                req.close();
            } else {
                if (method.equalsIgnoreCase("GET")) {
                    // Get request
                    HttpURLConnection conn = (HttpURLConnection) new URL(path).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36");

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

                    String responseContrentType = conn.getHeaderField("Content-Type");
                    byte[] dataByte;
                    if (responseContrentType.startsWith("application/javascript"))
                        dataByte = out.toString("UTF-8")
                                .replace("\"/_next/", "\"/api/route?path=https://quizlet.com/_next/")
                                .replace("el.qzlt.io", "/api/route/")
                                .replace("el.quizlet.com", "/api/route/")
                                .getBytes(StandardCharsets.UTF_8);
                    else if (responseContrentType.startsWith("text/css"))
                        dataByte = out.toString("UTF-8")
                                .replace("url(/_next/", "url(/api/route?path=https://quizlet.com/_next/")
                                .getBytes(StandardCharsets.UTF_8);
                    else
                        dataByte = out.toByteArray();

                    req.getResponseHeaders().set("Content-Type", responseContrentType);

                    // send response
                    req.sendResponseHeaders(200, dataByte.length);
                    OutputStream response = req.getResponseBody();
                    response.write(dataByte);
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
        } catch (IOException e) {
            req.close();
            e.printStackTrace();
        }
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
