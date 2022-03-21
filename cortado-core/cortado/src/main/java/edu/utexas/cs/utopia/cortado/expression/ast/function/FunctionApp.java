package edu.utexas.cs.utopia.cortado.expression.ast.function;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class FunctionApp extends FunctionExpr implements Iterable<Expr>
{
    protected final FunctionDecl decl;

    protected final Expr[] args;

    public FunctionApp(FunctionDecl decl, Expr... args)
    {
        super(decl.getType().getCoDomain());

        FunctionType funcType = decl.getType();
        int funcArity = funcType.getArity();

        if (args.length != funcArity)
            throw new IllegalArgumentException("Provided arguments do not match function declaration");

        ExprType[] domain = funcType.getDomain();
        for (int i = 0; i < funcArity; ++i)
        {
            if (domain[i] != args[i].getType())
                throw new IllegalArgumentException("Provided arguments do not match function declaration");
        }

        this.decl = decl;
        this.args = args;
    }

    public int getArgNum()
    {
        return args.length;
    }

    public Expr argAt(int i)
    {
        return args[i];
    }

    public FunctionDecl getDecl()
    {
        return decl;
    }

    @Override
    public ExprType getType()
    {
        return decl.getType().getCoDomain();
    }

    @Override
    public ExprKind getKind()
    {
        return ExprKind.FAPP;
    }

    @Override
    protected void prettyPrint(StringBuilder b, String indent)
    {
        String opStr = "(" + decl.getName();
        String identIcr = new String(new char[opStr.length()]).replace("\0", " ");

        b.append(opStr);
        prettyPrintExprWithArgs(b, indent + identIcr, args);
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
        return Arrays.stream(args).iterator();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionApp exprs = (FunctionApp) o;
        return decl.equals(exprs.decl) && Arrays.equals(args, exprs.args);
    }

    @Override
    public int hashCode()
    {
        return memoizedHashCode != 0 ? memoizedHashCode : (memoizedHashCode = Objects.hash(decl) + Arrays.hashCode(args));
    }
}
