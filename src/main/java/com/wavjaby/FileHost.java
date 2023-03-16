package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class FileHost implements EndpointModule {
    private static final String TAG = "[FileHost] ";
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

    public FileHost(Properties serverSettings) {
        String frontendFilePath = serverSettings.getProperty("frontendFilePath");
        if (frontendFilePath == null) {
            frontendFilePath = "./";
            Logger.warn(TAG, "Frontend file path not found, using current path");
        }
        fileRoot = new File(frontendFilePath);
        if (!fileRoot.exists())
            Logger.err(TAG, "Frontend file path not found");

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
                    if (!resFilePath.startsWith("res/") && !resFilePath.equals("index.js")) {
                        req.sendResponseHeaders(404, 0);
                        req.close();
                        return;
                    }

                    File file = new File(fileRoot, resFilePath);
                    if (!file.getAbsolutePath().startsWith(fileRoot.getAbsolutePath())) {
                        req.sendResponseHeaders(404, 0);
                        req.close();
                        return;
                    }

                    if (file.exists()) {
                        if (path.endsWith(".js"))
                            responseHeader.set("Content-Type", "application/javascript; charset=UTF-8");
                        else if (path.endsWith(".css"))
                            responseHeader.set("Content-Type", "text/css; charset=UTF-8");
                        else if (path.endsWith(".svg"))
                            responseHeader.set("Content-Type", "image/svg+xml; charset=UTF-8");

                        in = Files.newInputStream(file.toPath());
                    }
                }
                if (in != null) {
                    setAllowOrigin(req.getRequestHeaders(), responseHeader);
                    req.sendResponseHeaders(200, in.available());
                    OutputStream response = req.getResponseBody();
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        response.write(buff, 0, len);
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
