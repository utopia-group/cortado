package edu.utexas.cs.utopia.cortado.util.sat.satsolver;

import javax.annotation.Nonnull;

/**
 * Factory to generate solver using a specified backend
 */
public interface SATSolverFactory
{
    @Nonnull
    SATSolver getSolver();
}
