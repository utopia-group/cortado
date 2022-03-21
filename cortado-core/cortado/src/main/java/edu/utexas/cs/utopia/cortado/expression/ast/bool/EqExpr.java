package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class EqExpr extends BoolExpr
{
    private final Expr left;

    private final Expr right;

    public EqExpr(Expr left, Expr right)
    {
        if (!Objects.equals(left.getType(), right.getType()))
            throw new IllegalArgumentException("Incompatible types for operator =");

        this.left = left;
        this.right = right;
    }

    public Expr getLeft()
    {
        return left;
    }

    public Expr getRight()
    {
        return right;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.EQ;
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(= ";
        String indentIncr = "   ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + indentIncr, left, right);
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
        EqExpr eqExpr = (EqExpr) o;
        return (left.equals(eqExpr.left) && right.equals(eqExpr.right)) ||
               (left.equals(eqExpr.right) && right.equals(eqExpr.left));
    }

    @Override
    public int hashCode()
    {
        if (memoizedHashCode != 0)
            return memoizedHashCode;

        return (memoizedHashCode = Objects.hash(left, right) + Objects.hash(right, left) + Objects.hash(this.getClass()));
    }
}
