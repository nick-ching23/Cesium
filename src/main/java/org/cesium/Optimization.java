package org.cesium;

import org.cesium.ASTNode.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Optimization class aims to simplify the AST tree before code gen
 * My code handles:
 * - Constant folding
 * - Eliminating dead code
 * - Removing while(false) -- these will basically never execute...
 */
public class Optimization {

    /**
     * Simplify the entire Cesium program by optimizing its statements.
     */
    public static CesiumProgram simplifyProgram(CesiumProgram program) {
        List<Statement> simplified = new ArrayList<>();
        for (Statement statement : program.getStatements()) {
             Statement simplifiedStatement = simplifyStatement(statement);
            if (simplifiedStatement != null) {
                simplified.add(simplifiedStatement);
            }
        }
        return new CesiumProgram(simplified);
    }

    /**
     * Recursively simplify a statement by handling different statement types
     * and applying optimizations.
     */
    private static Statement simplifyStatement(Statement statement) {
        return switch (statement) {
            case DeclarationStatement declarationStatement -> simplifyDeclarationStatement(declarationStatement);
            case AssignmentStatement assignmentStatement -> simplifyAssignmentStatement(assignmentStatement);
            case ExpressionStatement expressionStatement -> simplifyExpressionStatement(expressionStatement);
            case PrintStatement printStatement -> simplifyPrintStatement(printStatement);
            case IfStatement ifStatement -> simplifyIfStatement(ifStatement);
            case WhileStatement whileStatement -> simplifyWhileStatement(whileStatement);
            case ForStatement forStatement -> simplifyForStatement(forStatement);
            case ReturnStatement returnStatement -> simplifyReturnStatement(returnStatement);
            case CodeBlock codeBlock -> simplifyCodeBlock(codeBlock);
            default -> statement;
        };
    }

    /**
     * Simplify a DeclarationStatement (could be a function or a variable that we simplify  )
     */
    private static Statement simplifyDeclarationStatement(DeclarationStatement declarationStatement) {
        Declaration declaration = declarationStatement.getDeclaration();

        // simplify initializer if it's present
        if (declaration instanceof VariableDeclaration variableDeclaration) {
            Expression init = variableDeclaration.getInitializer();

            if (init != null) {
                init = simplifyExpression(init);
                variableDeclaration = new VariableDeclaration(variableDeclaration.getType(),
                  variableDeclaration.getIdentifier(), init);
            }
            return new DeclarationStatement(variableDeclaration);
        }

        // simplify the function body
        if (declaration instanceof FunctionDeclaration functionDeclaration) {
            CodeBlock body = (CodeBlock) simplifyStatement(functionDeclaration.getBody());

            FunctionDeclaration  simplifiedFd = new FunctionDeclaration(
              functionDeclaration.getIdentifier(), functionDeclaration.getParameters(), body
            );

            return new DeclarationStatement(simplifiedFd);
        }

        return declarationStatement;
    }

    /**
     * Simplify an AssignmentStatement
     */
    private static Statement simplifyAssignmentStatement(AssignmentStatement as) {
        Expression simpExpr = simplifyExpression(as.getExpression());
        return new AssignmentStatement(as.getIdentifier(), simpExpr);
    }

    /**
     * Simplify an ExpressionStatement
     */
    private static Statement simplifyExpressionStatement(ExpressionStatement es) {
        Expression simpExpr = simplifyExpression(es.getExpression());
        return new ExpressionStatement(simpExpr);
    }

    /**
     * Simplify a PrintStatement
     */
    private static Statement simplifyPrintStatement(PrintStatement ps) {
        Expression simpExpr = simplifyExpression(ps.getExpression());
        return new PrintStatement(simpExpr);
    }

    /**
     * Simplify an IfStatement by evaluating its condition
     * we cut branches if the condition is just constantly true or false
     */
    private static Statement simplifyIfStatement(IfStatement is) {
        Expression condition = simplifyExpression(is.getCondition());
        CodeBlock thenBlock = (CodeBlock) simplifyStatement(is.getThenBlock());
         CodeBlock elseBlock;

        // simplify if there is an else block
        if (is.getElseBlock() != null) {
            elseBlock = (CodeBlock) simplifyStatement(is.getElseBlock());
        } else {
            elseBlock = null;
        }

        Boolean val = tryEvaluateCondition(condition);
        if (val != null) {
            // Condition is constant
            if (!val) {
                return elseBlock; // if we get here only use else
            } else {
                return thenBlock; // if we get here, only use the then block
            }
        }

        // return ifStatement if there are no optimizations to be made here.
        return new IfStatement(condition, thenBlock, elseBlock);
    }

