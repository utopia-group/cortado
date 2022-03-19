package edu.utexas.cs.utopia.cortado.util.sat.maxsat;

/**
 * A factory for particular implementations
 * of a {@link WeightedPartialMaxSatSolver}
 */
public interface WeightedPartialMaxSatSolverFactory {
    WeightedPartialMaxSatSolver getSolver();
}
