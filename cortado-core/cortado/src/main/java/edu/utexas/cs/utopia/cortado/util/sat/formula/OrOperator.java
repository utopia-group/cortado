package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.List;

import static edu.utexas.cs.utopia.cortado.util.sat.formula.BooleanOperator.OPERATOR_TYPE.OR;

public class OrOperator extends BinaryBooleanOperator {
    /**
     * @param operands the operands to take an OR over
     */
    OrOperator(@Nonnull List<PropositionalFormula> operands) {
        super(OR, operands);
    }

    @Nonnull
    @Override
    PropositionalFormula simplifyLiterals()
    {
        if(getLHS() instanceof Literal)
        {
            Literal lhs = (Literal) getLHS();
            // if lhs is true, the formula is true
            if(lhs.asBoolean())
            {
                return lhs;
            }
            else // otherwise, the rhs must hold
            {
                return getRHS();
            }
        }
        // case where RHS is literal, but LHS is not
        if(getRHS() instanceof Literal)
        {
            Literal rhs = (Literal) getRHS();
            // if rhs is true, the formula is true
            if(rhs.asBoolean())
            {
                return rhs;
            }
            else // otherwise, the lhs must hold
            {
                return getLHS();
            }
        }
        // Otherwise no simplification can be done
        return this;
    }
}
