package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;

public class LiteralFalse extends Literal {
    /**
     * @param builder  the {@link PropositionalFormulaBuilder} building this formula
     */
    LiteralFalse(@Nonnull PropositionalFormulaBuilder builder) {
        super(builder, false);
    }
}
