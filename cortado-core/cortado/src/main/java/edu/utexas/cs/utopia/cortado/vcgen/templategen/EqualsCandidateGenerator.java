package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;

public class EqualsCandidateGenerator implements BooleanCandidateGenerator
{
    private static final EqualsCandidateGenerator INSTANCE = new EqualsCandidateGenerator();

    private EqualsCandidateGenerator()
    {

    }

    public static EqualsCandidateGenerator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public BoolExpr genCandidate(Expr e1, Expr e2)
    {
        return eFac.mkEQ(e1, e2);
    }
}
