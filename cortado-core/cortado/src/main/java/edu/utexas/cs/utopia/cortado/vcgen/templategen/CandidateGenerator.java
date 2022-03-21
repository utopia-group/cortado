package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;

public interface CandidateGenerator<E extends Expr>
{
    ExprFactory eFac = CachedExprFactory.getInstance();

    E genCandidate(Expr e1, Expr e2);

    default boolean isValidOrUnsat(BoolExpr e)
    {
        return false;
//        return isUnsat(e) || isUnsat(eFac.mkNEG(e));
    }
}
