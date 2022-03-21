package edu.utexas.cs.utopia.cortado.expression.ast.terminal;

import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.math.BigInteger;
import java.util.Objects;

public class IntConstExpr extends TermExpr
{
    private static final IntegerType INT_TY = CachedExprTypeFactory.getInstance().mkIntegerType();

    private final BigInteger val;

    public IntConstExpr(BigInteger val)
    {
        super(INT_TY);

        Objects.requireNonNull(val);

        this.val = val;
    }

    public BigInteger getVal()
    {
        return val;
    }

    @Override
    public IntegerType getType()
    {
        return INT_TY;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.ICONST;
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
        IntConstExpr that = (IntConstExpr) o;
        return val.equals(that.val);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(val));
    }

    @Override
    public String toString()
    {
        return val.toString();
    }
}
