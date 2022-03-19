package edu.utexas.cs.utopia.cortado.expression.ast.integer;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class RemExpr extends IntExpr
{
    final private Expr dividend, divisor;

    public RemExpr(Expr dividend, Expr divisor)
    {
        if (dividend.getType() != INTEGER_TYPE || divisor.getType() != INTEGER_TYPE)
            throw new IllegalArgumentException("Non integer argument for operator mod");

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
        return ExprKind.REM;
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(rem ";
        String indentIncr = "     ";

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

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemExpr remExpr = (RemExpr) o;
        return dividend.equals(remExpr.dividend) && divisor.equals(remExpr.divisor);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(dividend, divisor, this.getClass()));
    }
}
