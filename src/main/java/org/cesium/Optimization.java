package org.cesium;

import org.cesium.ASTNode.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Optimization class contains static methods to simplify and optimize
 * the AST before code generation. It handles tasks like:
 * - Constant folding (e.g. replacing expressions like 3+5 with 8)
 * - Eliminating dead code (e.g. if(false) { ... } -> remove block)
 * - Removing while(false) loops and unreachable branches
 */
public class Optimization {

    public static CesiumProgram simplifyProgram(CesiumProgram program) {
        List<Statement> simplified = new ArrayList<>();
        for (Statement s : program.getStatements()) {
            Statement simp = simplifyStatement(s);
            if (simp != null) {
                simplified.add(simp);
            }
        }
        return new CesiumProgram(simplified);
    }

    private static Statement simplifyStatement(Statement stmt) {
        if (stmt instanceof DeclarationStatement) {
            DeclarationStatement ds = (DeclarationStatement) stmt;
            Declaration d = ds.getDeclaration();
            if (d instanceof VariableDeclaration) {
                VariableDeclaration vd = (VariableDeclaration)d;
                Expression init = vd.getInitializer();
                if (init != null) {
                    init = simplifyExpression(init);
                    vd = new VariableDeclaration(vd.getType(), vd.getIdentifier(), init);
                }
                return new DeclarationStatement(vd);
            } else if (d instanceof FunctionDeclaration) {
                FunctionDeclaration fd = (FunctionDeclaration)d;
                CodeBlock body = (CodeBlock) simplifyStatement(fd.getBody());
                return new DeclarationStatement(new FunctionDeclaration(fd.getIdentifier(), fd.getParameters(), body));
            }
        } else if (stmt instanceof AssignmentStatement) {
            AssignmentStatement as = (AssignmentStatement) stmt;
            Expression simpExpr = simplifyExpression(as.getExpression());
            return new AssignmentStatement(as.getIdentifier(), simpExpr);
        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement es = (ExpressionStatement) stmt;
            Expression simpExpr = simplifyExpression(es.getExpression());
            return new ExpressionStatement(simpExpr);
        } else if (stmt instanceof PrintStatement) {
            PrintStatement ps = (PrintStatement) stmt;
            Expression simpExpr = simplifyExpression(ps.getExpression());
            return new PrintStatement(simpExpr);
        } else if (stmt instanceof IfStatement) {
            IfStatement is = (IfStatement) stmt;
            Expression cond = simplifyExpression(is.getCondition());
            CodeBlock thenBlock = (CodeBlock) simplifyStatement(is.getThenBlock());
            CodeBlock elseBlock = is.getElseBlock() != null ? (CodeBlock) simplifyStatement(is.getElseBlock()) : null;

            Integer val = tryEvaluateCondition(cond);
            if (val != null) {
                if (val == 0) {
                    // condition false: use else block or remove entirely
                    return elseBlock != null ? elseBlock : null;
                } else {
                    // condition true: only then block matters
                    return thenBlock;
                }
            }
            return new IfStatement(cond, thenBlock, elseBlock);
        } else if (stmt instanceof WhileStatement) {
            WhileStatement ws = (WhileStatement) stmt;
            Expression cond = simplifyExpression(ws.getCondition());
            CodeBlock body = (CodeBlock) simplifyStatement(ws.getBody());

            Integer val = tryEvaluateCondition(cond);
            if (val != null && val == 0) {
                // while(false) -> remove entire loop
                return null;
            }
            return new WhileStatement(cond, body);
        } else if (stmt instanceof ForStatement) {
            ForStatement fs = (ForStatement) stmt;
            Statement init = fs.getInitialization() != null ? simplifyStatement(fs.getInitialization()) : null;
            Expression cond = fs.getCondition() != null ? simplifyExpression(fs.getCondition()) : null;
            Statement update = fs.getUpdate() != null ? simplifyStatement(fs.getUpdate()) : null;
            CodeBlock body = (CodeBlock) simplifyStatement(fs.getBody());

            if (cond != null) {
                Integer val = tryEvaluateCondition(cond);
                if (val != null && val == 0) {
                    // for(...; false; ...) no iteration
                    // Keep init if it has side effects (we assume no side effects for now)
                    List<Statement> res = new ArrayList<>();
                    if (init != null) res.add(init);
                    return new CodeBlock(res);
                }
            }
            return new ForStatement(init, cond, update, body);
        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement rs = (ReturnStatement) stmt;
            Expression simp = simplifyExpression(rs.getExpression());
            return new ReturnStatement(simp);
        } else if (stmt instanceof CodeBlock) {
            CodeBlock cb = (CodeBlock) stmt;
            List<Statement> simplifiedStmts = new ArrayList<>();
            for (Statement s : cb.getStatements()) {
                Statement simp = simplifyStatement(s);
                if (simp != null) {
                    simplifiedStmts.add(simp);
                }
            }
            return new CodeBlock(simplifiedStmts);
        }

        return stmt;
    }

