package com.burp.websocketlogger.analysis;

import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecurityScanner {

    // 1. JWT Pattern (Header.Payload.Signature in Base64URL)
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]*");

    // 2. High-entropy API Keys / Secrets
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("(?:AKIA|ASIA)[0-9A-Z]{16}");
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-(?:proj-)?[A-Za-z0-9]{20,}");
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}");
    private static final Pattern GENERIC_SECRET_PATTERN = Pattern.compile("(?i)\"(?:api[_-]?key|client[_-]?secret|auth[_-]?token|access[_-]?token|secret[_-]?key|private[_-]?key)\"\\s*:\\s*\"([^\"]{8,})\"");

    // 3. PII Patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern IRAN_PHONE_PATTERN = Pattern.compile("(?:\\+98|0)?9\\d{9}\\b");
    private static final Pattern GENERIC_PHONE_PATTERN = Pattern.compile("(?i)\"(?:phone|mobile|tel|cell)\"\\s*:\\s*\"([^\"]{7,15})\"");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("(?i)\"(?:national[_-]?id|national[_-]?code|ssn|melli[_-]?code)\"\\s*:\\s*\"?(\\d{10})\"?");

    // 4. Server Error / Exception Leaks
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(?i)(?:SQL syntax|ORA-\\d{5}|mysql_fetch|pg_query|sqlite3|NullPointerException|Stack(?:Trace)?|Traceback \\(most recent call last\\)|Unhandled(?:Rejection|Exception)|Exception in thread|Internal Server Error|Fatal error:)"
    );

    public static class ScanResult {
        private final List<String> tags = new ArrayList<>();
        private final List<String> details = new ArrayList<>();
        private boolean hasToken = false;
        private boolean hasPii = false;
        private boolean hasError = false;

        public void addTag(String tag, String detail) {
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
            if (detail != null && !details.contains(detail)) {
                details.add(detail);
            }
        }

        public List<String> getTags() {
            return tags;
        }

        public String getTagSummary() {
            if (tags.isEmpty()) return "";
            return String.join(", ", tags);
        }

        public List<String> getDetails() {
            return details;
        }

        public boolean hasFindings() {
            return !tags.isEmpty();
        }

        public boolean hasToken() {
            return hasToken;
        }

        public boolean hasPii() {
            return hasPii;
        }

        public boolean hasError() {
            return hasError;
        }
    }

    public static ScanResult scan(WebSocketLogEntry entry) {
        ScanResult result = new ScanResult();
        if (entry == null) return result;

        String payload = entry.getPayloadText();
        if (payload == null || payload.trim().isEmpty()) return result;

        // --- 1. Check for JWT ---
        Matcher jwtMatcher = JWT_PATTERN.matcher(payload);
        if (jwtMatcher.find()) {
            result.hasToken = true;
            result.addTag("🔑 Token", "JWT Token found");
        }

        // --- 2. Check for Specific API Secrets ---
        if (AWS_KEY_PATTERN.matcher(payload).find()) {
            result.hasToken = true;
            result.addTag("🔑 AWS Key", "AWS API Key detected");
        }
        if (OPENAI_KEY_PATTERN.matcher(payload).find()) {
            result.hasToken = true;
            result.addTag("🔑 Secret", "OpenAI API Key detected");
        }
        if (GITHUB_TOKEN_PATTERN.matcher(payload).find()) {
            result.hasToken = true;
            result.addTag("🔑 Secret", "GitHub Token detected");
        }

        Matcher secretMatcher = GENERIC_SECRET_PATTERN.matcher(payload);
        if (secretMatcher.find()) {
            result.hasToken = true;
            result.addTag("🔑 Secret", "Secret/Token field detected");
        }

        // --- 3. Check for PII (Emails, Phones, National IDs) ---
        Matcher emailMatcher = EMAIL_PATTERN.matcher(payload);
        if (emailMatcher.find()) {
            result.hasPii = true;
            result.addTag("👤 PII", "Email found: " + emailMatcher.group());
        }

        Matcher phoneMatcher = IRAN_PHONE_PATTERN.matcher(payload);
        if (phoneMatcher.find()) {
            result.hasPii = true;
            result.addTag("👤 PII", "Phone found: " + phoneMatcher.group());
        } else {
            Matcher genPhone = GENERIC_PHONE_PATTERN.matcher(payload);
            if (genPhone.find()) {
                result.hasPii = true;
                result.addTag("👤 PII", "Phone field found: " + genPhone.group(1));
            }
        }

        Matcher nidMatcher = NATIONAL_ID_PATTERN.matcher(payload);
        if (nidMatcher.find()) {
            result.hasPii = true;
            result.addTag("👤 PII", "National ID found: " + nidMatcher.group(1));
        }

        // --- 4. Server Errors & Exceptions ---
        Matcher errMatcher = ERROR_PATTERN.matcher(payload);
        if (errMatcher.find()) {
            result.hasError = true;
            result.addTag("⚠ Error", "Error signature found: " + errMatcher.group());
        }

        return result;
    }
}
