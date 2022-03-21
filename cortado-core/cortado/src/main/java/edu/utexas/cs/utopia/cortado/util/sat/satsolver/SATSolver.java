package edu.utexas.cs.utopia.cortado.util.sat.satsolver;

import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * SAT solving
 */
public interface SATSolver
{
    /**
     * Add f as a constraint. Should remain over multiple
     * calls to {@link #solve(long)}
     * @param f the constraint
     */
    void addConstraint(@Nonnull PropositionalFormula f);

    /**
     * Find a solution to the constraints
     *
     * @param timeoutInMs timeout expressed in milliseconds
     * @return a model of the formula, or null if the formula is unsat.
     * @throws SatSolveFailedException if solve fails (including if it times out)
     */
    @Nullable
    PartialInterpretation solve(long timeoutInMs) throws SatSolveFailedException;
}

