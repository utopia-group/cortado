package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;

public class RemCandidateGenerator implements IntegerCandidateGenerator
{
    private static final RemCandidateGenerator INSTANCE = new RemCandidateGenerator();

    private RemCandidateGenerator()
    {

    }

    public static RemCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public IntExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkREM(e1, e2);
    }
}
