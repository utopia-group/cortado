package edu.utexas.cs.utopia.cortado.util.sat.satsolver;

import com.microsoft.z3.Context;

import javax.annotation.Nonnull;

/**
 * Get solvers using {@link com.microsoft.z3} as a backend
 */
public class Z3SATSolverFactory implements SATSolverFactory
{
    @Nonnull
    @Override
    public SATSolver getSolver()
    {
        return new Z3SatSolver(new Context());
    }
}
