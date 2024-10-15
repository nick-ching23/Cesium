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
 TokenType.java defines this
 Scanner.java holds the main functionality for the Lexical Analysis phase. The constructor takes in the source code as an argument
 and initializes the scanner's state while observing the total length of the Cesium program. scanSourceCode() holds the logic to scan the entire source code until all characters are processed. First it looks for inputs that aren't tokenized, so characters such as '/'.
 These tokens are whitespace or comments; once detected we can skip over these until actual token characters are seen, such as NUMERIC_LITERAL. Several helper methods, such as isLetter(), isDigit(), or scanIdentifierOrKeyword(), are crucial for this step.
 The scanning methods return Token objects, which are built into the final returned list of tokens formatted as <Token Type, Token Value>.