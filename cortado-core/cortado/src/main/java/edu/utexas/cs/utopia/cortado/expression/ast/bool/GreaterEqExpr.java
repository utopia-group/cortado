package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class GreaterEqExpr extends BoolExpr
{
    private static final IntegerType INT_TY = CachedExprTypeFactory.getInstance().mkIntegerType();

    private final Expr left;

    private final Expr right;

    public GreaterEqExpr(Expr left, Expr right)
    {
        if(left.getType() != INT_TY || right.getType() != INT_TY)
            throw new IllegalArgumentException("Non-integer argument for operator >=");

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
        return ExprKind.GEQ;
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(>= ";
        String incrIndent = "    ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + incrIndent, left, right);
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
        GreaterEqExpr gEqExpr = (GreaterEqExpr) o;
        return left.equals(gEqExpr.left) && right.equals(gEqExpr.right);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(left, right, this.getClass()));
    }
}
