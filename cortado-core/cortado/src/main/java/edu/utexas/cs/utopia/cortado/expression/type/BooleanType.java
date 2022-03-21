package edu.utexas.cs.utopia.cortado.expression.type;

import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeVisitor;

public class BooleanType implements ExprType
{
    private static BooleanType INSTANCE = new BooleanType();

    private BooleanType()
    {

    }

    public static BooleanType getInstance()
    {
        return INSTANCE;
    }


    @Override
    public int getArity()
    {
        return 0;
    }

    @Override
    public void accept(ExprTypeVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(ExprTypeRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public String toString()
    {
        return "Bool";
    }
}
