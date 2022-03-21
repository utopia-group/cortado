package edu.utexas.cs.utopia.cortado.expression.ast.function;

import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Arrays;
import java.util.Objects;

public class BoundedVar extends FunctionApp
{
    public BoundedVar(FunctionDecl decl)
    {
        super(decl);
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.BVAR;
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
        BoundedVar exprs = (BoundedVar) o;
        return decl.equals(exprs.decl) && Arrays.equals(args, exprs.args);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(decl, args, this.getClass()));
    }
}
