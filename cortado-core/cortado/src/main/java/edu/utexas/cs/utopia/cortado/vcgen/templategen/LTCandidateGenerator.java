package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;

public class LTCandidateGenerator implements BooleanCandidateGenerator
{
    private final static LTCandidateGenerator INSTANCE = new LTCandidateGenerator();

    private LTCandidateGenerator()
    {

    }

    public static LTCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public BoolExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkLT(e1, e2);
    }
}
