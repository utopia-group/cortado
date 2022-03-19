package edu.utexas.cs.utopia.cortado.expression.ast.terminal;

import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.BooleanType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

public abstract class BoolConstExpr extends BoolExpr
{
    private final static BooleanType BOOL_TY = CachedExprTypeFactory.getInstance().mkBooleanType();

//    public BoolConstExpr()
//    {
//        super(BOOL_TY);
//    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.BCONST;
    }

    @Override
    public BooleanType getType()
    {
        return BOOL_TY;
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
