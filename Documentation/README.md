#MyCOMS5400Compiler 

This Compiler serve as term project for COMS 5400, the goal is follow the course instruction to build my own C compiler.

### Feature implemented for phase 2
All the Basic Lexer and Extra Features are implemented.

It capable convert hex into decimal for reference can check line 213 in Lexer.java. Didn't include in the output as it not mention in the project description.

### Feature implemented for phase 3
All the Basic Parser and extra feature are implemented.

The parser won't recover from error, it will exit once encounter an error.

### Feature implemented for phase 4
ALl the Basic type check and extra feature are implement.

Once encounter an error, the program will terminate.

### Feature implemented for phase 5
Should compile without any issue this time, if still encountering issue, this is a file mycc.jar under soruce that pre-compile can be used.

###Feature implemented for phase 6
This is pre-compiler mycc.jar file, if failed to compile, feel free to use.
### Prerequisites
Make sure you have:
- Java JDK 17
- GNU Make
- LaTeX 

### Compile and Run the program
Make sure you are under program source code root directory ~/coms5400/Source/
- make # Compile jar file
- make run ARGS="[arguments]" # Run the programs, replace [arguments] with the list of arguments.  
Or run program by type follow command in terminal: 
- java -jar mycc.jar [mode] [infile] # Example: -jar mycc.jar -1 [fileName]
- make clean # Clean build files include lexer file that generate

### Compile Developers.pdf
Make sure you have LaTeX tool installed and configured on your computer and current work directory is ~/coms5400/Documentation.
- make # Compile the pdf file
- make clean # Clean auxiliary files