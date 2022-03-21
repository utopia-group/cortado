package edu.utexas.cs.utopia.cortado.util.sat.maxsat;

import com.microsoft.z3.Context;

/**
 * Factory for generating {@link com.microsoft.z3} weighted
 * partial max-sat solvers
 */
public class Z3WeightedPartialMaxSatSolverFactory implements WeightedPartialMaxSatSolverFactory {
    @Override
    public WeightedPartialMaxSatSolver getSolver() {
        return new Z3WeightedPartialMaxSatSolver(new Context());
    }
}
