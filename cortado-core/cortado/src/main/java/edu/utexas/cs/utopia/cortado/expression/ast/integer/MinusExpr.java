package edu.utexas.cs.utopia.cortado.expression.ast.integer;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MinusExpr extends IntExpr implements Iterable<Expr>
{
    private final Expr[] args;

    public MinusExpr(Expr... args)
    {
        if (args.length < 2)
            throw new IllegalArgumentException("Operator - requires at least two arguments.");

        if (!Stream.of(args)
                   .filter(a -> a.getType() != IntExpr.INTEGER_TYPE)
                   .collect(Collectors.toSet())
                   .isEmpty())
            throw new IllegalArgumentException("Non integer argument for operator -");

        this.args = args;
    }

    public int argNum()
    {
        return args.length;
    }

    public Expr argAt(int i)
    {
        return args[i];
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.MINUS;
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(- ";
        String indentIncr = "   ";
        b.append(opStr);

        prettyPrintExprWithArgs(b, indent + indentIncr, args);
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
    public Iterator<Expr> iterator()
    {
        return Arrays.asList(args).iterator();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MinusExpr exprs = (MinusExpr) o;
        return Arrays.equals(args, exprs.args);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Arrays.hashCode(args) + Objects.hashCode(this.getClass()));
    }
}
