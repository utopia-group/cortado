package edu.utexas.cs.utopia.cortado.vcgen.invariants;

import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.vcgen.CachedStrongestPostConditionCalculatorFactory;
import edu.utexas.cs.utopia.cortado.vcgen.StrongestPostConditionCalculator;
import soot.BooleanType;
import soot.SootMethod;

import java.util.Objects;

/**
 * A candidate invariant for a monitor
 */
class CandidateInvariant
{
    private final SootMethod method;
    private final BoolExpr candidate;

    /**
     * Create candidate invariant
     * @param candidate the candidate invariant
     */
    CandidateInvariant(BoolExpr candidate)
    {
        this.method = null;
        this.candidate = candidate;
    }

    /**
     * Create a candidate invariant "method returns true"
     * @param method the method. Must have an active body and return a boolean type.
     */
    CandidateInvariant(SootMethod method)
    {
        // Make sure predicate returns a bool and has a body
        if(!method.hasActiveBody())
        {
            throw new IllegalArgumentException("predicate method " + method.getName() + " has no active body.");
        }
        if(!method.getReturnType().equals(BooleanType.v()))
        {
            throw new IllegalArgumentException("predicate method " + method.getName()
                    + " does not return a boolean.");
        }
        this.method = method;
        final StrongestPostConditionCalculator spCalc = CachedStrongestPostConditionCalculatorFactory.getInstance()
                .getOrCompute(method);
        final ExprFactory exprFactory = CachedExprFactory.getInstance();
        this.candidate = (BoolExpr) spCalc.getMethodEncoding(exprFactory.mkTRUE());
    }

    /**
     * @return  the candidate invariant
     */
    BoolExpr getCandidate()
    {
        return candidate;
    }

    @Override
    public String toString()
    {
        if(method != null)
        {
            return method.getName();
        }
        else
        {
            return candidate.toString();
        }
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(method, candidate);
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof CandidateInvariant)) return false;
        return Objects.equals(method, ((CandidateInvariant) other).method)
                && Objects.equals(candidate, ((CandidateInvariant) other).candidate);
    }
}
