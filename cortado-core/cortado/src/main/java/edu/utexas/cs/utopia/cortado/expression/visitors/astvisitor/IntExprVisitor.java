package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;

public interface IntExprVisitor
{
    void visit(DivExpr e);

    void visit(MinusExpr e);

    void visit(ModExpr e);

    void visit(MultExpr e);

    void visit(PlusExpr e);

    void visit(RemExpr e);
}
