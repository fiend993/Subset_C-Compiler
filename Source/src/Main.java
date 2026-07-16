package src;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("-0")) {
            System.out.println(
                    "My own C compiler for COMS 4400/5400, Spring\n"+
                    "Written by Charles(Chichang) Lin (clin@iastate.edu)\n"+
                    "Version 0.01, released 31 Jan, 2025\n"
                    );
        }else if (args.length == 2 && args[0].equals("-1")) {
            Lexer lexer = new Lexer(args[1]);
            lexer.scanFile();
        }else if(args.length == 2 && args[0].equals("-2")) {
            Lexer lexer = new Lexer(args[1]);
            lexer.scanFile();
            System.out.println("\n\n\n***Tokenize complete, now process parse...***\n\n\n");
            Parser parser = new Parser(lexer.getTokens(),args[1]);
            parser.parseProgram();
            parser.printOutput();
        }else if(args.length == 2 && args[0].equals("-3")) {
            Lexer lexer = new Lexer(args[1]);
            lexer.scanFile();
            System.out.println("\n\n\n***Tokenize complete, now process Type checking...***\n\n\n");
            String outputFileName = args[1].replaceFirst("\\.[^.]+$", ".lexer");
            File lexerFile = new File(outputFileName);
            lexerFile.delete();
            TypeChecker2 checker = new TypeChecker2(lexer.getTokens(),args[1]);
            checker.parseProgram();
            checker.printOutput();
        }else if(args.length == 2 && (args[0].equals("-4") || args[0].equals("-5"))) {
            Lexer lexer = new Lexer(args[1]);
            String outputFileName = args[1].replaceFirst("\\.[^.]+$", ".lexer");
            lexer.scanFile();
            File lexerFile = new File(outputFileName);
            lexerFile.delete();
            System.out.println("\n\n\n***Tokenize complete, now Generating code...***\n\n\n");
            TypeChecker checker = new TypeChecker(lexer.getTokens(),args[1]);
            checker.parseProgram();
            checker.printOutput();
        }else{
            System.out.println(
                    "Usage: mycc -mode infile\n" +
                    "Valid modes:\n" +
                    "\t-0: version information only\n" +
                    "\t-1: Tokenization\n" +
                    "\t-3: Parser\n" +
                    "\t-4: TypeChecker\n"
                    );
        }
    }
}
