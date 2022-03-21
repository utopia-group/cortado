package edu.utexas.cs.utopia.cortado.expression.ast.array;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class StoreExpr extends ArrayExpr
{
    private final Expr arrayExpr;

    private final Expr indexExpr;

    private final Expr newVal;

    public StoreExpr(Expr arrayExpr, Expr indexExpr, Expr newVal)
    {
        super(arrayExpr.getType());

        ArrType arrType = getArrayTypeFromApp(arrayExpr);
        ExprType idxExprType = arrType.getDomain()[0];

        if (!idxExprType.equals(indexExpr.getType()))
            throw new IllegalArgumentException("Illegal index expression for store expression");

        ExprType nestedExprType = getNestedExprType(arrayExpr);

        if (!Objects.equals(nestedExprType, newVal.getType()))
            throw new IllegalArgumentException("Illegal update value for store expression");

        this.arrayExpr = arrayExpr;
        this.indexExpr = indexExpr;
        this.newVal = newVal;
    }

    public Expr getArrayExpr()
    {
        return arrayExpr;
    }

    public Expr getIndexExpr()
    {
        return indexExpr;
    }

    public Expr getNewVal()
    {
        return newVal;
    }

    @Override
    public ExprType getType()
    {
        return arrayExpr.getType();
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.AR_STORE;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(store ";
        String identIncr = "       ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + identIncr, arrayExpr, indexExpr, newVal);
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
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoreExpr storeExpr = (StoreExpr) o;
        return arrayExpr.equals(storeExpr.arrayExpr) && indexExpr.equals(storeExpr.indexExpr) && newVal.equals(storeExpr.newVal);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(arrayExpr, indexExpr, newVal));
    }
}
