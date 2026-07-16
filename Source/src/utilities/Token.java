package src.utilities;

public class Token {
    private String fileName;
    private int lineNumber;
    private int tokenType;
    private String lexeme;
    private String tokenTypeString;

    public Token(String fileName, int lineNumber, int tokenType, String lexeme,String tokenTypeString) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.tokenType = tokenType;
        this.lexeme = lexeme;
        this.tokenTypeString = tokenTypeString;
    }

    public String getLexeme(){
        return lexeme;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getTokenType() {
        return tokenType;
    }

    public String getTokenTypeString() {
        return tokenTypeString;
    }

    @Override
    public String toString() {
        return String.format("File %s Line %d Token %d Text %s", fileName, lineNumber, tokenType, lexeme);
    }
}
