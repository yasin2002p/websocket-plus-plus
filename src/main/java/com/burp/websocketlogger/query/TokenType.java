package com.burp.websocketlogger.query;

public enum TokenType {
    FIELD,
    OPERATOR,
    STRING_LITERAL,
    NUMBER_LITERAL,
    AND,
    OR,
    NOT,
    LPAREN,
    RPAREN,
    EOF
}
