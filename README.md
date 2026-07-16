# C Subset Compiler

A compiler for a practical subset of the C programming language, implemented
independently in Java.

The compiler performs lexical analysis, recursive-descent parsing, semantic
analysis, type checking, and Java Assembly code generation. Valid C input is
translated into a `.j` file that can be assembled into JVM bytecode and
executed on the Java Virtual Machine.

## Compiler Pipeline

```text
C source file (.c)
        |
        v
Lexical analysis
        |
        v
Token stream
        |
        v
Recursive-descent parsing
        |
        v
Semantic analysis and type checking
        |
        v
Java Assembly generation (.j)
        |
        v
JVM bytecode assembly (.class)
        |
        v
Execution on the JVM
```

The compiler itself generates the Java Assembly `.j` file. An external
assembler is required to convert that file into an executable `.class` file.

## Features

### Lexical analysis

- Recognizes C keywords, identifiers, operators, and delimiters
- Supports integer, floating-point, hexadecimal, character, and string literals
- Handles single-line and block comments
- Processes local `#include` directives
- Tracks source-file names and line numbers for error reporting
- Detects invalid characters, unclosed comments, invalid includes, and
  oversized literals

### Parsing

- Hand-written recursive-descent parser
- Variable and constant declarations
- Global and local scopes
- Function declarations, definitions, parameters, calls, and return statements
- Arrays and array indexing
- Structures and member access
- Arithmetic, comparison, logical, assignment, and conditional expressions
- Prefix and postfix increment/decrement operations
- Nested statements and block scopes

### Semantic analysis and type checking

- Tracks variables, functions, structures, and nested scopes
- Detects duplicate identifiers and undefined symbols
- Validates function arguments and return types
- Checks assignment compatibility and modifiable lvalues
- Validates array access and structure-member access
- Enforces numeric conditions for branching and loop statements
- Rejects `break` and `continue` outside loops
- Reports source-aware lexer, parser, type-checking, and code-generation errors

### Code generation

- Produces Java Assembly in a `.j` output file
- Generates JVM method descriptors and local-variable slots
- Emits instructions for global and local variables
- Supports function calls and return instructions
- Generates code for:
  - `if` and `if-else`
  - `while`
  - `do-while`
  - `for`
  - `break`
  - `continue`
  - assignments and compound assignments
  - arithmetic and comparison expressions
  - conditional expressions
- Uses generated labels for branches and nested control flow
- Tracks operand-stack and local-variable requirements
- Adds JVM-compatible wrapper and initialization methods

## Example

Given an input file named `example.c`:

```c
int main()
{
    int value;
    value = 3;

    while (value > 0) {
        putint(value);
        value = value - 1;
    }

    return 0;
}
```

Run the compiler in final code-generation mode:

```bash
java -jar mycc.jar -5 example.c
```

The compiler creates:

```text
example.j
```

The `.j` file is written to the same directory as the input `.c` file.

A portion of the generated output will contain JVM-style instructions and
labels similar to:

```text
.class public example
.super java/lang/Object

.method public static main : ()I
.code stack ...
    iconst_3
    istore_0
L1:
    iload_0
    iconst_0
    ...
    ifeq L2
    ...
    goto L1
L2:
    iconst_0
    ireturn
.end code
.end method
```

The exact output depends on the source program and the compiler's generated
labels, stack limits, and local-variable assignments.

## Command-Line Modes

```text
-0  Display compiler version information
-1  Run lexical analysis
-2  Run lexical analysis and parsing
-3  Run semantic analysis and type checking
-4  Generate Java Assembly for expressions
-5  Run the complete pipeline and generate final Java Assembly
```

General usage:

```bash
java -jar mycc.jar <mode> <input-file>
```

Examples:

```bash
java -jar mycc.jar -1 examples/input.c
java -jar mycc.jar -2 examples/input.c
java -jar mycc.jar -3 examples/input.c
java -jar mycc.jar -5 examples/input.c
```

## Building the Compiler

### Requirements

- Java Development Kit 17
- GNU Make

LaTeX is required only when rebuilding the separate developer documentation.

### Build with Make

From the `Source` directory:

```bash
cd Source
make
```

This compiles the Java source and creates:

```text
mycc.jar
```

Run the compiler through the Makefile:

```bash
make run ARGS="-5 path/to/input.c"
```

