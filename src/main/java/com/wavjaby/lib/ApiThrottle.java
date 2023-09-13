package com.wavjaby.lib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiThrottle {
    public static final int FETCH_PER_MIN = 15;
    public static final int FETCH_INTERVAL = 60000 / FETCH_PER_MIN;
//    public static final int FETCH_MIN_INTERVAL = 200;

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

//            // Reset after a min
//            if (now - lastCleanFetchTime > 60000) {
//                lastCleanFetchTime = now;
//                lastFetchTime = now;
//                return true;
//            }

//            // Throttle
//            if (timePass < FETCH_MIN_INTERVAL) {
//                return false;
//            }

            if (timePass > FETCH_INTERVAL) {
                fetchCount -= (int) (timePass / FETCH_INTERVAL);
                if (fetchCount < 0)
                    fetchCount = 0;
                lastFetchTime = now;
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
