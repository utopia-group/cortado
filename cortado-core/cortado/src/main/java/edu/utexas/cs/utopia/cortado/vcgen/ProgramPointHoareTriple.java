package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

public class ProgramPointHoareTriple
{
    private static final ExprFactory eFac = CachedExprFactory.getInstance();

    private static final CachedStrongestPostConditionCalculatorFactory spCalcFactory = CachedStrongestPostConditionCalculatorFactory.getInstance();

    final Unit start;

    final Unit end;

    final Expr preCond;

    final Expr postCond;

    public ProgramPointHoareTriple(Expr preCond, Expr postCond, SootMethod inMethod, Unit start, Unit end)
    {
        StrongestPostConditionCalculator methSpCalc = spCalcFactory.getOrCompute(inMethod);

        this.preCond = methSpCalc.propagateInvariantUpTo(start, preCond);
        this.postCond = postCond;
        this.start = start;
        this.end = end;
    }
}
