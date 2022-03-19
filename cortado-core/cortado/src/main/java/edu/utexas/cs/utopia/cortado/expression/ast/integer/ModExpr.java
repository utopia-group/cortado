package edu.utexas.cs.utopia.cortado.expression.ast.integer;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class ModExpr extends IntExpr
{
    final private Expr dividend, divisor;

    public ModExpr(Expr dividend, Expr divisor)
    {
        if (dividend.getType() != IntExpr.INTEGER_TYPE || divisor.getType() != IntExpr.INTEGER_TYPE)
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
        return ExprKind.MINUS;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(mod ";
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
        ModExpr modExpr = (ModExpr) o;
        return dividend.equals(modExpr.dividend) && divisor.equals(modExpr.divisor);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(dividend, divisor, this.getClass()));
    }
}