    private static Expression simplifyExpression(Expression expr) {
        if (expr instanceof LiteralExpression) {
            return expr;
        } else if (expr instanceof VariableExpression) {
            return expr;
        } else if (expr instanceof UnaryExpression) {
            UnaryExpression ue = (UnaryExpression) expr;
            Expression simp = simplifyExpression(ue.getExpression());
            if (simp instanceof LiteralExpression && ue.getOperator().equals("-")) {
                LiteralExpression lit = (LiteralExpression) simp;
                if (lit.getLiteral().getType() == TokenType.NUMERIC_LITERAL) {
                    String val = lit.getLiteral().getValue();
                    if (val.startsWith("-")) {
                        val = val.substring(1);
                    } else {
                        val = "-" + val;
                    }
                    return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, val));
                }
            } else if (simp instanceof LiteralExpression && ue.getOperator().equals("!")) {
                Integer boolVal = tryEvaluateCondition(simp);
                if (boolVal != null) {
                    int inverted = (boolVal == 0) ? 1 : 0;
                    return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, String.valueOf(inverted)));
                }
            }
            return new UnaryExpression(ue.getOperator(), simp);
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression be = (BinaryExpression) expr;
            Expression left = simplifyExpression(be.getLeft());
            Expression right = simplifyExpression(be.getRight());

            // Constant folding
            if (left instanceof LiteralExpression && right instanceof LiteralExpression) {
                LiteralExpression lLit = (LiteralExpression) left;
                LiteralExpression rLit = (LiteralExpression) right;
                Token lTok = lLit.getLiteral();
                Token rTok = rLit.getLiteral();

                if (lTok.getType() == TokenType.NUMERIC_LITERAL && rTok.getType() == TokenType.NUMERIC_LITERAL) {
                    String op = be.getOperator();
                    float lVal = Float.parseFloat(lTok.getValue());
                    float rVal = Float.parseFloat(rTok.getValue());

                    boolean allIntegers = !(lTok.getValue().contains(".") || rTok.getValue().contains("."));
                    if (op.equals("+")) {
                        float res = lVal + rVal;
                        return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, formatNumeric(res, allIntegers)));
                    } else if (op.equals("-")) {
                        float res = lVal - rVal;
                        return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, formatNumeric(res, allIntegers)));
                    } else if (op.equals("*")) {
                        float res = lVal * rVal;
                        return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, formatNumeric(res, allIntegers)));
                    } else if (op.equals("/")) {
                        if (rVal != 0) {
                            float res = lVal / rVal;
                            return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, formatNumeric(res, allIntegers)));
                        }
                    } else if (isComparisonOp(op)) {
                        int cmp = evaluateComparison(lVal, rVal, op);
                        return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, String.valueOf(cmp)));
                    } else if (op.equals("||") || op.equals("&&")) {
                        int lInt = (int)lVal;
                        int rInt = (int)rVal;
                        int logic = evaluateLogic(lInt, rInt, op);
                        return new LiteralExpression(new Token(TokenType.NUMERIC_LITERAL, String.valueOf(logic)));
                    }
                }
            }

            return new BinaryExpression(left, be.getOperator(), right);
        } else if (expr instanceof FunctionCallExpression) {
            return expr;
        }
        return expr;
    }

    private static String formatNumeric(float val, boolean allInt) {
        if (allInt && val == (int)val) {
            return String.valueOf((int)val);
        } else {
            return String.valueOf(val);
        }
    }

    private static Integer tryEvaluateCondition(Expression expr) {
        if (expr instanceof LiteralExpression) {
            LiteralExpression lit = (LiteralExpression) expr;
            if (lit.getLiteral().getType() == TokenType.NUMERIC_LITERAL) {
                float v = Float.parseFloat(lit.getLiteral().getValue());
                return v == 0 ? 0 : 1;
            }
        }
        return null;
    }

    private static boolean isComparisonOp(String op) {
        return op.equals("==") || op.equals("!=")
          || op.equals(">") || op.equals("<")
          || op.equals(">=") || op.equals("<=");
    }

    private static int evaluateComparison(float lVal, float rVal, String op) {
        boolean res;
        switch(op) {
            case "==": res = (lVal == rVal); break;
            case "!=": res = (lVal != rVal); break;
            case ">": res = (lVal > rVal); break;
            case "<": res = (lVal < rVal); break;
            case ">=": res = (lVal >= rVal); break;
            case "<=": res = (lVal <= rVal); break;
            default: res = false;
        }
        return res ? 1 : 0;
    }

    private static int evaluateLogic(int lVal, int rVal, String op) {
        if (op.equals("||")) {
            return (lVal != 0 || rVal != 0) ? 1 : 0;
        } else {
            // &&
            return (lVal != 0 && rVal != 0) ? 1 : 0;
        }
    }
}