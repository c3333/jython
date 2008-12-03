// Autogenerated AST node
package org.python.antlr.ast;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.python.antlr.AST;
import org.python.antlr.PythonTree;
import org.python.antlr.adapter.AstAdapters;
import org.python.core.AstList;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyType;
import org.python.expose.ExposedGet;
import org.python.expose.ExposedMethod;
import org.python.expose.ExposedNew;
import org.python.expose.ExposedSet;
import org.python.expose.ExposedType;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

@ExposedType(name = "_ast.UnaryOp", base = AST.class)
public class UnaryOp extends exprType {
public static final PyType TYPE = PyType.fromClass(UnaryOp.class);
    private unaryopType op;
    public unaryopType getInternalOp() {
        return op;
    }
    @ExposedGet(name = "op")
    public PyObject getOp() {
        return AstAdapters.unaryop2py(op);
    }
    @ExposedSet(name = "op")
    public void setOp(PyObject op) {
        this.op = AstAdapters.py2unaryop(op);
    }

    private exprType operand;
    public exprType getInternalOperand() {
        return operand;
    }
    @ExposedGet(name = "operand")
    public PyObject getOperand() {
        return operand;
    }
    @ExposedSet(name = "operand")
    public void setOperand(PyObject operand) {
        this.operand = AstAdapters.py2expr(operand);
    }


    private final static PyString[] fields =
    new PyString[] {new PyString("op"), new PyString("operand")};
    @ExposedGet(name = "_fields")
    public PyString[] get_fields() { return fields; }

    private final static PyString[] attributes = new PyString[0];
    @ExposedGet(name = "_attributes")
    public PyString[] get_attributes() { return attributes; }

    public UnaryOp() {
        this(TYPE);
    }
    public UnaryOp(PyType subType) {
        super(subType);
    }
    @ExposedNew
    @ExposedMethod
    public void UnaryOp___init__(PyObject[] args, String[] keywords) {}
    public UnaryOp(PyObject op, PyObject operand) {
        setOp(op);
        setOperand(operand);
    }

    public UnaryOp(Token token, unaryopType op, exprType operand) {
        super(token);
        this.op = op;
        this.operand = operand;
        addChild(operand);
    }

    public UnaryOp(Integer ttype, Token token, unaryopType op, exprType operand) {
        super(ttype, token);
        this.op = op;
        this.operand = operand;
        addChild(operand);
    }

    public UnaryOp(PythonTree tree, unaryopType op, exprType operand) {
        super(tree);
        this.op = op;
        this.operand = operand;
        addChild(operand);
    }

    @ExposedGet(name = "repr")
    public String toString() {
        return "UnaryOp";
    }

    public String toStringTree() {
        StringBuffer sb = new StringBuffer("UnaryOp(");
        sb.append("op=");
        sb.append(dumpThis(op));
        sb.append(",");
        sb.append("operand=");
        sb.append(dumpThis(operand));
        sb.append(",");
        sb.append(")");
        return sb.toString();
    }

    public <R> R accept(VisitorIF<R> visitor) throws Exception {
        return visitor.visitUnaryOp(this);
    }

    public void traverse(VisitorIF visitor) throws Exception {
        if (operand != null)
            operand.accept(visitor);
    }

    private int lineno = -1;
    @ExposedGet(name = "lineno")
    public int getLineno() {
        if (lineno != -1) {
            return lineno;
        }
        return getLine();
    }

    @ExposedSet(name = "lineno")
    public void setLineno(int num) {
        lineno = num;
    }

    private int col_offset = -1;
    @ExposedGet(name = "col_offset")
    public int getCol_offset() {
        if (col_offset != -1) {
            return col_offset;
        }
        return getCharPositionInLine();
    }

    @ExposedSet(name = "col_offset")
    public void setCol_offset(int num) {
        col_offset = num;
    }

}
