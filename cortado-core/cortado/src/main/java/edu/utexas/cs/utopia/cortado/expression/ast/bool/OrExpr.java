package edu.utexas.cs.utopia.cortado.expression.ast.bool;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OrExpr extends BoolExpr implements Iterable<Expr>
{
    private final Set<Expr> args = new HashSet<>();

    private final Expr[] argsAsArray;

    public OrExpr(Expr... args)
    {
        if (args.length < 2)
            throw new IllegalArgumentException("Operator and needs at least two arguments.");

        if (!Stream.of(args)
                   .filter(a -> a.getType() != BOOL_TYPE)
                   .collect(Collectors.toSet())
                   .isEmpty())
            throw new IllegalArgumentException("Non boolean argument for operator and.");

        Collections.addAll(this.args, args);
        this.argsAsArray = this.args.toArray(new Expr[0]);
    }

    public int argNum()
    {
        return args.size();
    }

    public Expr argAt(int i)
    {
        return argsAsArray[i];
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.OR;
    }

    @Override
    public Iterator<Expr> iterator()
    {
        return args.iterator();
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(or ";
        String indentIncr = "    ";
        b.append(opStr);

        prettyPrintExprWithArgs(b, indent + indentIncr, argsAsArray);
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
        OrExpr exprs = (OrExpr) o;
        return args.equals(exprs.args);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(args, this.getClass()));
    }
}
