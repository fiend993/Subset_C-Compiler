package src.utilities;

import java.util.Map;

public class Struct {
    public String name;
    public String identifier;
    public Map<String,Variable> membersVariables;
    public Map<String,Struct> membersStruct;

    public Struct(String name, Map<String,Variable> membersVariables,Map<String,Struct> membersStruct) {
        this.name = name;
        this.membersVariables = membersVariables;
        this.membersStruct = membersStruct;
    }
}
