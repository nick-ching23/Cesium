 Nick Ching (nc2935)
 Nick Thevenin(nit2111)

 Lexical Analysis:
 The program's grammar produces token (handled natively within the class TokenType) for each of the following types of token:
 -  KEYWORD : Cornerstone words for Cesium such as 'Stream', 'Reactive', 'if', 'return', etc. These words are pre-defined
 -  IDENTIFIER : User-defined names for variables, functions and other entitites
 -  NUMERIC_LITERAL : Represents numeric values; can be floating-point numbers or integers
 -  STRING_LITERAL : Represents a sequence of characters enclosed by '' or "" 
 -  BOOLEAN_LITERAL : Represents the boolean values true or false
 -  OPERATOR : Represents symbols that perform operations on operands {+, -, *, /, ==, =, !=, <, >, <=, >=, &&, ||, !, =}
 -  DELIMITER : Represents punctuation marks group expressions {(, ), [, ], {, }, ;, ,} 
 -  UNKNOWN : Represents unrecognized tokens or characters that do not match any valid TokenType
 Whitespace and line comments are not tokenized by the compiler. Instead they are handled logically in order to build valid TokenTypes