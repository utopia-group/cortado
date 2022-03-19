package edu.utexas.cs.utopia.cortado.util.sat.enumeration;

import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.SolveAlreadyAttemptedException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * General implementation for a weighted partial max-sat enumerator
 */
public abstract class WeightedPartialMaxSatEnumerator implements AutoCloseable
{
    EnumerationStatus enumerationStatus = EnumerationStatus.NOT_STARTED;

    public enum EnumerationStatus
    {
        NOT_STARTED, COMPLETE, TIMEOUT
    }

    public enum MultiObjectiveOptimizationPriority
    {
        /** Pareto front (by objective names) */
        PARETO_FRONT,
        /** Lexicographic (by order in which objective names are declared) */
        LEXICOGRAPHIC,
        /** Optimize objectives independently */
        INDEPENDENT
    }

    /**
     * Add a hard constraint
     *
     * @param constraint the boolean formula expressing the constraint
     */
    abstract public void addConstraint(@Nonnull PropositionalFormula constraint);

    /**
     * Add a formula to the objective function. If satisfied,
     * it adds weight to the objective function
     *
     * @param formula the formula to attempt to satisfy
     * @param weight the nonnative weight added to the objective function if satisfied
     * @throws IllegalArgumentException if weight is negative
     */
    abstract public void addObjectiveClause(@Nonnull PropositionalFormula formula, int weight, @Nonnull String objectiveName);

    /**
     * Perform the enumeration (see {@link #enumerateMaximalSolutions(MultiObjectiveOptimizationPriority, Collection, long, int)}
     */
    abstract EnumerationStatus enumerate(@Nonnull MultiObjectiveOptimizationPriority multiObjectiveOptimizationPriority,
                                         @Nullable Collection<PropositionalFormula> nonAuxiliaryVariables,
                                         long timeoutInMs, 
                                         int maxSolves);

    /**
     * Attempt to find all maximal solutions to the objective function using the given strategy
     * subject to the constraints
     *
     * @param timeoutInMs how long to attempt the solve for (in milliseconds)
     * @param multiObjectiveOptimizationPriority the optimization strategy for joint objective
     * @param nonAuxiliaryVariables the non-auxiliary variables (null if all variables are non-auxiliary)
     * @throws SolveAlreadyAttemptedException if this method has already
     *      been called for this object.
     */
    public void enumerateMaximalSolutions(@Nonnull MultiObjectiveOptimizationPriority multiObjectiveOptimizationPriority,
                                          @Nullable Collection<PropositionalFormula> nonAuxiliaryVariables,
                                          long timeoutInMs,
                                          int maxSolves) throws SolveAlreadyAttemptedException {
        if(getEnumerationStatus() != EnumerationStatus.NOT_STARTED)
        {
            throw new SolveAlreadyAttemptedException();
        }
        enumerationStatus = enumerate(multiObjectiveOptimizationPriority, 
                nonAuxiliaryVariables, 
                timeoutInMs, 
                maxSolves);
    }

    /**
     * To be called after a call to {@link #enumerateMaximalSolutions(MultiObjectiveOptimizationPriority, Collection, long, int)}
     *
     * @return status of the enumeration
     */
    @Nonnull
    public EnumerationStatus getEnumerationStatus()
    {
        return enumerationStatus;
    }

    /**
     * To be called after a call to {@link #enumerateMaximalSolutions(MultiObjectiveOptimizationPriority, Collection, long, int)}
     *
     * @return the list of partial interpretations
     */
    @Nonnull
    abstract public List<PartialInterpretation> getInterpretations();

    /**
     * @return a map from the partial interpretations returned by {@link #getInterpretations()} to their achieved objective values
     */
    @Nonnull
    abstract public Map<PartialInterpretation, Map<String, Integer>> getObjectiveValues();

    /**
     * Relinquish any resources
     */
    public abstract void close();
}
