package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.List;

import static edu.utexas.cs.utopia.cortado.util.sat.formula.BooleanOperator.OPERATOR_TYPE.IFF;

public class IFFOperator extends BinaryBooleanOperator {
    /**
     * @param operands the operands to take an IFF over
     */
    IFFOperator(@Nonnull List<PropositionalFormula> operands) {
        super(IFF, operands);
    }

    @Nonnull
    @Override
    PropositionalFormula simplifyLiterals()
    {
        if(getLHS() instanceof Literal)
        {
            Literal lhs = (Literal) getLHS();
            // if both literals, just evaluate operator
            if(getRHS() instanceof Literal)
            {
                Literal rhs = (Literal) getRHS();
                return new Literal(getBuilder(), lhs.asBoolean() && rhs.asBoolean() || (!lhs.asBoolean() && !rhs.asBoolean()));
            }
            // if just LHS is literal, simplify it away
            if(lhs.asBoolean())
            {
                return getRHS();
            }
            else
            {
                return getBuilder().mkNOT(getRHS());
            }
        }
        // Similarly, if just RHS is literal, simplify it away
        if(getRHS() instanceof Literal)
        {
            Literal rhs = (Literal) getRHS();
            if(rhs.asBoolean())
            {
                return getLHS();
            }
            else
            {
                return getBuilder().mkNOT(getLHS());
            }
        }
        // otherwise, no simplification can be done
        return this;
    }
}
