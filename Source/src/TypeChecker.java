package src;

import src.utilities.Function;
import src.utilities.Struct;
import src.utilities.Token;
import src.utilities.Variable;

import java.io.*;
import java.util.*;

public class TypeChecker {
    private final List<Token> tokens;
    private int currentIndex = 0;
    private Token currentToken;
    private final String fileName;
    private List<String> output = new ArrayList<>();
    private boolean isInsideFunction = false;
    private boolean isInsideStruct = false;
    private boolean isInsideParameterList = false;

    //For symbol
    private Map<String, Variable> globalVariables = new HashMap<>();
    private Stack<Map<String, Variable>> localVariables = new Stack<>();
    private Map<String, Function> functions = new HashMap<>();
    private Map<String, Function> prototypes = new HashMap<>();
    private Map<String, Struct> globalStructs = new HashMap<>();
    private Stack<Map<String, Struct>> localStructs = new Stack<>();

    private static HashMap<String,String> typeMatch = new HashMap<>();
    private static HashMap<String, Function> lib = new HashMap<>();
    private boolean isDead = false;
    private int currentStack = 0;
    private int maxStack = 0;
    private int nextSlot = 0;
    private int labelCounter = 0;
    private Stack<String> breakLabels = new Stack<>();
    private Stack<String> continueLabels = new Stack<>();
    private String currentFunctionReturnType = null;


    static {
        typeMatch.put("float","F");
        typeMatch.put("char","C");
        typeMatch.put("int","I");
        typeMatch.put("void","V");
        typeMatch.put("int[]","[I");
        typeMatch.put("float[]","[F");
        typeMatch.put("char[]","[C");
        lib.put("getchar",new Function("getchar","int",new ArrayList<>(),false));
        lib.put("putchar",new Function("putchar","int",new ArrayList<>(),false));
        lib.get("putchar").parameters.add("int");
        lib.put("getint",new Function("getint","int",new ArrayList<>(),false));
        lib.put("putint",new Function("putint","void",new ArrayList<>(),false));
        lib.get("putint").parameters.add("int");
        lib.put("putfloat",new Function("putfloat","void",new ArrayList<>(),false));
        lib.get("putfloat").parameters.add("float");
        lib.put("getfloat",new Function("getfloat","float",new ArrayList<>(),false));
        lib.put("putstring",new Function("putstring","void",new ArrayList<>(),false));
        lib.get("putstring").parameters.add("char[]");
        lib.put("java2c",new Function("java2c","char[]",new ArrayList<>(),false));
        lib.get("java2c").parameters.add("String");
    }



    public TypeChecker(List<Token> tokens, String fileName) {
        this.tokens = tokens;
        this.fileName = fileName;
        currentToken = tokens.get(currentIndex);
    }
    private void advanceToken(){
        currentIndex++;
        if(currentIndex < tokens.size()){
            currentToken = tokens.get(currentIndex);
        }else{
            currentToken = null;
        }
    }
    private boolean match(String expected){
        if(currentToken != null && currentToken.getTokenTypeString().equals(expected)){
            advanceToken();
            return true;
        }
        return false;
    }
    private void expect(String expected){
        if(!match(expected)){
            panic("Expecting " + expected + " but found " + (currentToken != null ? currentToken.getTokenTypeString() : "null"));
        }
    }

    private void panic(String description){
        System.err.printf("Type checking error in file %s line %d\nDescription: %s\n",
                currentToken != null ? currentToken.getFileName() : fileName,
                currentToken != null ? currentToken.getLineNumber() : -1,
                description);
        System.exit(1);
    }
    private void panic(String description, int line){
        System.err.printf("Type checking error in file %s line %d\nDescription: %s\n",
                currentToken != null ? currentToken.getFileName() : fileName,
                line,
                description);
        System.exit(1);
    }

    public void printOutput(){
        String outputFileName = fileName.replaceFirst("\\.[^.]+$", ".j");
        emitClinit();
        try(PrintWriter writer = new PrintWriter(new FileWriter(outputFileName,true))){
            writer.println(emitClassHeader());
            for(String line : output){
                System.out.println(line);
                String s = !line.startsWith(".") && !line.startsWith("\t") ? "\t\t" + line : line;
                if(s.equals(".end method")){
                    s = s+"\n";
                }
                writer.println(s);
            }
            writer.println(emitJMainMethod());
            writer.println(emitInitMethod());
        }catch (IOException e){
            panic("file to create output file " + outputFileName);
            File outputFile = new File(outputFileName);
            outputFile.delete();
        }
    }
    private String determineVariableScope(){
        if(isInsideStruct){
            return "member";
        }
        if(isInsideParameterList){
            return "parameter";
        }
        if(isInsideFunction){
            return "local variable";
        }
        return "global variable";
    }
    public void parseProgram(){
        while(currentToken != null){
            if(isStructDefinition()){
                String structName = currentToken.getTokenTypeString() + " " + tokens.get(currentIndex+1).getLexeme();
                if(globalStructs.containsKey(structName)){
                    panic("Duplicate " + structName + " are not allowed.");
                }
                Struct struct = new Struct(structName,new HashMap<>(),new HashMap<>());
                globalStructs.put(structName, struct);
                parseStructDefinition(struct);
                isInsideStruct = false;
            }else if(isFunctionDefinition()){
                isInsideFunction = true;
                parseFunctionDefinition();
                isInsideFunction = false;
            }else if(isFunctionPrototype()){
                parseFunctionPrototype();
            }else if(isDeclaration()){
                parseGlobalDeclaration();
            }else{
                panic("Excepted declaration, function prototype, function definition, or struct definition");
            }
        }
    };

    private boolean isDeclaration(){
        if(currentToken == null){
            return false;
        }else{
            String type = currentToken.getTokenTypeString();
            return type.equals("void")||type.equals("int")||type.equals("float")||type.equals("char")||type.equals("struct")||type.equals("const");
        }
    }
    private boolean isFunctionPrototype(){
        boolean result = false;
        if(currentIndex + 3 < tokens.size()){
            Token n1 = tokens.get(currentIndex + 1);
            Token n2 = tokens.get(currentIndex + 2);
            Token n3 = tokens.get(currentIndex + 3);
            result =  n1.getTokenTypeString().equals("identifier") && n2.getTokenTypeString().equals("(")
                    ||currentToken.getTokenTypeString().equals("struct") && n1.getTokenTypeString().equals("identifier") && n2.getTokenTypeString().equals("identifier") && n3.getTokenTypeString().equals("(");
        }
        return result;
    }
    private boolean isFunctionDefinition(){
        int index = currentIndex;
        while(index+1 < tokens.size()){
            Token n1 = tokens.get(index);
            Token n2 = tokens.get(index + 1);
            if(n1.getTokenTypeString().equals(")")){
                return n2.getTokenTypeString().equals("{");
            }
            if(n2.getTokenTypeString().equals(";")){
                return false;
            }
            index++;
        }
        return false;
    }
    private boolean isStructDefinition(){
        if(currentIndex + 2 < tokens.size()){
            Token n1 = tokens.get(currentIndex + 1);
            Token n2 = tokens.get(currentIndex + 2);
            return currentToken.getTokenTypeString().equals("struct")
                    && n1.getTokenTypeString().equals("identifier")
                    && n2.getTokenTypeString().equals("{");
        }
        return false;
    }

