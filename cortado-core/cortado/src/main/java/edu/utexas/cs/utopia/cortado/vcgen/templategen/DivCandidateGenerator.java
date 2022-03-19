package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;

public class DivCandidateGenerator implements IntegerCandidateGenerator
{
    private static final DivCandidateGenerator INSTANCE = new DivCandidateGenerator();

    private DivCandidateGenerator()
    {

    }

    public static DivCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public IntExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkDIV(e1, e2);
    }
}
