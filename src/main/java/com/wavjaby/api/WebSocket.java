package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.logger.Logger;
import com.wavjaby.websocket.httpServer.HttpSocketServer;
import com.wavjaby.websocket.httpServer.SocketServerClient;
import com.wavjaby.websocket.httpServer.SocketServerEvent;

public class WebSocket implements EndpointModule, SocketServerEvent {
    private static final String TAG = "[WebSocket] ";
    private HttpSocketServer server;

    @Override
    public void start() {
        server = new HttpSocketServer(this);
    }

    @Override
    public void stop() {
        server.stopServer();
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        server.addClient(req);
        Logger.log(TAG, "Web socket " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    @Override
    public void ReceiveData(SocketServerClient client, String message) {
        client.sendText(message);
    }

    @Override
    public void ConnectionCountChange(int count) {
        Logger.log(TAG, count + " clients connected");
    }
}