    private void parseGlobalDeclaration() {
        String type = parseType(false);

        do {
            String name = currentToken.getLexeme();
            expect("identifier");

            boolean isArray = false;
            int size = 0;
            String aType = "";

            if (match("[")) {
                isArray = true;
                expect("intL");
                size = Integer.parseInt(tokens.get(currentIndex - 1).getLexeme());
                aType = type;
                type = type + "[]";
                expect("]");
            }

            if (globalVariables.containsKey(name)) {
                panic("Redefinition of global variable " + name, currentToken.getLineNumber());
            }

            Variable var = new Variable(name, type, isArray, false, false, currentToken.getLineNumber());
            var.isGlobal = true;

            if (isArray) {
                var.aType = aType;
                var.aSize = size;
            }

            if (match("=")) {
                if (type.equals("int") || type.equals("char")) {
                    if (!match("intL")) {
                        panic("Expected int literal for initialization of " + name, currentToken.getLineNumber());
                    }
                    var.initialValue = tokens.get(currentIndex - 1).getLexeme();
                } else if (type.equals("float")) {
                    if (!match("floatL")) {
                        panic("Expected float literal for initialization of " + name, currentToken.getLineNumber());
                    }
                    var.initialValue = tokens.get(currentIndex - 1).getLexeme();
                } else {
                    panic("Only primitive global variables can be initialized for now", currentToken.getLineNumber());
                }
            }

            globalVariables.put(name, var);

            String descriptor = getDescriptor(type);
            output.add(".field public static " + name + " " + descriptor);

        } while (match(","));

        expect(";");
    }



    private void parseDeclaration(List<String> code) {
        String type = parseType(false);

        do {
            String name = currentToken.getLexeme();
            expect("identifier");

            if (localVariables.peek().containsKey(name)) {
                panic("Redefinition of local variable " + name, currentToken.getLineNumber());
            }

            Variable var = new Variable(name, type, false, false, false, currentToken.getLineNumber());
            var.jvmSlot = nextSlot++;
            localVariables.peek().put(name, var);

            switch (type) {
                case "int":
                case "char":
                    code.add("iconst_0");
                    code.add("istore " + var.jvmSlot);
                    popStack(1);
                    break;
                case "float":
                    code.add("fconst_0");
                    code.add("fstore " + var.jvmSlot);
                    popStack(1);
                    break;
            }

        } while (match(","));

        expect(";");
    }

    private String parseType(boolean isVariable){
        boolean isConst = match("const");
        String type = currentToken.getTokenTypeString();
        if(isVariable && type.equals("void")){
            panic("A variable can not be the type of void.");
        }
        if(match("void")||match("int")||match("float")||match("char")){
        }else if(match("struct")){
            type = type + " " + currentToken.getLexeme();
            expect("identifier");
        }else{
            panic("Expected a valid type");
        }
        if(match("const")){
            isConst = true;
        };
        if(isConst){
            type = "const " + type;
        }
        return  type;
    }

    private void parseListIdentifierDeclaration(String type){
        parseIdentifierDeclaration(type);
        while(match(",")){
            parseIdentifierDeclaration(type);
        }
    }
    private void parseIdentifierDeclaration(String type){
        Variable var = new Variable(currentToken.getLexeme(), type,false,false,false, currentToken.getLineNumber());
        if (!isInsideFunction && !isInsideStruct && !isInsideParameterList) {
            if(globalVariables.containsKey(currentToken.getLexeme())){
                panic("Duplicate global variable " + currentToken.getLexeme());
            }
            globalVariables.put(currentToken.getLexeme(), var);
        }else{
            if(localVariables.peek().containsKey(currentToken.getLexeme())){
                panic("Duplicate local variable " + currentToken.getLexeme());
            }
            var.jvmSlot = nextSlot;
            nextSlot++;
            localVariables.peek().put(currentToken.getLexeme(), var);
        }
        expect("identifier");
        if(match("[")){
            expect("intL");
            var.aSize = Integer.parseInt(tokens.get(currentIndex-1).getLexeme());
            expect("]");
            var.aType = type;
            type = type+"[]";
            var.isArray = true;
            var.type = type;
        }
        if(type.startsWith("const ")){
            var.isConst = true;
        }
        if(type.startsWith("const struct ") || type.startsWith("struct ")){
            var.isStruct = true;
        }
        if(match("=")){
            int line = currentToken.getLineNumber();
            Expr expr = parseExpression();
            String widened = getWidenedType(type,expr.type);
            if(!type.equals(expr.type) && widened.equals("error")){
                panic("Assignment failed due to incompatible types " + type + " and " + expr.type, line);
            }
        }
    }

    private void parseFunctionDefinition() {
        int line = currentToken.getLineNumber();

        String returnType = parseType(false);
        expect("identifier");
        String name = tokens.get(currentIndex-1).getLexeme();

        if (functions.containsKey(name)) {
            panic("Function already defined: " + name, line);
        }

        expect("(");
        List<String> parameters = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        if (!match(")")) {
            do {
                String paramType = parseType(false);
                expect("identifier");
                String paramName = tokens.get(currentIndex-1).getLexeme();
                if(match("[")){
                    expect("]");
                    paramType += "[]";
                }
                parameters.add(paramType);
                paramNames.add(paramName);
            } while (match(","));

            expect(")");
        }

        // Register function
        Function func = new Function(name, returnType, parameters, false);
        functions.put(name, func);

        // Set function context
        currentFunctionReturnType = returnType;
        nextSlot = 0;
        resetStack();
        localVariables.push(new HashMap<>());

        // Register parameters
        for (int i = 0; i < paramNames.size(); i++) {
            String param = paramNames.get(i);
            String type = parameters.get(i);
            Variable v = new Variable(param, type, false, false, false, line);
            v.jvmSlot = nextSlot++;
            localVariables.peek().put(param, v);
        }

        expect("{");

        List<String> body = new ArrayList<>();
        while (!match("}")) {
            if (isType(currentToken)) {
                parseDeclaration(body);
            } else {
                parseStatement(body);
            }
        }

        // Begin method
        output.add(".method public static " + name + " : " + getFunctionDescriptor(func));
        output.add("\t.code stack " + maxStack + " locals " + nextSlot);

        // If void function has no explicit return, emit it
        if (returnType.equals("void") && (body.isEmpty() || !endsWithReturn(body))) {
            body.add("\t\treturn");
        }

        // Emit code and close method
        output.addAll(body);
        output.add("\t.end code");
        output.add(".end method");

        localVariables.pop();
        currentFunctionReturnType = null;
    }

    private void parseFunctionPrototype(){
        String type = parseType(false);
        String name = currentToken.getLexeme();
        Function function = new Function(name,type,new ArrayList<String>(),true);
        int line = currentToken.getLineNumber();
        expect("identifier");
        expect("(");
        localVariables.push(new HashMap<>());
        localStructs.push(new HashMap<>());
        if(!match(")")){
            parseListParameters(function);
            expect(")");
        }
        localVariables.pop();
        localStructs.pop();
        expect(";");
        if(prototypes.containsKey(name)){
            if(!prototypes.get(name).returnType.equals(type)){
                panic("The return type of function prototype " + type + " " + name + "is mismatched with exists prototype.",line);
            }
            if(prototypes.get(name).parameters.size() == function.parameters.size()){
                for(int i = 0; i < function.parameters.size(); i++){
                    if(!prototypes.get(name).parameters.get(i).equals(function.parameters.get(i))){
                        panic("Parameter " + i + " of function prototype " + type + " " + name + "mismatches existing prototype.",line);
                    }
                }
            }else{
                panic("The number of parameter of function prototype " + type + " " + name + "is mismatched with exists prototype.",line);
            }
        }else{
            prototypes.put(name, function);
        }
    }
    private void parseListParameters(Function function){
        isInsideParameterList = true;
        int line = currentToken.getLineNumber();
        String[] para = parseParameter();
        String type = para[0];
        function.parameters.add(type);
        if(localVariables.peek().containsKey(para[1])){
            panic("Duplicate parameter" + para[1],line);
        };
        Variable var = new Variable(para[1],para[0],para[2].equals("true"),para[0].startsWith("const "),false,line);
        var.jvmSlot = nextSlot;
        nextSlot++;
        localVariables.peek().put(para[1],var);
        while(match(",")){
            line = currentToken.getLineNumber();
            para = parseParameter();
            type = para[0];
            function.parameters.add(type);
            if(localVariables.peek().containsKey(para[1])){
                panic("Duplicate parameter" + para[1],line);
            };
            var = new Variable(para[1],para[0],para[2].equals("true"),para[0].startsWith("const "),false,line);
            var.jvmSlot = nextSlot;
            nextSlot++;
            localVariables.peek().put(para[1], var);
        }
        isInsideParameterList = false;
    }
    private String[] parseParameter(){
        String type = parseType(true);
        String name = currentToken.getLexeme();
        String isArray = "false";
        expect("identifier");
        if(match("[")){
            expect("]");
            type = type + "[]";
            isArray = "true";
        }
        return new String[]{type, name, isArray};
    }
    private void parseStructDefinition(Struct struct){
        isInsideStruct = true;
        localVariables.push(new HashMap<>());
        localStructs.push(new HashMap<>());
        String structType = currentToken.getTokenTypeString();
        int line = currentToken.getLineNumber();
        expect("struct");
        structType = structType + " " + currentToken.getLexeme();
        expect("identifier");
        expect("{");
        while(!match("}")){
            String type;
            if(isStructDefinition()){
                type = currentToken.getTokenTypeString() +" "+ tokens.get(currentIndex+1).getLexeme();
                if(structExists(type)){
                    panic(type + " is already defined");
                }else{
                    Struct newStruct = new Struct(type, new HashMap<>(), new HashMap<>());
                    struct.membersStruct.put(type, newStruct);
                    localStructs.peek().put(type, newStruct);
                    parseNestedStructDefinition(newStruct);
                }
            }else{
                type = parseType(true);
                if(type.startsWith("struct ")){
                    if(!structExists(type)){
                        panic(type + " is not defined");
                    }
                }
                parseListMember(struct,type);
                expect(";");
            }
        }
        expect(";");
        localVariables.pop();
        localStructs.pop();
    }

