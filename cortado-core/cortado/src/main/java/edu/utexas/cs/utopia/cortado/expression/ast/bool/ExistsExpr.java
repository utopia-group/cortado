package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExistsExpr extends QuantifiedExpr
{
    public ExistsExpr(Expr body, BoundedVar... quantifiers)
    {
        super(body, quantifiers);
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(exist " + String.join(",",  Arrays.stream(quantifiers)
                                                                   .map(Expr::toString)
                                                                   .collect(Collectors.toSet()));
        String indentIncr = new String(new char[opStr.length()]).replace("\0", " ");

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + indentIncr, body);
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.EXISTS;
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
        ExistsExpr that = (ExistsExpr) o;
        return body.equals(that.body) && Arrays.equals(quantifiers, that.quantifiers);
    }

    @Override
    public int hashCode()
    {
        if (memoizedHashCode != 0)
            return memoizedHashCode;

        int result = Objects.hash(body);
        result = 31 * result + Arrays.hashCode(quantifiers);
        return (memoizedHashCode = 31*result + Objects.hashCode(this.getClass()));
    }
}
