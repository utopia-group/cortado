package edu.utexas.cs.utopia.cortado.expression.ast.terminal;

import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

public class TrueExpr extends BoolConstExpr
{
    private final static TrueExpr INSTANCE = new TrueExpr();

    private TrueExpr()
    {

    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.TRUE;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        b.append("TRUE");
    }

    public static TrueExpr getInstance()
    {
        return INSTANCE;
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
    public String toString()
    {
        return "TRUE";
    }
}
