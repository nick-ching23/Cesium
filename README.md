<h1 align="center">Cesium: A Custom Reactive Programming Language</h1>
<p align="center">
Collaborators: Nick Ching (nc2935), Nick Thevenin (nit2111) 
</p>



## About The Project

Cesium is a custom-built programming language designed to natively support reactive programming. It empowers seamless data synchronization and execution flow. This phase of the project focuses on building the Lexical Analyzer, which tokenizes source code according to the Cesium language’s lexical grammar.



## **Current Progress**
   **9 Dec 2024:** Implemented the Code Generation phase and optimization. [Project is now complete] 

   **12 Nov 2024:** Completed the Parser. Please refer to the bottom of this document for an explanation of our grammar
   
   **15 Oct 2024:** Completed Lexical Analyzer (tokenizing source code into Lexemes)

## **How does Cesium Compile?**

At its core, Cesium's compiler is written and executing using Java. This compiler takes Cesium Code, converts it to Java Byte Code directly and executes that byte code using the Java virtual machine. As a result, this code is operable on any machine so long as the JVM is installed. This was an incredibly important component of the project. 

---

## **Requirements to run compiler**
1. Java Development Kit (JDK): Version 11 or later
   - Ensure that javac and java are installed and properly configured in your system's PATH.
   - you can verify this by running javac --version and java-version

2. Maven: Used for building the project
   - Maven should be installed and accessible via the CLI
   - you can verify this by running mvn --version
  
3. Bash Shell: For running test scripts to view results of testing lexical analyzer
   - On macOS and Linux, bash is pre-installed.
   - For Windows, you may need to install Git Bash or use Windows Subsystem for Linux (WSL).
  
4. Intellij: Note that this project was built using Intellij IDEA, but it should work with any Java-compatible IDE. 




## **Installation Guide** 

First, clone the Cesium project from the Git repository to your local machine:

```bash
git clone https://github.com/nick-ching23/Cesium.git
cd cesium
```

Run build the project using Maven: 
```bash
mvn clean install

````

Build the project: 
```bash
mvn compile
```

Running the project: 
```bash
java -cp target/classes org.cesium.Main src/main/resources/LATest_BasicStream.ces
```

## **Testing**

Note: Refer to  ```bash /src/main/resources/``` for more details about tests 


Running Tests for lexical analyzer: 
```bash
./test_script.sh
```

Running Tests for parser:
```bash
./parse_test_script.sh
```

Running Cesium code:
```bash
./cesiusm.sh src/main/resources/<cesium_file>.ces MyCesiumProgram
```
---

## **Phase 1: About the Lexical Analyzer**

**Cesium's Lexical Grammar**

This program's grammar produces tokens (handled natively within the class TokenType) for each of the following token types: 

 -  KEYWORD : Cornerstone words for Cesium such as 'Stream', 'Reactive', 'if', 'return', etc. These words are pre-defined
 -  IDENTIFIER : User-defined names for variables, functions and other entitites
 -  NUMERIC_LITERAL : Represents numeric values; can be floating-point numbers or integers
 -  STRING_LITERAL : Represents a sequence of characters enclosed by '' or "" 
 -  BOOLEAN_LITERAL : Represents the boolean values true or false
 -  OPERATOR : Represents symbols that perform operations on operands {+, -, *, /, ==, =, !=, <, >, <=, >=, &&, ||, !, =}
 -  DELIMITER : Represents punctuation marks group expressions ()[]{};,.
 -  UNKNOWN : Represents unrecognized tokens or characters that do not match any valid TokenType


**Key classes for our lexer:**
- Scanner.java: contains main logic for how to tokenize Cesium source code
- Token.java: Defines attributes of Tokens (type: value)
- TokenType.java: Defines types of tokens


**Key Tokenization Steps:**
1.	Whitespace & Comments: Skips over whitespace and handles single-line (//) and multi-line (/* */) comments.
2.	Keywords & Identifiers: Identifies keywords (e.g., Stream, Reactive) and user-defined identifiers (variable or function names).
3.	Numeric Literals: Processes numbers, handling both integers and floating-point literals. Throws exceptions for invalid numbers (e.g., 1.).
4.	String Literals: Handles string literals enclosed in double quotes ("). Throws an exception if the string is not properly closed.
5.	Operators & Delimiters: Detects single or multi-character operators (e.g., +, ==, !=) and punctuation like (), {}, ;.
6.	Error Handling:	If an unrecognized token is encountered, it throws a LexicalException to signal an error.
7.	Return Tokens: After processing all characters, it returns a list of tokens representing the source code.



Whitespace and comments in Cesium are not tokenized; they are skipped to ensure valid tokens are created. The TokenType.java file defines all valid tokens, while the main scanning logic is handled by Scanner.java. The scanner processes the source code by iterating through each character, skipping over whitespace and comments, and identifying valid tokens such as keywords, identifiers, numeric literals, and operators. Helper methods like isLetter() and isDigit() assist in identifying different token types. The scanSourceCode() method returns a list of tokens formatted as <Token Type, Token Value>, ready for further processing.​⬤


---------------------------------------------------------
**Programming Assignment #2:** 

Demo Link: [https://youtu.be/AvQQEknEulM]

Cesium's Context Free Grammar:


```plaintext
CesiumCode -> StatementList 
StatementList -> Statement | StatementList Statement 

