package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;

/**
 * A literal true/literal false
 */
public class Literal extends Atom
{
    /**
     * @param builder  the {@link PropositionalFormulaBuilder} building this formula
     * @param truthValue true or false
     */
    Literal(@Nonnull PropositionalFormulaBuilder builder, boolean truthValue)
    {
        super(builder, truthValue ? ATOM_TYPE.TRUE : ATOM_TYPE.FALSE);
    }

    /**
     * @return the value of the literal as a boolean
     */
    boolean asBoolean()
    {
        return getAtomType() == ATOM_TYPE.TRUE;
    }
}
