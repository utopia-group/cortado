package edu.utexas.cs.utopia.cortado.expression.ast.function;

import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class FunctionDecl extends FunctionExpr
{
    private final String name;

    private final FunctionType funcType;

    public FunctionDecl(String name, FunctionType funcType)
    {
        super(funcType);
        this.name = name;
        this.funcType = funcType;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public FunctionType getType()
    {
        return funcType;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.FDECL;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        b.append("(")
         .append(name)
         .append(": ")
         .append(funcType.toString())
         .append(")");
    }

    @Override
    public void accept(ExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(ExprRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FunctionDecl that = (FunctionDecl) o;

        if (!name.equals(that.name)) return false;
        return funcType.equals(that.funcType);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(name, funcType));
    }
}
