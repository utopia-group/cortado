package edu.utexas.cs.utopia.cortado.expression.ast.array;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.IntConstExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class IntConstArrayExpr extends ConstArrayExpr
{
    private static final IntegerType INT_TYPE = CachedExprTypeFactory.getInstance().mkIntegerType();

    private final IntConstExpr constExpr;

    private final ArrType arrType;

    public IntConstArrayExpr(ArrType exprTy, IntConstExpr constExpr)
    {
        super(exprTy);

        if (getNestedExprType(exprTy) != INT_TYPE)
            throw new IllegalStateException("array type must have int type as co-domain");

        this.constExpr = constExpr;
        this.arrType = exprTy;
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
    public Expr getConstantExpr()
    {
        return constExpr;
    }

    @Override
    public ArrType getType()
    {
        return arrType;
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.INT_CONST_AR;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        b.append("(const-array ")
         .append(exprTy.toString())
         .append(" ")
         .append(constExpr.toString())
         .append(")");
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntConstArrayExpr that = (IntConstArrayExpr) o;
        return constExpr.equals(that.constExpr) && arrType.equals(that.arrType);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(constExpr, arrType));
    }
}
