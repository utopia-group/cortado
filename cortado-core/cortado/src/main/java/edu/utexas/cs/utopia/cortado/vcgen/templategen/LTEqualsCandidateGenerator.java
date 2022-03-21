package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;

public class LTEqualsCandidateGenerator implements BooleanCandidateGenerator
{
    private static final LTEqualsCandidateGenerator INSTANCE = new LTEqualsCandidateGenerator();

    private LTEqualsCandidateGenerator()
    {

    }

    public static LTEqualsCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public BoolExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkLTEQ(e1, e2);
    }
}
