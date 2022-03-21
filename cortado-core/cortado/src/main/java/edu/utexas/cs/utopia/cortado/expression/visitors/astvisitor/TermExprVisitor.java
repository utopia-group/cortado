package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.terminal.*;

public interface TermExprVisitor
{
    void visit(BoolConstExpr e);

    void visit(FalseExpr e);

    void visit(IntConstExpr e);

    void visit(StringConstExpr e);

    void visit(TrueExpr e);
}
