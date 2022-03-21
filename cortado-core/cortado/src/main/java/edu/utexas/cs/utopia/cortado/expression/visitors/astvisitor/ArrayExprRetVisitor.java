package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.array.BoolConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.IntConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.StoreExpr;

public interface ArrayExprRetVisitor<R>
{
    R visit(BoolConstArrayExpr e);

    R visit(IntConstArrayExpr e);

    R visit(SelectExpr e);

    R visit(StoreExpr e);
}