Clean generated build files:

```bash
make clean
```

### Run the included JAR

A prebuilt `mycc.jar` is included in the `Source` directory.

```bash
cd Source
java -jar mycc.jar -5 path/to/input.c
```

## Project Structure

```text
Subset-C-Compiler/
├── Source/
│   ├── Makefile
│   ├── mycc.jar
│   └── src/
│       ├── Main.java
│       ├── Lexer.java
│       ├── Parser.java
│       ├── TypeChecker2.java
│       ├── TypeChecker.java
│       └── utilities/
│           ├── Token.java
│           ├── Variable.java
│           ├── Function.java
│           ├── Struct.java
│           └── lib440.java
│
├── Documentation/
│   ├── Developers.tex
│   ├── Makefile
│   └── README.md
│
└── README.md
```

## Main Components

### `Main.java`

Provides the command-line entry point and selects the requested compiler stage.

### `Lexer.java`

Reads C source files, identifies tokens, handles comments and include
directives, and reports lexical errors with source locations.

### `Parser.java`

Implements the syntax-analysis stage through recursive-descent parsing.

### `TypeChecker2.java`

Performs semantic analysis and type checking without final code generation.

### `TypeChecker.java`

Combines semantic analysis with Java Assembly generation. It builds the
generated instruction sequence and writes the final `.j` file.

### `utilities/`

Contains the compiler's shared models and runtime support, including tokens,
variables, functions, structures, and course-provided I/O helpers.

## Output Files

Depending on the selected mode, the compiler may produce stage-specific files
such as:

```text
input.lexer
input.parser
input.types
input.j
```

For code-generation modes, the output filename is created by replacing the
input file's extension with `.j`.

For example:

```text
tests/fibonacci.c  ->  tests/fibonacci.j
```

## Error Handling

Errors include the source filename and line number where available.

Examples of detected errors include:

- Unrecognized characters
- Unterminated block comments
- Duplicate declarations
- Undefined identifiers
- Invalid function arguments
- Assignment type mismatches
- Invalid return values
- Non-numeric branch or loop conditions
- `break` or `continue` outside a loop

The current implementation generally stops after encountering an error rather
than attempting full parser recovery.

## Design Highlights

### Hand-written compiler stages

The lexer, parser, semantic analyzer, and code generator were implemented
without a parser-generator framework. This required directly translating the
language grammar into parsing methods and managing token progression,
precedence, and associativity.

### Scoped symbol management

Separate symbol structures track global declarations, local block scopes,
function definitions, structure definitions, and JVM local-variable slots.

### Label-based control-flow generation

Branch and loop statements generate unique JVM labels. Stacks of active
`break` and `continue` targets allow nested loops to resolve control-flow
statements correctly.

### Source-to-JVM type mapping

C subset types are checked during semantic analysis and mapped to JVM
descriptors, load/store instructions, array operations, and return
instructions during code generation.

### Generated-file placement

The compiler derives the `.j` output path directly from the input path, keeping
the generated assembly beside the source program.

## Current Limitations

This is an educational compiler for a subset of C rather than a complete
implementation of the ISO C standard.

Current limitations include:

- Only the implemented subset of C syntax and semantics is supported
- Parser recovery is limited; compilation generally stops after the first error
- The Java Assembly assembler is not bundled with this repository
- Generated programs depend on the included runtime helper for supported I/O
  functions
- The project does not include an optimizer
- The project does not implement a native machine-code backend
- Some C language features and standard-library behavior are outside the
  project's scope

## My Contribution

I independently designed and implemented the complete compiler, including:

- Token definitions and lexical analysis
- Comment and include processing
- Recursive-descent parsing
- Symbol tables and scope management
- Semantic validation and type checking
- Function, array, and structure handling
- Expression parsing and operator precedence
- JVM descriptor and instruction selection
- Branch and loop label generation
- Operand-stack and local-slot tracking
- Java Assembly file generation
- Build configuration and developer documentation

## Project Context

This compiler was developed as an individual semester project for
COMS 4400/5400 at Iowa State University.

The project was delivered incrementally through lexical analysis, parsing,
semantic analysis, expression code generation, and final control-flow code
generation.

## Author

**Charles Lin**

Computer Science graduate interested in Java, backend engineering, compilers,
distributed systems, and software architecture.
