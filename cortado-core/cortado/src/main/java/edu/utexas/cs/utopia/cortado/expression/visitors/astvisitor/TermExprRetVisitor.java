package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.terminal.*;

public interface TermExprRetVisitor<R>
{
    R visit(BoolConstExpr e);

    R visit(FalseExpr e);

    R visit(IntConstExpr e);

    R visit(StringConstExpr e);

    R visit(TrueExpr e);
}