    /**
     * Simplify a WhileStatement by looking at the condition.
     * If the while condition is false, then we can simply skip the entire while loop.
     */
    private static Statement simplifyWhileStatement(WhileStatement whileStatement) {
        Expression condition = simplifyExpression(whileStatement.getCondition());
        CodeBlock body = (CodeBlock) simplifyStatement(whileStatement.getBody());

        Boolean val = tryEvaluateCondition(condition);

        // if the condition is false, we can just skip the entire while loop.
        if (val != null && !val) {
            return null;
        }

        // otherwise we simply return the while statement.
        return new WhileStatement(condition, body);
    }


    /**
      * Simplify a ForStatement by checking if its condition is always false. It is conceivable
     * that a for loop will not run at all (if initialized vars are already out of the bounds)
     * I am currently assuming no side-effects.
     */
    private static Statement simplifyForStatement(ForStatement forStatement) {
        // Simplify each component of the for loop
        Statement initStatement = null;
        if (forStatement.getInitialization() != null) {
            initStatement = simplifyStatement(forStatement.getInitialization());
        }

        // simplify the condition
        Expression condition = null;
        if (forStatement.getCondition() != null) {
            condition = simplifyExpression(forStatement.getCondition());
        }

        // simplify the update statement
        Statement update = null;
        if (forStatement.getUpdate() != null) {
             update = simplifyStatement(forStatement.getUpdate());
        }

        CodeBlock body = (CodeBlock) simplifyStatement(forStatement.getBody());

        // If the condition is known to be false, the loop never runs
        if (condition != null) {
            Boolean value = tryEvaluateCondition(condition);
            if (value != null && !value) {
                List<Statement> initOnly = new ArrayList<>();

                if (initStatement != null) {
                    initOnly.add(initStatement);
                }

                return new CodeBlock(initOnly);
            }
        }


        // If condition is not false at compile, we just return the for loop as is
        return new ForStatement(initStatement, condition, update, body);
    }

    /**
     * Simplify a ReturnStatement
     */
    private static Statement simplifyReturnStatement(ReturnStatement returnStatement) {
        Expression simplifiedReturn = simplifyExpression(returnStatement.getExpression());
        return new ReturnStatement(simplifiedReturn);
    }



    /**
     * Simplify a CodeBlock. Note that here we basically simplify the contents of the code block
     * which can be recursive.
     */
    private static Statement simplifyCodeBlock(CodeBlock codeBlock) {
        List<Statement> simplifiedStatements = new ArrayList<>();
        for (Statement statement : codeBlock.getStatements()) {
            Statement simplifiedStatement = simplifyStatement(statement);

            if (simplifiedStatement != null) {
                simplifiedStatements.add(simplifiedStatement);
            }
        }

        // we will end up with a clean and simplified code block here...
        return new CodeBlock(simplifiedStatements);
    }

    /**
     * Actual logic for simplifying expressions: apply constnat folding, and logical simplifications
     */
    private static Expression simplifyExpression(Expression expression) {

        // we can't really simplify these
        if (expression instanceof LiteralExpression
          || expression instanceof VariableExpression
          || expression instanceof FunctionCallExpression) {
            return expression;
        }
        else if (expression instanceof UnaryExpression) {
            return simplifyUnaryExpression((UnaryExpression) expression);
        }

        else if (expression instanceof BinaryExpression) {
            return simplifyBinaryExpression((BinaryExpression) expression);
        }

        // Fallback if no other rule applies
        return expression;
     }

    /**
     * Simplify a UnaryExpression by applying constant folding if operand is
     * a literal.
     */
    private static Expression simplifyUnaryExpression(UnaryExpression unaryExpression) {
        // First, simplify the inner expression
        Expression simplifiedInner = simplifyExpression(unaryExpression.getExpression());

        // If the inner expression is a literal, attempt constant folding
        if (simplifiedInner instanceof LiteralExpression literalExpression) {
             String operator = unaryExpression.getOperator();

            // Handles numeric negations like: -(-5) = 5
            if ("-".equals(operator) && literalExpression.getLiteral().getType() == TokenType.NUMERIC_LITERAL) {
                    String value = literalExpression.getLiteral().getValue();

                if (value.startsWith("-")) {
                     value = value.substring(1);
                 } else {
                    value = "-" + value;
                }
                return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, value));
            }

