# Variables
JAVAC = javac
JAR = jar
JAVA = java
SRC_DIR = src
BIN_DIR = bin
MAIN_CLASS = src.Main
JAR_FILE = mycc.jar
MANIFEST = manifest.txt
SOURCES = $(wildcard $(SRC_DIR)/*.java)
CLASSES = $(SOURCES:$(SRC_DIR)/%.java=$(BIN_DIR)/%.class)

# Default target: Compile and create JAR
all: jar

# Use 'ARGS' to pass arguments dynamically
ARGS =

# Compile Java files
$(BIN_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(BIN_DIR)
	$(JAVAC) -d $(BIN_DIR) $<

# Create JAR file
jar: $(CLASSES)
	echo "Main-Class: $(MAIN_CLASS)" > $(MANIFEST)
	$(JAR) cfm $(JAR_FILE) $(MANIFEST) -C $(BIN_DIR) .

# Run the JAR file
run: jar
	$(JAVA) -jar $(JAR_FILE) $(ARGS)

# Clean compiled files and JAR
clean:
	rm -rf $(BIN_DIR)/*.class $(JAR_FILE) $(MANIFEST) *.lexer *.parser *.types
