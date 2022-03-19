package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;

public interface BoolExprRetVisitor<R>
{
    R visit(AndExpr e);

    R visit(EqExpr e);

    R visit(ExistsExpr e);

    R visit(ForAllExpr e);

    R visit(GreaterEqExpr e);

    R visit(GreaterExpr e);

    R visit(ImplExpr e);

    R visit(LessEqExpr e);

    R visit(LessExpr e);

    R visit(NegExpr e);

    R visit(OrExpr e);
}
