package com.burp.websocketlogger.query;

import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeartbeatAnalyzer {

    private static final Pattern PAIR_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+|true|false|null)");

    /**
     * Generates a recommended WebSocket++ query filter for a given WebSocket log entry based on payload content.
     */
    public static String generateQuery(WebSocketLogEntry entry) {
        return generatePayloadQuery(entry);
    }

    /**
     * Generates a filter query based on payload content (substring / contains / exact).
     */
    public static String generatePayloadQuery(WebSocketLogEntry entry) {
        if (entry == null) return "payload == \"\"";

        String dirStr = (entry.getDirection() == DirectionType.CLIENT_TO_SERVER) ? "client" : "server";
        String payload = entry.getPayloadText();
        if (payload == null) payload = "";
        payload = payload.trim();

        // 1. Empty payload or empty JSON object
        if (payload.isEmpty()) {
            return "dir == " + dirStr + " and payload == \"\"";
        }
        if (payload.equals("{}")) {
            return "dir == " + dirStr + " and payload == \"{}\"";
        }

        // 2. Short pure numeric frame (e.g. Engine.IO "2", "3")
        if (payload.matches("^\\d{1,4}$")) {
            return "dir == " + dirStr + " and payload == \"" + escapeStringLiteral(payload) + "\"";
        }

        // 3. Short plain text ping/pong
        if (payload.equalsIgnoreCase("ping") || payload.equalsIgnoreCase("pong")) {
            return "dir == " + dirStr + " and payload == \"" + escapeStringLiteral(payload) + "\"";
        }

        // 4. JSON payload analysis - FAST CONTAINS APPROACH
        if (payload.startsWith("{") && payload.endsWith("}")) {
            String fastJsonQuery = buildFastJsonQuery(dirStr, payload);
            if (fastJsonQuery != null) {
                return fastJsonQuery;
            }
        }

        // 5. Fallback: Exact match if short, or simple contains if longer
        if (payload.length() <= 120) {
            return "dir == " + dirStr + " and payload == \"" + escapeStringLiteral(payload) + "\"";
        } else {
            return "dir == " + dirStr + " and payload contains \"" + escapeStringLiteral(payload.substring(0, 60)) + "\"";
        }
    }

    /**
     * Generates a filter query based strictly on the payload byte length.
     * Example: dir == server and length == 78
     */
    public static String generateLengthQuery(WebSocketLogEntry entry) {
        if (entry == null) return "length == 0";
        String dirStr = (entry.getDirection() == DirectionType.CLIENT_TO_SERVER) ? "client" : "server";
        return "dir == " + dirStr + " and length == " + entry.getLength();
    }

    /**
     * Builds an ultra-fast query using `contains` clauses for static key-values and keys.
     * Avoids CPU-heavy backtracking regexes.
     * Example output:
     *   dir == server and payload contains "\"id\":0" and payload contains "\"time\":" and payload contains "\"arnstep\":0"
     */
    public static String buildFastJsonQuery(String dirStr, String json) {
        List<String> staticPairs = new ArrayList<>();
        List<String> dynamicKeys = new ArrayList<>();

        Matcher matcher = PAIR_PATTERN.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2);

            if (isDynamicField(key, val)) {
                dynamicKeys.add("\"" + key + "\":");
            } else {
                // Static signature pair, normalized without spaces
                staticPairs.add("\"" + key + "\":" + val);
            }
        }

        if (staticPairs.isEmpty() && dynamicKeys.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("dir == ").append(dirStr);

        // Add static signatures first (most selective)
        for (String pair : staticPairs) {
            sb.append(" and payload contains \"").append(escapeStringLiteral(pair)).append("\"");
        }

        // Add dynamic keys (e.g. "time":)
        for (String dynKey : dynamicKeys) {
            sb.append(" and payload contains \"").append(escapeStringLiteral(dynKey)).append("\"");
        }

        return sb.toString();
    }

    /**
     * Builds a safe linear regex that avoids catastrophic backtracking if a regex is explicitly needed.
     */
    public static String buildSafeLinearRegex(String json) {
        if (json == null || json.trim().isEmpty()) return ".*";

        Matcher matcher = PAIR_PATTERN.matcher(json);
        StringBuilder regex = new StringBuilder("^\\{.*?");
        boolean matchedAny = false;

        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2);

            if (isDynamicField(key, val)) {
                regex.append("\"").append(Pattern.quote(key)).append("\"\\s*:\\s*\\d+.*?");
            } else {
                regex.append("\"").append(Pattern.quote(key)).append("\"\\s*:\\s*").append(Pattern.quote(val)).append(".*?");
            }
            matchedAny = true;
        }

        if (!matchedAny) {
            return Pattern.quote(json.trim());
        }

        regex.append("\\}$");
        return regex.toString();
    }

    private static boolean isDynamicField(String key, String val) {
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("time") || lowerKey.contains("stamp")
                || lowerKey.contains("nonce") || lowerKey.contains("retry")
                || lowerKey.contains("seq") || lowerKey.contains("step")
                || lowerKey.contains("date") || lowerKey.contains("epoch")) {
            return true;
        }

        // If numeric value is a large timestamp (e.g. > 1000000000 ms or seconds)
        if (val.matches("^\\d{10,14}$")) {
            return true;
        }

        return false;
    }

    private static String escapeStringLiteral(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
