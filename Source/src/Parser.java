package src;

import src.utilities.Token;

import java.io.*;
import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private int currentIndex = 0;
    private Token currentToken;
    private final String fileName;
    private List<String> output = new ArrayList<>();
    private boolean isInsideFunction = false;
    private boolean isInsideStruct = false;
    private boolean isInsideParameterList = false;

    public Parser(List<Token> tokens, String fileName) {
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
        System.err.printf("Parser error in file %s line %d at text %s\nDescription: %s\n",
                currentToken != null ? currentToken.getFileName() : fileName,
                currentToken != null ? currentToken.getLineNumber() : -1,
                currentToken != null ? currentToken.getLexeme() : "<NULL>",
                description);
        System.exit(1);
    }
    private void prepareOutput(Token token, String kind){
        if(token == null){
            return;
        }
        String line = String.format("File %s Line %d: %s %s",
                token.getFileName(), token.getLineNumber(), kind,token.getLexeme());
        output.add(line);
    }
    public void printOutput(){
        String outputFileName = fileName.replaceFirst("\\.[^.]+$", ".parser");
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
                parseStructDefinition();
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
        parseType();
        parseListIdentifierDeclaration();
    };
    private void parseType(){
        match("const");
        if(match("void")||match("int")||match("float")||match("char")){

        }else if(match("struct")){
            expect("identifier");
        }else{
            panic("Expected a valid type");
        }
        match("const");
    }
    private void parseListIdentifierDeclaration(){
        parseIdentifierDeclaration();
        while(match(",")){
            parseIdentifierDeclaration();
        }
    }
    private void parseIdentifierDeclaration(){
        prepareOutput(currentToken, determineVariableScope());
        expect("identifier");
        if(match("[")){
            expect("intL");
            expect("]");
        }
        if(match("=")){
            parseExpression();
        }
    }
    private void parseFunctionDefinition(){
        isInsideFunction = true;
        parseType();
        prepareOutput(currentToken, "function");
        expect("identifier");
        expect("(");
        if(!match(")")){
            parseListParameters();
            expect(")");
        }
        parseStatementBlock();
    }
    private void parseListParameters(){
        isInsideParameterList = true;
        parseParameter();
        while(match(",")){
            parseParameter();
        }
        isInsideParameterList = false;
    }
    private void parseParameter(){
        parseType();
        prepareOutput(currentToken, determineVariableScope());
        expect("identifier");
        if(match("[")){
            expect("]");
        }
    }
    private void parseFunctionPrototype(){
        parseType();
        prepareOutput(currentToken, "function");
        expect("identifier");
        expect("(");
        if(!match(")")){
            parseListParameters();
            expect(")");
        }
        expect(";");
    }
    private void parseStructDefinition(){
        isInsideStruct = true;
        expect("struct");
        prepareOutput(currentToken, isInsideFunction ? "local struct" : "global struct");
        expect("identifier");
        expect("{");
        while(!match("}")){
            parseType();
            parseListIdentifierDeclaration();
            expect(";");
        }
        expect(";");
    }
    private void parseStatementBlock(){
        expect("{");
        if(!match("}")){
            parseStatement();
            while(currentToken != null && !currentToken.getTokenTypeString().equals("}")){
                parseStatement();
            }
            expect("}");
        }
    }
    private void parseStatement() {
        if (currentToken == null) {
            return;
        }

        if (currentToken.getTokenTypeString().equals("{")) {
            parseStatementBlock();
        } else if (match("if")) {
            expect("(");
            parseExpression();
            expect(")");
            parseStatement();
            if (match("else")) {
                parseStatement();
            }
        } else if (match("while")) {
            expect("(");
            parseExpression();
            expect(")");
            parseStatement();
        } else if (match("do")) {
            parseStatement();
            expect("while");
            expect("(");
            parseExpression();
            expect(")");
        } else if (match("for")) {
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
            parseStatement();
        } else if (match("return")) {
            if (!match(";")) {
                parseExpression();
                expect(";");
            }
        } else if (match("break") || match("continue")) {
        } else if(isStructDefinition()){
            parseStructDefinition();
            isInsideStruct = false;
        }else if(isDeclaration()){
            parseDeclaration();
        } else if(match(";")){
        }else {
            parseExpression();
        }
    }
    private void parseExpression(){
        parseAssignmentExpression();
    }
    //The precedence from low to high, 1 to 10
    //Precedence for assignment: 1
    private void parseAssignmentExpression(){
        parseConditionalExpression();
        if(match("=")||match("+=")||match("-=")||match("*=")||match("/=")){
            parseExpression();
        }
    }
    //Precedence for conditional: 2
    private void parseConditionalExpression(){
        parseLogicalOrExpression();
        if(match("?")){
            parseExpression();
            expect(":");
            parseExpression();
        }
    }
    //Precedence for LogicalOr: 3
    private void parseLogicalOrExpression(){
        parseLogicalAndExpression();
        while(match("||")){
            parseLogicalAndExpression();
        }
    }
    //Precedence for LogicalAnd: 4
    private void parseLogicalAndExpression(){
        parseBitwiseOrExpression();
        while(match("&&")){
            parseBitwiseOrExpression();
        }
    }
    //Precedence for BitwiseOr: 5
    private void parseBitwiseOrExpression(){
        parseBitwiseAndExpression();
        while(match("|")){
            parseBitwiseAndExpression();
        }
    }
    //Precedence for BitwiseAnd: 6
    private void parseBitwiseAndExpression(){
        parseEqualityExpression();
        while(match("&")){
            parseEqualityExpression();
        }
    }
    //Precedence for Equality: 7
    private void parseEqualityExpression(){
        parseRelationalExpression();
        while(match("==")||match("!=")){
            parseRelationalExpression();
        }
    }
    //Precedence for Relational: 8
    private void parseRelationalExpression(){
        parseAdditiveExpression();
        while(match("<")||match("<=")||match(">")||match(">=")){
            parseAdditiveExpression();
        }
    }
    //Precedence for Additive: 9
    private void parseAdditiveExpression(){
        parseMultiplicativeExpression();
        while(match("+")||match("-")){
            parseMultiplicativeExpression();
        }
    }
    //Precedence for Multiplicative: 10
    private void parseMultiplicativeExpression(){
        parseUnaryExpression();
        while(match("*")||match("/")||match("%")){
            parseUnaryExpression();
        }
    }
    //Precedence for Unary: 11
    private void parseUnaryExpression(){
        if(match("!")||match("~")||match("-")||match("++")||match("--")){
            parseUnaryExpression();
        }else if(match("(")){
            if(isType(currentToken)){
                parseType();
                expect(")");
                parseUnaryExpression();
            }else{
                parseExpression();
                expect(")");
            }
        }else{
            parsePostfixExpression();
        }
    }
    //Precedence for postfix()[]: 12
    private void parsePostfixExpression(){
        parsePrimaryExpression();
        while(true){
            if(match("++")||match("--")){
            }else if(match("[")){
                parseExpression();
                expect("]");
            }else if(match(".")){
                expect("identifier");
            }else if(match("(")){
                if(!match(")")){
                    parseListExpression();
                    expect(")");
                }
            }else{
                break;
            }
        }
    }
    private void parseListExpression(){
        parseExpression();
        while(match(",")){
            parseExpression();
        }
    }
    private void parseListArgument(){
        parseExpression();
        while(match(",")){
            parseExpression();
        }
    }
    //TOP
    private void parsePrimaryExpression(){
        if(match("identifier")||match("intL")||match("realL")||match("charL")||match("stringL")){
        }else if(match("(")){
            parseExpression();
            expect(")");
        }else{
            panic("Expected an expression");
        }
    }
    private boolean isType(Token token){
        if(token == null){
            return false;
        }
        return token.getTokenTypeString().equals("int")||token.getTokenTypeString().equals("float")||token.getTokenTypeString().equals("char")||
                token.getTokenTypeString().equals("struct")||token.getTokenTypeString().equals("const")||token.getTokenTypeString().equals("void");
    }

}
