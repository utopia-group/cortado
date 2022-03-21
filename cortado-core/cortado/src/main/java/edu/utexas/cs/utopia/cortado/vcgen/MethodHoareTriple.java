package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import soot.SootClass;
import soot.SootMethod;

public class MethodHoareTriple
{
    private static final ExprFactory eFac = CachedExprFactory.getInstance();

    final Expr preCond;

    final Expr postCond;

    final SootMethod method;

    public MethodHoareTriple(Expr preCond, Expr postCond, SootMethod method)
    {
        SootClass inClass = method.getDeclaringClass();
        MonitorInvariantGenerator invGen = MonitorInvariantGenerator.getInstance(inClass);
        this.preCond = eFac.mkAND(invGen.getInvariant(), preCond);
        this.postCond = postCond;
        this.method = method;
    }
}
