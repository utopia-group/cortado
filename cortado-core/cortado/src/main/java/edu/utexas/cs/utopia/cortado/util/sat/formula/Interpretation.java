package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * An interpretation mapping boolean variables to truth values.
 */
public interface Interpretation extends PartialInterpretation
{
    @Nonnull
    default Iterator<Interpretation> getCompletions(@Nonnull Collection<PropositionalFormula> nonAuxiliaryVariables)
    {
        return Collections.singleton(this).iterator();
    }

    @Override
    default boolean isInterpreted(@Nonnull PropositionalFormula f)
    {
        return true;
    }
}
