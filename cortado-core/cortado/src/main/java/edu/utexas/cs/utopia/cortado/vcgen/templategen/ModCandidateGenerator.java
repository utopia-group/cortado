package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr;

public class ModCandidateGenerator implements IntegerCandidateGenerator
{
    private static final ModCandidateGenerator INSTANCE = new ModCandidateGenerator();

    private ModCandidateGenerator()
    {

    }

    public static ModCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public IntExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkMOD(e1, e2);
    }
}
