package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;

/**
 * A boolean variable
 */
public class BooleanVariable extends Atom {
    private final String name;

    /**
     * @param builder the {@link PropositionalFormulaBuilder} building this formula
     * @param name the name. This is for printing only, two logically
     *             distinct different variables
     *             may exist with the same name.
     */
    BooleanVariable(@Nonnull PropositionalFormulaBuilder builder, @Nonnull String name) {
        super(builder, ATOM_TYPE.VARIABLE);
        this.name = name;
    }

    /**
     * @return the variable name
     */
    public String toString()
    {
        return name;
    }
}