Statement -> 
            DeclarationStatement ; 
          | AssignmentStatement ; 
          | ReturnStatement ; 
          | ExpressionStatement ;
          | IfStatement 
          | ForStatement 
          | WhileStatement 
          | PrintStatement ;     
          | CodeBlock 

DeclarationStatement -> VariableDeclaration | FunctionDeclaration
VariableDeclaration -> Type IDENTIFIER '=' Expression | Type IDENTIFIER
FunctionDeclaration -> 'function' IDENTIFIER '(' ParameterList ')' '{' CodeBlock '}'
ParameterList -> Parameter | ParameterList ',' Parameter 
Parameter -> Type IDENTIFIER 
Type -> 'int' | 'float' | 'string' | 'Stream' | 'Reactive' 

AssignmentStatement -> IDENTIFIER '=' Expression 
ExpressionStatement -> Expression 
ReturnStatement -> 'return' Expression

PrintStatement -> 'print' '(' Expression ')'

Expression -> OrExpression 
OrExpression -> OrExpression '||' AndExpression | AndExpression 
AndExpression -> AndExpression '&&' EqualityExpression | EqualityExpression 
EqualityExpression -> EqualityExpression ('==' | '!=') RelationalExpression | RelationalExpression 
RelationalExpression -> RelationalExpression ('>' | '<' | '>=' | '<=') AdditiveExpression | AdditiveExpression
AdditiveExpression -> AdditiveExpression ('+' | '-') MultiplicativeExpression | MultiplicativeExpression
MultiplicativeExpression -> MultiplicativeExpression ('*' | '/') UnaryExpression | UnaryExpression
UnaryExpression -> '!' UnaryExpression | PrimaryExpression
PrimaryExpression ->
      '(' Expression ')'
    | IDENTIFIER
    | NUMERIC_LITERAL
    | STRING_LITERAL
    | BOOLEAN_LITERAL
    | FunctionCall

FunctionCall -> IDENTIFIER '(' ')' | IDENTIFIER '(' ArgumentList ')'
ArgumentList -> Expression | ArgumentList ',' Expression

ForStatement -> 'for' '(' InitFor ';' Expression ';' UpdateFor ')' '{' CodeBlock '}' 
IfStatement -> 'if' '(' Expression ')' '{' CodeBlock '}' ('else' CodeBlock) // brackets indicate optional here!
InitFor -> VariableDeclaration | AssignmentStatement | ε
UpdateFor -> AssignmentStatement | ε
WhileStatement -> 'while' '(' Expression ')' '{' CodeBlock '}'

CodeBlock -> '{' StatementList '}' | '{' '}'
```

## Our parsing algorithm: 
Our solution implement a recursive descent parser with LL(1) approach. 


## A note on Cesium Reactivity
The current implmentation of reactivity is not fully fleshed out due to the time constraint of this project. As it stands you are able to create a Stream variable which receives continuous data, and a Reactive variable which subscribes to a Stream. Whenever a stream updates a value, so do all of the reactive variables. 

I understand that this is an incredibly bare bones implementation, but it is suitable for the purposes of this project. Please refer to test case:
src/main/resources/Reactivity.ces for an example of reactivity has been implemnted here. 
