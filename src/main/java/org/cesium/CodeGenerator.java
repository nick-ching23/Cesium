package org.cesium;

import org.cesium.ASTNode.*;
import org.objectweb.asm.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * The CodeGenerator class is tasked with taking an Abstract Syntax Tree, optimizing it,
 * and then converting it to byte code. Note that in this instance, I am converting from
 * Cesium code to Java Bytecode which is then executable on a JVM.
 *
 * <p>This kind of formatting allows Cesium to run on any platform that supports the JVM
 * which is every platform. To actually generate the Java Bytecode, I am using the ASM
 * framework that lets you work with low-level java byte-code.
 * </p>
 */
public class CodeGenerator {

    private ClassWriter classWriter;

    private String className;
    private Deque<MethodVisitor> methodStack = new ArrayDeque<>();
    private MethodVisitor currentMethodVisitor;


    // Each scope holds a mapping of variable names to local variable indexes and types.
    // Satcks are used so that entering a new scope
    private Deque<Map<String, Integer>> variableScopes = new ArrayDeque<>();
    private Deque<Map<String, String>> variableTypes = new ArrayDeque<>();

    // Tracks the next available local variable index for the current scope.
    private Deque<Integer> localVariableIndex = new ArrayDeque<>();

    // Holds function name -> MethodDescriptor mappings for user- defined functions.
    private Map<String, MethodDescriptor> functionSignatures = new HashMap<>();

    static class MethodDescriptor {
        String name;
        String descriptor;
    }

    /**
     * Generates bytecode for a given Cesium AST and writes it out as a .class file.
     * This method acts as the entry point to the generateCode function.
     */
    public void generateCode(CesiumProgram program, String outputClassName) throws IOException {

        // start by simplifying the AST.
        CesiumProgram simplifiedProgram = Optimization.simplifyProgram(program);

        this.className = outputClassName.replace('.', '/');
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Generates header code for java file
         generateDefaultConstructor();
        startMethod("main", "([Ljava/lang/String;)V", ACC_PUBLIC + ACC_STATIC);

        // Generates body for file.
        for (Statement statement : simplifiedProgram.getStatements()) {
            generateStatement(statement);
        }


        // Generates the closure of the java file
        currentMethodVisitor.visitInsn(RETURN);

        endMethod();
        classWriter.visitEnd();

        // write everything to our new class
        try (FileOutputStream fos = new FileOutputStream(outputClassName + ".class")) {
            fos.write(classWriter.toByteArray());
        }
    }

