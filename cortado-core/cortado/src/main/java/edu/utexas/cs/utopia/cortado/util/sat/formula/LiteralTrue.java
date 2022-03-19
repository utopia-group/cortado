package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;

public class LiteralTrue extends Literal {
    /**
     * @param builder  the {@link PropositionalFormulaBuilder} building this formula
     */
    LiteralTrue(@Nonnull PropositionalFormulaBuilder builder) {
        super(builder, true);
    }
}
