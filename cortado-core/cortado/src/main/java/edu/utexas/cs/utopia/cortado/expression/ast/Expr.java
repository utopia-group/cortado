package edu.utexas.cs.utopia.cortado.expression.ast;

import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

public abstract class Expr
{
    // TODO: We can also optimize the equals methods. We just need to compare children expressions with operator ==, no need to call equals method.
    protected volatile int memoizedHashCode = 0;

    protected ExprType exprTy;

    public Expr(ExprType exprTy)
    {
        this.exprTy = exprTy;
    }

    public abstract ExprType getType();

    public abstract ExprKind getKind();

    protected abstract void prettyPrint(StringBuilder b, String indent);

    protected void prettyPrintExprWithArgs(StringBuilder b, String indent, Expr... args)
    {
        if (args.length > 0)
        {
            args[0].prettyPrint(b, indent);

            for (int i = 1; i < args.length; ++i)
            {
                Expr e = args[i];
                b.append("\n").append(indent);
                e.prettyPrint(b, indent);
            }
        }

        b.append(")");
    }

    @Override
    public String toString()
    {
        StringBuilder b = new StringBuilder();
        this.prettyPrint(b, "");
        return b.toString();
    }

    public void accept(ExprVisitor v)
    {
        v.visit(this);
    }

    public <R> R accept(ExprRetVisitor<R> v)
    {
        return v.visit(this);
    }

    public enum ExprKind
    {
        // Array ExprKind
        AR_SELECT, AR_STORE,
        INT_CONST_AR, BOOL_CONST_AR,
        // Bool ExprKind
        AND, EQ, EXISTS, FOR_ALL, GEQ,
        GT, IMPL, LEQ, LT, NEG, OR,
        // Function ExprKind
        BVAR, FAPP, FDECL,
        // Integer ExprKind
        DIV, MINUS, MOD, MULT, PLUS, REM,
        // Term ExprKind
        BCONST, FALSE, ICONST, SCONST, TRUE
    }
}
