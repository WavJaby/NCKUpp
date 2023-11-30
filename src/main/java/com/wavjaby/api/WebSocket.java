package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.request.CustomResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.websocket.httpServer.HttpSocketServer;
import com.wavjaby.websocket.httpServer.SocketServerClient;
import com.wavjaby.websocket.httpServer.SocketServerEvent;


@RequestMapping("/api/v0")
public class WebSocket implements Module, SocketServerEvent {
    private static final String TAG = "WebSocket";
    private static final Logger logger = new Logger(TAG);
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

    @SuppressWarnings("unused")
    @RequestMapping("/socket")
    @CustomResponse
    public void socket(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        server.addClient(req);
        logger.log((System.currentTimeMillis() - startTime) + "ms");
    }


    @Override
    public void ReceiveData(SocketServerClient client, String message) {
        if (message == null)
            return;
        client.sendText(message);
    }

    @Override
    public void ConnectionCountChange(int count) {
        logger.log(count + " clients connected");
    }
}
