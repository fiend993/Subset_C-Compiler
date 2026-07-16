package src.utilities;

import java.util.List;

public class Function {
    public String identifier;
    public String returnType;
    public List<String> parameters; //Type
    public boolean isPrototype;

    public Function(String identifier, String returnType, List<String> parameters, boolean isPrototype) {
        this.identifier = identifier;
        this.returnType = returnType;
        this.parameters = parameters;
        this.isPrototype = isPrototype;
    }
}
