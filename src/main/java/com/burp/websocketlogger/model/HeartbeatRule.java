package com.burp.websocketlogger.model;

import com.burp.websocketlogger.query.QueryNode;
import com.burp.websocketlogger.query.QueryParseException;
import com.burp.websocketlogger.query.QueryParser;

public class HeartbeatRule {
    private String id;
    private String name;
    private String query;
    private boolean enabled;
    private transient QueryNode compiledNode;
    private transient String compileError;

    public HeartbeatRule(String id, String name, String query, boolean enabled) {
        this.id = id;
        this.name = name != null ? name : "";
        this.query = query != null ? query.trim() : "";
        this.enabled = enabled;
        compile();
    }

    public void compile() {
        if (query == null || query.trim().isEmpty()) {
            this.compiledNode = null;
            this.compileError = "Empty query";
            return;
        }
        try {
            this.compiledNode = QueryParser.parse(query.trim());
            this.compileError = null;
        } catch (QueryParseException e) {
            this.compiledNode = null;
            this.compileError = e.getMessage();
        } catch (Exception e) {
            this.compiledNode = null;
            this.compileError = "Error: " + e.getMessage();
        }
    }

    public boolean matches(WebSocketLogEntry entry) {
        if (!enabled || compiledNode == null || entry == null) {
            return false;
        }
        try {
            return compiledNode.matches(entry);
        } catch (Exception e) {
            return false;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query != null ? query.trim() : "";
        compile();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isValid() {
        return compileError == null && compiledNode != null;
    }

    public String getCompileError() {
        return compileError;
    }
}
