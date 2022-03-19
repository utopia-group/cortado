package edu.utexas.cs.utopia.cortado.expression.type;

import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeVisitor;

public class StringType implements ExprType
{
    private static final StringType INSTANCE = new StringType();

    private StringType()
    {

    }

    public static StringType getInstance()
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
        return "String";
    }
}
