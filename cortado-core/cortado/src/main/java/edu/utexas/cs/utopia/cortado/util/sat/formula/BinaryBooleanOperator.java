package edu.utexas.cs.utopia.cortado.util.sat.formula;

import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract class BinaryBooleanOperator extends BooleanOperator {
    private final List<PropositionalFormula> operands;

    /**
     * @param opType the operator type
     * @param operands the operands of this operator. Must be at least 2.
     *                 If more than 2, interpreted as a reduction of this
     *                 operator from left to right.
     */
    BinaryBooleanOperator(OPERATOR_TYPE opType, @Nonnull List<PropositionalFormula> operands) {
        super(operands, opType);
        if(operands.size() < 2) throw new IllegalArgumentException("Expected at least 2 operands.");
        this.operands = operands;
    }

    /**
     * @return left-hand side of operator
     */
    @Nonnull
    public PropositionalFormula getLHS() {
        assert operands.size() >= 2;
        if(operands.size() == 2) return operands.get(0);
        final ArrayList<PropositionalFormula> lhsOperands = new ArrayList<>(operands);
        lhsOperands.remove(lhsOperands.size() - 1);
        return getBuilder().mkBinop(getOperatorType(), lhsOperands);
    }

    /**
     * @return right-hand side of operator
     */
    @Nonnull
    public PropositionalFormula getRHS() {
        return operands.get(operands.size() - 1);
    }

    @Nonnull
    @Override
    public Set<PropositionalFormula> getAllVariables()
    {
        return Sets.union(getLHS().getAllVariables(), getRHS().getAllVariables());
    }

    @Override
    public String toString()
    {
        String op = getOperatorType().toString();
        String shift = ("(" + op + " ").replaceAll("."," ");
        final List<String> childrenToString = operands.stream()
                .map(PropositionalFormula::toString)
                .map(string -> string.replaceAll("\n", "\n" + shift))
                .collect(Collectors.toList());
        String str = childrenToString.stream().reduce("(" + op + " ", (s1, s2) -> s1 + s2 + "\n" + shift);
        // strip off last newline, and put a ')' in its place
        return str.substring(0, str.lastIndexOf('\n')) + ")";
    }
}
