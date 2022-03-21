package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Set;

public class NotOperator extends BooleanOperator {
    private final PropositionalFormula operand;
    /**
     * @param operand formula being negated
     */
    NotOperator(@Nonnull PropositionalFormula operand) {
        super(operand.getBuilder(), OPERATOR_TYPE.NOT);
        this.operand = operand;
    }

    @Nonnull
    public PropositionalFormula getOperand()
    {
        return operand;
    }

    @Nonnull
    @Override
    public String toString()
    {
        String shift = ("(" + operatorName() + " ").replaceAll("."," ");
        return "(" + operatorName() + " " +
                operand.toString().replaceAll("\n", "\n" + shift)
                + ")";
    }

    @Nonnull
    @Override
    public Set<PropositionalFormula> getAllVariables()
    {
        return getOperand().getAllVariables();
    }

    @Nonnull
    @Override
    PropositionalFormula simplifyLiterals()
    {
        if(getOperand() instanceof Literal)
        {
            final Literal operand = (Literal) getOperand();
            return new Literal(getBuilder(), !operand.asBoolean());
        }
        return this;
    }
}
