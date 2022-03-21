package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class NegExpr extends  BoolExpr
{
    private final Expr arg;

    public NegExpr(Expr arg)
    {
        if (arg.getType() != BOOL_TYPE)
            throw new IllegalArgumentException("Non boolean argument for operator not");

        this.arg = arg;
    }

    public Expr getArg()
    {
        return arg;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.NEG;
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(neg ";
        String indentIncr = "     ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + indentIncr, arg);

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
        NegExpr negExpr = (NegExpr) o;
        return arg.equals(negExpr.arg);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(arg, this.getClass()));
    }
}
