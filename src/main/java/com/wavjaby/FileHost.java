package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.file.Files;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class FileHost implements EndpointModule {
    private static final String TAG = "[FileHost]";
    private static final Logger logger = new Logger(TAG);
    private final File fileRoot, quizletFileRot;
    private final HttpHandler httpHandler;
    private final FileNameMap fileNameMap = URLConnection.getFileNameMap();

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
        String quizletFilePath = serverSettings.getProperty("quizletFilePath", "./quizlet");
        fileRoot = new File(frontendFilePath);
        quizletFileRot = new File(quizletFilePath);
        if (!fileRoot.exists())
            logger.err("Frontend file path not found");
        if (!quizletFileRot.exists())
            logger.err("Quizlet file path not found");

        httpHandler = req -> {
            String path = req.getRequestURI().getPath();
            Headers responseHeader = req.getResponseHeaders();
            try {
                boolean filePass = false;
                boolean quizlet = false;
                if (path.startsWith("/NCKUpp")) {
                    path = path.substring(7);
                    if (path.equals("/")) {
                        path += "index.html";
                        filePass = true;
                    } else if (path.startsWith("/res/") ||
                            path.startsWith("/index") ||
                            path.equals("/web_manifest.json"))
                        filePass = true;
                    else if (path.startsWith("/quizlet")) {
                        path = path.substring(8);
                        quizlet = true;
                        filePass = true;
                    }
                }
                if (!filePass) {
                    req.sendResponseHeaders(404, 0);
                    req.close();
                    return;
                }

                // Remove parent dir path
                path = path.replace("/.", "");

                // Add default file ext
                if (path.lastIndexOf('.') < path.lastIndexOf('/'))
                    path += ".html";

                File file = new File(quizlet ? quizletFileRot : fileRoot, path);

                // File not found
                if (!file.isFile()) {
                    req.sendResponseHeaders(404, 0);
                    req.close();
                    return;
                }

                setContentType(file, responseHeader);

                InputStream in = Files.newInputStream(file.toPath());
                setAllowOrigin(req.getRequestHeaders(), responseHeader);
                req.sendResponseHeaders(200, in.available());
                OutputStream response = req.getResponseBody();
                byte[] buff = new byte[1024];
                int len;
                while ((len = in.read(buff, 0, buff.length)) > 0)
                    response.write(buff, 0, len);
                in.close();
                response.flush();
                req.close();
            } catch (Exception e) {
                req.sendResponseHeaders(500, 0);
                req.close();
                logger.errTrace(e);
            }
        };
    }

    private void setContentType(File file, Headers responseHeader) {
        String filename = file.getName();
        String mimeType = fileNameMap.getContentTypeFor(filename);
        if (mimeType == null) {
            if (filename.endsWith(".js"))
                mimeType = "application/javascript";
            else if (filename.endsWith(".css"))
                mimeType = "text/css";
            else if (filename.endsWith(".svg"))
                mimeType = "image/svg+xml";
        }
        if (mimeType != null)
            responseHeader.set("Content-Type", mimeType + "; charset=UTF-8");
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
