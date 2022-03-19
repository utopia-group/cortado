package edu.utexas.cs.utopia.cortado.expression.ast.terminal;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.ExprVisitor;

public abstract class TermExpr extends Expr
{
    public TermExpr(ExprType exprTy)
    {
        super(exprTy);
    }

    @Override
    public void prettyPrint(StringBuilder b, String indent)
    {
        b.append(this.toString());
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
}
