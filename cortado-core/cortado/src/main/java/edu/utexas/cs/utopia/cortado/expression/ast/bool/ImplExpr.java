package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class ImplExpr extends BoolExpr
{
    private final Expr antecedent;

    private final Expr consequent;

    public ImplExpr(Expr antecedent, Expr consequent)
    {
        if (antecedent.getType() != BOOL_TYPE || consequent.getType() != BOOL_TYPE)
            throw new IllegalArgumentException("Non boolean argument for operator ->");

        this.antecedent = antecedent;
        this.consequent = consequent;
    }

    public Expr getAntecedent()
    {
        return antecedent;
    }

    public Expr getConsequent()
    {
        return consequent;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.IMPL;
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(--> ";
        String indentIncr = "     ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + indentIncr, antecedent, consequent);
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
        ImplExpr implExpr = (ImplExpr) o;
        return antecedent.equals(implExpr.antecedent) && consequent.equals(implExpr.consequent);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(antecedent, consequent, this.getClass()));
    }
}
