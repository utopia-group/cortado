package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;

public class MinusCandidateGenerator implements IntegerCandidateGenerator
{
    private final static MinusCandidateGenerator INSTANCE = new MinusCandidateGenerator();

    private MinusCandidateGenerator()
    {

    }

    public static MinusCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public IntExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkMINUS(e1, e2);
    }
}