    private void parseNestedStructDefinition(Struct struct){
        isInsideStruct = true;
        localVariables.push(new HashMap<>());
        localStructs.push(new HashMap<>());
        String structType = currentToken.getTokenTypeString();
        expect("struct");
        structType = structType + " " + currentToken.getLexeme();
        int line = currentToken.getLineNumber();
        expect("identifier");
        expect("{");
        while(!match("}")){
            String type;
            if(isStructDefinition()){
                type = currentToken.getTokenTypeString() +" "+ tokens.get(currentIndex+1).getLexeme();
                if(structExists(type)){
                    panic(type + " is already defined");
                }else{
                    Struct newStruct = new Struct(type, new HashMap<>(), new HashMap<>());
                    struct.membersStruct.put(type, newStruct);
                    localStructs.peek().put(type, newStruct);
                    parseNestedStructDefinition(newStruct);
                }
            }else{
                type = parseType(true);
                if(type.startsWith("struct ")){
                    if(!structExists(type)){
                        panic(type + " is not defined");
                    }
                }
                parseListMember(struct,type);
                expect(";");
            }
        }
        expect("identifier");
        expect(";");
        localVariables.pop();
        localStructs.pop();
    }

    private boolean structExists(String structType){
        if(globalStructs.containsKey(structType)){
            return true;
        }
        for(int i = localStructs.size()-1; i >= 0; i--){
            if(localStructs.get(i).containsKey(structType)){
                return true;
            }
        }
        return false;
    }
    private void parseListMember(Struct struct, String type){
        parseMember(struct,type);
        while(match(",")){
            parseMember(struct,type);
        }
    }
    private void parseMember(Struct struct, String type){
        String name = currentToken.getLexeme();
        int line = currentToken.getLineNumber();
        expect("identifier");
        String t = type;
        Variable v = new Variable(name,t,false,false,false,currentToken.getLineNumber());
        if(match("[")){
            expect("intL");
            expect("]");
            t = type + "[]";
            v.type = t;
            v.isArray = true;
        }
        if(type.startsWith("const struct ") ||type.startsWith("struct ")){
            v.isStruct = true;
        }
        if(type.startsWith("const ")){
            v.isConst = true;
        }
        if(struct.membersVariables.containsKey(name)){
            panic("duplicate identifier within scope are not allowed " + t + " " + name,line);
        }
        struct.membersVariables.put(name, v);
    }
    private void parseStatementBlock(List<String> code) {

        // New block scope
        localVariables.push(new HashMap<>());

        while (!match("}")) {
            if (isType(currentToken)) {
                parseDeclaration(code);
            } else {
                parseStatement(code);
            }
        }

        localVariables.pop();
    }

    private void parseStatement(List<String> code) {
        if (match("{")) {
            parseStatementBlock(code);
        } else if (match("if")) {
            parseIfStatement(code);
        } else if (match("while")) {
            parseWhileStatement(code);
        } else if (match("do")) {
            parseDoWhileStatement(code);
        } else if (match("for")) {
            parseForStatement(code);
        } else if (match("break")) {
            parseBreak(code);
        } else if (match("continue")) {
            parseContinue(code);
        } else if (match("return")) {
            parseReturn(code);
        } else {
            parseExpressionStatement(code);  // handle expr;
        }
    }


    private void parseIfStatement(List<String> code) {
        int line = currentToken.getLineNumber();
        expect("(");
        Expr cond = parseExpression();
        expect(")");

        if (!isNumeric(cond.type)) {
            panic("Condition in if-statement must be numeric, got " + cond.type, line);
        }

        String labelElse = newLabel();
        String labelEnd = newLabel();

        // Evaluate condition
        code.addAll(cond.code);
        code.add("ifeq " + labelElse);  // jump if false

        // Then branch
        parseStatement(code);  // statement after if

        if (match("else")) {
            code.add("goto " + labelEnd);  // skip else
            code.add(labelElse + ":");
            parseStatement(code);         // else branch
            code.add(labelEnd + ":");
        } else {
            code.add(labelElse + ":");    // no else branch
        }
    }

    private void parseWhileStatement(List<String> code) {
        int line = currentToken.getLineNumber();

        String labelTop = newLabel();
        String labelEnd = newLabel();

        // Push break/continue targets for use inside body
        breakLabels.push(labelEnd);
        continueLabels.push(labelTop);

        code.add(labelTop + ":");

        expect("(");
        Expr cond = parseExpression();
        expect(")");

        if (!isNumeric(cond.type)) {
            panic("Condition in while-statement must be numeric, got " + cond.type, line);
        }

        code.addAll(cond.code);
        code.add("ifeq " + labelEnd);  // break if false

        parseStatement(code);          // loop body

        code.add("goto " + labelTop);  // repeat
        code.add(labelEnd + ":");

        // Pop loop labels after body
        breakLabels.pop();
        continueLabels.pop();
    }

    private void parseDoWhileStatement(List<String> code) {
        int line = currentToken.getLineNumber();

        String labelTop = newLabel();
        String labelEnd = newLabel();

        breakLabels.push(labelEnd);
        continueLabels.push(labelTop);  // continue jumps to start of test

        code.add(labelTop + ":");

        parseStatement(code);  // loop body

        expect("while");
        expect("(");
        Expr cond = parseExpression();
        expect(")");
        expect(";");

        if (!isNumeric(cond.type)) {
            panic("Condition in do-while must be numeric, got " + cond.type, line);
        }

        code.addAll(cond.code);
        code.add("ifne " + labelTop);  // loop again if true

        code.add(labelEnd + ":");

        breakLabels.pop();
        continueLabels.pop();
    }

