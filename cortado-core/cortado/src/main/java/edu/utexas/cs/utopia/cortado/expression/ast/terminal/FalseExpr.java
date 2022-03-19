package edu.utexas.cs.utopia.cortado.expression.ast.terminal;

import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

public class FalseExpr extends BoolConstExpr
{
    private static final FalseExpr INSTANCE = new FalseExpr();

    private FalseExpr()
    {

    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.FALSE;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        b.append("FALSE");
    }

    public static FalseExpr getInstance()
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
        return "FALSE";
    }
}
