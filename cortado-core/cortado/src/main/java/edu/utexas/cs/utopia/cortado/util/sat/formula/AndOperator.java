package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.List;

import static edu.utexas.cs.utopia.cortado.util.sat.formula.BooleanOperator.OPERATOR_TYPE.AND;

public class AndOperator extends BinaryBooleanOperator {
    /**
     * @param operands the operands to take an AND over
     */
    AndOperator(@Nonnull List<PropositionalFormula> operands) {
        super(AND, operands);
    }

    @Nonnull
    @Override
    PropositionalFormula simplifyLiterals()
    {
        if(getLHS() instanceof Literal)
        {
            Literal lhs = (Literal) getLHS();
            if(lhs.asBoolean()) // if LHS is true, return RHS
            {
                return getRHS();
            }
            else // if LHS is false, return false
            {
                return lhs;
            }
        }
        // now check if RHS is literal
        if(getRHS() instanceof Literal)
        {
            Literal rhs = (Literal) getRHS();
            if(rhs.asBoolean()) // if RHS is true, return LHS
            {
                return getLHS();
            }
            else // if RHS is false, return false
            {
                return rhs;
            }
        }
        // no simplification can be done
        return this;
    }
}
