// Autogenerated AST node
package org.python.antlr.ast;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.python.antlr.AST;
import org.python.antlr.PythonTree;
import org.python.antlr.adapter.AstAdapters;
import org.python.antlr.base.excepthandler;
import org.python.antlr.base.expr;
import org.python.antlr.base.mod;
import org.python.antlr.base.slice;
import org.python.antlr.base.stmt;
import org.python.core.ArgParser;
import org.python.core.AstList;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PyType;
import org.python.expose.ExposedGet;
import org.python.expose.ExposedMethod;
import org.python.expose.ExposedNew;
import org.python.expose.ExposedSet;
import org.python.expose.ExposedType;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

@ExposedType(name = "_ast.Slice", base = AST.class)
public class Slice extends slice {
public static final PyType TYPE = PyType.fromClass(Slice.class);
    private expr lower;
    public expr getInternalLower() {
        return lower;
    }
    @ExposedGet(name = "lower")
    public PyObject getLower() {
        return lower;
    }
    @ExposedSet(name = "lower")
    public void setLower(PyObject lower) {
        this.lower = AstAdapters.py2expr(lower);
    }

    private expr upper;
    public expr getInternalUpper() {
        return upper;
    }
    @ExposedGet(name = "upper")
    public PyObject getUpper() {
        return upper;
    }
    @ExposedSet(name = "upper")
    public void setUpper(PyObject upper) {
        this.upper = AstAdapters.py2expr(upper);
    }

    private expr step;
    public expr getInternalStep() {
        return step;
    }
    @ExposedGet(name = "step")
    public PyObject getStep() {
        return step;
    }
    @ExposedSet(name = "step")
    public void setStep(PyObject step) {
        this.step = AstAdapters.py2expr(step);
    }


    private final static PyString[] fields =
    new PyString[] {new PyString("lower"), new PyString("upper"), new PyString("step")};
    @ExposedGet(name = "_fields")
    public PyString[] get_fields() { return fields; }

    private final static PyString[] attributes = new PyString[0];
    @ExposedGet(name = "_attributes")
    public PyString[] get_attributes() { return attributes; }

    public Slice(PyType subType) {
        super(subType);
    }
    public Slice() {
        this(TYPE);
    }
    @ExposedNew
    @ExposedMethod
    public void Slice___init__(PyObject[] args, String[] keywords) {
        ArgParser ap = new ArgParser("Slice", args, keywords, new String[]
            {"lower", "upper", "step"}, 3, true);
        setLower(ap.getPyObject(0, Py.None));
        setUpper(ap.getPyObject(1, Py.None));
        setStep(ap.getPyObject(2, Py.None));
    }

    public Slice(PyObject lower, PyObject upper, PyObject step) {
        setLower(lower);
        setUpper(upper);
        setStep(step);
    }

    public Slice(Token token, expr lower, expr upper, expr step) {
        super(token);
        this.lower = lower;
        addChild(lower);
        this.upper = upper;
        addChild(upper);
        this.step = step;
        addChild(step);
    }

    public Slice(Integer ttype, Token token, expr lower, expr upper, expr step) {
        super(ttype, token);
        this.lower = lower;
        addChild(lower);
        this.upper = upper;
        addChild(upper);
        this.step = step;
        addChild(step);
    }

    public Slice(PythonTree tree, expr lower, expr upper, expr step) {
        super(tree);
        this.lower = lower;
        addChild(lower);
        this.upper = upper;
        addChild(upper);
        this.step = step;
        addChild(step);
    }

    @ExposedGet(name = "repr")
    public String toString() {
        return "Slice";
    }

    public String toStringTree() {
        StringBuffer sb = new StringBuffer("Slice(");
        sb.append("lower=");
        sb.append(dumpThis(lower));
        sb.append(",");
        sb.append("upper=");
        sb.append(dumpThis(upper));
        sb.append(",");
        sb.append("step=");
        sb.append(dumpThis(step));
        sb.append(",");
        sb.append(")");
        return sb.toString();
    }

    public <R> R accept(VisitorIF<R> visitor) throws Exception {
        return visitor.visitSlice(this);
    }

    public void traverse(VisitorIF<?> visitor) throws Exception {
        if (lower != null)
            lower.accept(visitor);
        if (upper != null)
            upper.accept(visitor);
        if (step != null)
            step.accept(visitor);
    }

    public PyObject __dict__;

    @Override
    public PyObject fastGetDict() {
        ensureDict();
        return __dict__;
    }

    @ExposedGet(name = "__dict__")
    public PyObject getDict() {
        return fastGetDict();
    }

    private void ensureDict() {
        if (__dict__ == null) {
            __dict__ = new PyStringMap();
        }
    }

}