            // Handle boolean negation
            if ("!".equals(operator)) {
                Boolean boolVal = tryEvaluateCondition(simplifiedInner);
                if (boolVal != null) {
                    Boolean inverted = !boolVal;
                    return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, String.valueOf(inverted)));
                }
             }
        }

        // If no constant folding or simplification could be applied, we return the simplified inner
        // statement...
        return new UnaryExpression(unaryExpression.getOperator(), simplifiedInner);
    }


    /**
     * Simplifies a BinaryExpression
     */
    private static Expression simplifyBinaryExpression(BinaryExpression binaryExpression) {
        Expression left = simplifyExpression(binaryExpression.getLeft());
        Expression right = simplifyExpression(binaryExpression.getRight());

        // Constant folding if both sides are literals. we recursively treat each side
        // of the binary expression as something to eb simplified!
        if (left instanceof LiteralExpression && right instanceof LiteralExpression) {
            return foldBinaryExpression((LiteralExpression) left,
              (LiteralExpression) right, binaryExpression.getOperator());
        }

        // otherwise if no simplifications can be made, we just return as is.
        return new BinaryExpression(left, binaryExpression.getOperator(), right);
    }

    /**
     * Attempt constant folding on a binary operation.
     * We can split up binary expressions into arithmetic, or boolean. Here we handle both
     */
    private static Expression foldBinaryExpression(LiteralExpression leftLiteral,
                                                   LiteralExpression rightOperation, String operator) {

        Token leftToken = leftLiteral.getLiteral();
        Token rightToken = rightOperation.getLiteral();
        TokenType numeric = TokenType.NUMERIC_LITERAL;

        if (leftToken.getType() == numeric && rightToken.getType() == numeric) {
            float leftValue = Float.parseFloat(leftToken.getValue());
            float rightValue = Float.parseFloat(rightToken.getValue());
            boolean allIntegers = !(leftToken.getValue().contains(".") || rightToken.getValue().contains("."));

            // Handle basic arithmetic operators
            switch (operator) {
                case "+":
                    return numericLiteral(leftValue + rightValue, allIntegers);
                case "-":
                    return numericLiteral(leftValue - rightValue, allIntegers);
                case "*":
                    return numericLiteral(leftValue * rightValue, allIntegers);
                case "/": // remember to handle div by 0 errors.
                    if (rightValue != 0) {
                        return numericLiteral(leftValue / rightValue, allIntegers );
                    }
                    break;
                default:
                    break;
            }

            // handle logical operators
            if (isComparisonOp(operator)) {
                boolean comparsion = evaluateComparison(leftValue, rightValue, operator);
                return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, String.valueOf(comparsion)));
            }

            // handle && and ||
            if (operator.equals("||") || operator.equals("&&")) {
                int leftInt = (int) leftValue;
                int rightInt = (int) rightValue;
                boolean logic = evaluateLogic(leftInt, rightInt, operator);
                return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, String. valueOf(logic)));
            }
        }

        // again return if its not foldable at all
        return new BinaryExpression(leftLiteral, operator, rightOperation);
    }

    /**
     * Create a numeric literal expression with proper integer or float format
     */
    private static Expression numericLiteral(float val, boolean allIntegers) {
        return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, formatNumeric(val, allIntegers)));
    }


    /**
     * Format numeric value as integer if it is a whole number and allInt is true.
     */
    private static String formatNumeric(float val, boolean allInt) {
        if (allInt && val == (int) val) {
            return String.valueOf((int) val);
        }
        return String.valueOf(val);
    }

    /**
     * Try to evaluate condition to a boolean if it's a numeric literal.
     * Returns null if you can't even evaluate it
     */
    private static Boolean tryEvaluateCondition(Expression expression) {
        if (expression instanceof LiteralExpression literalExpression) {

            if (literalExpression.getLiteral().getType() == TokenType.NUMERIC_LITERAL) {
                float numericValue = Float.parseFloat(literalExpression.getLiteral().getValue());
                return numericValue != 0.0f;
            }
        }

        return null;
    }

    /**
     * Check if the given  operator is a comparison operator
     */
    private static boolean isComparisonOp(String op) {
        return op.equals("==") || op.equals("!=")
          || op.equals(">") || op.equals("<")
          || op.equals(">=") || op.equals("<=");
    }

    /**
     * Evaluate a comparison operation between two float values. returns true or false
     */
    private static boolean evaluateComparison(float leftValue, float rightValue, String operator) {
        return switch (operator) {
            case ">" -> (leftValue > rightValue);
            case "<" -> (leftValue <  rightValue);
            case ">=" -> (leftValue >= rightValue);
            case "<=" -> (leftValue <= rightValue);
            case "==" -> (leftValue == rightValue);
            case "!=" -> (leftValue != rightValue);

            default -> false;
        };
    }


    /**
     * Evaluate logical operations (|| and &&)
     */
    private static boolean evaluateLogic(int leftValue, int rightValue, String operator) {
        if (operator.equals("||")) {
            return (leftValue != 0 || rightValue != 0);
        }
        return (leftValue != 0 && rightValue != 0);
    }
}





