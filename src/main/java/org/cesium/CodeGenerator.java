package org.cesium;

import org.cesium.ASTNode.*;
import org.objectweb.asm.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class CodeGenerator {

    private ClassWriter classWriter;
    private String className;
    private MethodVisitor currentMethodVisitor;
    private Deque<MethodVisitor> methodStack = new ArrayDeque<>();

    // Each scope: variable name -> local var index
    private Deque<Map<String, Integer>> variableScopes = new ArrayDeque<>();
    // Each scope: variable name -> type ("int", "float", "string", "Stream", "Reactive")
    private Deque<Map<String, String>> variableTypes = new ArrayDeque<>();

    private Deque<Integer> localVarIndex = new ArrayDeque<>();

    private Map<String, MethodDescriptor> functionSignatures = new HashMap<>();

    static class MethodDescriptor {
        String name;
        String desc; // e.g. "(II)I"
    }

    public void generateCode(CesiumProgram program, String outputClassName) throws IOException {
        // First, simplify the entire program's AST before code generation using the Optimization class
        CesiumProgram simplifiedProgram = Optimization.simplifyProgram(program);

        this.className = outputClassName.replace('.', '/');
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Generate default constructor
        generateDefaultConstructor();

        // Create main method
        startMethod("main", "([Ljava/lang/String;)V", ACC_PUBLIC + ACC_STATIC);

        // Generate code for top-level statements (now simplified)
        for (Statement stmt : simplifiedProgram.getStatements()) {
            generateStatement(stmt);
        }

        // return from main
        currentMethodVisitor.visitInsn(RETURN);
        endMethod();

        classWriter.visitEnd();
        try (FileOutputStream fos = new FileOutputStream(outputClassName + ".class")) {
            fos.write(classWriter.toByteArray());
        }
    }

    private void generateDefaultConstructor() {
        MethodVisitor constructor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private void startMethod(String name, String desc, int access) {
        MethodVisitor mv = classWriter.visitMethod(access, name, desc, null, null);
        mv.visitCode();
        currentMethodVisitor = mv;
        methodStack.push(mv);

        Map<String, Integer> varMap = new HashMap<>();
        variableScopes.push(varMap);
        Map<String, String> typeMap = new HashMap<>();
        variableTypes.push(typeMap);

        int startIndex = (name.equals("main") ? 1 : 0) + (desc.equals("([Ljava/lang/String;)V") ? 1 : 0);
        localVarIndex.push(startIndex);
    }

    private void endMethod() {
        MethodVisitor mv = methodStack.pop();
        mv.visitMaxs(0,0);
        mv.visitEnd();
        if (!methodStack.isEmpty()) {
            currentMethodVisitor = methodStack.peek();
        } else {
            currentMethodVisitor = null;
        }
        variableScopes.pop();
        variableTypes.pop();
        localVarIndex.pop();
    }

    private void generateStatement(Statement statement) {
        if (statement instanceof DeclarationStatement) {
            generateDeclarationStatement((DeclarationStatement) statement);
        } else if (statement instanceof AssignmentStatement) {
            generateAssignmentStatement((AssignmentStatement) statement);
        } else if (statement instanceof ExpressionStatement) {
            generateExpression(((ExpressionStatement) statement).getExpression());
            currentMethodVisitor.visitInsn(POP);
        } else if (statement instanceof PrintStatement) {
            generatePrintStatement((PrintStatement) statement);
        } else if (statement instanceof IfStatement) {
            generateIfStatement((IfStatement) statement);
        } else if (statement instanceof WhileStatement) {
            generateWhileStatement((WhileStatement) statement);
        } else if (statement instanceof ForStatement) {
            generateForStatement((ForStatement) statement);
        } else if (statement instanceof ReturnStatement) {
            generateReturnStatement((ReturnStatement) statement);
        } else if (statement instanceof CodeBlock) {
            generateCodeBlock((CodeBlock) statement);
        } else {
            throw new UnsupportedOperationException("Unsupported statement type: " + statement.getClass());
        }
    }

    private void generateDeclarationStatement(DeclarationStatement stmt) {
        Declaration decl = stmt.getDeclaration();
        if (decl instanceof VariableDeclaration) {
            VariableDeclaration varDecl = (VariableDeclaration) decl;
            String varName = varDecl.getIdentifier();
            String type = varDecl.getType();

            if(!isSupportedType(type)) {
                throw new UnsupportedOperationException("Unsupported type: " + type);
            }

            int index = allocateLocalVar(varName);
            variableTypes.peek().put(varName, type);

            if (varDecl.getInitializer() != null) {
                generateExpression(varDecl.getInitializer());
                storeVariable(type, index);
            } else {
                // default init
                defaultInit(type, index);
            }
        } else if (decl instanceof FunctionDeclaration) {
            generateFunctionDeclaration((FunctionDeclaration) decl);
        } else {
            throw new UnsupportedOperationException("Unknown declaration type");
        }
    }

    private boolean isSupportedType(String t) {
        switch (t) {
            case "int":
            case "float":
            case "string":
            case "Stream":
            case "Reactive":
                return true;
            default:
                return false;
        }
    }

    private void defaultInit(String type, int index) {
        switch (type) {
            case "int":
                currentMethodVisitor.visitLdcInsn(0);
                currentMethodVisitor.visitVarInsn(ISTORE, index);
                break;
            case "float":
                currentMethodVisitor.visitLdcInsn(0.0f);
                currentMethodVisitor.visitVarInsn(FSTORE, index);
                break;
            case "string":
                currentMethodVisitor.visitLdcInsn("");
                currentMethodVisitor.visitVarInsn(ASTORE, index);
                break;
            case "Stream":
            case "Reactive":
                // Default to null
                currentMethodVisitor.visitInsn(ACONST_NULL);
                currentMethodVisitor.visitVarInsn(ASTORE, index);
                break;
        }
    }

    private void generateFunctionDeclaration(FunctionDeclaration funcDecl) {
        StringBuilder desc = new StringBuilder("(");
        for (Parameter p : funcDecl.getParameters()) {
            String pType = p.getType();
            if (!isSupportedType(pType)) {
                throw new UnsupportedOperationException("Parameter type not supported: " + pType);
            }
            desc.append(getTypeDescriptor(pType));
        }
        desc.append(")I");
        MethodDescriptor md = new MethodDescriptor();
        md.name = funcDecl.getIdentifier();
        md.desc = desc.toString();
        functionSignatures.put(funcDecl.getIdentifier(), md);

        startMethod(funcDecl.getIdentifier(), md.desc, ACC_PUBLIC + ACC_STATIC);
        // Add parameters
        int idx = 0;
        for (Parameter p : funcDecl.getParameters()) {
            variableScopes.peek().put(p.getIdentifier(), idx);
            variableTypes.peek().put(p.getIdentifier(), p.getType());
            idx += getLocalVarIncrement(p.getType());
        }
        generateCodeBlock(funcDecl.getBody());
        // If no explicit return
        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitInsn(IRETURN);
        endMethod();
    }

    private int getLocalVarIncrement(String type) {
        // all one slot
        return 1;
    }

    private String getTypeDescriptor(String type) {
        switch (type) {
            case "int":
                return "I";
            case "float":
                return "F";
            case "string":
                return "Ljava/lang/String;";
            case "Stream":
            case "Reactive":
                return "Ljava/lang/Object;";
            default:
                throw new UnsupportedOperationException("getTypeDescriptor: unsupported type " + type);
        }
    }

    private void generateAssignmentStatement(AssignmentStatement stmt) {
        String varName = stmt.getIdentifier();
        Integer idx = variableScopes.peek().get(varName);
        if (idx == null) {
            throw new RuntimeException("Variable not declared: " + varName);
        }
        String type = variableTypes.peek().get(varName);
        generateExpression(stmt.getExpression());
        storeVariable(type, idx);
    }

    private void generatePrintStatement(PrintStatement stmt) {
        currentMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

        Expression expr = stmt.getExpression();
        String exprType = getExpressionType(expr);

        generateExpression(expr);
        switch (exprType) {
            case "int":
                currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
                break;
            case "float":
                currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(F)V", false);
                break;
            case "string":
                currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                break;
            case "Stream":
            case "Reactive":
                currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                break;
            default:
                currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                break;
        }
    }

    private void generateIfStatement(IfStatement stmt) {
        Label elseLabel = new Label();
        Label endLabel = new Label();

        generateExpression(stmt.getCondition());
        currentMethodVisitor.visitJumpInsn(IFEQ, elseLabel);

        generateStatement(stmt.getThenBlock());
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(elseLabel);
        if (stmt.getElseBlock() != null) {
            generateStatement(stmt.getElseBlock());
        }

        currentMethodVisitor.visitLabel(endLabel);
    }

    private void generateWhileStatement(WhileStatement stmt) {
        Label loopStart = new Label();
        Label loopEnd = new Label();

        currentMethodVisitor.visitLabel(loopStart);
        generateExpression(stmt.getCondition());
        currentMethodVisitor.visitJumpInsn(IFEQ, loopEnd);

        generateStatement(stmt.getBody());
        currentMethodVisitor.visitJumpInsn(GOTO, loopStart);

        currentMethodVisitor.visitLabel(loopEnd);
    }

    private void generateForStatement(ForStatement stmt) {
        if (stmt.getInitialization() != null) {
            generateStatement(stmt.getInitialization());
        }
        Label loopStart = new Label();
        Label loopEnd = new Label();

        currentMethodVisitor.visitLabel(loopStart);

        if (stmt.getCondition() != null) {
            generateExpression(stmt.getCondition());
            currentMethodVisitor.visitJumpInsn(IFEQ, loopEnd);
        }

        generateStatement(stmt.getBody());

        if (stmt.getUpdate() != null) {
            generateStatement(stmt.getUpdate());
        }

        currentMethodVisitor.visitJumpInsn(GOTO, loopStart);
        currentMethodVisitor.visitLabel(loopEnd);
    }

    private void generateReturnStatement(ReturnStatement stmt) {
        // main method: void return, function: int return
        if (methodStack.size() == 1) {
            // in main
            if (stmt.getExpression() != null) {
                generateExpression(stmt.getExpression());
                currentMethodVisitor.visitInsn(POP);
            }
            currentMethodVisitor.visitInsn(RETURN);
        } else {
            // in a function returning int
            generateExpression(stmt.getExpression());
            currentMethodVisitor.visitInsn(IRETURN);
        }
    }

    private void generateCodeBlock(CodeBlock block) {
        for (Statement s : block.getStatements()) {
            generateStatement(s);
        }
    }

    private void generateExpression(Expression expr) {
        if (expr instanceof LiteralExpression) {
            generateLiteralExpression((LiteralExpression) expr);
        } else if (expr instanceof BinaryExpression) {
            generateBinaryExpression((BinaryExpression) expr);
        } else if (expr instanceof VariableExpression) {
            generateVariableExpression((VariableExpression) expr);
        } else if (expr instanceof UnaryExpression) {
            generateUnaryExpression((UnaryExpression) expr);
        } else if (expr instanceof FunctionCallExpression) {
            generateFunctionCallExpression((FunctionCallExpression) expr);
        } else {
            throw new UnsupportedOperationException("Unsupported expression type: " + expr.getClass());
        }
    }

    private void generateLiteralExpression(LiteralExpression expr) {
        Token lit = expr.getLiteral();
        switch (lit.getType()) {
            case NUMERIC_LITERAL:
                String val = lit.getValue();
                if (val.contains(".")) {
                    float fVal = Float.parseFloat(val);
                    currentMethodVisitor.visitLdcInsn(fVal);
                } else {
                    int iVal = Integer.parseInt(val);
                    currentMethodVisitor.visitLdcInsn(iVal);
                }
                break;
            case BOOLEAN_LITERAL:
                currentMethodVisitor.visitLdcInsn(lit.getValue().equals("true") ? 1 : 0);
                break;
            case STRING_LITERAL:
                currentMethodVisitor.visitLdcInsn(lit.getValue());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported literal type: " + lit.getType());
        }
    }

    private void generateVariableExpression(VariableExpression expr) {
        String varName = expr.getIdentifier();
        Integer idx = variableScopes.peek().get(varName);
        if (idx == null) {
            throw new RuntimeException("Undeclared variable: " + varName);
        }
        String type = variableTypes.peek().get(varName);
        loadVariable(type, idx);
    }

    private void generateUnaryExpression(UnaryExpression expr) {
        String op = expr.getOperator();
        if (op.equals("!")) {
            generateExpression(expr.getExpression());
            Label trueLabel = new Label();
            Label endLabel = new Label();
            currentMethodVisitor.visitJumpInsn(IFEQ, trueLabel);
            currentMethodVisitor.visitLdcInsn(0);
            currentMethodVisitor.visitJumpInsn(GOTO, endLabel);
            currentMethodVisitor.visitLabel(trueLabel);
            currentMethodVisitor.visitLdcInsn(1);
            currentMethodVisitor.visitLabel(endLabel);
        } else if (op.equals("-")) {
            generateExpression(expr.getExpression());
            String type = getExpressionType(expr.getExpression());
            if (type.equals("int")) {
                currentMethodVisitor.visitInsn(INEG);
            } else if (type.equals("float")) {
                currentMethodVisitor.visitInsn(FNEG);
            } else {
                throw new UnsupportedOperationException("Unary minus on non-numeric type");
            }
        } else {
            throw new UnsupportedOperationException("Unsupported unary operator: " + op);
        }
    }

    private void generateBinaryExpression(BinaryExpression expr) {
        String op = expr.getOperator();
        String leftType = getExpressionType(expr.getLeft());
        String rightType = getExpressionType(expr.getRight());

        if (op.equals("||") || op.equals("&&")) {
            ensureTypeForLogical(leftType);
            ensureTypeForLogical(rightType);
            generateLogicalExpression(expr);
        } else if (isComparisonOp(op)) {
            ensureNumericOrThrowForComparison(leftType);
            ensureNumericOrThrowForComparison(rightType);
            generateComparisonExpression(expr, leftType, rightType);
        } else if (op.equals("+") || op.equals("-")
          || op.equals("*") || op.equals("/")) {
            ensureNumericOrThrow(leftType);
            ensureNumericOrThrow(rightType);
            generateArithmeticExpression(expr, leftType, rightType);
        } else {
            throw new UnsupportedOperationException("Unsupported operator: " + op);
        }
    }

    private boolean isComparisonOp(String op) {
        return op.equals("==") || op.equals("!=")
          || op.equals(">") || op.equals("<")
          || op.equals(">=") || op.equals("<=");
    }

    private void ensureTypeForLogical(String type) {
        if (!type.equals("int")) {
            throw new UnsupportedOperationException("Logical operations only supported on int (0/1 boolean)");
        }
    }

    private void ensureNumericOrThrow(String type) {
        if (!(type.equals("int") || type.equals("float"))) {
            throw new UnsupportedOperationException("Arithmetic only supported on int/float types");
        }
    }

    private void ensureNumericOrThrowForComparison(String type) {
        if (!(type.equals("int") || type.equals("float"))) {
            throw new UnsupportedOperationException("Comparison only supported on int/float types");
        }
    }

    private void generateArithmeticExpression(BinaryExpression expr, String leftType, String rightType) {
        generateExpression(expr.getLeft());

        if (leftType.equals("int") && rightType.equals("float")) {
            currentMethodVisitor.visitInsn(I2F);
            leftType = "float";
        }

        generateExpression(expr.getRight());

        if (leftType.equals("float") && rightType.equals("int")) {
            currentMethodVisitor.visitInsn(I2F);
            rightType = "float";
        }

        String op = expr.getOperator();
        if (leftType.equals("int") && rightType.equals("int")) {
            switch (op) {
                case "+":
                    currentMethodVisitor.visitInsn(IADD); break;
                case "-":
                    currentMethodVisitor.visitInsn(ISUB); break;
                case "*":
                    currentMethodVisitor.visitInsn(IMUL); break;
                case "/":
                    currentMethodVisitor.visitInsn(IDIV); break;
            }
        } else {
            // float arithmetic
            switch (op) {
                case "+":
                    currentMethodVisitor.visitInsn(FADD); break;
                case "-":
                    currentMethodVisitor.visitInsn(FSUB); break;
                case "*":
                    currentMethodVisitor.visitInsn(FMUL); break;
                case "/":
                    currentMethodVisitor.visitInsn(FDIV); break;
            }
        }
    }

    private void generateComparisonExpression(BinaryExpression expr, String leftType, String rightType) {
        generateExpression(expr.getLeft());
        if (leftType.equals("int") && rightType.equals("float")) {
            currentMethodVisitor.visitInsn(I2F);
            leftType = "float";
        }

        generateExpression(expr.getRight());
        if (leftType.equals("float") && rightType.equals("int")) {
            currentMethodVisitor.visitInsn(I2F);
            rightType = "float";
        }

        String op = expr.getOperator();
        Label trueLabel = new Label();
        Label endLabel = new Label();

        if (leftType.equals("int") && rightType.equals("int")) {
            switch (op) {
                case "==": currentMethodVisitor.visitJumpInsn(IF_ICMPEQ, trueLabel); break;
                case "!=": currentMethodVisitor.visitJumpInsn(IF_ICMPNE, trueLabel); break;
                case ">": currentMethodVisitor.visitJumpInsn(IF_ICMPGT, trueLabel); break;
                case "<": currentMethodVisitor.visitJumpInsn(IF_ICMPLT, trueLabel); break;
                case ">=": currentMethodVisitor.visitJumpInsn(IF_ICMPGE, trueLabel); break;
                case "<=": currentMethodVisitor.visitJumpInsn(IF_ICMPLE, trueLabel); break;
            }
        } else {
            currentMethodVisitor.visitInsn(FCMPG);
            switch (op) {
                case "==": currentMethodVisitor.visitJumpInsn(IFEQ, trueLabel); break;
                case "!=": currentMethodVisitor.visitJumpInsn(IFNE, trueLabel); break;
                case ">": currentMethodVisitor.visitJumpInsn(IFGT, trueLabel); break;
                case "<": currentMethodVisitor.visitJumpInsn(IFLT, trueLabel); break;
                case ">=": currentMethodVisitor.visitJumpInsn(IFGE, trueLabel); break;
                case "<=": currentMethodVisitor.visitJumpInsn(IFLE, trueLabel); break;
            }
        }

        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(trueLabel);
        currentMethodVisitor.visitLdcInsn(1);

        currentMethodVisitor.visitLabel(endLabel);
    }

    private void generateLogicalExpression(BinaryExpression expr) {
        String op = expr.getOperator();
        if (op.equals("||")) {
            generateOrExpression(expr);
        } else if (op.equals("&&")) {
            generateAndExpression(expr);
        } else {
            throw new UnsupportedOperationException("Unknown logical operator: " + op);
        }
    }

    private void generateOrExpression(BinaryExpression expr) {
        generateExpression(expr.getLeft());

        Label endLabel = new Label();
        Label pushFalse = new Label();
        Label trueLabel = new Label();

        currentMethodVisitor.visitJumpInsn(IFNE, trueLabel);

        generateExpression(expr.getRight());
        currentMethodVisitor.visitJumpInsn(IFEQ, pushFalse);

        currentMethodVisitor.visitLdcInsn(1);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(pushFalse);
        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(trueLabel);
        currentMethodVisitor.visitLdcInsn(1);

        currentMethodVisitor.visitLabel(endLabel);
    }

    private void generateAndExpression(BinaryExpression expr) {
        generateExpression(expr.getLeft());

        Label endLabel = new Label();
        Label pushFalse = new Label();

        currentMethodVisitor.visitJumpInsn(IFEQ, pushFalse);

        generateExpression(expr.getRight());
        currentMethodVisitor.visitJumpInsn(IFEQ, pushFalse);

        currentMethodVisitor.visitLdcInsn(1);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(pushFalse);
        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(endLabel);
    }

    private void generateFunctionCallExpression(FunctionCallExpression expr) {
        MethodDescriptor md = functionSignatures.get(expr.getIdentifier());
        if (md == null) {
            throw new RuntimeException("Call to undefined function: " + expr.getIdentifier());
        }

        for (Expression arg : expr.getArguments()) {
            generateExpression(arg);
        }

        currentMethodVisitor.visitMethodInsn(INVOKESTATIC, className, md.name, md.desc, false);
    }

    private int allocateLocalVar(String varName) {
        int idx = localVarIndex.pop();
        localVarIndex.push(idx+1);
        variableScopes.peek().put(varName, idx);
        return idx;
    }

    private void loadVariable(String type, int index) {
        switch (type) {
            case "int":
                currentMethodVisitor.visitVarInsn(ILOAD, index);
                break;
            case "float":
                currentMethodVisitor.visitVarInsn(FLOAD, index);
                break;
            case "string":
            case "Stream":
            case "Reactive":
                currentMethodVisitor.visitVarInsn(ALOAD, index);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported variable type: " + type);
        }
    }

    private void storeVariable(String type, int index) {
        switch (type) {
            case "int":
                currentMethodVisitor.visitVarInsn(ISTORE, index);
                break;
            case "float":
                currentMethodVisitor.visitVarInsn(FSTORE, index);
                break;
            case "string":
            case "Stream":
            case "Reactive":
                currentMethodVisitor.visitVarInsn(ASTORE, index);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported variable type: " + type);
        }
    }

    private String getExpressionType(Expression expr) {
        if (expr instanceof LiteralExpression) {
            Token lit = ((LiteralExpression) expr).getLiteral();
            switch (lit.getType()) {
                case NUMERIC_LITERAL:
                    String v = lit.getValue();
                    if (v.contains(".")) return "float";
                    return "int";
                case BOOLEAN_LITERAL:
                    return "int";
                case STRING_LITERAL:
                    return "string";
                default:
                    throw new UnsupportedOperationException("Unknown literal type");
            }
        } else if (expr instanceof VariableExpression) {
            String varName = ((VariableExpression) expr).getIdentifier();
            String type = variableTypes.peek().get(varName);
            if (type == null) {
                throw new RuntimeException("Undeclared variable: " + varName);
            }
            return type;
        } else if (expr instanceof BinaryExpression) {
            String left = getExpressionType(((BinaryExpression) expr).getLeft());
            String right = getExpressionType(((BinaryExpression) expr).getRight());
            String op = ((BinaryExpression) expr).getOperator();

            if (op.equals("||") || op.equals("&&")
              || op.equals("==") || op.equals("!=")
              || op.equals(">") || op.equals("<")
              || op.equals(">=") || op.equals("<=")) {
                // comparison/logical -> int
                return "int";
            } else {
                // arithmetic
                if (left.equals("float") || right.equals("float")) return "float";
                return "int";
            }
        } else if (expr instanceof UnaryExpression) {
            if (((UnaryExpression) expr).getOperator().equals("!")) {
                return "int";
            } else {
                // unary minus
                Expression sub = ((UnaryExpression) expr).getExpression();
                String subtype = getExpressionType(sub);
                return subtype;
            }
        } else if (expr instanceof FunctionCallExpression) {
            // all functions return int for simplicity
            return "int";
        } else {
            throw new UnsupportedOperationException("Cannot infer expression type: " + expr.getClass());
        }
    }
}