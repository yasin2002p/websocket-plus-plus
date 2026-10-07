package com.burp.websocketlogger.model;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WebSocketLogEntry {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final int id;
    private final LocalDateTime timestamp;
    private final int connectionId;
    private final String tool;
    private final DirectionType direction;
    private final String type; // "Text" or "Binary"
    private final String host;
    private final int port;
    private final String path;
    private final String url;
    private final ByteArray payload;
    private final String payloadText;
    private final int length;
    private final HttpRequest upgradeRequest;

    private volatile String comment = "";
    private volatile Color highlightColor = null;
    private final com.burp.websocketlogger.analysis.SecurityScanner.ScanResult scanResult;
    private volatile Object rawMessage = null;

    public WebSocketLogEntry(
            int id,
            int connectionId,
            String tool,
            DirectionType direction,
            String type,
            String host,
            int port,
            String path,
            String url,
            ByteArray payload,
            String payloadText,
            HttpRequest upgradeRequest
    ) {
        this.id = id;
        this.timestamp = LocalDateTime.now();
        this.connectionId = connectionId;
        this.tool = tool;
        this.direction = direction;
        this.type = type;
        this.host = host != null ? host : "";
        this.port = port;
        this.path = path != null ? path : "";
        this.url = url != null ? url : "";
        this.payload = payload;
        String text = payloadText;
        if (text == null && payload != null) {
            try {
                byte[] b = payload.getBytes();
                text = (b != null) ? new String(b, StandardCharsets.UTF_8) : payload.toString();
            } catch (Throwable t) {
                try { text = payload.toString(); } catch (Throwable ignored) { text = ""; }
            }
        }
        this.payloadText = (text != null) ? text : "";
        this.length = payload != null ? payload.length() : this.payloadText.getBytes(StandardCharsets.UTF_8).length;
        this.upgradeRequest = upgradeRequest;

        // Passive Security Scan
        this.scanResult = com.burp.websocketlogger.analysis.SecurityScanner.scan(this);
        // Automatic soft highlight for detected errors or tokens if no highlight is set
        if (this.scanResult.hasError()) {
            this.highlightColor = new Color(255, 204, 204); // Soft Red for Errors
        } else if (this.scanResult.hasToken()) {
            this.highlightColor = new Color(255, 235, 179); // Soft Amber for Tokens / Secrets
        } else if (this.scanResult.hasPii()) {
            this.highlightColor = new Color(225, 245, 254); // Soft Blue for PII
        }
    }

    public int getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return timestamp.format(TIME_FORMATTER);
    }

    public int getConnectionId() {
        return connectionId;
    }

    public String getTool() {
        return tool;
    }

    public DirectionType getDirection() {
        return direction;
    }

    public String getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getUrl() {
        return url;
    }

    public ByteArray getPayload() {
        return payload;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public int getLength() {
        return length;
    }

    public HttpRequest getUpgradeRequest() {
        return upgradeRequest;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment != null ? comment : "";
    }

    public Color getHighlightColor() {
        return highlightColor;
    }

    public void setHighlightColor(Color highlightColor) {
        this.highlightColor = highlightColor;
    }

    public String getPreview(int maxLen) {
        if (payloadText == null || payloadText.isEmpty()) {
            return type.equals("Binary") ? "[Binary data " + length + " bytes]" : "";
        }
        String clean = payloadText.replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.length() <= maxLen) {
            return clean;
        }
        return clean.substring(0, maxLen) + "...";
    }

    public boolean isHeartbeat() {
        if (payloadText == null) return false;
        String trimmed = payloadText.trim();
        // Common heartbeats: "2" (Engine.IO ping), "3" (Engine.IO pong), "ping", "pong", {"type":"ping"}
        return trimmed.equals("2") || trimmed.equals("3")
                || trimmed.equalsIgnoreCase("ping") || trimmed.equalsIgnoreCase("pong")
                || trimmed.equalsIgnoreCase("{\"type\":\"ping\"}")
                || trimmed.equalsIgnoreCase("{\"type\":\"pong\"}")
                || trimmed.equalsIgnoreCase("{\"action\":\"ping\"}")
                || trimmed.equalsIgnoreCase("{\"event\":\"ping\"}")
                || trimmed.isEmpty();
    }

    public com.burp.websocketlogger.analysis.SecurityScanner.ScanResult getScanResult() {
        return scanResult;
    }

    public String getSecurityTags() {
        return scanResult != null ? scanResult.getTagSummary() : "";
    }

    public Object getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(Object rawMessage) {
        this.rawMessage = rawMessage;
    }

    public boolean isSecure() {
        return (url != null && url.toLowerCase().startsWith("wss://")) || port == 443;
    }
}
