package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Arrays;

import static edu.utexas.cs.utopia.cortado.util.sat.formula.BooleanOperator.OPERATOR_TYPE.IMPLIES;

public class ImpliesOperator extends BinaryBooleanOperator {
    /**
     * @param antecedent     left-hand side of the operator
     * @param consequent     right-hand side of the operator
     */
    ImpliesOperator(@Nonnull PropositionalFormula antecedent,
                    @Nonnull PropositionalFormula consequent) {
        super(IMPLIES, Arrays.asList(antecedent, consequent));
    }

    @Nonnull
    @Override
    PropositionalFormula simplifyLiterals()
    {
        if(getLHS() instanceof Literal)
        {
            Literal lhs = (Literal) getLHS();
            // if LHS is true, the consequent must hold
            if(lhs.asBoolean())
            {
                return getRHS();
            }
            else // Otherwise, the formula is just true
            {
                return new LiteralTrue(getBuilder());
            }
        }
        // if LHS is not a literal, but RHS is a literal
        if(getRHS() instanceof Literal)
        {
            Literal rhs = (Literal) getRHS();
            // if RHS is true, this formula is true
            if(rhs.asBoolean())
            {
                return getRHS();
            }
            else // otherwise, the antecedent must not hold
            {
                return getBuilder().mkNOT(getLHS());
            }
        }
        // otherwise, no simplification can be done
        return this;
    }
}
