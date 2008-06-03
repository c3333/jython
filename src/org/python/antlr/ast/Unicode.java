// Autogenerated AST node
package org.python.antlr.ast;
import org.python.antlr.PythonTree;
import org.python.core.PyString;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import java.io.DataOutputStream;
import java.io.IOException;

public class Unicode extends exprType {
    public PyString s;

    public static final String[] _fields = new String[] {"s"};

    public Unicode(Token token, PyString s) {
        super(token);
        this.s = s;
    }

    public Unicode(int ttype, Token token, PyString s) {
        super(ttype, token);
        this.s = s;
    }

    public Unicode(PythonTree tree, PyString s) {
        super(tree);
        this.s = s;
    }

    public String toString() {
        return "Unicode";
    }

    public <R> R accept(VisitorIF<R> visitor) throws Exception {
        return visitor.visitUnicode(this);
    }

    public void traverse(VisitorIF visitor) throws Exception {
    }

    public int getLineno() {
        return getLine();
    }

    public int getCol_offset() {
        return getCharPositionInLine();
    }

}
