package com.burp.websocketlogger.query;

import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.util.Locale;
import java.util.regex.Pattern;

public interface QueryNode {
    boolean matches(WebSocketLogEntry entry);

    class AlwaysTrueNode implements QueryNode {
        @Override
        public boolean matches(WebSocketLogEntry entry) {
            return true;
        }
    }

    class FullTextNode implements QueryNode {
        private final String searchTerm;
        private final String lowerSearchTerm;

        public FullTextNode(String searchTerm) {
            this.searchTerm = searchTerm;
            this.lowerSearchTerm = searchTerm.toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean matches(WebSocketLogEntry entry) {
            if (entry == null) return false;
            if (entry.getPayloadText() != null && entry.getPayloadText().toLowerCase(Locale.ROOT).contains(lowerSearchTerm)) {
                return true;
            }
            if (entry.getHost() != null && entry.getHost().toLowerCase(Locale.ROOT).contains(lowerSearchTerm)) {
                return true;
            }
            if (entry.getPath() != null && entry.getPath().toLowerCase(Locale.ROOT).contains(lowerSearchTerm)) {
                return true;
            }
            if (entry.getUrl() != null && entry.getUrl().toLowerCase(Locale.ROOT).contains(lowerSearchTerm)) {
                return true;
            }
            if (entry.getComment() != null && entry.getComment().toLowerCase(Locale.ROOT).contains(lowerSearchTerm)) {
                return true;
            }
            return false;
        }
    }

    class AndNode implements QueryNode {
        private final QueryNode left;
        private final QueryNode right;

        public AndNode(QueryNode left, QueryNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(WebSocketLogEntry entry) {
            return left.matches(entry) && right.matches(entry);
        }
    }

    class OrNode implements QueryNode {
        private final QueryNode left;
        private final QueryNode right;

        public OrNode(QueryNode left, QueryNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(WebSocketLogEntry entry) {
            return left.matches(entry) || right.matches(entry);
        }
    }

    class NotNode implements QueryNode {
        private final QueryNode child;

        public NotNode(QueryNode child) {
            this.child = child;
        }

        @Override
        public boolean matches(WebSocketLogEntry entry) {
            return !child.matches(entry);
        }
    }

    class ComparisonNode implements QueryNode {
        private final String field;
        private final String operator;
        private final String value;
        private final String lowerValue;
        private Pattern compiledRegex = null;

        public ComparisonNode(String field, String operator, String value) {
            this.field = field.toLowerCase(Locale.ROOT);
            this.operator = operator.toLowerCase(Locale.ROOT);
            this.value = value;
            this.lowerValue = value != null ? value.toLowerCase(Locale.ROOT) : "";

            if (this.operator.equals("matches") || this.operator.equals("regex")
                    || this.operator.equals("!matches") || this.operator.equals("!regex")) {
                try {
                    this.compiledRegex = Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                } catch (Exception e) {
                    this.compiledRegex = null;
                }
            }
        }

        @Override
        public boolean matches(WebSocketLogEntry entry) {
            if (entry == null) return false;

            switch (field) {
                case "payload":
                case "body":
                case "data":
                case "p":
                    return matchString(entry.getPayloadText());

                case "dir":
                case "direction":
                case "d":
                    return matchDirection(entry);

                case "host":
                case "h":
                    return matchString(entry.getHost());

                case "path":
                    return matchString(entry.getPath());

                case "url":
                    return matchString(entry.getUrl());

                case "tool":
                    return matchString(entry.getTool());

                case "type":
                    return matchString(entry.getType());

                case "comment":
                    return matchString(entry.getComment());

                case "color":
                case "highlight":
                    return matchColor(entry);

                case "length":
                case "len":
                case "size":
                    return matchNumeric(entry.getLength());

                case "id":
                    return matchNumeric(entry.getId());

                case "port":
                    return matchNumeric(entry.getPort());

                case "conn":
                case "connection":
                    return matchNumeric(entry.getConnectionId());

                case "tag":
                case "tags":
                case "security":
                    return matchString(entry.getSecurityTags());

                default:
                    return false;
            }
        }

        private boolean matchString(String target) {
            if (target == null) target = "";
            String targetLower = target.toLowerCase(Locale.ROOT);

            switch (operator) {
                case "==":
                case "=":
                    return targetLower.equals(lowerValue);
                case "!=":
                    return !targetLower.equals(lowerValue);
                case "contains":
                    return targetLower.contains(lowerValue);
                case "!contains":
                    return !targetLower.contains(lowerValue);
                case "startswith":
                    return targetLower.startsWith(lowerValue);
                case "endswith":
                    return targetLower.endsWith(lowerValue);
                case "matches":
                case "regex":
                    return compiledRegex != null && compiledRegex.matcher(target).find();
                case "!matches":
                case "!regex":
                    return compiledRegex != null && !compiledRegex.matcher(target).find();
                default:
                    return false;
            }
        }

        private boolean matchDirection(WebSocketLogEntry entry) {
            boolean isOutgoing = entry.getDirection() == com.burp.websocketlogger.model.DirectionType.CLIENT_TO_SERVER;
            boolean targetIsOutgoing;

            if (lowerValue.equals("client") || lowerValue.equals("outgoing") || lowerValue.equals("out")
                    || lowerValue.equals("c2s") || lowerValue.contains("client -> server") || lowerValue.contains("to server")
                    || lowerValue.equals("to_server") || lowerValue.equals("toserver")) {
                targetIsOutgoing = true;
            } else if (lowerValue.equals("server") || lowerValue.equals("incoming") || lowerValue.equals("in")
                    || lowerValue.equals("s2c") || lowerValue.contains("server -> client") || lowerValue.contains("to client")
                    || lowerValue.equals("to_client") || lowerValue.equals("toclient")) {
                targetIsOutgoing = false;
            } else {
                return false;
            }

            boolean isMatch = (isOutgoing == targetIsOutgoing);
            if (operator.equals("==") || operator.equals("=") || operator.equals("contains")) {
                return isMatch;
            } else if (operator.equals("!=") || operator.equals("!contains")) {
                return !isMatch;
            }
            return false;
        }

        private boolean matchColor(WebSocketLogEntry entry) {
            String colorName = "none";
            if (entry.getHighlightColor() != null) {
                // Approximate color name
                int rgb = entry.getHighlightColor().getRGB() & 0xFFFFFF;
                colorName = Integer.toHexString(rgb);
            }
            return matchString(colorName);
        }

        private boolean matchNumeric(long actual) {
            long targetVal;
            try {
                targetVal = Long.parseLong(value);
            } catch (NumberFormatException e) {
                return false;
            }

            switch (operator) {
                case "==":
                case "=":
                    return actual == targetVal;
                case "!=":
                    return actual != targetVal;
                case ">":
                    return actual > targetVal;
                case ">=":
                    return actual >= targetVal;
                case "<":
                    return actual < targetVal;
                case "<=":
                    return actual <= targetVal;
                default:
                    return false;
            }
        }
    }
}
