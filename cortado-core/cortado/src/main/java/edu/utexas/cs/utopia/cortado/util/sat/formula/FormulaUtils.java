package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;
import java.util.Arrays;

public class FormulaUtils
{
    /**
     * @param formula the formula
     * @param variable the variable
     * @return true iff formula contains the sub-formula
     */
    static public boolean containsVar(PropositionalFormula formula, PropositionalFormula variable)
    {
        if(!(variable instanceof BooleanVariable))
        {
            throw new IllegalArgumentException("variable " + variable + " is not a boolean variable.");
        }
        BooleanVariable var = (BooleanVariable) variable;
        final ContainsVariableChecker containsVariableChecker = new ContainsVariableChecker(var);
        return containsVariableChecker.visit(formula);
    }

    /**
     * The visitor used to implement {@link #containsVar(PropositionalFormula, PropositionalFormula)}
     */
    private static class ContainsVariableChecker extends PropositionalFormulaVisitor<Boolean>
    {
        private final BooleanVariable var;

        public ContainsVariableChecker(@Nonnull BooleanVariable var)
        {
            this.var = var;
        }

        @Nonnull
        @Override
        protected Boolean visitFalse(@Nonnull LiteralFalse literalFalse)
        {
            return false;
        }

        @Nonnull
        @Override
        protected Boolean visitTrue(@Nonnull LiteralTrue literalTrue)
        {
            return false;
        }

        @Nonnull
        @Override
        protected Boolean visitVariable(@Nonnull BooleanVariable var)
        {
            return this.var.equals(var);
        }

        /**
         * Recursively check if binop contains the variable
         * @param binop the binop to check
         * @return true iff binop contains the var
         */
        private boolean recursivelyCheck(@Nonnull BinaryBooleanOperator binop)
        {
            return visit(binop.getLHS()) || visit(binop.getRHS());
        }

        @Nonnull
        @Override
        protected Boolean visitAnd(@Nonnull AndOperator and)
        {
            return recursivelyCheck(and);
        }

        @Nonnull
        @Override
        protected Boolean visitIFF(@Nonnull IFFOperator iff)
        {
            return recursivelyCheck(iff);
        }

        @Nonnull
        @Override
        protected Boolean visitImplies(@Nonnull ImpliesOperator impl)
        {
            return recursivelyCheck(impl);
        }

        @Nonnull
        @Override
        protected Boolean visitNot(@Nonnull NotOperator not)
        {
            return visit(not.getOperand());
        }

        @Nonnull
        @Override
        protected Boolean visitOr(@Nonnull OrOperator or)
        {
            return recursivelyCheck(or);
        }
    }

    /**
     * @param formula the formula
     * @param variable the variable
     * @param value the value of the variable
     * @return the formula, with variable replaced by value
     */
    static public PropositionalFormula setVar(PropositionalFormula formula, PropositionalFormula variable, boolean value)
    {
        if(!(variable instanceof BooleanVariable))
        {
            throw new IllegalArgumentException("variable " + variable + " is not a boolean variable.");
        }
        BooleanVariable var = (BooleanVariable) variable;
        final VariableSetter variableSetter = new VariableSetter(var, value);
        return variableSetter.visit(formula);
    }


    /**
     * The visitor used to implement {@link #setVar(PropositionalFormula, PropositionalFormula, boolean)}
     */
    private static class VariableSetter extends PropositionalFormulaVisitor<PropositionalFormula>
    {
        private final PropositionalFormula value;
        private final BooleanVariable var;

        public VariableSetter(@Nonnull BooleanVariable var, boolean value)
        {
            this.var = var;
            this.value = value ? var.getBuilder().mkTRUE() : var.getBuilder().mkFALSE();
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitFalse(@Nonnull LiteralFalse literalFalse)
        {
            return literalFalse;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitTrue(@Nonnull LiteralTrue literalTrue)
        {
            return literalTrue;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitVariable(@Nonnull BooleanVariable var)
        {
            return this.var.equals(var) ? value : var;
        }

        @Nonnull
        private PropositionalFormula visitBinop(@Nonnull BinaryBooleanOperator binop)
        {
            final PropositionalFormula lhs = visit(binop.getLHS());
            final PropositionalFormula rhs = visit(binop.getRHS());
            return binop.getBuilder()
                    .mkBinop(binop.getOperatorType(), Arrays.asList(lhs, rhs))
                    .simplifyLiterals();
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitAnd(@Nonnull AndOperator and)
        {
            return visitBinop(and);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitIFF(@Nonnull IFFOperator iff)
        {
            return visitBinop(iff);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitImplies(@Nonnull ImpliesOperator impl)
        {
            return visitBinop(impl);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitNot(@Nonnull NotOperator not)
        {
            final PropositionalFormula operand = visit(not.getOperand());
            return ((NotOperator) not.getBuilder().mkNOT(operand)).simplifyLiterals();
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitOr(@Nonnull OrOperator or)
        {
            return visitBinop(or);
        }
    }
}
