package src;
import src.utilities.Token;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Lexer {
    private static final Map<String, Integer> TOKEN_VALUE = new HashMap<>();
    private static final Map<Integer,String> TOKEN_KEY = new HashMap<>();

    static {
        //Operator with two characters
        TOKEN_VALUE.put("==", 351);
        TOKEN_VALUE.put("!=", 352);
        TOKEN_VALUE.put(">=", 353);
        TOKEN_VALUE.put("<=", 354);
        TOKEN_VALUE.put("++", 355);
        TOKEN_VALUE.put("--", 356);
        TOKEN_VALUE.put("||", 357);
        TOKEN_VALUE.put("&&", 358);
        TOKEN_VALUE.put("+=", 361);
        TOKEN_VALUE.put("-=", 362);
        TOKEN_VALUE.put("*=", 363);
        TOKEN_VALUE.put("/=", 364);
        //Keywords
        TOKEN_VALUE.put("const", 401);
        TOKEN_VALUE.put("struct", 402);
        TOKEN_VALUE.put("for", 403);
        TOKEN_VALUE.put("while", 404);
        TOKEN_VALUE.put("do", 405);
        TOKEN_VALUE.put("if", 406);
        TOKEN_VALUE.put("else", 407);
        TOKEN_VALUE.put("break", 408);
        TOKEN_VALUE.put("continue", 409);
        TOKEN_VALUE.put("return", 410);
        TOKEN_VALUE.put("switch", 411);
        TOKEN_VALUE.put("case", 412);
        TOKEN_VALUE.put("default", 413);
        //Type
        TOKEN_VALUE.put("void", 301);
        TOKEN_VALUE.put("char", 301);
        TOKEN_VALUE.put("int", 301);
        TOKEN_VALUE.put("float", 301);
        //TOKEN_KEY
        for(Map.Entry<String, Integer> entry : TOKEN_VALUE.entrySet()){
            if(entry.getValue()!=301){
                TOKEN_KEY.put(entry.getValue(), entry.getKey());
            }
        }
        TOKEN_KEY.put(302, "charL");
        TOKEN_KEY.put(303, "intL");
        TOKEN_KEY.put(304, "realL");
        TOKEN_KEY.put(305, "stringL");
        TOKEN_KEY.put(306, "identifier");
        TOKEN_KEY.put(307, "hexL");
    }

    private static List<Token> tokens = new ArrayList<>();
    private String fileName;
    private static String rootFileName;
    /*
     * Track if in the block comment mode
     */
    private boolean inBlockComment = false;
    private int blockCommentDepth = 0;
    /*
     * Flag if encounter error and notify scanner
     */
    private static boolean encounterError = false;
    private static String errorMessage = "";
    /*
     * length limitation of literal
     */
    private static final int INTEGER_REAL_IDENTIFIER_LIMIT = 48;
    private static final int STRING_LIMIT = 1024;
    /*
     * depth limit for include
     */
    private static int includeDepth = 0;
    private static final int INCLUDE_DEPTH_LIMIT = 256;

    public Lexer(String fileName) {
        this.fileName = fileName;
        this.rootFileName = fileName;
    }

    public Lexer(String fileName, String rootFileName) {
        this.fileName = fileName;
        this.rootFileName = rootFileName;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void scanFile(){
        String outputFileName = rootFileName.replaceFirst("\\.[^.]+$", ".lexer");
        int lineNumber = 1;
        try(BufferedReader reader = new BufferedReader(new FileReader(fileName));
            PrintWriter writer = new PrintWriter(new FileWriter(outputFileName, true))){
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, lineNumber, writer);
                lineNumber++;
                if(encounterError){
                    throw new RuntimeException("encounter error");
                }
            }
            if(inBlockComment){
                int commentStart = lineNumber - blockCommentDepth + 1;
                encounterError = true;
                errorMessage = String.format("Lexer error in file %s line %d at text /*\n" +
                        "Description: \n" +
                        "Comment never closed.", fileName, commentStart);
                throw new RuntimeException("comment never closed");
            }
        }catch(IOException e){
            errorMessage =  String.format("Lexer error in file %s line %d at text %s\n" +
                    "Description: \n" +
                    "Failed to open file or %s.", fileName, lineNumber, fileName, e.getMessage());
            if(includeDepth != 0){
                errorMessage =  String.format("Lexer error in file %s line %d at text %s\n" +
                        "Description: \n" +
                        "Failed to open include file or %s.", fileName, lineNumber, fileName, e.getMessage());
            }
            File file = new File(outputFileName);
            file.delete();
            System.err.println(errorMessage);
        }catch (RuntimeException e){
            File file = new File(outputFileName);
            file.delete();
            System.err.println(errorMessage);
        }
    }

    private void processLine(String line, int lineNumber, PrintWriter writer){
        int index = 0;
        line = line.stripLeading();
        line = line.stripTrailing();
        if(line.startsWith("#",index)){
            String include = line.replaceAll("\\s+","");
            index++;
            if(include.startsWith("include",index)){
                index += 8;
                include = include.substring(index,include.length()-1);
                includeDepth++;
                if(includeDepth >= INCLUDE_DEPTH_LIMIT){
                    encounterError = true;
                    errorMessage = String.format("Lexer error in file %s line %d at text #include" + include
                            + "\nDescription:\nInclude exceeds limit 256", fileName, lineNumber);
                    return;
                }
                Lexer includeLexer = new Lexer(include,rootFileName);
                includeLexer.scanFile();
                includeDepth--;
                if(encounterError){
                    errorMessage = String.format("Lexer error in file %s line %d at text #include \"" + include + "\"\nDescription:\nEncounter error when process included file.", fileName, lineNumber);
                    return;
                }
            }else{
                encounterError = true;
                errorMessage = String.format("Lexer error in file %s line %d at text %s"
                        + "\nDescription:\nInvalid include statement.", fileName, lineNumber, line);
                return;
            }
            return;
        }
        int commentIndex = line.indexOf("//");
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex).stripTrailing();;
        }
        int blockCommentStart = line.indexOf("/*");
        boolean withValidCode = true;
        if (blockCommentStart != -1) {
            int blockCommentEnd = line.indexOf("*/", blockCommentStart + 2);
            if (blockCommentEnd != -1) {
                line = line.substring(0, blockCommentStart).stripTrailing();
            } else {
                line = line.substring(0, blockCommentStart).stripTrailing();
                inBlockComment = true;
                blockCommentDepth++;
                withValidCode = false;
            }
        }
        while (index < line.length()){
            if(!inBlockComment && withValidCode){
                //detect the start of a block comment
                if(line.startsWith("/*", index)){
                    inBlockComment = true;
                    index += 2;
                    blockCommentDepth ++;
                    continue;
                }

                if(line.startsWith("*/", index)){
                    encounterError = true;
                    errorMessage = String.format("Lexer error in file %s line %d at text */\n" +
                            "encounter */ before a block comment start", fileName, lineNumber);
                    return;
                }

//                //detect single-line comment
//                if(line.startsWith("//", index)){
//                    break;
//                }

                Pattern pattern = Pattern.compile(
                        "(\"([^\"\\\\]|\\\\.)*\")|"+ //string
                                "(\\bconst\\b|\\bstruct\\b|\\bfor\\b|\\bwhile\\b|\\bdo\\b)|" + //keyword
                                "(\\bif\\b|\\belse\\b|\\bbreak\\b|\\bcontinue\\b)|" + //keyword
                                "(\\breturn\\b|\\bswitch\\b|\\bcase\\b|\\bdefault\\b)|" + //keyword
                                "(\\bvoid\\b|\\bchar\\b|\\bint\\b|\\bfloat\\b)|" + //type
                                "(==|!=|>=|<=|\\+\\+|--|\\|\\||&&|\\+=|-=|\\*=|/=)|" + //Operators with two characters
                                "('(\\\\.|[^\\\\'])')|" + //char
                                "(0x[0-9a-fA-F]+|0X[0-9a-fA-F]+)|"+ //Hex number
                                "((\\d+[eE][+\\-]?\\d+)|(\\d+\\.(([eE][+\\-]?\\d+)|(\\d+([eE][+\\-]?\\d+)?)))|" + //Real number
                                "(\\.(([eE][+\\-]?\\d+)|(\\d+([eE][+\\-]?\\d+)?)))|(\\d+\\.))|" + //Real number
                                "(\\d+)|" + //integer
                                "([a-zA-Z_]\\w*)|" + //identifier
                                "(!|%|&|\\(|\\)|\\*|\\+|,|-|\\.|/|:|;|<|=|>|\\?|\\[|]|\\{|}|\\||~)" //Operators with single characters

                );

                Matcher matcher = pattern.matcher(line);

                int lastIndex = 0;
                while(matcher.find()){
                    String lexeme = matcher.group();
                    int tokenType = getTokenType(lexeme);
                    String tokenTypeS ="";
                    if(tokenType == 301){
                        tokenTypeS = lexeme;
                    }else{
                        tokenTypeS = getTokenTypeString(tokenType);
                    }
                    Token current = new Token(fileName,lineNumber,tokenType,lexeme,tokenTypeS);
                    if(tokenType == 303||tokenType == 304||tokenType == 306) {
                        if (lexeme.length() > INTEGER_REAL_IDENTIFIER_LIMIT){
                            encounterError = true;
                            errorMessage = String.format("Lexer error in file %s line %d at text %s\nDescription:\nExceeded Length limit 48",fileName, lineNumber, lexeme);
                            return;
                        }
                    }
                    if(tokenType == 305){
                        if(lexeme.length() > STRING_LIMIT){
                            encounterError = true;
                            errorMessage = String.format("Lexer error in file %s line %d at text %s\nDescription:\nExceeded Length limit 1024",fileName, lineNumber, lexeme);
                            return;
                        }
                    }
                    if(tokenType == 307){
                        //@TODO hex convert
                        String hex = lexeme.substring(2);
                        int decimalValue = Integer.parseInt(hex, 16);
                        tokens.add(current);
                        System.out.println(current);
                        writer.println(current);
                    }else{
                        tokens.add(current);
                        lastIndex = matcher.end();
                        System.out.println(current);
                        writer.println(current);
                    }
                    index = matcher.end();
                }
                if(lastIndex < line.length()){
                    encounterError = true;
                    errorMessage = String.format("Lexer error in file %s line %d at text %c\nDescription:\nUnrecognized Character: %c at position %d.",
                            fileName, lineNumber, line.charAt(lastIndex), line.charAt(lastIndex), lastIndex);
                    return;
                }
            }else{
                if(line.startsWith("*/", index)){
                    inBlockComment = false;
                    blockCommentDepth = 0;
                    index += 2;
                    continue;
                }
                if(line.startsWith("/*", index)){
                    encounterError = true;
                    errorMessage = String.format("Lexer error in file %s line %d at text /*\nDescription:\nNested loop not allowed.", fileName, lineNumber);
                    return;
                }
            }
            index++;
        }
        if(inBlockComment){
            blockCommentDepth++;
        }
    }

    private int getTokenType(String lexeme) {
        if (TOKEN_VALUE.containsKey(lexeme)) {
            return TOKEN_VALUE.get(lexeme);
        }else if(lexeme.matches("\"([^\"\\\\]|\\\\.)*\"")){ //String literal
            return 305;
        }else if(lexeme.matches("'(\\\\.|[^\\\\'])'")){ //character literal
            return 302;
        }else if(lexeme.matches("0x[0-9a-fA-F]+|0X[0-9a-fA-F]+")){ //Hexadecimal number
            return 307;
        }else if(lexeme.matches("(\\d+[eE][+\\-]?\\d+)|(\\d+\\.(([eE][+\\-]?\\d+)|(\\d+([eE][+\\-]?\\d+)?)))|(\\.(([eE][+\\-]?\\d+)|(\\d+([eE][+\\-]?\\d+)?)))|(\\d+\\.)")){  //Real literal
            return 304;
        }else if (lexeme.matches("\\d+")){ //Integer literal
            return 303;
        }else if(lexeme.matches("[a-zA-Z_]\\w*")){  //identifier
            return 306;
        }else{
            return lexeme.charAt(0);
        }
    }

    private String getTokenTypeString(int tokenType){
        if(TOKEN_KEY.containsKey(tokenType)){
            return TOKEN_KEY.get(tokenType);
        }else{
            return ""+(char)tokenType;
        }
    }
}
