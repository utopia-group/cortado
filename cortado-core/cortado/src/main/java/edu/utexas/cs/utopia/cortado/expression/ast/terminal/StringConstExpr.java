package edu.utexas.cs.utopia.cortado.expression.ast.terminal;

import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class StringConstExpr extends TermExpr
{
    // TODO: Modeling String Constants as regular objects for now. Once we have specific ASTs for String operations, we can switch back to STRING_TYPE
    private static final IntegerType INTEGER_TYPE = CachedExprTypeFactory.getInstance().mkIntegerType();

//    private static final StringType STRING_TYPE = CachedExprTypeFactory.getInstance().mkStringType();

    private final String val;

    public StringConstExpr(String val)
    {
        super(INTEGER_TYPE);

        Objects.requireNonNull(val);

        this.val = val;
    }

    @Override
    public IntegerType getType()
    {
        return INTEGER_TYPE;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.SCONST;
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
        StringConstExpr that = (StringConstExpr) o;
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
        return val;
    }
}
