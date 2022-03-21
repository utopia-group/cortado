package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

abstract class Atom extends PropositionalFormula {
    private final ATOM_TYPE atomType;

    enum ATOM_TYPE
    {
        TRUE,
        FALSE,
        VARIABLE
    }

    /**
     * @param builder  the {@link PropositionalFormulaBuilder} building this formula
     */
    Atom(@Nonnull PropositionalFormulaBuilder builder, ATOM_TYPE atomType) {
        super(builder, NODE_TYPE.ATOM);
        this.atomType = atomType;
    }

    /**
     * @return the type of atom
     */
    ATOM_TYPE getAtomType() {
        return atomType;
    }

    @Override
    @Nonnull
    public Set<PropositionalFormula> getAllVariables()
    {
        switch(getAtomType())
        {
            case TRUE:
            case FALSE: return Collections.emptySet();
            case VARIABLE: return Collections.singleton(this);
            default: throw new IllegalStateException("Unrecognized atom type " + getAtomType());
        }
    }

    /**
     * @return string representation of the atom
     */
    @Override
    public String toString() {return getAtomType().toString();}
}
