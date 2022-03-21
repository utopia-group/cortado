package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;

public interface BoolExprVisitor
{
    void visit(AndExpr e);

    void visit(EqExpr e);

    void visit(ExistsExpr e);

    void visit(ForAllExpr e);

    void visit(GreaterEqExpr e);

    void visit(GreaterExpr e);

    void visit(ImplExpr e);

    void visit(LessEqExpr e);

    void visit(LessExpr e);

    void visit(NegExpr e);

    void visit(OrExpr e);
}
