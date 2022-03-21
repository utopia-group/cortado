package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.util.soot.SootActiveBodyCache;
import soot.SootMethod;

import javax.annotation.Nonnull;

/**
 * Follows the java singleton pattern (i.e. there can only
 * be one instance of this class)
 */
public class CachedStrongestPostConditionCalculatorFactory
        extends SootActiveBodyCache<StrongestPostConditionCalculator>
{
    private static final CachedStrongestPostConditionCalculatorFactory INSTANCE = new CachedStrongestPostConditionCalculatorFactory();

    /**
     * Nobody outside the class can construct it
     */
    private CachedStrongestPostConditionCalculatorFactory() {}

    /**
     * @return the instance of {@link CachedStrongestPostConditionCalculatorFactory}
     */
    static public CachedStrongestPostConditionCalculatorFactory getInstance()
    {
        return INSTANCE;
    }

    /**
     *
     * @param method the method (may assume it has an active body)
     * @return A {@link StrongestPostConditionCalculator} for the method
     */
    @Nonnull
    @Override
    protected StrongestPostConditionCalculator computeFromActiveBody(@Nonnull SootMethod method) {
        return new StrongestPostConditionCalculator(method);
    }
}
