package src;

import src.utilities.Function;
import src.utilities.Struct;
import src.utilities.Token;
import src.utilities.Variable;

import java.io.*;
import java.util.*;

public class TypeChecker2 {
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




    public TypeChecker2(List<Token> tokens, String fileName) {
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
    private void prepareOutput(Token token, String type){
        if(token == null){
            return;
        }
        String line = String.format("File %s Line %d: expression has type %s",
                token.getFileName(), token.getLineNumber(), type);
        output.add(line);
    }
    public void printOutput(){
        String outputFileName = fileName.replaceFirst("\\.[^.]+$", ".types");
        try(PrintWriter writer = new PrintWriter(new FileWriter(outputFileName,true))){
            for(String line : output){
                System.out.println(line);
                writer.println(line);
            }
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
                parseFunctionDefinition();
                isInsideFunction = false;
            }else if(isFunctionPrototype()){
                parseFunctionPrototype();
            }else if(isDeclaration()){
                parseDeclaration();
                expect(";");
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


    private void parseDeclaration(){
        String type = parseType(true);
        parseListIdentifierDeclaration(type);
    };
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
            localVariables.peek().put(currentToken.getLexeme(), var);
        }
        expect("identifier");
        if(match("[")){
            expect("intL");
            expect("]");
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
    private void parseFunctionDefinition(){
        isInsideFunction = true;
        localVariables.push(new HashMap<>());
        localStructs.push(new HashMap<>());
        String type = parseType(false);
        String name = currentToken.getLexeme();
        Function function = new Function(name,type,new ArrayList<String>(),false);
        int line = currentToken.getLineNumber();
        expect("identifier");
        expect("(");
        if(!match(")")){
            parseListParameters(function);
            expect(")");
        }
        if(functions.containsKey(name)){
            panic("The name of function " + type + " " + name + " are duplicated, this is not allowed.",line);
        }else {
            functions.put(name, function);
        }
        if(prototypes.containsKey(name)){
            if(!prototypes.get(name).returnType.equals(type)){
                panic("The return type of function " + type + " " + name + "is mismatched with exists prototype.",line);
            }
            if(prototypes.get(name).parameters.size() == function.parameters.size()){
                for(int i = 0; i < function.parameters.size(); i++){
                    if(!prototypes.get(name).parameters.get(i).equals(function.parameters.get(i))){
                        panic("Parameter " + i + " of function " + type + " " + name + "mismatches existing prototype.",line);
                    }
                }
            }else{
                panic("The number of parameter of function " + type + " " + name + "is mismatched with exists prototype.",line);
            }
        }
        parseStatementBlock(function.returnType);
        localVariables.pop();
        localStructs.pop();
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
        localVariables.peek().put(para[1],new Variable(para[1],para[0],para[2].equals("true"),para[0].startsWith("const "),false,line));
        while(match(",")){
            line = currentToken.getLineNumber();
            para = parseParameter();
            type = para[0];
            function.parameters.add(type);
            if(localVariables.peek().containsKey(para[1])){
                panic("Duplicate parameter" + para[1],line);
            };
            localVariables.peek().put(para[1],new Variable(para[1],para[0],para[2].equals("true"),para[0].startsWith("const "),false,line));
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
    private void parseStatementBlock(String returnType){
        expect("{");
        if(!match("}")){
            parseStatement(returnType);
            while(currentToken != null && !currentToken.getTokenTypeString().equals("}")){
                parseStatement(returnType);
            }
            expect("}");
        }
    }
    private void parseStatement(String currentFunctionReturnType) {
        if (match("if")) {
            expect("(");
            Expr cond = parseExpression();
            expect(")");
            parseStatement(currentFunctionReturnType);
            if (match("else")) {
                parseStatement(currentFunctionReturnType);
            }
        } else if (match("while")) {
            expect("(");
            Expr cond = parseExpression();
            expect(")");
            parseStatement(currentFunctionReturnType);
        } else if(match("break") || match("continue")){
        }else if(match("do")){
            parseStatement(currentFunctionReturnType);
            expect("while");
            expect("(");
            parseExpression();
            expect(")");
        }else if (match("for")) {
            expect("(");
            if (!match(";")) {
                parseExpression();
                expect(";");
            }
            if (!match(";")) {
                parseExpression();
                expect(";");
            }
            if (!match(")")) {
                parseExpression();
                expect(")");
            }
            parseStatement(currentFunctionReturnType);
        }else if (match("return")) {
            if (!match(";")) {
                Token t = currentToken;
                Expr ret = parseExpression();
                expect(";");
                if (!getWidenedType(currentFunctionReturnType, ret.type).equals("error")) {
                    // ok
                } else {
                    panic("Return type mismatch, expected " + currentFunctionReturnType + " got " + ret.type,t.getLineNumber());
                }
            } else {
                if (!currentFunctionReturnType.equals("void")) {
                    panic("Function must return " + currentFunctionReturnType,tokens.get(currentIndex-1).getLineNumber());
                }
            }
        } else if (match("{")) {
            localVariables.push(new HashMap<>());
            localStructs.push(new HashMap<>());
            while (!match("}")) {
                parseStatement(currentFunctionReturnType);
            }
            localVariables.pop();
            localStructs.pop();
        } else if(match(";")){

        }else if(isStructDefinition()){
            Struct s = new Struct(currentToken.getTokenTypeString() + " " + tokens.get(currentIndex+1).getLexeme(),new HashMap<>(),new HashMap<>());
            int line = currentToken.getLineNumber();
            parseStructDefinition(s);
            if(localStructs.peek().containsKey(s.name)){
                panic("duplicate local struct are not allowed",line);
            }
            localStructs.peek().put(s.name, s);
            isInsideStruct = false;
        } else {
            Boolean isExpress = true;
            if(isType(currentToken)){
                isExpress = false;
            }
            Token t = currentToken;
            Expr expr = parseExpression();
            if(isExpress){
                prepareOutput(t,expr.type);
            }
            expect(";");
        }
    }
    private Expr parseExpression() {
        return parseAssignmentExpression();
    }

    private Expr parseAssignmentExpression() {
        int line = currentToken.getLineNumber();
        Expr left = parseConditionalExpression();
        if (match("=") || match("+=") || match("-=") || match("*=") || match("/=")) {
            if (!left.isLvalue) {
                panic("Left-hand side is not modifiable variable",line);
            }
            if (left.type.startsWith("const ")) { panic("Cannot assign to const variable"); } // MODIFIED
            Expr right = parseExpression();
            String finalType = getWidenedType(left.type, right.type);
            if (finalType.equals("error")) {
                panic("Type mismatch in assignment: cannot convert " + right.type + " to " + left.type,line);
            }
            return new Expr(finalType, false);
        }
        return left;
    }

    private Expr parseConditionalExpression() {
        int line = currentToken.getLineNumber();
        Expr cond = parseLogicalOrExpression();
        if (match("?")) {
            Expr left = parseExpression();
            expect(":");
            Expr right = parseConditionalExpression();
            // check that left.type and right.type are the same or widen
            String finalType = getWidenedType(left.type, right.type);
            if (finalType.equals("error")) {
                panic("Ternary operands type mismatch: " + left.type + " and " + right.type, line);
            }
            return new Expr(finalType, false);
        }
        return cond;
    }

    private Expr parseLogicalOrExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseLogicalAndExpression();
        while (match("||")) {
            Expr right = parseLogicalAndExpression();
            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Logical OR requires numeric type",line);
            }
            expr = new Expr("char", false);
        }
        return expr;
    }

    private Expr parseLogicalAndExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseEqualityExpression();
        while (match("&&")) {
            Expr right = parseEqualityExpression();
            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Logical AND requires numeric type",line);
            }
            expr = new Expr("char", false);
        }
        return expr;
    }

    private Expr parseEqualityExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseRelationalExpression();
        while (match("==") || match("!=")) {
            Expr right = parseRelationalExpression();
            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Equality operator requires numeric type",line);
            }
            expr = new Expr("char", false);
        }
        return expr;
    }

    private Expr parseRelationalExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseAdditiveExpression();
        while (match("<") || match(">") || match("<=") || match(">=")) {
            Expr right = parseAdditiveExpression();
            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Relational operator requires numeric type",line);
            }
            expr = new Expr("char", false);
        }
        return expr;
    }

    private Expr parseAdditiveExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseMultiplicativeExpression();
        while (match("+") || match("-")) {
            Expr right = parseMultiplicativeExpression();
            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Additive operator requires numeric type",line);
            }
            String finalType = getWidenedType(expr.type, right.type);
            if (finalType.equals("error")) {
                panic("Type mismatch in additive expression: " + expr.type + " and " + right.type,line);
            }
            expr = new Expr(finalType, false);
        }
        return expr;
    }

    private Expr parseMultiplicativeExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parseUnaryExpression();
        while (match("*") || match("/") || match("%")) {
            Expr right = parseUnaryExpression();
            if (!isNumeric(expr.type) || !isNumeric(right.type)) {
                panic("Multiplicative operator requires numeric type",line);
            }
            String finalType = getWidenedType(expr.type, right.type);
            if (finalType.equals("error")) {
                panic("Type mismatch in multiplicative expression: " + expr.type + " and " + right.type,line);
            }
            expr = new Expr(finalType, false);
        }
        return expr;
    }

    private Expr parseUnaryExpression() {
        int line = currentToken.getLineNumber();
        if (match("(")) {
            if (isType(currentToken)) {
                String castType = parseType(false);
                expect(")");
                Expr inner = parseUnaryExpression();

                if (!isNumeric(inner.type)) {
                    panic("Cannot cast non-numeric type " + inner.type + " to " + castType, line);
                }
                if (!isNumeric(castType)) {
                    panic("Cannot cast to non-numeric type " + castType, line);
                }

                return new Expr(castType, false);
            } else {
                Expr inner = parseExpression();
                expect(")");
                return inner;
            }
        }
        if (match("++") || match("--")) {
            line = currentToken.getLineNumber();
            Expr expr = parseUnaryExpression();
            if (!isNumeric(expr.type)) {
                panic("Increment/decrement requires numeric type or non const variable",line);
            }
            if (!expr.isLvalue) {
                panic("Cannot apply ++/-- to non-lvalue",line);
            }
            if(expr.type.startsWith("const ")) {
                panic("Cannot apply ++/-- to const variable",line);
            }
            return expr;
        }
        return parsePostfixExpression();
    }

    private Expr parsePostfixExpression() {
        int line = currentToken.getLineNumber();
        Expr expr = parsePrimaryExpression();
        while (true) {
            if (match("++") || match("--")) {
                if (!isNumeric(expr.type)) {
                    panic("Increment/decrement requires numeric type or non const variable.",line);
                }
                if (!expr.isLvalue) {
                    panic("Cannot apply ++/-- to non-lvalue",line);
                }
                if(expr.type.startsWith("const ")) {
                    panic("Cannot apply ++/-- to const variable",line);
                }
            } else if (match("[")) {
                if (expr != null && !expr.type.endsWith("[]")) {
                    panic("indexing requires array type but with "+expr.type,line);
                }
                line = currentToken.getLineNumber();
                Expr index = parseExpression();
                expect("]");
                if (!index.type.equals("int")) {
                    panic("Array index must be int",line);
                }
                // remove one "[]" from expr type
                String elementType = expr.type.substring(0, expr.type.length() - 2);
                expr = new Expr(elementType, true);
            } else if (match(".")) {
                if (expr != null && !expr.type.startsWith("struct ") && !expr.type.startsWith("const struct ")) {
                    panic("Member access requires struct type",line);
                }
                Token member = currentToken;
                expect(member.getTokenTypeString());

                String t = expr.type.replaceFirst("const ", "");
                Struct struct = globalStructs.get(t);

                if(struct == null){
                    struct = globalStructs.get(t);
                }
                if (struct == null) {
                    struct = localStructs.peek().get(t);
                }
                if (struct == null) {
                    panic(expr.type + " struct is not defined",member.getLineNumber());
                } else {
                    Variable memberVar = struct.membersVariables.get(member.getLexeme());
                    Struct nestedStruct = struct.membersStruct.get(member.getLexeme());
                    if (memberVar != null) {
                        boolean isConst = expr.type.startsWith("const ") || memberVar.type.startsWith("const ");
                        expr = new Expr(isConst? "const " + memberVar.type : memberVar.type, !isConst);
                    } else if (nestedStruct != null) {
                        String typeName = "struct " + member.getLexeme();
                        boolean isConst = expr.type.startsWith("const ");
                        expr = new Expr(isConst ? ("const " + typeName) : typeName, true);
                    } else {
                        panic("struct " + expr.type + " has no member named " + member.getLexeme(),member.getLineNumber());
                    }
                }
            }else if(match("(")){
                List<String> argumentTypes = new ArrayList<>();
                if(!match(")")){
                    argumentTypes.add(parseExpression().type);
                    while(match(",")){
                        argumentTypes.add(parseExpression().type);
                    }
                    expect(")");
                }
                String funcName = expr.type;
                Function f = functions.get(funcName);
                Function p = prototypes.get(funcName);

                if(f == null && p == null){
                    panic(funcName + " is undefined",line);
                }else if(f != null){
                    if(f.parameters.size() != argumentTypes.size()){
                        panic("Function " + funcName + " expected " + f.parameters.size() + " arguments, got " + argumentTypes.size(),line);
                    }
                    for(int i = 0; i < argumentTypes.size(); i++){
                        if(!f.parameters.get(i).equals(argumentTypes.get(i))){
                            panic("Function argument" + (i + 1) + " type mismatch: expected " + f.parameters.get(i) + " but got " + argumentTypes.get(i),line);
                        }
                    }
                    expr = new Expr(f.returnType,false);
                }else {
                    if(p.parameters.size() != argumentTypes.size()){
                        panic("Function " + funcName + " expected " + p.parameters.size() + " arguments, got " + argumentTypes.size(),line);
                    }
                    for(int i = 0; i < argumentTypes.size(); i++){
                        if(!p.parameters.get(i).equals(argumentTypes.get(i))){
                            panic("Function argument" + (i + 1) + " type mismatch: expected " + p.parameters.get(i) + " but got " + argumentTypes.get(i),line);
                        }
                    }
                    expr = new Expr(p.returnType,false);
                }
            }else{
                break;
            }
        }
        return expr;
    }
    //TOP
    private Expr parsePrimaryExpression(){
        int line = currentToken.getLineNumber();
        if(match("identifier")){
            String name = tokens.get(currentIndex-1).getLexeme();
            Variable var = getVariable(name);
            Function func = functions.get(name);
            Function pfunc = prototypes.get(name);
            if(var == null && func == null && pfunc == null) {
                panic(name + " is undefined",line);
            }else if (var != null) {
                return new Expr(var.type, !var.isConst);
            }else if (func != null) {
                expect("(");
                ArrayList<String> types = new ArrayList<>();
                Expr e = parseExpression();
                types.add(e.type);
                while(match(",")){
                    e = parseExpression();
                    types.add(e.type);
                }
                if(func.parameters.size() != types.size()){
                    panic("function require " + func.parameters.size() + " arguments, got " + types.size(),line);
                }
                for(int i = 0; i < types.size(); i++){
                    String widened = getWidenedType(func.parameters.get(i), types.get(i));
                    if(!func.parameters.get(i).equals(types.get(i)) && widened == "error"){
                        panic("argument mismatched at index " + (i+1),line);
                    }
                }
                expect(")");
                return new Expr(func.returnType, false);
            }else{
                expect("(");
                ArrayList<String> types = new ArrayList<>();
                Expr e = parseExpression();
                types.add(e.type);
                while(match(",")){
                    e = parseExpression();
                    types.add(e.type);
                }
                if(pfunc.parameters.size() != types.size()){
                    panic("function require " + pfunc.parameters.size() + " arguments, got " + types.size(),line);
                }
                for(int i = 0; i < types.size(); i++){
                    String widened = getWidenedType(pfunc.parameters.get(i), types.get(i));
                    if(!pfunc.parameters.get(i).equals(types.get(i))){
                        panic("argument mismatched at index " + (i+1),line);
                    }
                }
                expect(")");
                return new Expr(pfunc.returnType, false);
            }
        }else if(match("intL")) {
            return new Expr("int",false);
        }else if(match("charL")) {
            return new Expr("char",false);
        }else if(match("realL")) {
            return new Expr("float",false);
        }else if(match("stringL")){
            return new Expr("const char[]",false);
        }else if(match("(")){
            Expr innerT = parseExpression();
            expect(")");
            return new Expr(innerT.type,false);
        }else if(isType(currentToken)){
            String type = parseType(true);
            parseListIdentifierDeclaration(type);
            return new Expr(type,!type.startsWith("const "));
        }else{
            panic("Expected an expression",line);
        }
        return null;
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
                token.getTokenTypeString().equals("struct")||token.getTokenTypeString().equals("const")||token.getTokenTypeString().equals("void");
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

    private static class Expr{
        String type;
        boolean isLvalue;

        Expr(String type, boolean isLvalue){
            this.type = type;
            this.isLvalue = isLvalue;
        }
    }

}