    /**
     * This code will generate the basic byte code for a constructor:
     * public <class_name>() {
     *     super();
     * }
     */
    private void generateDefaultConstructor() {
        MethodVisitor constructor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V",
          null, null );

        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V",
          false);
        constructor.visitInsn(RETURN);
         constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    /**
     * Begins a method definition by pushing a new MethodVisitor on the stack and setting up a new scope.
     */
    private void startMethod(String name, String descriptor, int accessFlags) {
        MethodVisitor methodVisitor = classWriter.visitMethod(accessFlags, name, descriptor,
          null, null);
        methodVisitor.visitCode();
        currentMethodVisitor = methodVisitor;
        methodStack.push(methodVisitor);

        // Create new maps for variables and their types for the current method's scope
        Map<String, Integer> variableMap = new HashMap<>();
        Map<String, String> typeMap = new HashMap<>();
        variableScopes.push(variableMap);
        variableTypes.push(typeMap);

        // Calculate the starting local variable index
        int startIndex = (name.equals("main") ? 1 : 0) + (descriptor.equals("([Ljava/lang/String;)V") ? 1 : 0);
         localVariableIndex.push(startIndex);
    }

    /**
     *  Completes the current method definition and restores the previous state.
     */
    private void endMethod() {

        MethodVisitor methodVisitor = methodStack.pop();
        methodVisitor.visitMaxs(0,0);
        methodVisitor.visitEnd();

        // If there's a previously active method , return to it. Othwerwise we clear.
        if (!methodStack.isEmpty()) {
            currentMethodVisitor = methodStack.peek();
        }
        else {
            currentMethodVisitor = null;
        }

        // Restore the previous variable and type scopes
        variableTypes.pop();
        variableScopes.pop();
        localVariableIndex.pop();
    }

    /**
     * Generates bytecode for the given statement by calling the right helper method
     */
    private void generateStatement(Statement statement) {
        if (statement instanceof DeclarationStatement) {
            generateDeclarationStatement((DeclarationStatement) statement);
            return;
        }
        else if (statement instanceof AssignmentStatement) {
            generateAssignmentStatement((AssignmentStatement) statement);
            return;
        }
        else if (statement instanceof ExpressionStatement) {
            Expression expression = ((ExpressionStatement) statement).getExpression();

            // setValue call has no return value to pop (this is for reactivity)
            if (expression instanceof FunctionCallExpression functionCallExpression &&
                         functionCallExpression.getIdentifier().equals("setValue")) {
                generateFunctionCallExpression(functionCallExpression);
            } else {
                generateExpression(expression);
                currentMethodVisitor.visitInsn(POP);
            }
            return;
        }
        else if (statement instanceof PrintStatement) {
            generatePrintStatement((PrintStatement) statement);
             return;
        }
        else if (statement instanceof IfStatement) {
            generateIfStatement((IfStatement) statement);
            return;
        }
        else if (statement instanceof WhileStatement) {
            generateWhileStatement((WhileStatement) statement);
            return;
        }
        else if (statement instanceof ForStatement) {
            generateForStatement((ForStatement) statement);
            return;
        }
        else if (statement instanceof ReturnStatement) {
            generateReturnStatement((ReturnStatement) statement);
            return;
        }
        else if (statement instanceof CodeBlock) {
            generateCodeBlock((CodeBlock) statement);
            return;
        }
        throw new UnsupportedOperationException("Unsupported statement type: " + statement.getClass());
    }


    /**
     * Generates bytecode for a declaration statement
     */
    private void generateDeclarationStatement(DeclarationStatement statement) {
        Declaration declaration = statement.getDeclaration();

        if (declaration instanceof VariableDeclaration) {
            generateVariableDeclaration((VariableDeclaration) declaration);
        } else if (declaration instanceof FunctionDeclaration) {
            generateFunctionDeclaration((FunctionDeclaration) declaration);
        } else {
            throw new UnsupportedOperationException("Unknown declaration type: " + declaration.getClass());
        }
    }

    /**
     * Generate bytecode for a variable declaration, handling type checks and initializers.
     */
    private void generateVariableDeclaration(VariableDeclaration variableDeclaration) {
        String variableName = variableDeclaration.getIdentifier();
        String type = variableDeclaration.getType();

        if (!isSupportedType(type)) {
            throw new UnsupportedOperationException("Unsupported type: " + type);
        }

        // Allocate a slot for this local variable in the current scope
        int index = allocateLocalVariable(variableName);
        variableTypes.peek().put(variableName, type);

        Expression initializer = variableDeclaration.getInitializer();
        if (initializer != null) {

            // in this case we are initializing a Stream which is different
            if (type.equals("Stream") && initializer instanceof LiteralExpression) {
                initializeStreamWithLiteral(index, (LiteralExpression) initializer);
            } else {
                generateExpression(initializer);
                storeVariable(type, index);
            }
        } else {
            initializeVariableToDefault(type, index);
        }
    }

    /**
     * Initialize a Stream variable with a numeric literal by creating a new Stream.
     * This is a separate case since streams are a different type.
     */
    private void initializeStreamWithLiteral(int index, LiteralExpression literalExpression) {
        // Instantiate a new Stream object
        currentMethodVisitor.visitTypeInsn(NEW, "org/cesium/Stream");
        currentMethodVisitor.visitInsn(DUP);
        currentMethodVisitor.visitMethodInsn(INVOKESPECIAL, "org/cesium/Stream", "<init>",
            "()V", false);
        currentMethodVisitor.visitVarInsn(ASTORE, index);


        // Extract the numeric value from the literal and assign to stream.
        Token literal = literalExpression.getLiteral();
        if (literal.getType() == TokenType.NUMERIC_LITERAL) {
            int iVal = Integer.parseInt(literal.getValue());
            currentMethodVisitor.visitVarInsn(ALOAD, index);
             currentMethodVisitor.visitLdcInsn(iVal);
            currentMethodVisitor.visitMethodInsn(INVOKESTATIC, "org/cesium/Util", "setValue",
              "(Lorg/cesium/Stream;I)V", false);
        } else {
            throw new UnsupportedOperationException("Can't initialize Stream with non-numeric literal");
        }
    }

    /**
     * Just tells us which types are supported :) nothing fancy
     */
    private boolean isSupportedType(String type) {
        switch (type) {
            case "Stream":
            case "Reactive":
            case "int":
            case "float":
            case "string":
                return true;
            default:
                return false;
        }
    }


    /**
     * Provides default initialization for variables when no initializer is given
     */
    private void initializeVariableToDefault(String type, int index) {
        switch (type) {
            case "Stream":
                // Instantiate a new Stream by default
                currentMethodVisitor.visitTypeInsn(NEW, "org/cesium/Stream");
                 currentMethodVisitor.visitInsn(DUP);
                currentMethodVisitor.visitMethodInsn(INVOKESPECIAL, "org/cesium/Stream",
                   "<init>", "()V", false);
                 currentMethodVisitor.visitVarInsn(ASTORE, index);
                break;
            case "Reactive":
                // Default to null
                currentMethodVisitor.visitInsn(ACONST_NULL);
                currentMethodVisitor.visitVarInsn(ASTORE, index);
                break;
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
        }
    }

    /**
     * Generates bytecode for function declarations.
     */
    private void generateFunctionDeclaration(FunctionDeclaration functionDeclaration) {
        StringBuilder descriptorBuilder = new StringBuilder("(");

        for (Parameter param : functionDeclaration.getParameters()) {
            String paramType = param.getType();
            if (!isSupportedType(paramType)) {
                throw new UnsupportedOperationException("Unsupported parameter type: " + paramType);
            }
            descriptorBuilder.append(getTypeDescriptor(paramType));
        }
        descriptorBuilder.append(")I");

        // s Store the function signature for later reference
        MethodDescriptor methodDescriptor = new MethodDescriptor();
        methodDescriptor.name = functionDeclaration.getIdentifier();
        methodDescriptor.descriptor = descriptorBuilder.toString();
        functionSignatures.put(functionDeclaration.getIdentifier(), methodDescriptor);

        // Begin generating the method
        startMethod(functionDeclaration.getIdentifier(), methodDescriptor.descriptor, ACC_PUBLIC + ACC_STATIC);

        // Set up parameters in the local variable scope
        int localIndex = 0;
        for (Parameter param : functionDeclaration.getParameters()) {
            variableScopes.peek().put(param.getIdentifier(), localIndex);
            variableTypes.peek().put(param.getIdentifier(), param.getType());
            localIndex += 1;
        }

        // Generate code for the function body
        generateCodeBlock(functionDeclaration.getBody());

        // If no explicit return is encountered, return a default integer (0)
         currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitInsn(IRETURN);
        endMethod();
    }

    /**
     * Converts a Cesium type into the corresponding JVM type descriptor.
     */
    private String getTypeDescriptor(String type) {
        return switch (type) {
            case "int" -> "I";
            case "float" -> "F";
            case "string" -> "Ljava/lang/String;";
            case "Stream", "Reactive" -> "Ljava/lang/Object;";
            default -> throw new UnsupportedOperationException("getTypeDescriptor: unsupported type " + type);
        };
    }

    /**
     * Generates bytecode for assigning a value to an already declared variable.
     */
    private void generateAssignmentStatement(AssignmentStatement statement) {
        String variableName = statement.getIdentifier();
        Integer variableIndex = variableScopes.peek().get(variableName);

        // Ensure the variable has been declared
        if (variableIndex == null) {
            throw new RuntimeException("Variable not declared: " + variableName);
        }

        String variableType = variableTypes.peek().get(variableName);
        generateExpression(statement.getExpression());

        // Store the computed value into the local variable slot
        storeVariable(variableType, variableIndex);
    }

    /**
     * Generates bytecode to print the value of an expression.
     */
    private void generatePrintStatement(PrintStatement statement) {
        Expression expressionToPrint = statement.getExpression();
        String expressionType = getExpressionType(expressionToPrint);

        if (expressionType.equals("Reactive")) {
            // For Reactive, call getValue() and then print via Util.printReactiveValue()
            generateExpression(expressionToPrint);
            currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/cesium/Reactive", "getValue",
              "()Ljava/lang/Integer;", false);
            currentMethodVisitor.visitMethodInsn(INVOKESTATIC, "org/cesium/Util", "printReactiveValue",
              "(Ljava/lang/Integer;)V", false);
        } else {
            // Use System.out for other types
            currentMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out",
              "Ljava/io/PrintStream;");

            // Generate the code for the expression to be printed
            generateExpression(expressionToPrint);

            // Call the appropriate println method based on the expression type
            switch (expressionType) {
                case "int":
                     currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                      "(I)V", false);
                    break;
                case "float":
                    currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                      "(F)V", false);
                    break;
                case "string":
                    currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                      "(Ljava/lang/String;)V", false);
                    break;
                case "Stream":
                case "Reactive":
                    currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                      "(Ljava/lang/Object;)V", false);
                    break;
                default:
                    // For any other type, fall back to printing as an Object
                    currentMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                      "(Ljava/lang/Object;)V", false);
                    break;
            }
        }
    }

    /**
     * Generates bytecode for an if-statement
     */
    private void generateIfStatement(IfStatement statement) {
        Label elseLabel = new Label();
        Label endLabel = new Label();

        // Generate code for condition and jump to else if condition == 0 (false)
        generateExpression(statement.getCondition());
        currentMethodVisitor.visitJumpInsn(IFEQ, elseLabel);

        // Condition was true, generate 'then' block
        generateStatement(statement.getThenBlock());
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        // If condition was false and there's an 'else' block then we make that
        currentMethodVisitor.visitLabel(elseLabel);
        if (statement.getElseBlock() != null) {
            generateStatement(statement.getElseBlock());
        }

        currentMethodVisitor.visitLabel(endLabel);
    }

    /**
     * Generates bytecode for a while-statement.
     */
    private void generateWhileStatement(WhileStatement statement) {
        Label loopStart = new Label();
        Label loopEnd = new Label();

        // Mark the beginning of the loop
        currentMethodVisitor.visitLabel(loopStart);
        generateExpression(statement.getCondition());
        currentMethodVisitor.visitJumpInsn(IFEQ, loopEnd);

        // Condition is true, execute the loop body
        generateStatement(statement.getBody());
        currentMethodVisitor.visitJumpInsn(GOTO, loopStart);

        // Mark the end of the loop
        currentMethodVisitor.visitLabel(loopEnd);
    }

    /**
     * Generates bytecode for a for-statement
     */
    private void generateForStatement(ForStatement statement) {
        if (statement.getInitialization() != null) {
            generateStatement(statement.getInitialization());
        }
        Label loopStart = new Label();
        Label loopEnd = new Label();

        // Start of the loop
        currentMethodVisitor.visitLabel(loopStart);

        if (statement.getCondition() != null) {
            generateExpression(statement.getCondition());
            currentMethodVisitor.visitJumpInsn(IFEQ, loopEnd);
        }

        // Execute the body of the loop
        generateStatement(statement.getBody());

        if (statement.getUpdate() != null) {
            generateStatement(statement.getUpdate());
        }
        currentMethodVisitor.visitJumpInsn(GOTO, loopStart);

        // Mark the end of the loop
        currentMethodVisitor.visitLabel(loopEnd);
    }


    /**
     * Generates bytecode for a return statement.
     */
    private void generateReturnStatement(ReturnStatement statement) {
        // main method: void return, function: int return
        if (methodStack.size() == 1) {
            // in main
            if (statement.getExpression() != null) {
                generateExpression(statement.getExpression());
                currentMethodVisitor.visitInsn(POP);
            }
            currentMethodVisitor.visitInsn(RETURN);
         } else {
            // in a function returning int
            generateExpression(statement.getExpression());
            currentMethodVisitor.visitInsn(IRETURN);
        }
    }


    /**
     * Generates bytecode for all statements contained within a code block.
     */
    private void generateCodeBlock(CodeBlock codeBlock) {
        for (Statement statement : codeBlock.getStatements()) {
            generateStatement(statement);
        }
    }

    /**
     * Generates bytecode for an expression by delegating to specialized methods
     * based on the type
     */
    private void generateExpression(Expression expression) {
        if (expression instanceof LiteralExpression) {
            generateLiteralExpression((LiteralExpression) expression);
        } else if (expression instanceof BinaryExpression) {
            generateBinaryExpression((BinaryExpression) expression);
        } else if (expression instanceof VariableExpression) {
            generateVariableExpression((VariableExpression) expression);
        } else if (expression instanceof UnaryExpression) {
            generateUnaryExpression((UnaryExpression) expression);
        } else if (expression instanceof FunctionCallExpression) {
            generateFunctionCallExpression((FunctionCallExpression) expression);
        } else {
            throw new UnsupportedOperationException("Unsupported expression type: " + expression.getClass());
        }
    }

    /**
     * Generates bytecode for a literal expression by pushing its value onto the stack.
     */
    private void generateLiteralExpression(LiteralExpression expression) {
        Token literalToken = expression.getLiteral();
         switch (literalToken.getType()) {

            // Distinguish between int and float by checking if the value contains a decimal point
            case NUMERIC_LITERAL:
                String numericValue = literalToken.getValue();
                if (numericValue.contains(".")) {
                    float fVal = Float.parseFloat(numericValue);
                    currentMethodVisitor.visitLdcInsn(fVal);
                } else {
                    int integerVal = Integer.parseInt(numericValue);
                    currentMethodVisitor.visitLdcInsn(integerVal);
                }
                break;

            // Booleans are represented as integers: 1 for true, 0 for false
            case BOOLEAN_LITERAL:
                boolean isTrue = literalToken.getValue().equals("true");
                currentMethodVisitor.visitLdcInsn(isTrue ? 1 : 0);
                break;

            // Push the string constant onto the stack
            case STRING_LITERAL:
                currentMethodVisitor.visitLdcInsn(literalToken.getValue());
                break;

             default:
                throw new UnsupportedOperationException("Unsupported literal type: " + literalToken.getType());
        }
    }

    /**
     * Generates bytecode to load the value of a variable by name onto the stack.
     */
    private void generateVariableExpression(VariableExpression expression) {
        String varName = expression.getIdentifier();

        // Get the variable index from the current scope
        Integer index = variableScopes.peek().get(varName);
        if (index == null) {
            throw new RuntimeException("Undeclared variable: " + varName);
        }

        // Load the variable onto the stack based on its type
        String type = variableTypes.peek().get(varName);
        loadVariable(type, index);
    }

    /**
     * Generates bytecode for a unary expression.
     */
    private void generateUnaryExpression(UnaryExpression expression) {
        String operator = expression.getOperator();
        Expression operand = expression.getExpression();

        if (operator.equals("!")) {
            generateExpression(operand);

            Label trueLabel = new Label();
            Label endLabel = new Label();

            // If operand == 0, jump to trueLabel
            currentMethodVisitor.visitJumpInsn(IFEQ, trueLabel);

            // Operand was non-zero, jump to end
            currentMethodVisitor.visitLdcInsn(0);
            currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

            // Operand was zero, so at trueLabel push 1 (true)
            currentMethodVisitor.visitLabel(trueLabel);
            currentMethodVisitor.visitLdcInsn(1);

            // end
            currentMethodVisitor.visitLabel(endLabel);

        } else if (operator.equals("-")) {
             generateExpression(operand);
            String type = getExpressionType(operand);

            if (type.equals("int")) {
                currentMethodVisitor.visitInsn(INEG);
            } else if (type.equals("float")) {
                currentMethodVisitor.visitInsn(FNEG);
            } else {
                throw new UnsupportedOperationException("Unary minus on non-numeric type: " + type);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported unary operator: " + operator);
        }
    }

    /**
     * Generates bytecode for a binary expression, which applies op to
     * two operands (left and right).
     */
    private void generateBinaryExpression(BinaryExpression expression) {
        String operator = expression.getOperator();
        String leftType = getExpressionType(expression.getLeft());
        String rightType = getExpressionType(expression.getRight());

        // Logical operations on boolean (int) values
        if (operator.equals("||") || operator.equals("&&")) {
            ensureTypeForLogical(leftType);
            ensureTypeForLogical(rightType);
             generateLogicalExpression(expression);
        }
        // Comparison operators on numeric values
         else if (isComparisonOperator(operator)) {
            ensureNumericOrThrowForComparison(leftType);
            ensureNumericOrThrowForComparison(rightType);
            generateComparisonExpression(expression, leftType, rightType);

        }

        // Arithmetic operators on numeric or Stream/Reactive types
        else if (operator.equals("+") || operator.equals("-") || operator.equals("*") || operator.equals("/")) {
            if (isNumericType(leftType) && isNumericType(rightType)) {
                generateArithmeticExpression(expression, leftType, rightType);
            } else {
                // note we handle reactive arithmetic differently.
                generateReactiveArithmetic(expression, operator, leftType, rightType);
            }
        }

        else {
            throw new UnsupportedOperationException("Unsupported operator: " + operator);
        }
    }

    /**
     * Generates bytecode for arithmetic operations on primitive numeric types (int and float).
     */
    private void generateArithmeticExpression(BinaryExpression expression, String leftType, String rightType) {

        // look at the left
        generateExpression(expression.getLeft());
        if (leftType.equals("int") && rightType.equals("float")) {
            currentMethodVisitor.visitInsn(I2F);
            leftType = "float";
        }

        // look at the right
        generateExpression(expression.getRight());
        if (leftType.equals("float") && rightType.equals("int")) {
            currentMethodVisitor.visitInsn(I2F);
            rightType = "float";
        }

        // Determine the correct arithmetic instruction based on operator and final types

        String op = expression.getOperator();
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
            // if one operatnd is a float, we treat both numerics as a float...
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

    /**
     * Generates bytecode for arithmetic operations involving Stream/Reactive types.
     * These operations are delegated to static methods in the ReactiveOps class, which handle
     * combining a Stream or Reactive value with an integer. The resulting value is always a Reactive.
     *
     */
    private void generateReactiveArithmetic(BinaryExpression expression, String operator,
                                            String leftType, String rightType) {
        // Generate operands
        generateExpression(expression.getLeft());
        generateExpression(expression.getRight());

        String opsClass = "org/cesium/ReactiveOps";
        boolean isLeftStream = leftType.equals("Stream");

        String methodName;
        String methodDescriptor;
        switch (operator) {
            case "+":
                methodName = "add";
                break;
            case "-":
                methodName = "subtract";
                break;
            case "*":
                methodName = "multiply";
                break;
            case "/":
                methodName = "divide";
                break;
            default:
                throw new UnsupportedOperationException("Unsupported reactive operator: " + operator);
        }

        // If the left operand is a Stream, we use (Lorg/cesium/Stream;I)Lorg/cesium/Reactive;
        // Otherwise, we use (Lorg/cesium/Reactive;I)Lorg/cesium/Reactive;
        if (isLeftStream) {
            methodDescriptor = "(Lorg/cesium/Stream;I)Lorg/cesium/Reactive;";
        } else {
            methodDescriptor = "(Lorg/cesium/Reactive;I)Lorg/cesium/Reactive;";
          }

        currentMethodVisitor.visitMethodInsn(INVOKESTATIC, opsClass, methodName, methodDescriptor, false);
    }


    /**
     * Generates bytecode for comparison operations between two numeric expressions.
     * The supported comparison operators are:
     *  ==, !=, >, <, >=, <=
     */
    private void generateComparisonExpression(BinaryExpression expr, String leftType, String rightType) {
        // Generate left operand
        generateExpression(expr.getLeft());
        if (leftType.equals("int") && rightType.equals("float")) {
            currentMethodVisitor.visitInsn(I2F);
            leftType = "float";
        }

        // Generate right operand
        generateExpression(expr.getRight());
        if (leftType.equals("float") && rightType.equals("int")) {
            currentMethodVisitor.visitInsn(I2F);
             rightType = "float";
        }

        String operator = expr.getOperator();
        Label trueLabel = new Label();
        Label endLabel = new Label();

        // Both operands are int, use integer comparison instructions
        if (leftType.equals("int") && rightType.equals("int")) {
             switch (operator) {
                case "==": currentMethodVisitor.visitJumpInsn(IF_ICMPEQ, trueLabel); break;
                case "!=": currentMethodVisitor.visitJumpInsn(IF_ICMPNE, trueLabel); break;
                case ">":  currentMethodVisitor.visitJumpInsn(IF_ICMPGT, trueLabel); break;
                case "<":  currentMethodVisitor.visitJumpInsn(IF_ICMPLT, trueLabel); break;
                case ">=": currentMethodVisitor.visitJumpInsn(IF_ICMPGE, trueLabel); break;
                case "<=": currentMethodVisitor.visitJumpInsn(IF_ICMPLE, trueLabel); break;
                default: throw new UnsupportedOperationException("Unknown comparison operator: " + operator);
            }

        }
        // At least one operand is float, use FCMPG followed by a conditional jump
        else {
            currentMethodVisitor.visitInsn(FCMPG);
            switch (operator) {
                case "==": currentMethodVisitor.visitJumpInsn(IFEQ, trueLabel); break;
                case "!=": currentMethodVisitor.visitJumpInsn(IFNE, trueLabel); break;
                case ">":  currentMethodVisitor.visitJumpInsn(IFGT, trueLabel); break;
                case "<":  currentMethodVisitor.visitJumpInsn(IFLT, trueLabel); break;
                case ">=": currentMethodVisitor.visitJumpInsn(IFGE, trueLabel); break;
                case "<=": currentMethodVisitor.visitJumpInsn(IFLE, trueLabel); break;
                default: throw new UnsupportedOperationException("Unknown comparison operator: " + operator);
            }
        }

        // If comparison failed, push 0 and jump to end
        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        // If comparison succeeded, push 1
        currentMethodVisitor.visitLabel(trueLabel);
        currentMethodVisitor.visitLdcInsn(1);

        currentMethodVisitor.visitLabel(endLabel);
    }


    /**
     * Generates bytecode for logical expressions: logical OR (||) and logical AND (&&).
     * Both || and && are handled as short-circuit operators:
     * For "||": If the left operand is true, we don't evaluate the right operand.
     * For "&&": If the left operand is false, we don't evaluate the right operand.
     */
    private void generateLogicalExpression(BinaryExpression expression) {
        String op = expression.getOperator();
        if (op.equals("||")) {
            generateOrExpression(expression);
        } else if (op.equals("&&")) {
            generateAndExpression(expression);
        } else {
            throw new UnsupportedOperationException("Unknown logical operator: " + op);
        }
      }

    /**
     * Generates bytecode for a logical OR (||) expression using short-circuit evaluation.
     */
    private void generateOrExpression(BinaryExpression expression) {
        generateExpression(expression.getLeft());

        Label endLabel = new Label();
        Label pushFalse = new Label();
        Label trueLabel = new Label();

        currentMethodVisitor.visitJumpInsn(IFNE, trueLabel);

        generateExpression(expression.getRight());
        currentMethodVisitor.visitJumpInsn(IFEQ, pushFalse);


        // right is true , push 1 and jump to end
        currentMethodVisitor.visitLdcInsn(1);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        // right false, push 0 jump to end
        currentMethodVisitor.visitLabel(pushFalse);
        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        // left true, push 1
        currentMethodVisitor.visitLabel(trueLabel);
        currentMethodVisitor.visitLdcInsn(1);
        currentMethodVisitor.visitLabel(endLabel);
    }

    /**
     * Generates bytecode for a logical AND (&&) expression using short-circuit evaluation.
     */
    private void generateAndExpression(BinaryExpression expression) {
        generateExpression(expression.getLeft());

        Label endLabel = new Label();
        Label pushFalse = new Label();

        // left is false, jumpt to pushFalse
        currentMethodVisitor.visitJumpInsn(IFEQ, pushFalse);

        // left is true, evaluate right
        generateExpression(expression.getRight());

        // right is false, jump to pushFalse
        currentMethodVisitor.visitJumpInsn(IFEQ, pushFalse);

        // both are true, push one and jump to the end
        currentMethodVisitor.visitLdcInsn(1);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        // left or right is false, push 0
        currentMethodVisitor.visitLabel(pushFalse);
        currentMethodVisitor.visitLdcInsn(0);
        currentMethodVisitor.visitJumpInsn(GOTO, endLabel);

        currentMethodVisitor.visitLabel(endLabel);
    }


    /**
     * Generates bytecode to call a function.
     */
    private void generateFunctionCallExpression(FunctionCallExpression expression) {
        String functionName = expression.getIdentifier();

        // Special case: setValue call (for streams)
        if (functionName.equals("setValue")) {
            for (Expression arg : expression.getArguments()) {
                 generateExpression(arg);
            }
            currentMethodVisitor.visitMethodInsn(
              INVOKESTATIC,
              "org/cesium/Util",
              "setValue",
              "(Lorg/cesium/Stream;I)V",
              false
            );
            return;
        }

        // Handle user-defined functions
        MethodDescriptor methodDescriptor = functionSignatures.get(functionName);
        if (methodDescriptor == null) {
            throw new RuntimeException("Call to undefined function: " + functionName);
        }

        for (Expression argument : expression.getArguments()) {
            generateExpression(argument);
        }

        // call the user-defined function
        currentMethodVisitor.visitMethodInsn(
          INVOKESTATIC,
          className,
          methodDescriptor.name,
          methodDescriptor.descriptor,
          false
        );
    }


    /**
     * Infers the type of a given expression in the Cesium language. Since our Cesium code is running on
     * the JVM, it is important to statically check the type of expressions at compile time. Here we are doing that
     * through the following logic below.
     *
     */
    private String getExpressionType(Expression expression) {
        return switch (expression) {
            case LiteralExpression literalExpression -> {
                Token lit = literalExpression.getLiteral();
                yield switch (lit.getType()) {
                    case NUMERIC_LITERAL -> lit.getValue().contains(".") ? "float" : "int";
                    case BOOLEAN_LITERAL -> "int";
                    case STRING_LITERAL -> "string";
                    default -> throw new UnsupportedOperationException("Unknown literal type: " + lit.getType());
                };
            }

            case VariableExpression variableExpression -> {
                String varName = variableExpression.getIdentifier();
                String type = variableTypes.peek().get(varName);
                if (type == null) {
                    throw new RuntimeException("Undeclared variable: " + varName);
                }
                yield type;
            }

             case BinaryExpression binaryExpressionpr -> {
                String leftType = getExpressionType(binaryExpressionpr.getLeft());
                String rightType = getExpressionType(binaryExpressionpr.getRight());
                String operator = binaryExpressionpr.getOperator();

                // Logical or comparison operators -> "int"
                if (operator.matches("\\|\\||&&|==|!=|>|<|>=|<=")) {
                    yield "int";
                } else {
                    // Arithmetic case
                    if (leftType.equals("Stream") || leftType.equals("Reactive")
                      || rightType.equals("Stream") || rightType.equals("Reactive")) {
                        yield "Reactive";
                    } else {
                        yield (leftType.equals("float") || rightType.equals("float")) ? "float" : "int";
                    }
                }
            }

            case UnaryExpression unaryExpression -> {
                String operator = unaryExpression.getOperator();
                if (operator.equals("!")) {
                    yield "int";
                } else {
                    // Unary minus, return the operand's type
                    yield getExpressionType(unaryExpression.getExpression());
                }
              }

            case FunctionCallExpression functionCallExpression -> "int";

            default -> throw new UnsupportedOperationException("Cannot infer expression type: " + expression.getClass());
        };
    }

    // HELPER METHODS:
    private boolean isComparisonOperator(String operator) {
        return operator.equals("==") || operator.equals("!=")
          || operator.equals(">") || operator.equals("<")
          || operator.equals(">=") || operator.equals("<=");
    }

    private boolean isNumericType(String type) {
        return type.equals("int") || type.equals("float");
    }

    private void ensureTypeForLogical(String type) {
        if (!type.equals("int")) {
            throw new UnsupportedOperationException("Logical operations only supported on boolean)");
        }
    }

    private void ensureNumericOrThrowForComparison(String type) {
        if (!(type.equals("int") || type.equals("float"))) {
            throw new UnsupportedOperationException("Comparison only supported on int/float types");
        }
    }

    private int allocateLocalVariable(String variableName) {
        int index = localVariableIndex.pop();
        localVariableIndex.push(index+1);
        variableScopes.peek().put(variableName, index);
        return index;
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

}