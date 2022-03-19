package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;

public class MultCandidateGenerator implements IntegerCandidateGenerator
{
    private static final MultCandidateGenerator INSTANCE = new MultCandidateGenerator();

    public static MultCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    private MultCandidateGenerator()
    {

    }

    @Override
    public IntExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkMULT(e1, e2);
    }
}
