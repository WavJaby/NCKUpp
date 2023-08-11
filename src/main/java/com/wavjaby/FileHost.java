package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class FileHost implements EndpointModule {
    private static final String TAG = "[FileHost]";
    private static final Logger logger = new Logger(TAG);
    private final File fileRoot;
    private final HttpHandler httpHandler;

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

    public FileHost(PropertiesReader serverSettings) {
        String frontendFilePath = serverSettings.getProperty("frontendFilePath", "./");
        fileRoot = new File(frontendFilePath);
        if (!fileRoot.exists())
            logger.err("Frontend file path not found");

        httpHandler = req -> {
            String path = req.getRequestURI().getPath();
            Headers responseHeader = req.getResponseHeaders();
            try {
                InputStream in = null;
                if (path.equals("/NCKUpp/")) {
                    responseHeader.set("Content-Type", "text/html; charset=UTF-8");
                    in = Files.newInputStream(new File(fileRoot, "index.html").toPath());
                } else {
                    String resFilePath = path.substring(8);
                    if (!resFilePath.startsWith("res/") &&
                            !resFilePath.startsWith("quizlet/") &&
                            !resFilePath.startsWith("GameOfLife/") &&
                            !resFilePath.equals("index.js")) {
                        req.sendResponseHeaders(404, 0);
                        req.close();
                        return;
                    }
                    if (resFilePath.startsWith("GameOfLife/"))
                        resFilePath = "../GameOfLife/GameOfLife/src/com/java/Web/" +
                                resFilePath.substring(11);
                    if (resFilePath.lastIndexOf('.') == -1)
                        resFilePath += ".html";

                    File file = new File(fileRoot, resFilePath);
                    if (!file.getAbsolutePath().startsWith(fileRoot.getAbsolutePath())) {
                        req.sendResponseHeaders(404, 0);
                        req.close();
                        return;
                    }

                    if (file.exists()) {
                        if (resFilePath.endsWith(".js"))
                            responseHeader.set("Content-Type", "application/javascript; charset=UTF-8");
                        else if (resFilePath.endsWith(".css"))
                            responseHeader.set("Content-Type", "text/css; charset=UTF-8");
                        else if (resFilePath.endsWith(".svg"))
                            responseHeader.set("Content-Type", "image/svg+xml; charset=UTF-8");
                        else if (resFilePath.endsWith(".html"))
                            responseHeader.set("Content-Type", "text/html; charset=UTF-8");

                        in = Files.newInputStream(file.toPath());
                    }
//                    responseHeader.set("Cache-Control", "max-age=600");
//                    responseHeader.set("Age", "100");
//                    responseHeader.set("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now()));
//                    responseHeader.set("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(file.lastModified())));
                }

                if (in != null) {
                    setAllowOrigin(req.getRequestHeaders(), responseHeader);
                    req.sendResponseHeaders(200, in.available());
                    OutputStream response = req.getResponseBody();
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        response.write(buff, 0, len);
                    in.close();
                    response.flush();
                } else
                    req.sendResponseHeaders(404, 0);
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
        };
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
