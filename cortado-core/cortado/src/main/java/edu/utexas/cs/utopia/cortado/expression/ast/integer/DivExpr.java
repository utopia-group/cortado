package edu.utexas.cs.utopia.cortado.expression.ast.integer;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class DivExpr extends IntExpr
{
    private static final IntegerType INTEGER_TYPE = CachedExprTypeFactory.getInstance().mkIntegerType();

    private final Expr dividend, divisor;

    public DivExpr(Expr dividend, Expr divisor)
    {
        if (dividend.getType() != INTEGER_TYPE || divisor.getType() != INTEGER_TYPE)
            throw new IllegalArgumentException("Non integer argument for operator /");

        this.dividend = dividend;
        this.divisor = divisor;
    }

    public Expr getDividend()
    {
        return dividend;
    }

    public Expr getDivisor()
    {
        return divisor;
    }


    @Override
    public ExprKind getKind()
    {
        return ExprKind.DIV;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DivExpr divExpr = (DivExpr) o;
        return dividend.equals(divExpr.dividend) && divisor.equals(divExpr.divisor);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(dividend, divisor, this.getClass()));
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(/ ";
        String indentIncr = "   ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + indentIncr, dividend, divisor);
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
}
