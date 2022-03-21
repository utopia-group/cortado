package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;

public interface IntExprRetVisitor<R>
{
    R visit(DivExpr e);

    R visit(MinusExpr e);

    R visit(ModExpr e);

    R visit(MultExpr e);

    R visit(PlusExpr e);

    R visit(RemExpr e);
}
