package edu.utexas.cs.utopia.cortado.expression.ast.integer;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

public abstract class IntExpr extends Expr
{
    protected static final IntegerType INTEGER_TYPE = CachedExprTypeFactory.getInstance().mkIntegerType();

    public IntExpr()
    {
        super(INTEGER_TYPE);
    }

    @Override
    public ExprType getType()
    {
        return INTEGER_TYPE;
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