    private void parseForStatement(List<String> code) {
        int line = currentToken.getLineNumber();

        String labelTop  = newLabel();
        String labelIncr = newLabel();
        String labelEnd  = newLabel();

        breakLabels.push(labelEnd);
        continueLabels.push(labelIncr);

        expect("(");


        if (!match(";")) {
            Expr init = parseExpression();
            code.addAll(init.code);
            appendPopIfNeeded(code, init.type);
            expect(";");
        }

        ArrayList<String> condCode = new ArrayList<>();
        Expr cond = null;
        if (!match(";")) {
            cond = parseExpression();
            if (!isNumeric(cond.type))
                panic("Loop condition must be numeric, got " + cond.type, line);
            condCode.addAll(cond.code);
            expect(";");
        }

        ArrayList<String> incrCode = new ArrayList<>();
        Expr incr = null;
        if (!match(")")) {
            incr = parseExpression();
            incrCode.addAll(incr.code);
            appendPopIfNeeded(incrCode, incr.type);
            expect(")");
        }

        code.add(labelTop + ":");

        if (cond != null) {
            code.addAll(condCode);
            code.add("ifeq " + labelEnd);   // break if false (value popped by ifeq)
        }

        parseStatement(code);               // body

        code.add(labelIncr + ":");          // target for continue
        code.addAll(incrCode);              // increment (result already popped)
        code.add("goto " + labelTop);       // repeat

        code.add(labelEnd + ":");

        breakLabels.pop();
        continueLabels.pop();
    }


    private void parseBreak(List<String> code) {
        int line = currentToken.getLineNumber();

        if (breakLabels.isEmpty()) {
            panic("break used outside of loop", line);
        }

        code.add("goto " + breakLabels.peek());
        expect(";");
    }

    private void parseContinue(List<String> code) {
        int line = currentToken.getLineNumber();

        if (continueLabels.isEmpty()) {
            panic("continue used outside of loop", line);
        }

        code.add("goto " + continueLabels.peek());
        expect(";");
    }

    private void parseReturn(List<String> code) {
        int line = currentToken.getLineNumber();

        if (match(";")) {
            if (!currentFunctionReturnType.equals("void")) {
                panic("return without value in non-void function", line);
            }
            code.add("\t\treturn");
            return;
        }

        Expr expr = parseExpression();
        expect(";");

        if (currentFunctionReturnType.equals("void")) {
            panic("Cannot return a value from a void function", line);
        }

        String widened = getWidenedType(currentFunctionReturnType, expr.type);
        if (widened.equals("error")) {
            panic("Type mismatch in return: expected " + currentFunctionReturnType + ", got " + expr.type, line);
        }

        code.addAll(expr.code);

        switch (currentFunctionReturnType) {
            case "int":
            case "char":
                code.add("\t\tireturn");
                break;
            case "float":
                code.add("\t\tfreturn");
                break;
            default:
                panic("Unsupported return type: " + currentFunctionReturnType, line);
        }
    }

    private void parseExpressionStatement(List<String> code) {
        Expr expr = parseExpression();
        code.addAll(expr.code);
        expect(";");

        if (!expr.type.equals("void")) {
            code.add("pop");
            popStack(1);
        }
    }

    private Expr parseExpression() {
        return parseAssignmentExpression();
    }

    private Expr parseAssignmentExpression() {
        int line = currentToken.getLineNumber();
        Expr lhs = parseConditionalExpression();

        if (match("=") || match("+=") || match("-=") || match("*=") || match("/=") || match("%=")) {
            String op = tokens.get(currentIndex - 1).getTokenTypeString();
            Expr rhs = parseAssignmentExpression();  // right-associative

            if (!lhs.isLvalue || lhs.type.startsWith("const ")) {
                panic("Left-hand side of assignment must be a modifiable lvalue", line);
            }

            List<String> code = new ArrayList<>();

            if (lhs.structBaseExpr != null) {
                // This is a struct field assignment: a.b = x;
                code.addAll(lhs.structBaseExpr.code); // struct object
                code.addAll(rhs.code);                // rhs value

                String descriptor = getDescriptor(lhs.type);
                code.add("putfield Field " + lhs.structType + " " + lhs.structFieldName + " " + descriptor);
                popStack(1);

                code.addAll(lhs.structBaseExpr.code); // reload struct
                code.add("getfield Field " + lhs.structType + " " + lhs.structFieldName + " " + descriptor);
                pushStack(1);
                return new Expr(lhs.type, false, "<top-of-stack>", code);
            }

            String resultType = getWidenedType(lhs.type, rhs.type);
            if (resultType.equals("error")) {
                panic("Type mismatch in assignment: cannot assign " + rhs.type + " to " + lhs.type, line);
            }

            // Array element assignment: a[i] = ...
            if (isArrayAccess(lhs)) {
                int last = lhs.code.size() - 1;                 // iaload / faload / aaload
                List<String> addr = new ArrayList<>(lhs.code.subList(0, last));  // arr idx
                String loadInstr = lhs.code.get(last);          // "iaload"

                int idx = nextSlot++;
                int arr = nextSlot++;
                int val = nextSlot++;

                code = new ArrayList<>();

                code.addAll(addr);
                code.add("istore " + idx);
                code.add("astore " + arr);

                if (op.equals("=")) {
                    code.addAll(rhs.code);                 // ... rhs
                } else {
                    code.add("aload " + arr);              // ... arr
                    code.add("iload " + idx);              // ... arr idx
                    code.add(loadInstr);                   // ... oldVal
                    code.addAll(rhs.code);                 // ... oldVal rhs
                    if(op.equals("+=")){
                        code.add("iadd");
                    }else if(op.equals("-=")){
                        code.add("isub");
                    }else if(op.equals("*=")){
                        code.add("imul");
                    }else if(op.equals("/=")){
                        code.add("idiv");
                    }else{
                        panic("unsupport operator: " + op, line);
                    }
                }
                code.add("istore " + val);

                code.add("aload " + arr);
                code.add("iload " + idx);
                code.add("iload " + val);
                code.add(arrayStoreOp(lhs.type));

                code.add("iload " + val);
                pushStack(1);

                return new Expr("int", false, "<top-of-stack>", code);
            }



            String load, store;
            boolean isGlobal = globalVariables.containsKey(lhs.place);

            if (!isGlobal) {
                int slot = getLocalSlot(lhs.place);
                load = loadForType(lhs.type, slot);
                store = storeForType(lhs.type, slot);
            } else {
                String descriptor = getDescriptor(lhs.type);
                load = "getstatic Field " + getClassName() + " " + lhs.place + " " + descriptor;
                store = "putstatic Field " + getClassName() + " " + lhs.place + " " + descriptor;
            }


            if (op.equals("=")) {
                code.addAll(rhs.code);
                code.add(store);
                popStack(1);
            } else {
                // compound assignment
                code.add(load);         // load lhs
                pushStack(1);
                code.addAll(rhs.code); // eval rhs

                switch (op) {
                    case "+=":
                        code.add(lhs.type.equals("float") ? "fadd" : "iadd");
                        break;
                    case "-=":
                        code.add(lhs.type.equals("float") ? "fsub" : "isub");
                        break;
                    case "*=":
                        code.add(lhs.type.equals("float") ? "fmul" : "imul");
                        break;
                    case "/=":
                        code.add(lhs.type.equals("float") ? "fdiv" : "idiv");
                        break;
                    case "%=":
                        if (lhs.type.equals("float")) {
                            panic("Modulo not supported for float", line);
                        }
                        code.add("irem");
                        break;
                    default:
                        panic("Unsupported assignment operator: " + op, line);
                }

                code.add(store);
                popStack(1);
            }

            code.add(load);  // leave final value on stack for expression result
            pushStack(1);
            return new Expr(lhs.type, false, "<top-of-stack>", code);
        }

        return lhs;
    }

    private Expr parseConditionalExpression() {
        int line = currentToken.getLineNumber();
        Expr cond = parseLogicalOrExpression();

        if (!match("?")) {
            return cond;  // no ternary, pass through
        }

        if (!isNumeric(cond.type)) {
            panic("Ternary condition must be numeric, got " + cond.type, line);
        }

        Expr trueExpr = parseExpression();
        expect(":");
        Expr falseExpr = parseConditionalExpression();

        String resultType = getWidenedType(trueExpr.type, falseExpr.type);
        if (resultType.equals("error")) {
            panic("Ternary branches must have compatible types, got " + trueExpr.type + " and " + falseExpr.type, line);
        }

        String labelElse = newLabel();
        String labelEnd = newLabel();

        List<String> code = new ArrayList<>();
        code.addAll(cond.code);
        code.add("ifeq " + labelElse);         // if cond is false → jump to else
        code.addAll(trueExpr.code);
        code.add("goto " + labelEnd);          // skip else branch
        code.add(labelElse + ":");
        code.addAll(falseExpr.code);
        code.add(labelEnd + ":");
        pushStack(1);
        return new Expr(resultType, false, "<top-of-stack>", code);
    }

