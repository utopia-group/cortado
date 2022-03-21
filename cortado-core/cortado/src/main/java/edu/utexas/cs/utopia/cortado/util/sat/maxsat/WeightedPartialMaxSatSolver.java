package edu.utexas.cs.utopia.cortado.util.sat.maxsat;

import edu.utexas.cs.utopia.cortado.util.sat.formula.Interpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;

import javax.annotation.Nonnull;

/**
 * General implementation for a weighted partial max-sat solver
 */
public abstract class WeightedPartialMaxSatSolver implements AutoCloseable {
    private SolverResult solverResult = SolverResult.NO_SOLVE_ATTEMPTED;
    public enum SolverResult {
        SAT, UNSAT, SOLVER_FAILED, NO_SOLVE_ATTEMPTED
    }

    /**
     * Add a hard constraint
     *
     * @param constraint the boolean formula expressing the constraint
     */
    abstract public void addConstraint(PropositionalFormula constraint);

    /**
     * Add a formula to the objective function. If satisfied,
     * it adds weight to the objective function
     *
     * @param formula the formula to attempt to satisfy
     * @param weight the nonnative weight added to the objective function if satisfied
     * @throws IllegalArgumentException if weight is negative
     */
    abstract public void addObjectiveClause(PropositionalFormula formula, int weight, String objectiveName);

    /**
     * Solve the current weighted partial max-sat problem
     *
     * @param timeoutInMs the timeout in milliseconds
     * @return the solver result
     */
    @Nonnull
    abstract SolverResult solveWPMaxSat(long timeoutInMs);

    /**
     * Attempt to maximize the objective function. If no objective
     * function has been defined, finds a satisfying solution.
     *
     * @param timeoutInMs how long to attempt solve for (in milliseconds)
     * @throws SolveAlreadyAttemptedException if this method has already
     *      been called for this object.
     */
    public void maximizeObjective(long timeoutInMs) throws SolveAlreadyAttemptedException {
        if(solverResult != SolverResult.NO_SOLVE_ATTEMPTED)
        {
            throw new SolveAlreadyAttemptedException();
        }
        solverResult = solveWPMaxSat(timeoutInMs);
    }

    /**
     * To be called after a call to solveConstraint() or
     * maximizeObjective()
     *
     * @return SAT if the constraints were solved or the objective
     *         was maximized, UNSAT if the constraints are infeasible,
     *         SOLVER_FAILED if a satisfying/optimal solution could
     *         not be found, and NO_SOLVE_ATTEMPTED if there is
     *         no preceding call to solveConstraints or
     *         maximizeObjective
     */
    @Nonnull
    public SolverResult getSolverResult()
    {
        return solverResult;
    }

    /**
     * After calling solveConstraint() or maximizeObjective(),
     * this function retrieves a satisfying or maximal interpretation.
     *
     * @return A satisfying/maximal interpretation
     * @throws IllegalStateException if this.getSolverResult() != SAT.
     */
    abstract public Interpretation getModel();

    abstract public int getObjectiveVal();

    /**
     * Relinquish any resources
     */
    public abstract void close();
}
