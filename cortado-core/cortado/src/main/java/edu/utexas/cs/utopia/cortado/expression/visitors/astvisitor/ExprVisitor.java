package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.ArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.TermExpr;

public interface ExprVisitor extends ArrayExprVisitor,
                                     BoolExprVisitor,
                                     FuncExprVisitor,
                                     IntExprVisitor,
                                     TermExprVisitor
{
    void visit(ArrayExpr e);

    void visit(BoolExpr e);

    void visit(FunctionExpr e);

    void visit(IntExpr e);

    void visit(TermExpr e);

    void visit(Expr e);
}
