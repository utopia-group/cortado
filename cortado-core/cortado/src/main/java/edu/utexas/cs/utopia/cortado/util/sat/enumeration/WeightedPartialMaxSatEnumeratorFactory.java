package edu.utexas.cs.utopia.cortado.util.sat.enumeration;

/**
 * A factory for particular implementations
 * of a {@link WeightedPartialMaxSatEnumerator}
 */
public interface WeightedPartialMaxSatEnumeratorFactory
{
    WeightedPartialMaxSatEnumerator getEnumerator();
}
