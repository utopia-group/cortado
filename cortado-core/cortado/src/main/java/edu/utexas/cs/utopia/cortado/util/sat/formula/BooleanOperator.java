package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * An operator on boolean formulas
 */
abstract class BooleanOperator extends PropositionalFormula {
    private final OPERATOR_TYPE operatorType;

    enum OPERATOR_TYPE {
        AND,
        IFF,
        IMPLIES,
        NOT,
        OR
    }

    /**
     * @param subFormulas subformulas of this formula. Used to
     *                     get a {@link PropositionalFormulaBuilder} for this formula
     * @param opType the operator type
     */
    BooleanOperator(@Nonnull Collection<PropositionalFormula> subFormulas, OPERATOR_TYPE opType) {
        super(subFormulas);
        this.operatorType = opType;
    }

    /**
     * @param builder  the {@link PropositionalFormulaBuilder} building this formula
     * @param opType the operator type
     */
    BooleanOperator(@Nonnull PropositionalFormulaBuilder builder, OPERATOR_TYPE opType) {
        super(builder, NODE_TYPE.OPERATOR);
        this.operatorType = opType;
    }

    /**
     * @return the operator type
     */
    OPERATOR_TYPE getOperatorType() {
        return operatorType;
    }

    /**
     * @return Human-readable name of this operator
     */
    @Nonnull
    String operatorName() {
        return getOperatorType().toString();
    }

    /**
     * If any arguments of this operator are {@link Literal}s, and this
     * operator can thereby be simplified, compute the simplification.
     *
     * e.g. AND FALSE p  --> FALSE
     *
     * Note this is not recursive. If we try to simplify the root of
     * AND
     *      AND FALSE FALSE
     *      AND FALSE FALSE
     * then no simplification is performed
     *
     * @return  the simplified formula
     */
    @Nonnull
    abstract PropositionalFormula simplifyLiterals();
}
