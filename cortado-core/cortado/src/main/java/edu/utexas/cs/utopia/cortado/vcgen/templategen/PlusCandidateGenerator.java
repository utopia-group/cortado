package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;

public class PlusCandidateGenerator implements IntegerCandidateGenerator
{
    private static final PlusCandidateGenerator INSTANCE = new PlusCandidateGenerator();

    private PlusCandidateGenerator()
    {

    }

    public static PlusCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public IntExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkPLUS(e1, e2);
    }
}
