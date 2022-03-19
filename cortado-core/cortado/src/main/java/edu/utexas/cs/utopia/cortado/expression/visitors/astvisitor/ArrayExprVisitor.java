package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.array.BoolConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.IntConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.StoreExpr;

public interface ArrayExprVisitor
{
    void visit(BoolConstArrayExpr e);

    void visit(IntConstArrayExpr e);

    void visit(SelectExpr e);

    void visit(StoreExpr e);
}
