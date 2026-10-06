package com.burp.websocketlogger.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Tokenizer {
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "payload", "body", "data", "p",
            "dir", "direction", "d",
            "host", "h",
            "port",
            "path",
            "url",
            "tool",
            "type",
            "length", "len", "size",
            "id",
            "conn", "connection",
            "comment",
            "color", "highlight",
            "tag", "tags", "security"
    );

    private final String input;
    private int pos = 0;

    public Tokenizer(String input) {
        this.input = input != null ? input : "";
    }

    public List<Token> tokenize() throws QueryParseException {
        List<Token> tokens = new ArrayList<>();
        skipWhitespace();

        while (pos < input.length()) {
            char c = input.charAt(pos);

            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", pos));
                pos++;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", pos));
                pos++;
            } else if (c == '&' && pos + 1 < input.length() && input.charAt(pos + 1) == '&') {
                tokens.add(new Token(TokenType.AND, "&&", pos));
                pos += 2;
            } else if (c == '|' && pos + 1 < input.length() && input.charAt(pos + 1) == '|') {
                tokens.add(new Token(TokenType.OR, "||", pos));
                pos += 2;
            } else if (c == '=' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "==", pos));
                pos += 2;
            } else if (c == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "==", pos));
                pos++;
            } else if (c == '!' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "!=", pos));
                pos += 2;
            } else if (c == '!' && pos + 8 < input.length() && input.substring(pos, pos + 9).equalsIgnoreCase("!contains")) {
                tokens.add(new Token(TokenType.OPERATOR, "!contains", pos));
                pos += 9;
            } else if (c == '!' && pos + 7 < input.length() && input.substring(pos, pos + 8).equalsIgnoreCase("!matches")) {
                tokens.add(new Token(TokenType.OPERATOR, "!matches", pos));
                pos += 8;
            } else if (c == '!' && pos + 5 < input.length() && input.substring(pos, pos + 6).equalsIgnoreCase("!regex")) {
                tokens.add(new Token(TokenType.OPERATOR, "!regex", pos));
                pos += 6;
            } else if (c == '!') {
                tokens.add(new Token(TokenType.NOT, "!", pos));
                pos++;
            } else if (c == '>' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, ">=", pos));
                pos += 2;
            } else if (c == '>') {
                tokens.add(new Token(TokenType.OPERATOR, ">", pos));
                pos++;
            } else if (c == '<' && pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "<=", pos));
                pos += 2;
            } else if (c == '<') {
                tokens.add(new Token(TokenType.OPERATOR, "<", pos));
                pos++;
            } else if (c == '"' || c == '\'') {
                tokens.add(readQuotedString(c));
            } else {
                tokens.add(readIdentifierOrLiteral());
            }

            skipWhitespace();
        }

        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private Token readQuotedString(char quoteChar) throws QueryParseException {
        int start = pos;
        pos++; // Skip opening quote
        StringBuilder sb = new StringBuilder();

        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\\') {
                pos++;
                if (pos < input.length()) {
                    char escaped = input.charAt(pos);
                    if (escaped == 'n') sb.append('\n');
                    else if (escaped == 'r') sb.append('\r');
                    else if (escaped == 't') sb.append('\t');
                    else if (escaped == quoteChar) sb.append(quoteChar);
                    else if (escaped == '\\') sb.append('\\');
                    else {
                        // Preserve backslash for regex patterns like \s, \d, \w, etc.
                        sb.append('\\').append(escaped);
                    }
                    pos++;
                }
            } else if (c == quoteChar) {
                pos++; // Skip closing quote
                return new Token(TokenType.STRING_LITERAL, sb.toString(), start);
            } else {
                sb.append(c);
                pos++;
            }
        }

        throw new QueryParseException("Unterminated string quote starting at position " + start);
    }

    private Token readIdentifierOrLiteral() {
        int start = pos;
        StringBuilder sb = new StringBuilder();

        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c) || c == '(' || c == ')' || c == '=' || c == '!' || c == '<' || c == '>' || c == '&' || c == '|' || c == '"' || c == '\'') {
                break;
            }
            sb.append(c);
            pos++;
        }

        String word = sb.toString();
        String lower = word.toLowerCase();

        if (lower.equals("and")) {
            return new Token(TokenType.AND, word, start);
        } else if (lower.equals("or")) {
            return new Token(TokenType.OR, word, start);
        } else if (lower.equals("not")) {
            return new Token(TokenType.NOT, word, start);
        } else if (lower.equals("contains") || lower.equals("matches") || lower.equals("regex")
                || lower.equals("startswith") || lower.equals("endswith")) {
            return new Token(TokenType.OPERATOR, lower, start);
        } else if (KNOWN_FIELDS.contains(lower)) {
            return new Token(TokenType.FIELD, lower, start);
        } else if (isInteger(word)) {
            return new Token(TokenType.NUMBER_LITERAL, word, start);
        } else {
            return new Token(TokenType.STRING_LITERAL, word, start);
        }
    }

    private boolean isInteger(String s) {
        if (s.isEmpty()) return false;
        int i = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') {
            if (s.length() == 1) return false;
            i = 1;
        }
        for (; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
