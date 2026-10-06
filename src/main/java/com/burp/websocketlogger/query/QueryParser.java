package com.burp.websocketlogger.query;

import java.util.List;

public class QueryParser {
    private final List<Token> tokens;
    private int current = 0;

    public QueryParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static QueryNode parse(String queryText) throws QueryParseException {
        if (queryText == null || queryText.trim().isEmpty()) {
            return new QueryNode.AlwaysTrueNode();
        }
        Tokenizer tokenizer = new Tokenizer(queryText);
        List<Token> tokens = tokenizer.tokenize();
        QueryParser parser = new QueryParser(tokens);
        return parser.parseQuery();
    }

    public QueryNode parseQuery() throws QueryParseException {
        if (isAtEnd()) {
            return new QueryNode.AlwaysTrueNode();
        }
        QueryNode expr = parseOr();
        if (!isAtEnd()) {
            Token unexpected = peek();
            throw new QueryParseException("Unexpected token '" + unexpected.getValue() + "' at position " + unexpected.getPosition());
        }
        return expr;
    }

    private QueryNode parseOr() throws QueryParseException {
        QueryNode node = parseAnd();

        while (match(TokenType.OR)) {
            QueryNode right = parseAnd();
            node = new QueryNode.OrNode(node, right);
        }

        return node;
    }

    private QueryNode parseAnd() throws QueryParseException {
        QueryNode node = parseUnary();

        while (true) {
            if (match(TokenType.AND)) {
                QueryNode right = parseUnary();
                node = new QueryNode.AndNode(node, right);
            } else if (canStartExpression()) {
                // Implicit AND between adjacent expressions
                QueryNode right = parseUnary();
                node = new QueryNode.AndNode(node, right);
            } else {
                break;
            }
        }

        return node;
    }

    private QueryNode parseUnary() throws QueryParseException {
        if (match(TokenType.NOT)) {
            QueryNode child = parseUnary();
            return new QueryNode.NotNode(child);
        }
        return parsePrimary();
    }

    private QueryNode parsePrimary() throws QueryParseException {
        if (match(TokenType.LPAREN)) {
            QueryNode inner = parseOr();
            consume(TokenType.RPAREN, "Expected ')' after parenthesized expression");
            return inner;
        }

        if (peek().getType() == TokenType.FIELD) {
            Token fieldToken = advance();
            if (peek().getType() == TokenType.OPERATOR) {
                Token opToken = advance();
                Token valToken = advanceValue();
                return new QueryNode.ComparisonNode(fieldToken.getValue(), opToken.getValue(), valToken.getValue());
            } else {
                // Field used as raw search word
                return new QueryNode.FullTextNode(fieldToken.getValue());
            }
        }

        if (peek().getType() == TokenType.STRING_LITERAL || peek().getType() == TokenType.NUMBER_LITERAL) {
            Token valToken = advance();
            return new QueryNode.FullTextNode(valToken.getValue());
        }

        Token unexpected = peek();
        throw new QueryParseException("Expected expression at position " + unexpected.getPosition() + " but found '" + unexpected.getValue() + "'");
    }

    private boolean canStartExpression() {
        if (isAtEnd()) return false;
        TokenType t = peek().getType();
        return t == TokenType.FIELD || t == TokenType.STRING_LITERAL || t == TokenType.NUMBER_LITERAL || t == TokenType.LPAREN || t == TokenType.NOT;
    }

    private Token advanceValue() throws QueryParseException {
        Token token = peek();
        if (token.getType() == TokenType.STRING_LITERAL || token.getType() == TokenType.NUMBER_LITERAL
                || token.getType() == TokenType.FIELD) {
            return advance();
        }
        throw new QueryParseException("Expected value after operator at position " + token.getPosition());
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(TokenType type, String errorMessage) throws QueryParseException {
        if (check(type)) return advance();
        Token token = peek();
        throw new QueryParseException(errorMessage + " at position " + token.getPosition());
    }
}
