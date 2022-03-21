package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;

/**
 * An interpretation which only associates values
 * to some of the symbols in a formula.

 */
public interface PartialInterpretation
{
    /**
     * @param f the formula to interpret. Must be interpreted (see {@link #isInterpreted(PropositionalFormula)})
     * @return true iff the formula interprets to true
     */
    boolean interpret(@Nonnull PropositionalFormula f);

    /**
     * @return true iff f is interpreted
     */
    boolean isInterpreted(@Nonnull PropositionalFormula f);

    /**
     * We allow two "types" of variables. auxiliary variables
     * are variables which are used as part of the encoding, but
     * the user is not interested in their actual values.
     * Non-auxiliary variables are the variables of interest.
     *
     * We consider two completions of a partial interpretation to
     * be equal iff they assign the same truth values to all uninterpreted non-auxiliary
     * variables.
     *
     * @param nonAuxiliaryVariables the non-auxiliary variables.
     * @return the completions of this partial interpretation
     */
    @Nonnull
    Iterator<Interpretation> getCompletions(@Nonnull Collection<PropositionalFormula> nonAuxiliaryVariables);
}
