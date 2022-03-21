package edu.utexas.cs.utopia.cortado.util.sat.formula;

import edu.utexas.cs.utopia.cortado.util.naming.SATNamingUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PropositionalFormulaBuilder {
    private final Set<String> names = new HashSet<>();
    int varNamingID = 0;
    private final NNFConverter nnfConverter = new NNFConverter();
    private final TseitinTransformer tseitinTransformer = new TseitinTransformer();

    /// Atoms ///////////////////////////////////////////////////////////////

    /**
     * @return a literal true
     */
    @Nonnull
    public PropositionalFormula mkTRUE()
    {
        return new LiteralTrue(this);
    }

    /**
     * @return a literal false
     */
    @Nonnull
    public PropositionalFormula mkFALSE()
    {
        return new LiteralFalse(this);
    }

    /**
     * @param name the name prefix. If the name is already taken,
     *             _0, _1, etc. will be appended until the name
     *             is fresh.
     * @return the fresh variable
     */
    @Nonnull
    public PropositionalFormula mkFreshVar(@Nonnull String name)
    {
        String freshName = name;
        int count = 0;
        while(names.contains(freshName))
        {
            freshName = name + "_" + count++;
        }
        names.add(freshName);
        return new BooleanVariable(this, freshName);
    }

    /**
     * @return a fresh variable
     */
    @Nonnull
    public PropositionalFormula mkFreshVar()
    {
        String freshName;
        do {
            freshName = SATNamingUtils.getFreshBooleanVarName(varNamingID++);
        } while(names.contains(freshName));
        return mkFreshVar(freshName);
    }

    ///////////////////////////////////////////////////////////////////////////

    /// Unary operators ////////////////////////////////////////////////////////

    /**
     * @param formula the formula to negate
     * @return a negated formula
     */
    @Nonnull
    public PropositionalFormula mkNOT(@Nonnull PropositionalFormula formula)
    {
        if(formula.getBuilder() != this)
        {
            throw new IllegalArgumentException("this object is not formula's builder.");
        }
        return new NotOperator(formula);
    }

    ///////////////////////////////////////////////////////////////////////////

    /// Binary operators //////////////////////////////////////////////////////

    /**
     * Return a binary operation of the appropriate type
     *
     * @param opType the operation type
     * @param operands the operands
     * @return opType(lhs, rhs)
     */
    @Nonnull
    BinaryBooleanOperator mkBinop(BooleanOperator.OPERATOR_TYPE opType, List<PropositionalFormula> operands)
    {
        switch(opType)
        {
            case AND: return new AndOperator(operands);
            case IFF: return new IFFOperator(operands);
            case IMPLIES:
                assert operands.size() == 2;
                return new ImpliesOperator(operands.get(0), operands.get(1));
            case OR: return new OrOperator(operands);
            default: throw new IllegalStateException("Unrecognized binary operator type " + opType);
        }
    }

    /**
     * As {@link #mkIFF(List)}
     */
    @Nonnull
    public PropositionalFormula mkIFF(@Nonnull PropositionalFormula... formulas) {
        return mkIFF(Arrays.asList(formulas));
    }

    /**
     * @param formulas the formulas to take iffs of
     * @throws IllegalArgumentException if formulas is length 1 or less
     * @return iffs of formulas: either all true or all false
     */
    public PropositionalFormula mkIFF(@Nonnull List<PropositionalFormula> formulas)
    {
        return new IFFOperator(formulas);
    }

    /**
     * @param antecedent the antecedent of the implication
     * @param consequent the consequent of the implication
     * @return Or of all formulas true, with all formulas false
     */
    @Nonnull
    public PropositionalFormula mkIMPLIES(@Nonnull PropositionalFormula antecedent, @Nonnull PropositionalFormula consequent) {
        return new ImpliesOperator(antecedent, consequent);
    }

    /**
     * As {@link #mkOR(List)}
     */
    @Nonnull
    public PropositionalFormula mkOR(@Nonnull PropositionalFormula... formulas) {
        return mkOR(Arrays.asList(formulas));
    }

    /**
     * @param formulas the formulas to disjunct
     * @throws IllegalArgumentException if formulas is length 1 or less
     * @return disjunction of formulas
     */
    public PropositionalFormula mkOR(@Nonnull List<PropositionalFormula> formulas)
    {
        return new OrOperator(formulas);
    }

    /**
     * As {@link #mkAND(List)}
     */
    @Nonnull
    public PropositionalFormula mkAND(@Nonnull PropositionalFormula... formulas) {
        return mkAND(Arrays.asList(formulas));
    }

    /**
     * @param formulas the formulas to conjunct
     * @throws IllegalArgumentException if formulas is length 1 or less
     * @return conjunction of formulas
     */
    public PropositionalFormula mkAND(@Nonnull List<PropositionalFormula> formulas)
    {
        return new AndOperator(formulas);
    }

    /**
     * Converts formulas to negation normal form
     */
    private class NNFConverter extends PropositionalFormulaVisitor<PropositionalFormula> {
        private boolean negated = false;
        private final PropositionalFormulaBuilder builder = PropositionalFormulaBuilder.this;

        @Nonnull
        @Override
        protected PropositionalFormula visitFalse(@Nonnull LiteralFalse literalFalse) {
            if(negated) return builder.mkTRUE();
            return literalFalse;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitTrue(@Nonnull LiteralTrue literalTrue) {
            if(negated) return builder.mkFALSE();
            return literalTrue;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitVariable(@Nonnull BooleanVariable var) {
            if(negated) return builder.mkNOT(var);
            return var;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitAnd(@Nonnull AndOperator and) {
            final PropositionalFormula lhsNNF = visit(and.getLHS()),
                rhsNNF = visit(and.getRHS());
            if(negated) {
                return builder.mkOR(lhsNNF, rhsNNF);
            }
            return builder.mkAND(lhsNNF, rhsNNF);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitIFF(@Nonnull IFFOperator iff) {
            final PropositionalFormula lhs = iff.getLHS(),
                    rhs = iff.getRHS(),
                    lhsNNF = visit(lhs),
                    rhsNNF = visit(rhs),
                    notlhsNNF = visit(builder.mkNOT(lhs)),
                    notrhsNNF = visit(builder.mkNOT(rhs));
            if(negated) return builder.mkOR(
                    builder.mkAND(lhsNNF, notrhsNNF),
                    builder.mkAND(notlhsNNF, rhsNNF)
            );
            return builder.mkOR(
                    builder.mkAND(lhsNNF, rhsNNF),
                    builder.mkAND(notlhsNNF, notrhsNNF)
            );
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitImplies(@Nonnull ImpliesOperator impl) {
            final PropositionalFormula lhs = impl.getLHS(),
                    rhs = impl.getRHS();
            if(negated){
                final PropositionalFormula lhsNNF = visit(lhs),
                        notrhsNNF = visit(builder.mkNOT(rhs));
                return builder.mkAND(lhsNNF, notrhsNNF);
            }
            final PropositionalFormula rhsNNF = visit(rhs),
                    notlhsNNF = visit(builder.mkNOT(lhs));
            return builder.mkOR(notlhsNNF, rhsNNF);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitNot(@Nonnull NotOperator not) {
            negated = !negated;
            final PropositionalFormula toReturn = visit(not.getOperand());
            negated = !negated;
            return toReturn;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitOr(@Nonnull OrOperator or) {
            final PropositionalFormula lhsNNF = visit(or.getLHS()),
                    rhsNNF = visit(or.getRHS());
            if(negated) {
                return builder.mkAND(lhsNNF, rhsNNF);
            }
            return builder.mkOR(lhsNNF, rhsNNF);
        }
    }

    /**
     * Apply Tseitin transformation to formula in NNF
     */
    private class TseitinTransformer extends PropositionalFormulaVisitor<PropositionalFormula>
    {
        private final PropositionalFormulaBuilder bldr = PropositionalFormulaBuilder.this;
        private final Map<PropositionalFormula, PropositionalFormula> subFormulaToTseitinVar = new HashMap<>();

        /**
         * @param formula the formula
         * @return the tseitin variable associated to formula, or null
         *         if there is no associated tseitin variable
         */
        @Nullable
        public PropositionalFormula getTseitinVar(PropositionalFormula formula) {
            return subFormulaToTseitinVar.get(formula);
        }

        private PropositionalFormula encodeAND(@Nonnull PropositionalFormula tseitinVar,
                                               @Nonnull PropositionalFormula lhsTseitinVar,
                                               @Nonnull PropositionalFormula rhsTseitinVar)
        {
            return bldr.mkAND(
                    // not(tseitinVar) implies ( not(lhs) OR not(rhs) )
                    bldr.mkOR(tseitinVar, toNNF(bldr.mkNOT(lhsTseitinVar)), toNNF(bldr.mkNOT(rhsTseitinVar))),
                    // tseitinVar implies lhs
                    bldr.mkOR(toNNF(bldr.mkNOT(tseitinVar)), lhsTseitinVar),
                    // tseitinVar implies rhs
                    bldr.mkOR(toNNF(bldr.mkNOT(tseitinVar)), rhsTseitinVar)
            );
        }

        private PropositionalFormula encodeOR(@Nonnull PropositionalFormula tseitinVar,
                                              @Nonnull PropositionalFormula lhsTseitinVar,
                                              @Nonnull PropositionalFormula rhsTseitinVar)
        {
            return bldr.mkAND(
                    // not(tseitinVar) implies not(lhs)
                    bldr.mkOR(tseitinVar, toNNF(bldr.mkNOT(lhsTseitinVar))),
                    // not(tseitinVar) implies not(rhs)
                    bldr.mkOR(tseitinVar, toNNF(bldr.mkNOT(rhsTseitinVar))),
                    // tseitinVar implies (lhs OR rhs)
                    bldr.mkOR(toNNF(bldr.mkNOT(tseitinVar)), lhsTseitinVar, rhsTseitinVar)
            );
        }

        private PropositionalFormula handleBinop(@Nonnull BinaryBooleanOperator binop)
        {
            final PropositionalFormula transformedLHS = visit(binop.getLHS()),
                    transformedRHS = visit(binop.getRHS());
            assert subFormulaToTseitinVar.containsKey(binop.getRHS());
            assert subFormulaToTseitinVar.containsKey(binop.getRHS());
            assert !subFormulaToTseitinVar.containsKey(binop);
            final PropositionalFormula tseitinVar = bldr.mkFreshVar(),
                    lhsTseitinVar = subFormulaToTseitinVar.get(binop.getLHS()),
                    rhsTseitinVar = subFormulaToTseitinVar.get(binop.getRHS());
            subFormulaToTseitinVar.put(binop, tseitinVar);
            PropositionalFormula tseitinEncoding;
            switch(binop.getOperatorType())
            {
                case AND:
                    tseitinEncoding = encodeAND(tseitinVar, lhsTseitinVar, rhsTseitinVar);
                    break;
                case OR:
                    tseitinEncoding = encodeOR(tseitinVar, lhsTseitinVar, rhsTseitinVar);
                    break;
                default: throw new IllegalStateException("Unexpected operator type: " + binop.getOperatorType());
            }
            // make sure not to accidentally add constraints
            // when LHS/RHS is just a literal true or a (possibly negated) variable.
            //
            // i.e. Only and together tseitin consistency constraints
            //      that we generated.
            List<PropositionalFormula> toAnd = Stream.of(tseitinEncoding, transformedLHS, transformedRHS)
                    .filter(form -> subFormulaToTseitinVar.get(form) != form)
                    .collect(Collectors.toList());
            assert !toAnd.isEmpty();
            if(toAnd.size() == 1) return toAnd.get(0);
            return bldr.mkAND(toAnd);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitFalse(@Nonnull LiteralFalse literalFalse) {
            subFormulaToTseitinVar.put(literalFalse, literalFalse);
            return literalFalse;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitTrue(@Nonnull LiteralTrue literalTrue) {
            subFormulaToTseitinVar.put(literalTrue, literalTrue);
            return literalTrue;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitVariable(@Nonnull BooleanVariable var) {
            subFormulaToTseitinVar.put(var, var);
            return var;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitAnd(@Nonnull AndOperator and) {
            return handleBinop(and);
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitIFF(@Nonnull IFFOperator iff) {
            throw new IllegalStateException("Formula in NNF must have no IFF operators.");
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitImplies(@Nonnull ImpliesOperator impl) {
            throw new IllegalStateException("Formula in NNF must have no IMPLIES operators.");
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitNot(@Nonnull NotOperator not) {
            assert not.getOperand().getNodeType() == PropositionalFormula.NODE_TYPE.ATOM;
            subFormulaToTseitinVar.put(not, not);
            return not;
        }

        @Nonnull
        @Override
        protected PropositionalFormula visitOr(@Nonnull OrOperator or) {
            return handleBinop(or);
        }
    }

    /**
     * @param formula a propositional formula
     * @return An equivalent formula in negation normal form
     */
    @Nonnull
    public PropositionalFormula toNNF(@Nonnull PropositionalFormula formula)
    {
        return nnfConverter.visit(formula);
    }

    /**
     * Apply the Tseitin transformation to formula.
     * Variables in formula are the same ones used in the return
     * formula.
     *
     * Returns an equisatisfiable CNFFormula which implies formula
     * and such that whenever sigma is a satisfying assignment to formula,
     * it can be extended to a satisfying assignment of CNFFormula.
     *
     * @param formula the formula to
     * @return Tseitin transformation applied to formula. It is
     *      guaranteed to be a single AND, all of whose
     *      children are ORs of variables or negated variables,
     *      and has no constants, as a single clause, or
     *      as a literal TRUE/FALSE
     */
    @Nonnull
    public PropositionalFormula applyTseitinTransformation(@Nonnull PropositionalFormula formula) {
        PropositionalFormula formulaInNNF = toNNF(formula);
        final PropositionalFormula tseitinEncodings = tseitinTransformer.visit(formulaInNNF);
        PropositionalFormula tseitinVar = tseitinTransformer.getTseitinVar(formulaInNNF);
        assert tseitinVar != null;
        return mkAND(tseitinVar, tseitinEncodings);
    }
}