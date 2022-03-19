package edu.utexas.cs.utopia.cortado.util.sat.enumeration;

import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * SAT model enumeration/counting designed to be used in tandem
 * with {@link WeightedPartialMaxSatEnumerator}
 */
abstract class AchievedObjectiveValueModelEnumerator
{
    enum EnumerationStatus
    {
        NOT_STARTED,
        TIMEOUT,
        MAX_SOLVES_REACHED,
        COMPLETE
    }
    private EnumerationStatus status = EnumerationStatus.NOT_STARTED;

    /**
     * @param formula the constraint to add
     */
    abstract void addConstraint(@Nonnull PropositionalFormula formula);

    /**
     * @param formulas the formulas
     * @param k the number which should be true
     */
    abstract void requireExactlyKAreTrue(@Nonnull List<PropositionalFormula> formulas, int k);

    /**
     * count how many assignments to nonAuxiliaryVariables
     * can be extended to a satisfying assignment.
     *
     * Example: if f = p ^ (q v r), there is one assignment to p
     * which can be extended to a satisfying assignment,
     * two for q, and two for r. There are 4 assignments of {q, r}
     * which can be extended to a satisfying assignment.
     *
     * @param nonAuxiliaryVariables the variables we are counting over.
     *                      Passing null indicates ALL variables are non-auxiliary
     *                      variables.
     * @param timeoutInMs timeout per solution
     * @param maxSolves max number of solves
     * @return the solve status
     * @throws EnumerationAlreadyAttemptedException if an enumeration has already been attempted
     */
    @Nonnull
    EnumerationStatus enumerateSolutions(@Nullable Collection<PropositionalFormula> nonAuxiliaryVariables,
                                         long timeoutInMs,
                                         int maxSolves)
            throws EnumerationAlreadyAttemptedException
    {
        if(status != EnumerationStatus.NOT_STARTED)
        {
            throw new EnumerationAlreadyAttemptedException();
        }
        status = enumerate(nonAuxiliaryVariables, timeoutInMs, maxSolves);
        return status;
    }

    protected abstract EnumerationStatus enumerate(@Nullable Collection<PropositionalFormula> nonAuxiliaryVariables,
                                                   long timeoutInMs,
                                                   int maxSolves);

    /**
     * To be called after {@link #enumerateSolutions(Collection, long, int)}
     * @return the interpretations
     */
    @Nonnull
    abstract List<PartialInterpretation> getInterpretations();
}

