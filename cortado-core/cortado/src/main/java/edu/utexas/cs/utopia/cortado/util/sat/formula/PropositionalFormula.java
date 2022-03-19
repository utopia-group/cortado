package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Set;

/**
 * Representation of a propositional formula
 */
abstract public class PropositionalFormula {
    private final PropositionalFormulaBuilder builder;
    private final NODE_TYPE nodeType;

    enum NODE_TYPE {
        ATOM, // Constants and variables
        OPERATOR // Boolean operation
    }

    /// Constructor ///////////////////////////////////////////////////////////

    /**
     * @param subFormulas the subformulas of this formula. Used
     *                    to extract a builder.
     */
    PropositionalFormula(@Nonnull Collection<PropositionalFormula> subFormulas)
    {
        if(subFormulas.isEmpty())
        {
            throw new IllegalArgumentException("Expected at least one subformula.");
        }
        this.builder = subFormulas.iterator().next().getBuilder();
        this.nodeType = NODE_TYPE.OPERATOR;
    }

    /**
     * @param builder the {@link PropositionalFormulaBuilder} building this formula
     * @param nodeType the type of the root
     */
    PropositionalFormula(@Nonnull PropositionalFormulaBuilder builder, NODE_TYPE nodeType)
    {
        this.builder = builder;
        this.nodeType = nodeType;
    }

    /**
     * @return the {@link PropositionalFormulaBuilder} responsible for building this formula
     */
    public PropositionalFormulaBuilder getBuilder()
    {
        return this.builder;
    }

    /**
     * @return the node type
     */
    NODE_TYPE getNodeType()
    {
        return nodeType;
    }

    /**
     * @return A string representation of this formula
     */
    abstract public String toString();

    /**
     * @return the set of variables used in this formula
     */
    @Nonnull
    abstract public Set<PropositionalFormula> getAllVariables();
}