    private Expr parseLogicalOrExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseLogicalAndExpression();

        while (match("||")) {
            Expr right = parseLogicalAndExpression();

            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Logical OR (||) requires numeric types, got " + expr.type + " and " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            String labelTrue = newLabel();
            String labelEnd = newLabel();

            code.addAll(expr.code);
            code.add("ifne " + labelTrue);

            code.addAll(right.code);
            code.add("ifne " + labelTrue);

            code.add("iconst_0");
            code.add("goto " + labelEnd);

            code.add(labelTrue + ":");
            code.add("iconst_1");

            code.add(labelEnd + ":");
            pushStack(1);
            expr = new Expr("char", false, "<top-of-stack>", code);
        }
        return expr;
    }


    private Expr parseLogicalAndExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseBitwiseOrExpression();

        while (match("&&")) {
            Expr right = parseBitwiseOrExpression();

            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Logical AND (&&) requires numeric types, got " + expr.type + " and " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            String labelFalse = newLabel();
            String labelEnd = newLabel();


            code.addAll(expr.code);
            code.add("ifeq " + labelFalse);

            code.addAll(right.code);
            code.add("ifeq " + labelFalse);

            code.add("iconst_1");
            code.add("goto " + labelEnd);

            code.add(labelFalse + ":");
            code.add("iconst_0");

            code.add(labelEnd + ":");
            pushStack(1);
            expr = new Expr("char", false, "<top-of-stack>", code);
        }
        return expr;
    }

    private Expr parseBitwiseOrExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseBitwiseAndExpression();

        while (match("|")) {
            Expr right = parseBitwiseAndExpression();

            if (!isIntegerLike(expr.type) || !isIntegerLike(right.type)) {
                panic("Bitwise OR (|) requires int or char types, got " + expr.type + " and " + right.type, line);
            }

            String resultType = getWidenedType(expr.type, right.type);
            if (resultType.equals("error")) {
                panic("Cannot apply | to incompatible types: " + expr.type + ", " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            code.addAll(expr.code);
            code.addAll(right.code);
            code.add("ior");

            expr = new Expr("char", false, "<top-of-stack>", code);
        }

        return expr;
    }

    private Expr parseBitwiseAndExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseEqualityExpression();

        while (match("&")) {
            Expr right = parseEqualityExpression();

            if (!isIntegerLike(expr.type) || !isIntegerLike(right.type)) {
                panic("Bitwise AND (&) requires int or char types, got " + expr.type + " and " + right.type, line);
            }

            String resultType = getWidenedType(expr.type, right.type);
            if (resultType.equals("error")) {
                panic("Cannot apply & to incompatible types: " + expr.type + ", " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            code.addAll(expr.code);
            code.addAll(right.code);
            code.add("iand");

            expr = new Expr("char", false, "<top-of-stack>", code);
        }

        return expr;
    }

    private Expr parseEqualityExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseRelationalExpression();

        while (match("==") || match("!=")) {
            String op = tokens.get(currentIndex - 1).getTokenTypeString();
            Expr right = parseRelationalExpression();

            String type = expr.type.replaceFirst("const ","");
            if (!isNumeric(type) || !isNumeric(right.type)) {
                panic("Equality operator requires numeric types, got " + expr.type + " and " + right.type, line);
            }

            String resultType = getWidenedType(type, right.type);
            if (resultType.equals("error")) {
                panic("Incompatible types for equality operator: " + expr.type + " and " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            code.addAll(expr.code);
            code.addAll(right.code);

            String labelTrue = newLabel();
            String labelEnd = newLabel();

            if (resultType.equals("float")) {
                code.add("fcmpg");
                if (op.equals("==")) {
                    code.add("ifeq " + labelTrue);
                } else {
                    code.add("ifne " + labelTrue);
                }
            } else {
                if (op.equals("==")) {
                    code.add("if_icmpeq " + labelTrue);
                } else {
                    code.add("if_icmpne " + labelTrue);
                }
            }

            code.add("iconst_0");
            code.add("goto " + labelEnd);
            code.add(labelTrue + ":");
            code.add("iconst_1");
            code.add(labelEnd + ":");
            pushStack(1);
            expr = new Expr("char", false, "<top-of-stack>", code);
        }
        pushStack(1);
        return expr;
    }

    private Expr parseRelationalExpression() {
        int line = currentToken.getLineNumber();

        Expr lhs = parseAdditiveExpression();

        while (match("<") || match(">") || match("<=") || match(">=")) {
            String op = tokens.get(currentIndex - 1).getLexeme();
            Expr rhs = parseAdditiveExpression();

            String type = lhs.type.replaceFirst("const ","");
            if (!isNumeric(type) || !isNumeric(rhs.type)) {
                panic("Relational operators require numeric types, got "
                        + lhs.type + " and " + rhs.type, line);
            }

            ArrayList<String> code = new ArrayList<>();
            code.addAll(lhs.code);
            code.addAll(rhs.code);

            String labTrue = newLabel();
            String labEnd  = newLabel();

            if (getWidenedType(type, rhs.type).equals("float")) {
                code.add("fcmpg");         // leaves int −1/0/1
                switch (op) {
                    case "<" : code.add("iflt  " + labTrue); break;
                    case "<=": code.add("ifle  " + labTrue); break;
                    case ">" : code.add("ifgt  " + labTrue); break;
                    case ">=": code.add("ifge  " + labTrue); break;
                    default  : panic("Unknown op", line);
                }
                popStack(2);  pushStack(1);
                popStack(1);
            } else {
                switch (op) {
                    case "<" : code.add("if_icmplt " + labTrue); break;
                    case "<=": code.add("if_icmple " + labTrue); break;
                    case ">" : code.add("if_icmpgt " + labTrue); break;
                    case ">=": code.add("if_icmpge " + labTrue); break;
                    default  : panic("Unknown op", line);
                }
                popStack(2);
            }

            code.add("iconst_0");
            code.add("goto " + labEnd);
            code.add(labTrue + ":");
            code.add("iconst_1");
            code.add(labEnd + ":");
            pushStack(1);

            lhs = new Expr("int", false, null, code);
        }

        return lhs;
    }


    private Expr parseAdditiveExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseMultiplicativeExpression();

        while (match("+") || match("-")) {
            String op = tokens.get(currentIndex - 1).getTokenTypeString();
            Expr right = parseMultiplicativeExpression();

            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Additive operator requires numeric types, got " + expr.type + " and " + right.type, line);
            }

            String resultType = getWidenedType(expr.type, right.type);
            if (resultType.equals("error")) {
                panic("Incompatible types for " + op + ": " + expr.type + ", " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            code.addAll(expr.code);
            code.addAll(right.code);

            switch (op) {
                case "+":
                    if (resultType.equals("float")) {
                        code.add("fadd");
                    } else {
                        code.add("iadd");
                    }
                    break;
                case "-":
                    if (resultType.equals("float")) {
                        code.add("fsub");
                    } else {
                        code.add("isub");
                    }
                    break;
                default:
                    panic("Unsupported additive operator: " + op, line);
            }
            pushStack(1);
            expr = new Expr(resultType, false, "<top-of-stack>", code);
        }

        return expr;
    }

    private Expr parseMultiplicativeExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseUnaryExpression();

        while (match("*") || match("/") || match("%")) {
            String op = tokens.get(currentIndex - 1).getTokenTypeString();
            Expr right = parseUnaryExpression();

            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Multiplicative operator requires numeric types, got " + expr.type + " and " + right.type, line);
            }

            String resultType = getWidenedType(expr.type, right.type);
            if (resultType.equals("error")) {
                panic("Cannot apply operator " + op + " to incompatible types: " + expr.type + " and " + right.type, line);
            }

            List<String> code = new ArrayList<>();
            code.addAll(expr.code);
            code.addAll(right.code);

            switch (op) {
                case "*":
                    if (resultType.equals("float")) {
                        code.add("fmul");
                    } else {
                        code.add("imul");
                    }
                    break;
                case "/":
                    if (resultType.equals("float")) {
                        code.add("fdiv");
                    } else {
                        code.add("idiv");
                    }
                    break;
                case "%":
                    if (resultType.equals("float")) {
                        panic("Modulo operator not supported for float", line);
                    } else {
                        code.add("irem");
                    }
                    break;
                default:
                    panic("Unsupported multiplicative operator: " + op, line);
            }
            pushStack(1);
            expr = new Expr(resultType, false, "<top-of-stack>", code);
        }

        return expr;
    }

    private Expr parseUnaryExpression() {
        int line = currentToken.getLineNumber();

        if (match("++") || match("--")) {
            String op = tokens.get(currentIndex-1).getLexeme();
            Expr var = parseUnaryExpression();

            if (!var.isLvalue)
                panic("Operand of " + op + " must be an l-value",
                        currentToken.getLineNumber());

            ArrayList<String> code = new ArrayList<>();

            if (var.code.size() == 1 && var.code.get(0).startsWith("iload ")) {
                int slot = Integer.parseInt(var.code.get(0).substring(6));
                int delta = op.equals("++") ? 1 : -1;

                code.add("iinc " + slot + " " + delta);
                code.add("iload " + slot);
                pushStack(1);
            }

            else {
                code.addAll(var.code);
                code.add("iconst_1");
                code.add(op.equals("++") ? "iadd" : "isub");
                code.add("dup");
                code.add("istore " + getLocalSlot(var.place));

                popStack(1);
                pushStack(1);
            }

            return new Expr("int", false, null, code);
        }


        if (match("(")) {
            if (isType(currentToken)) {
                String castType = parseType(false);
                expect(")");
                Expr expr = parseUnaryExpression();

                String type = expr.type.replaceFirst("const ","");
                if (!isNumeric(type) || !isNumeric(castType)) {
                    panic("Cannot cast " + expr.type + " to " + castType, line);
                }

                List<String> code = new ArrayList<>(expr.code);

                if (!castType.equals(type)) {
                    if (type.equals("int") && castType.equals("float")) {
                        code.add("i2f");
                    } else if (type.equals("float") && castType.equals("int")) {
                        code.add("f2i");
                    } else if (type.equals("char") && castType.equals("int")) {
                    } else if (type.equals("int") && castType.equals("char")) {
                    } else {
                        panic("Unsupported numeric cast from " + expr.type + " to " + castType, line);
                    }
                }

                return new Expr(castType, false, "<top-of-stack>", code);
            } else {
                Expr inner = parseExpression();
                expect(")");
                return inner;
            }
        }

        // Unary minus or plus
        if (match("-") || match("+")) {
            String op = tokens.get(currentIndex - 1).getTokenTypeString();
            Expr expr = parseUnaryExpression();

            if (!isNumeric(expr.type)) {
                panic("Unary " + op + " requires numeric type, got " + expr.type, line);
            }

            List<String> code = new ArrayList<>(expr.code);
            if (op.equals("-")) {
                if (expr.type.equals("int") || expr.type.equals("char")) {
                    code.add("ineg");
                } else if (expr.type.equals("float")) {
                    code.add("fneg");
                } else {
                    panic("Unsupported type for unary -: " + expr.type, line);
                }
            }

            return new Expr(expr.type, false, "<top-of-stack>", code);
        }

        if (match("!")) {
            Expr expr = parseUnaryExpression();

            if (!isNumeric(expr.type)) {
                panic("Logical NOT requires numeric type, got " + expr.type, line);
            }

            List<String> code = new ArrayList<>(expr.code);
            String lTrue = newLabel();
            String lEnd = newLabel();

            code.add("ifeq " + lTrue);
            code.add("iconst_0");
            code.add("goto " + lEnd);
            code.add(lTrue + ":");
            code.add("iconst_1");
            code.add(lEnd + ":");

            return new Expr("char", false, "<top-of-stack>", code);
        }

        return parsePostfixExpression();
    }


    private Expr parsePostfixExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parsePrimaryExpression();

        while (true) {

            // Post-increment/decrement
            if (match("++") || match("--")) {
                String op = tokens.get(currentIndex - 1).getTokenTypeString();
                if (!expr.isLvalue || expr.type.startsWith("const ")) {
                    panic("Cannot apply " + op + " to non-modifiable lvalue", line);
                }
                if (!isNumeric(expr.type)) {
                    panic("Postfix " + op + " requires numeric type, got " + expr.type, line);
                }

                List<String> code = new ArrayList<>();

                if (expr.code.size() == 1 && expr.code.get(0).startsWith("iload ")) {
                    int slot  = Integer.parseInt(expr.code.get(0).substring(6));
                    int delta = op.equals("++") ? 1 : -1;

                    code.add("iload " + slot);
                    code.add("iinc " + slot + " " + delta);
                    pushStack(1);
                }
                else {
                    code.addAll(expr.code);

                    code.addAll(expr.code);
                    if (expr.type.equals("float"))
                        code.add("fconst_1");
                    else
                        code.add("iconst_1");

                    code.add(op.equals("++")
                            ? (expr.type.equals("float") ? "fadd" : "iadd")
                            : (expr.type.equals("float") ? "fsub" : "isub"));

                    code.add(storeForType(expr.type, getLocalSlot(expr.place)));
                }

                expr = new Expr(expr.type, false, "<top-of-stack>", code);
            }

            else if (match("[")) {
                if (!expr.type.endsWith("[]")) {
                    panic("Cannot index non-array type: " + expr.type, line);
                }

                Expr index = parseExpression();
                expect("]");

                String elemType = expr.type.substring(0, expr.type.length() - 2).replaceFirst("const ","");
                List<String> code = new ArrayList<>(expr.code);
                code.addAll(index.code);

                code.add(arrayLoadOp(elemType));
                pushStack(1);

                expr = new Expr(elemType, true, null, code);
            }

            else if (match(".")) {
                if (!expr.type.startsWith("struct") && !expr.type.startsWith("const struct")) {
                    panic("Member access requires struct type, got " + expr.type, line);
                }

                Token member = currentToken;
                expect("identifier");

                String structName = expr.type.replaceFirst("const ", "").trim();
                Struct s = getStruct(structName);
                if (s == null) panic("Struct not defined: " + structName, member.getLineNumber());

                Variable field = s.membersVariables.get(member.getLexeme());
                if (field == null) {
                    panic("No member named " + member.getLexeme() + " in struct " + structName, member.getLineNumber());
                }

                List<String> code = new ArrayList<>(expr.code);
                code.add("getfield Field " + structName + " " + field.identifier + " " + getDescriptor(field.type));
                Expr filedExpr = new Expr(field.type, !field.isConst, field.identifier, code);
                filedExpr.structBaseExpr = expr;
                filedExpr.structFieldName = member.getLexeme();
                filedExpr.structType = structName;
                expr = filedExpr;
            }

            else if (match("(")) {
                List<Expr> args = new ArrayList<>();
                List<String> code = new ArrayList<>();

                if (!match(")")) {
                    Expr arg = parseExpression();
                    args.add(arg);
                    code.addAll(arg.code);

                    while (match(",")) {
                        arg = parseExpression();
                        args.add(arg);
                        code.addAll(arg.code);
                    }

                    expect(")");
                }

                String funcName = expr.place;
                Function f = functions.get(funcName);
                if (f == null) f = prototypes.get(funcName);
                if (f == null) panic("Function not found: " + funcName, line);

                if (f.parameters.size() != args.size()) {
                    panic("Function " + funcName + " expects " + f.parameters.size() + " arguments, got " + args.size(), line);
                }

                for (int i = 0; i < args.size(); i++) {
                    String expected = f.parameters.get(i);
                    String actual = args.get(i).type;
                    if (!expected.equals(actual) && getWidenedType(expected, actual).equals("error")) {
                        panic("Argument " + (i + 1) + " type mismatch: expected " + expected + " but got " + actual, line);
                    }
                }

                code.add("invokestatic Method " + getClassName() + " " + funcName + " " + getFunctionDescriptor(f));
                expr = new Expr(f.returnType, false, "<top-of-stack>", code);
            }

            else {
                break;
            }
        }

        return expr;
    }

    //TOP
    private Expr parsePrimaryExpression() {
        int line = currentToken.getLineNumber();

        if (match("identifier")) {
            String name = tokens.get(currentIndex - 1).getLexeme();
            Variable var = getVariable(name);
            Function func = functions.get(name);
            Function pfunc = prototypes.get(name);
            Function libFunc = lib.get(name);

            if (var != null) {
                List<String> code = new ArrayList<>();
                String place = name;

                if (var.isGlobal) {
                    code.add("getstatic Field " + getClassName() + " " + name + " " + getDescriptor(var.type));
                } else {
                    int slot = getLocalSlot(name);
                    code.add(loadForType(var.type, slot));
                }

                return new Expr(var.type, !var.isConst, place, code);
            } else if (func != null || pfunc != null) {
                Function f = func != null ? func : pfunc;
                List<String> argTypes = new ArrayList<>();
                List<String> code = new ArrayList<>();
                expect("(");

                if (!match(")")) {
                    Expr arg = parseExpression();
                    code.addAll(arg.code);
                    argTypes.add(arg.type);

                    while (match(",")) {
                        arg = parseExpression();
                        code.addAll(arg.code);
                        argTypes.add(arg.type);
                    }

                    expect(")");
                }

                if (argTypes.size() != f.parameters.size()) {
                    panic("Function " + name + " expects " + f.parameters.size() + " arguments, got " + argTypes.size(), line);
                }

                for (int i = 0; i < argTypes.size(); i++) {
                    String expected = f.parameters.get(i);
                    String actual = argTypes.get(i);
                    if (!expected.equals(actual) && getWidenedType(expected, actual).equals("error")) {
                        panic("Argument " + (i + 1) + " type mismatch: expected " + expected + " but got " + actual, line);
                    }
                }

                code.add("invokestatic Method " + getClassName() + " " + name + " " + getFunctionDescriptor(f));
                return new Expr(f.returnType, false, "<top-of-stack>", code);
            } else if(functions.get(name) == null && libFunc != null){
                Function f = libFunc;
                List<String> actualTypes = new ArrayList<>();
                List<String> code = new ArrayList<>();
                expect("(");

                if (!match(")")) {
                    // First argument
                    Expr arg = parseExpression();
                    code.addAll(arg.code);
                    actualTypes.add(arg.type);

                    // Remaining arguments
                    while (match(",")) {
                        arg = parseExpression();
                        code.addAll(arg.code);
                        actualTypes.add(arg.type);
                    }

                    expect(")");
                }

                // Arity check
                if (actualTypes.size() != f.parameters.size()) {
                    panic(f.identifier + " requires " + f.parameters.size() + " arguments, got " + actualTypes.size(), line);
                }

                // Type check for each argument
                for (int i = 0; i < actualTypes.size(); i++) {
                    String expected = f.parameters.get(i);
                    String given = actualTypes.get(i);
                    if (!expected.equals(given) && getWidenedType(expected, given).equals("error")) {
                        panic(f.identifier + " argument mismatch at index " + (i + 1), line);
                    }
                }

                // Emit function call
                code.add("invokestatic Method lib440 " + name + " " + getFunctionDescriptor(f));

                if (!f.returnType.equals("void")) {
                    pushStack(1);
                    return new Expr(f.returnType, false, "<top-of-stack>", code);
                } else {
                    return new Expr("void", false, null, code);
                }
            }else {
                panic(name + " is undefined", line);
            }
        }

        if (match("intL")) {
            String value = tokens.get(currentIndex - 1).getLexeme();
            List<String> code = List.of("bipush " + value);
            pushStack(1);
            return new Expr("int", false, "<top-of-stack>", code);
        }

        if (match("charL")) {
            String value = tokens.get(currentIndex - 1).getLexeme().charAt(1) + "";
            List<String> code = List.of("bipush " + (int)value.charAt(0));
            pushStack(1);
            return new Expr("char", false, "<top-of-stack>", code);
        }

        if (match("realL")) {
            float value = Float.parseFloat(tokens.get(currentIndex - 1).getLexeme());
            ArrayList<String> code = new ArrayList<>();
            emitFloatConst(code,value);
            pushStack(1);
            return new Expr("float", false, "<top-of-stack>", code);
        }

        if (match("stringL")) {
            String str = tokens.get(currentIndex - 1).getLexeme();
            List<String> code = new ArrayList<>();
            code.add("ldc " + str);
            code.add("invokestatic Method lib440 java2c (Ljava/lang/String;)[C");
            pushStack(1);
            return new Expr("const char[]", false, "<top-of-stack>", code);
        }

        if (match("(")) {
            Expr inner = parseExpression();
            expect(")");
            return new Expr(inner.type, false, "<top-of-stack>", inner.code);
        }

        panic("Expected an expression", line);
        return null;
    }

    private void emitClinit() {
        List<String> clinitCode = new ArrayList<>();
        resetStack();

        for (Variable var : globalVariables.values()) {
            String name = var.identifier;
            String type = var.type;
            String descriptor = getDescriptor(type);
            String className = getClassName();

            clinitCode.add("; initializing variable at line " + var.lineNumber);

            if (var.isArray) {
                if (var.aSize <= 5) {
                    clinitCode.add("iconst_" + var.aSize);
                } else if (var.aSize < 128) {
                    clinitCode.add("bipush " + var.aSize);
                } else {
                    clinitCode.add("ldc " + var.aSize);
                }

                switch (var.aType) {
                    case "int":
                    case "char":
                        clinitCode.add("newarray int");
                        break;
                    case "float":
                        clinitCode.add("newarray float");
                        break;
                    default:
                        panic("Unsupported array type: " + var.aType, var.lineNumber);
                }

                pushStack(1);
                clinitCode.add("putstatic Field " + className + " " + name + " " + descriptor);
                popStack(1);

            } else if (type.equals("int") || type.equals("char")) {
                if (var.initialValue != null) {
                    clinitCode.add("ldc " + var.initialValue);
                } else {
                    clinitCode.add("iconst_0");
                }
                pushStack(1);
                clinitCode.add("putstatic Field " + className + " " + name + " " + descriptor);
                popStack(1);

            } else if (type.equals("float")) {
                if (var.initialValue != null) {
                    clinitCode.add("ldc " + var.initialValue);
                } else {
                    clinitCode.add("fconst_0");
                }
                pushStack(1);
                clinitCode.add("putstatic Field " + className + " " + name + " " + descriptor);
                popStack(1);

            } else if (type.startsWith("struct")) {
                clinitCode.add("aconst_null");
                pushStack(1);
                clinitCode.add("putstatic Field " + className + " " + name + " " + descriptor);
                popStack(1);
            }
        }

        clinitCode.add("return");

        output.add(".method static <clinit> : ()V");
        output.add(".code stack " + maxStack + " locals 0");
        output.addAll(clinitCode);
        output.add(".end code");
        output.add(".end method");
    }


    private String emitClassHeader(){
        return ".class public " + getClassName() + "\n.super java/lang/Object\n";
    }

    private String emitInitMethod(){
        StringBuilder code = new StringBuilder();
        code.append(".method <init> : ()V\n");
        code.append("\t.code stack 1 locals 1\n");
        code.append("\t\taload_0\n");
        code.append("\t\tinvokespecial Method java/lang/Object <init> ()V\n");
        code.append("\t\treturn\n");
        code.append("\t.end code\n");
        code.append(".end method\n");
        return code.toString();
    }

    private String emitCMainMethod(){
        StringBuilder code = new StringBuilder();
        code.append(".method public static main : ()I\n");
        code.append("\t.code stack 1 locals 0\n");
        code.append("\t\tinvokestatic Method " + getClassName() + " main ()I\n");
        code.append("\t\tireturn\n");
        code.append("\t.end code\n");
        code.append(".end method\n");
        return code.toString();
    }

    private String emitJMainMethod(){
        StringBuilder code = new StringBuilder();
        code.append(".method public static main : ([Ljava/lang/String;)V\n");
        code.append("\t.code stack 1 locals 1\n");
        code.append("\t\tinvokestatic Method " + getClassName() + " main ()I\n");
        code.append("\t\tinvokestatic Method java/lang/System exit (I)V\n");
        code.append("\t\treturn\n");
        code.append("\t.end code\n");
        code.append(".end method\n");
        return code.toString();
    }

    private Variable getVariable(String name){
        for(int i = localVariables.size()-1; i >= 0; i--){
            if(localVariables.get(i).containsKey(name)){
                return localVariables.get(i).get(name);
            }
        }
        if(globalVariables.containsKey(name)){
            return globalVariables.get(name);
        }
        return null;
    }
    private int getLocalSlot(String name) {
        for (int i = localVariables.size() - 1; i >= 0; i--) {
            Variable v = localVariables.get(i).get(name);
            if (v != null) return v.jvmSlot;
        }
        panic("No slot found for local variable " + name);
        return -1;
    }
    private Struct getStruct(String type) {
        if (globalStructs.containsKey(type)) {
            return globalStructs.get(type);
        }
        for (int i = localStructs.size() - 1; i >= 0; i--) {
            if (localStructs.get(i).containsKey(type)) {
                return localStructs.get(i).get(type);
            }
        }
        return null;
    }


    private boolean isType(Token token){
        if(token == null){
            return false;
        }
        return token.getTokenTypeString().equals("int")||token.getTokenTypeString().equals("float")||token.getTokenTypeString().equals("char")||
                token.getTokenTypeString().contains("struct")||token.getTokenTypeString().equals("const")||token.getTokenTypeString().equals("void");
    }

    private boolean isNumeric(String type){
        return type.equals("int") || type.equals("float") || type.equals("char");
    }

    private String getWidenedType(String a, String b) {
        if (a.equals(b)) return a;
        if (a.equals("char") && b.equals("int")) return "int";
        if (a.equals("int") && b.equals("char")) return "int";
        if (a.equals("char") && b.equals("float")) return "float";
        if (a.equals("float") && b.equals("char")) return "float";
        if (a.equals("int") && b.equals("float")) return "float";
        if (a.equals("float") && b.equals("int")) return "float";

        if (a.startsWith("const ") && b.startsWith("const ")) {
            String aa = a.replaceFirst("const ", "");
            String bb = b.replaceFirst("const ", "");
            String result = getWidenedType(aa, bb);
            if (!result.equals("error")) {
                return "const " + result;
            }
        }
        if (a.startsWith("const ")) {
            String aa = a.replaceFirst("const ", "");
            String r = getWidenedType(aa, b);
            if (!r.equals("error")) return "const " + r;
        }
        if (b.startsWith("const ")) {
            String bb = b.replaceFirst("const ", "");
            String r = getWidenedType(a, bb);
            if (!r.equals("error")) return "const " + r;
        }

        return "error";
    }

    private static String arrayLoadOp(String elemType) {
        switch (elemType) {
            case "int":    return "iaload";
            case "float":  return "faload";
            case "char":   return "caload";
            case "byte":
            case "boolean":return "baload";
            case "short":  return "saload";
            case "long":   return "laload";
            case "double": return "daload";
            default:       return "aaload";
        }
    }
    private static String arrayStoreOp(String elemType) {
        switch (elemType) {
            case "int":    return "iastore";
            case "float":  return "fastore";
            case "char":   return "castore";
            case "byte":
            case "boolean":return "bastore";
            case "short":  return "sastore";
            case "long":   return "lastore";
            case "double": return "dastore";
            default:       return "aastore";
        }
    }


    private void appendPopIfNeeded(List<String> code, String type) {
        if (!type.equals("void")) {
            code.add("pop");
            popStack(1);
        }
    }

    private void emitFloatConst(ArrayList<String> code, float v) {
        if (v == 0.0f) {
            code.add("fconst_0");
        } else if (v == 1.0f) {
            code.add("fconst_1");
        } else if (v == 2.0f) {
            code.add("fconst_2");
        } else {
            String text = Float.toString(v);
            if (!text.endsWith("f") && !text.endsWith("F"))
                text += "f";
            code.add("ldc " + text);
        }
    }


    private boolean endsWithReturn(List<String> code) {
        if (code.isEmpty()) return false;
        String last = code.get(code.size() - 1);
        return last.equals("return") || last.equals("ireturn") || last.equals("freturn");
    }

    private boolean isArrayAccess(Expr e) {
        return e.isLvalue &&
                !e.code.isEmpty() &&
                (e.code.get(e.code.size() - 1).equals("iaload")
                        || e.code.get(e.code.size() - 1).equals("faload")
                        || e.code.get(e.code.size() - 1).equals("aaload"));
    }

    private boolean isIntegerLike(String type) {
        return type.equals("int") || type.equals("char") || type.equals("const int") || type.equals("const char");
    }

    private String newLabel() {
        return "L" + (labelCounter++);
    }

    private String loadForType(String type, int slot) {
        pushStack(1);
        if (type.equals("int") || type.equals("char")) {
            return "iload " + slot;
        } else if (type.equals("float")) {
            return "fload " + slot;
        } else if (type.endsWith("[]") || type.startsWith("struct") || type.startsWith("const struct")) {
            return "aload " + slot;
        } else {
            panic("Unsupported type in loadForType: " + type);
            return "";
        }
    }

    private String storeForType(String type, int slot) {
        popStack(1);
        if (type.equals("int") || type.equals("char")) {
            return "istore " + slot;
        } else if (type.equals("float")) {
            return "fstore " + slot;
        } else if (type.endsWith("[]") || type.startsWith("struct") || type.startsWith("const struct")) {
            return "astore " + slot;
        } else {
            panic("Unsupported type in storeForType: " + type);
            return "";
        }
    }

    private String getClassName() {
        return fileName.replaceFirst("\\.[^.]+$", "");
    }

    private String getDescriptor(String type) {
        type = type.replace("const ", "").trim();
        if(type.startsWith("struct ")){
            String structName = type.replace("struct ", "").trim();
            return "L" + structName + ";";
        }else if(typeMatch.containsKey(type)){
            return typeMatch.get(type);
        }
        panic("Unsupported type for descriptor: " + type);
        return "?";
    }

    private String getFunctionDescriptor(Function func) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (String paramType : func.parameters) {
            sb.append(getDescriptor(paramType));
        }
        sb.append(")");
        sb.append(getDescriptor(func.returnType));
        return sb.toString();
    }


    private void pushStack(int count){
        currentStack += count;
        maxStack = Math.max(maxStack, currentStack);
    }

    private void popStack(int count){
        currentStack -= count;
    }

    private void resetStack(){
        currentStack = 0;
        maxStack = 0;
    }

    private static class Expr {
        String type;
        boolean isLvalue;
        String place;
        List<String> code;
        public Expr structBaseExpr = null;
        public String structFieldName = null;
        public String structType = null;


        Expr(String type, boolean isLvalue) {
            this.type = type;
            this.isLvalue = isLvalue;
            this.place = null;
            this.code = new ArrayList<>();
        }

        Expr(String type, boolean isLvalue, String place, List<String> code) {
            this.type = type;
            this.isLvalue = isLvalue;
            this.place = place;
            this.code = code;
        }
    }

}