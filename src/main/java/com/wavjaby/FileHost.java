package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.request.CustomResponse;
import com.wavjaby.logger.Logger;

import java.io.*;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

import static com.wavjaby.lib.Lib.setAllowOrigin;

@RequestMapping("/NCKUpp")
public class FileHost implements Module {
    private static final String TAG = "FileHost";
    private static final Logger logger = new Logger(TAG);
    private final File fileRoot;
    private final FileNameMap fileNameMap = URLConnection.getFileNameMap();

    @Override
    public String getTag() {
        return TAG;
    }

    public FileHost(PropertiesReader serverSettings) {
        String frontendFilePath = serverSettings.getProperty("frontendFilePath", "./");
        fileRoot = new File(frontendFilePath);
        if (!fileRoot.exists())
            logger.err("Frontend file path not found");
    }

    @SuppressWarnings("unused")
    @RequestMapping("/**")
    @CustomResponse
    public void fileHost(HttpExchange req) {
        String path = req.getRequestURI().getPath();
        try {
            boolean filePass = false;
            if (path.startsWith("/NCKUpp")) {
                path = path.substring(7);
                if (path.equals("/")) {
                    path += "index.html";
                    filePass = true;
                } else if (path.startsWith("/res/") ||
                        path.startsWith("/index") ||
                        path.equals("/web_manifest.json"))
                    filePass = true;
            }
            if (!filePass) {
                req.sendResponseHeaders(404, 0);
                req.close();
                return;
            }

            // Remove parent dir path
            path = path.replace("..", "");

            // Add default file ext
            if (path.lastIndexOf('.') < path.lastIndexOf('/'))
                path += ".html";

            File file = new File(fileRoot, path);

            // File not found
            if (!file.isFile()) {
                req.sendResponseHeaders(404, 0);
                req.close();
                return;
            }


            InputStream in = Files.newInputStream(file.toPath());

            // Check encoding
            Headers requestHeader = req.getRequestHeaders();
            Headers responseHeader = req.getResponseHeaders();
            String encoding = requestHeader.getFirst("Accept-Encoding");
            if (encoding != null && encoding.contains("gzip")) {
                responseHeader.set("Content-Encoding", "gzip");
                ByteArrayOutputStream gzipBytes = new ByteArrayOutputStream();
                GZIPOutputStream out = new GZIPOutputStream(gzipBytes);
                byte[] buff = new byte[1024];
                int len;
                while ((len = in.read(buff, 0, buff.length)) > 0)
                    out.write(buff, 0, len);
                out.close();
                in = new ByteArrayInputStream(gzipBytes.toByteArray());
            }

            // Set header
            setAllowOrigin(requestHeader, responseHeader);
            setContentType(file, responseHeader);
            responseHeader.set("Cache-Control", "public, max-age=6000");

            req.sendResponseHeaders(200, in.available());

            // Create output stream
            OutputStream out = req.getResponseBody();

            // Read file and write to output
            byte[] buff = new byte[1024];
            int len;
            while ((len = in.read(buff, 0, buff.length)) > 0)
                out.write(buff, 0, len);
            in.close();
            out.flush();
            out.close();
            req.close();
        } catch (Exception e) {
            req.close();
            logger.errTrace(e);
        }
    }

    private void setContentType(File file, Headers responseHeader) {
        String filename = file.getName();
        String mimeType = fileNameMap.getContentTypeFor(filename);
        if (mimeType == null) {
            if (filename.endsWith(".js"))
                mimeType = "application/javascript;charset=UTF-8";
            else if (filename.endsWith(".css"))
                mimeType = "text/css;charset=UTF-8";
            else if (filename.endsWith(".svg"))
                mimeType = "image/svg+xml;charset=UTF-8";
            else if (filename.endsWith(".png"))
                mimeType = "image/png";
            else if (filename.endsWith(".map"))
                mimeType = "application/json";
            else
                mimeType = "application/octet-stream";
        }
        responseHeader.set("Content-Type", mimeType);
    }


}
