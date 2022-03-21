package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Arrays;
import java.util.Iterator;

public abstract class QuantifiedExpr extends BoolExpr implements Iterable<BoundedVar>
{
    protected final Expr body;

    protected final BoundedVar[] quantifiers;

    public QuantifiedExpr(Expr body, BoundedVar... quantifiers)
    {
        this.body = body;
        this.quantifiers = quantifiers;
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
    public Iterator<BoundedVar> iterator()
    {
        return Arrays.stream(quantifiers).iterator();
    }

    public int quantifierNum()
    {
        return quantifiers.length;
    }

    public BoundedVar boundedVarAr(int i)
    {
        return quantifiers[i];
    }

    public Expr getBody()
    {
        return body;
    }
}
