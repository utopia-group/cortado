package edu.utexas.cs.utopia.cortado.expression.ast.array;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.typefactory.ExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Arrays;

public abstract class ArrayExpr extends Expr
{
    private static final ExprTypeFactory exprTypeFactory = CachedExprTypeFactory.getInstance();

    public ArrayExpr(ExprType exprTy)
    {
        super(exprTy);
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

    protected ArrType getArrayTypeFromApp(Expr arrayExpr)
    {
        ExprType exprType = arrayExpr.getType();

        if (!(exprType instanceof ArrType))
            throw new IllegalArgumentException(arrayExpr + " is not an array expression");

        return (ArrType) exprType;
    }

    protected ExprType getNestedExprType(Expr arrayExpr)
    {
        return getNestedExprType(getArrayTypeFromApp(arrayExpr));
    }

    protected ExprType getNestedExprType(ArrType arrType)
    {
        ExprType[] domain = arrType.getDomain();
        ExprType coDomain = arrType.getCoDomain();

        return domain.length == 1 ? coDomain : exprTypeFactory.mkArrayType(Arrays.copyOfRange(domain, 1, domain.length), coDomain);
    }
}
