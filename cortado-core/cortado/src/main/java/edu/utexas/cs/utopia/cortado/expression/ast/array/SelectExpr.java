package edu.utexas.cs.utopia.cortado.expression.ast.array;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Objects;

public class SelectExpr extends ArrayExpr
{
    private final Expr arrayExpr;

    private final Expr indexExpr;

    public SelectExpr(Expr arrayExpr, Expr indexExpr)
    {
        super(null);
        super.exprTy = getNestedExprType(arrayExpr);

        ArrType arrType = getArrayTypeFromApp(arrayExpr);
        ExprType idxType = arrType.getDomain()[0];

        if (indexExpr.getType() != idxType)
            throw new IllegalArgumentException("invalid index expression for select");

        this.arrayExpr = arrayExpr;
        this.indexExpr = indexExpr;
    }

    public Expr getArrayExpr()
    {
        return arrayExpr;
    }

    public Expr getIndexExpr()
    {
        return indexExpr;
    }

    @Override
    public ExprType getType()
    {
        return getNestedExprType(arrayExpr);
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.AR_SELECT;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(select ";
        String identIncr = "        ";

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + identIncr, arrayExpr, indexExpr);
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
        SelectExpr that = (SelectExpr) o;
        return arrayExpr.equals(that.arrayExpr) && indexExpr.equals(that.indexExpr);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(arrayExpr, indexExpr));
    }
}
