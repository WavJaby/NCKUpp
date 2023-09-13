package com.wavjaby.lib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiThrottle {
    private static final int FETCH_PER_MIN = 15;
    private static final int FETCH_MIN_INTERVAL = 300;

    public static class ClientInfo {
        public long lastCleanFetchTime;
        public long lastFetchTime;
        public int fetchCount;

        public ClientInfo(long cleanFetchTime) {
            lastCleanFetchTime = cleanFetchTime;
            fetchCount = 0;
        }

        public boolean checkPass() {
            fetchCount++;
            long now = System.currentTimeMillis();
            long timePass = now - lastFetchTime;

            // Reset after a min
            if (now - lastCleanFetchTime > 60000) {
                lastCleanFetchTime = now;
                return true;
            }

            // Throttle
            if (timePass < FETCH_MIN_INTERVAL) {
                return false;
            }

            lastFetchTime = now;

            if (timePass > 60000f / FETCH_PER_MIN) {
                fetchCount -= (int) (timePass / FETCH_PER_MIN);
                if (fetchCount < 0)
                    fetchCount = 0;
                return true;
            }

            // Throttle
            if (fetchCount >= FETCH_PER_MIN) {
                fetchCount = FETCH_PER_MIN;
                return false;
            }

            return true;
        }

        public void endRequest() {
            lastFetchTime = System.currentTimeMillis();
        }
    }

    // Ip, info
    private static final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    public static boolean checkIpThrottle(String ip) {
        ClientInfo clientInfo = clients.get(ip);
        if (clientInfo == null) {
            clients.put(ip, new ClientInfo(System.currentTimeMillis()));
            return true;
        }
        return clientInfo.checkPass();
    }

    public static int doneIpThrottle(String ip) {
        ClientInfo clientInfo = clients.get(ip);
        if (clientInfo == null)
            return 0;

        clientInfo.endRequest();
        return clientInfo.fetchCount;
    }
}
