package src.utilities;

public class Variable {
    public String identifier;
    public String type;
    public boolean isArray;
    public boolean isConst;
    public boolean isStruct;
    public int lineNumber;
    public boolean isGlobal = false;
    public int aSize = -1;
    public String aType = null;
    public int jvmSlot = -1;
    public String initialValue;

    public Variable(String identifier, String type, boolean isArray, boolean isConst, boolean isStruct, int lineNumber) {
        this.identifier = identifier;
        this.type = type;
        this.isArray = isArray;
        this.isConst = isConst;
        this.isStruct = isStruct;
        this.lineNumber = lineNumber;
    }
}
