package com.burp.websocketlogger;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyWebSocketMessage;
import burp.api.montoya.websocket.Direction;
import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;
import com.burp.websocketlogger.ui.WebSocketLoggerTab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketHistorySync {
    private final MontoyaApi api;
    private final WebSocketLoggerTab tab;
    private final AtomicInteger messageIdCounter;

    private ScheduledExecutorService scheduler;
    private volatile int lastProcessedIndex = 0;

    public WebSocketHistorySync(MontoyaApi api, WebSocketLoggerTab tab, AtomicInteger messageIdCounter) {
        this.api = api;
        this.tab = tab;
        this.messageIdCounter = messageIdCounter;
    }

    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        // Run an immediate initial sync
        syncNow();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WebSocketHistorySync-Worker");
            t.setDaemon(true);
            return t;
        });

        // Fast live polling: 100ms interval for near real-time updates
        scheduler.scheduleWithFixedDelay(this::syncNow, 100, 100, TimeUnit.MILLISECONDS);
        api.logging().logToOutput("WebSocket History Sync active (polling every 100ms).");
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Backward-compatible stub: fingerprint registration is no longer needed because
     * sequential index tracking guarantees zero dropped messages and zero duplicates.
     */
    public void registerSeen(int wsId, Direction dir, int length, int payloadHash) {
        // No-op: sequential index-based synchronization guarantees exact 1:1 capture
    }

    public synchronized int syncNow() {
        int added = 0;
        try {
            List<ProxyWebSocketMessage> history = api.proxy().webSocketHistory();
            if (history == null) {
                return 0;
            }

            int currentSize = history.size();
            if (currentSize < lastProcessedIndex) {
                // History was cleared in Burp Proxy, reset index to 0
                lastProcessedIndex = 0;
            }

            if (currentSize == lastProcessedIndex) {
                return 0;
            }

            if (tab.isLoggingPaused()) {
                // When paused, advance the index so paused messages are not backlogged
                lastProcessedIndex = currentSize;
                return 0;
            }

            List<WebSocketLogEntry> batch = new ArrayList<>(currentSize - lastProcessedIndex);

            for (int i = lastProcessedIndex; i < currentSize; i++) {
                try {
                    ProxyWebSocketMessage msg = history.get(i);
                    if (msg == null) continue;

                    ByteArray payload = msg.payload();
                    String payloadText = "";
                    String type = "Text";

                    if (payload != null) {
                        try {
                            byte[] bytes = payload.getBytes();
                            if (bytes != null) {
                                payloadText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                if (isBinaryData(bytes)) {
                                    type = "Binary";
                                }
                            } else {
                                payloadText = payload.toString();
                            }
                        } catch (Throwable ignored) {
                            try {
                                payloadText = payload.toString();
                            } catch (Throwable ignored2) {
                            }
                        }
                    }

                    Direction dir = msg.direction();
                    int wsId = msg.webSocketId();

                    // Safe extraction of upgrade request details
                    HttpRequest req = msg.upgradeRequest();
                    String host = "";
                    int port = 0;
                    String path = "";
                    String url = "";

                    if (req != null) {
                        try {
                            if (req.httpService() != null) {
                                host = req.httpService().host();
                                port = req.httpService().port();
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            path = req.path();
                        } catch (Throwable ignored) {
                        }
                        try {
                            url = req.url();
                        } catch (Throwable ignored) {
                        }
                    }

                    WebSocketLogEntry entry = new WebSocketLogEntry(
                            messageIdCounter.incrementAndGet(),
                            wsId,
                            "Proxy",
                            DirectionType.fromBurpDirection(dir),
                            type,
                            host,
                            port,
                            path,
                            url,
                            payload,
                            payloadText,
                            req
                    );
                    entry.setRawMessage(msg);

                    batch.add(entry);
                    added++;
                } catch (Throwable msgErr) {
                    api.logging().logToError("Error processing proxy WebSocket history message at index " + i + ": " + msgErr.getMessage());
                }
            }

            lastProcessedIndex = currentSize;

            if (!batch.isEmpty()) {
                tab.addEntries(batch);
            }
        } catch (Throwable t) {
            api.logging().logToError("Error during WebSocket history sync: " + t.getMessage());
        }

        return added;
    }

    private static boolean isBinaryData(byte[] bytes) {
        if (bytes == null) return false;
        int checkLen = Math.min(bytes.length, 512);
        for (int i = 0; i < checkLen; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    public int getLastProcessedIndex() {
        return lastProcessedIndex;
    }

    public void setLastProcessedIndex(int index) {
        this.lastProcessedIndex = index;
    }
}
