package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.ArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.TermExpr;

public interface ExprRetVisitor<R> extends ArrayExprRetVisitor<R>,
                                           BoolExprRetVisitor<R>,
                                           FuncExprRetVisitor<R>,
                                           IntExprRetVisitor<R>,
                                           TermExprRetVisitor<R>
{
    R visit(ArrayExpr e);

    R visit(BoolExpr e);

    R visit(FunctionExpr e);

    R visit(IntExpr e);

    R visit(TermExpr e);

    R visit(Expr e);
}
