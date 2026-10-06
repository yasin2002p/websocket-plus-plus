package com.burp.websocketlogger.export;

import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LogExporter {

    public static void exportToCsv(File file, List<WebSocketLogEntry> entries) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write("\"#\",\"Time\",\"Direction\",\"Tool\",\"Host\",\"Port\",\"Path\",\"Type\",\"Length\",\"Payload\",\"Comment\"\n");

            for (WebSocketLogEntry entry : entries) {
                StringBuilder line = new StringBuilder();
                line.append(entry.getId()).append(",");
                line.append(escapeCsv(entry.getFormattedTime())).append(",");
                line.append(escapeCsv(entry.getDirection().getDisplayName())).append(",");
                line.append(escapeCsv(entry.getTool())).append(",");
                line.append(escapeCsv(entry.getHost())).append(",");
                line.append(entry.getPort()).append(",");
                line.append(escapeCsv(entry.getPath())).append(",");
                line.append(escapeCsv(entry.getType())).append(",");
                line.append(entry.getLength()).append(",");
                line.append(escapeCsv(entry.getPayloadText())).append(",");
                line.append(escapeCsv(entry.getComment()));
                line.append("\n");
                writer.write(line.toString());
            }
        }
    }

    public static void exportToJson(File file, List<WebSocketLogEntry> entries) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write("[\n");
            for (int i = 0; i < entries.size(); i++) {
                WebSocketLogEntry entry = entries.get(i);
                writer.write("  {\n");
                writer.write("    \"id\": " + entry.getId() + ",\n");
                writer.write("    \"time\": " + escapeJson(entry.getFormattedTime()) + ",\n");
                writer.write("    \"connectionId\": " + entry.getConnectionId() + ",\n");
                writer.write("    \"direction\": " + escapeJson(entry.getDirection().getDisplayName()) + ",\n");
                writer.write("    \"tool\": " + escapeJson(entry.getTool()) + ",\n");
                writer.write("    \"host\": " + escapeJson(entry.getHost()) + ",\n");
                writer.write("    \"port\": " + entry.getPort() + ",\n");
                writer.write("    \"path\": " + escapeJson(entry.getPath()) + ",\n");
                writer.write("    \"url\": " + escapeJson(entry.getUrl()) + ",\n");
                writer.write("    \"type\": " + escapeJson(entry.getType()) + ",\n");
                writer.write("    \"length\": " + entry.getLength() + ",\n");
                writer.write("    \"payload\": " + escapeJson(entry.getPayloadText()) + ",\n");
                writer.write("    \"comment\": " + escapeJson(entry.getComment()) + "\n");
                writer.write("  }" + (i < entries.size() - 1 ? "," : "") + "\n");
            }
            writer.write("]\n");
        }
    }

    private static String escapeCsv(String str) {
        if (str == null) return "\"\"";
        return "\"" + str.replace("\"", "\"\"") + "\"";
    }

    private static String escapeJson(String str) {
        if (str == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
