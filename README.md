#MyCOMS5400Compiler 

This Compiler serve as a term project for COMS 5400; the goal is to follow the course instruction to build my own C compiler.

### Feature implemented for phase 2
All the Basic Lexer and Extra Features are implemented.

It is capable of converting hex to decimal for reference; check line 213 in Lexer.java. Didn't include in the output because it wasn't mentioned in the project description.

### Feature implemented for phase 3
All the Basic Parser and extra features are implemented.

The parser won't recover from an error; it will exit once it encounters one.

### Feature implemented for phase 4
All the Basic type checks and extra features are implemented.

Once an error is encountered, the program will terminate.

### Feature implemented for phase 5
Should compile without any issue this time, if still encountering issue, there is a file mycc.jar under soruce that pre-compile can be used.

###Feature implemented for phase 6
This is a pre-compiler mycc.jar file; if failed to compile, feel free to use.
### Prerequisites
Make sure you have:
- Java JDK 17
- GNU Make
- LaTeX 

### Compile and Run the program
Make sure you are under the program source code root directory ~/coms5400/Source/
- make # Compile jar file
- make run ARGS="[arguments]" # Run the programs, replace [arguments] with the list of arguments.  
Or run the program by type follow command in the terminal: 
- java -jar mycc.jar [mode] [infile] # Example: -jar mycc.jar -1 [fileName]
- make clean # Clean build files, include lexer file that generates

### Compile Developers.pdf
Make sure you have LaTeX tool installed and configured on your computer and current work directory is ~/coms5400/Documentation.
- make # Compile the pdf file
- make clean # Clean auxiliary files
