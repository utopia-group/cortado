package edu.utexas.cs.utopia.cortado.expression.type;

import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeVisitor;

public class ArrType extends FunctionType
{
    public ArrType(ExprType[] domain, ExprType coDomain)
    {
        super(domain, coDomain);
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
        StringBuilder rv = new StringBuilder("[");
        for (int i = 0; i < domain.length; i++)
        {
            ExprType ty = domain[i];
            rv.append(ty.toString()).append(i == domain.length - 1 ? "" : " x ");
        }

        rv.append("] -> ").append(coDomain.toString());
        return rv.toString();
    }
}
