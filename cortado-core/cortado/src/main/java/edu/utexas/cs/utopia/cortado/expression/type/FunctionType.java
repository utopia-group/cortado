package edu.utexas.cs.utopia.cortado.expression.type;

import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeVisitor;

import java.util.Arrays;
import java.util.Objects;

public class FunctionType implements ExprType
{
    final protected ExprType[] domain;

    final protected ExprType coDomain;

    public FunctionType(ExprType[] domain, ExprType coDomain)
    {
//        if (domain == null || domain.length == 0)
//            throw new IllegalArgumentException("Domain of function cannot be empty");
        if (!Objects.nonNull(coDomain))
            throw new IllegalArgumentException("Co-domain of function cannot be null");

        this.domain = domain;
        this.coDomain = coDomain;
    }

    public ExprType[] getDomain()
    {
        return domain;
    }

    public ExprType getCoDomain()
    {
        return coDomain;
    }

    @Override
    public int getArity()
    {
        return domain.length;
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
        StringBuilder rv = new StringBuilder("(");
        for (int i = 0; i < domain.length; i++)
        {
            ExprType ty = domain[i];
            rv.append(ty.toString()).append(i == domain.length - 1 ? "" : " x ");
        }

        rv.append(") -> ").append(coDomain.toString());
        return rv.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionType that = (FunctionType) o;
        return Arrays.equals(domain, that.domain) && coDomain.equals(that.coDomain);
    }

    @Override
    public int hashCode()
    {
        int result = Objects.hash(coDomain);
        result = 31 * result + Objects.hashCode(this.getClass());
        result = 31 * result + Arrays.hashCode(domain);
        return result;
    }
}
