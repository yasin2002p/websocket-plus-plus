package com.burp.websocketlogger.analysis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PayloadBeautifier {

    private static final Pattern JWT_PATTERN = Pattern.compile("(eyJ[A-Za-z0-9_-]{10,})\\.([A-Za-z0-9_-]{10,})\\.?([A-Za-z0-9_-]*)");

    /**
     * Formats/indents JSON payloads cleanly.
     * Also handles Socket.io prefixes (e.g. 42["event", {...}]) by extracting and formatting the inner JSON.
     */
    public static String beautify(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String trimmed = raw.trim();

        // Check if Socket.io prefix exists, e.g. 42[...] or 42{...}
        String prefix = "";
        String jsonPart = trimmed;
        if (trimmed.matches("^\\d{1,4}\\[.*") || trimmed.matches("^\\d{1,4}\\{.*")) {
            int firstBrace = trimmed.indexOf('[');
            int firstCurly = trimmed.indexOf('{');
            int splitIdx = -1;
            if (firstBrace != -1 && firstCurly != -1) splitIdx = Math.min(firstBrace, firstCurly);
            else if (firstBrace != -1) splitIdx = firstBrace;
            else splitIdx = firstCurly;

            if (splitIdx > 0) {
                prefix = trimmed.substring(0, splitIdx);
                jsonPart = trimmed.substring(splitIdx);
            }
        }

        if ((jsonPart.startsWith("{") && jsonPart.endsWith("}")) || (jsonPart.startsWith("[") && jsonPart.endsWith("]"))) {
            String formatted = formatJson(jsonPart);
            return prefix.isEmpty() ? formatted : (prefix + " (Socket.io frame)\n" + formatted);
        }

        return raw;
    }

    /**
     * Extracts and decodes JWT tokens and Base64 structures found inside the payload.
     */
    public static String decodeInspect(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "No content to decode.";

        StringBuilder sb = new StringBuilder();
        boolean foundAny = false;

        // 1. JWT Detection & Decoding
        Matcher jwtMatcher = JWT_PATTERN.matcher(raw);
        int jwtCount = 1;
        while (jwtMatcher.find()) {
            foundAny = true;
            String headerB64 = jwtMatcher.group(1);
            String payloadB64 = jwtMatcher.group(2);
            String signature = jwtMatcher.group(3);

            sb.append("=== [JWT Token #").append(jwtCount++).append("] ===\n");
            sb.append("Header:\n");
            sb.append(formatJson(safeBase64UrlDecode(headerB64))).append("\n\n");
            sb.append("Payload (Claims):\n");
            sb.append(formatJson(safeBase64UrlDecode(payloadB64))).append("\n\n");
            sb.append("Signature:\n").append(signature.isEmpty() ? "[unsigned]" : signature).append("\n\n");
        }

        // 2. Formatted JSON representation
        String beautified = beautify(raw);
        if (!beautified.equals(raw)) {
            sb.append("=== [Formatted JSON Payload] ===\n");
            sb.append(beautified).append("\n\n");
            foundAny = true;
        }

        if (!foundAny) {
            sb.append("=== [Raw Content] ===\n");
            sb.append(raw);
        }

        return sb.toString().trim();
    }

    private static String safeBase64UrlDecode(String str) {
        try {
            String padded = str;
            int missing = (4 - (str.length() % 4)) % 4;
            padded += "=".repeat(missing);
            byte[] bytes = Base64.getUrlDecoder().decode(padded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return str;
        }
    }

    /**
     * Lightweight JSON indentation formatter without external library dependencies.
     */
    public static String formatJson(String json) {
        if (json == null || json.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        boolean inQuote = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                sb.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                sb.append(c);
                if (inQuote) escape = true;
                continue;
            }

            if (c == '"') {
                inQuote = !inQuote;
                sb.append(c);
                continue;
            }

            if (!inQuote) {
                switch (c) {
                    case '{':
                    case '[':
                        sb.append(c).append("\n");
                        indentLevel++;
                        appendIndent(sb, indentLevel);
                        continue;
                    case '}':
                    case ']':
                        sb.append("\n");
                        indentLevel = Math.max(0, indentLevel - 1);
                        appendIndent(sb, indentLevel);
                        sb.append(c);
                        continue;
                    case ',':
                        sb.append(c).append("\n");
                        appendIndent(sb, indentLevel);
                        continue;
                    case ':':
                        sb.append(": ");
                        continue;
                    default:
                        if (Character.isWhitespace(c)) continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void appendIndent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }
}
