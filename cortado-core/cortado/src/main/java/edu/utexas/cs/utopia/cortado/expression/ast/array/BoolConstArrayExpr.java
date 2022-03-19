package edu.utexas.cs.utopia.cortado.expression.ast.array;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.BoolConstExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.BooleanType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class BoolConstArrayExpr extends ConstArrayExpr
{
    private static final BooleanType BOOL_TYPE = CachedExprTypeFactory.getInstance().mkBooleanType();

    private final BoolConstExpr constExpr;

    private final ArrType arrType;

    public BoolConstArrayExpr(ArrType exprTy, BoolConstExpr constExpr)
    {
        super(exprTy);

        if (getNestedExprType(exprTy) != BOOL_TYPE)
            throw new IllegalArgumentException("array type must have bool type as co-domain");

        this.constExpr = constExpr;
        this.arrType = exprTy;
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
        return ExprKind.BOOL_CONST_AR;
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
        BoolConstArrayExpr that = (BoolConstArrayExpr) o;
        return constExpr.equals(that.constExpr) && arrType.equals(that.arrType);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(constExpr, arrType));
    }
}
