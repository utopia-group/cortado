package edu.utexas.cs.utopia.cortado.util.sat.formula;

import javax.annotation.Nonnull;

/**
 * Tool to walk the formula tree
 * @param <T> return type of the walk
 */
public abstract class PropositionalFormulaVisitor<T> {
    @Nonnull
    public T visit(@Nonnull PropositionalFormula formula) {
        // otherwise switch on node type
        switch (formula.getNodeType()) {
            case ATOM:
                assert formula instanceof Atom;
                final Atom atom = (Atom) formula;
                switch(atom.getAtomType())
                {
                    case FALSE:
                        assert atom instanceof LiteralFalse;
                        return visitFalse((LiteralFalse) atom);
                    case TRUE:
                        assert atom instanceof LiteralTrue;
                        return visitTrue((LiteralTrue) atom);
                    case VARIABLE:
                        assert atom instanceof BooleanVariable;
                        return visitVariable((BooleanVariable) atom);
                    default: throw new IllegalStateException("Unrecognized atom type " + atom.getAtomType());
                }
            case OPERATOR:
                assert formula instanceof BooleanOperator;
                final BooleanOperator boolOp = (BooleanOperator) formula;
                switch ((boolOp).getOperatorType()) {
                    case AND:
                        assert boolOp instanceof AndOperator;
                        return visitAnd((AndOperator) boolOp);
                    case IMPLIES:
                        assert boolOp instanceof ImpliesOperator;
                        return visitImplies((ImpliesOperator) boolOp);
                    case IFF:
                        assert boolOp instanceof IFFOperator;
                        return visitIFF((IFFOperator) boolOp);
                    case NOT:
                        assert boolOp instanceof NotOperator;
                        return visitNot((NotOperator) boolOp);
                    case OR:
                        assert boolOp instanceof OrOperator;
                        return visitOr((OrOperator) boolOp);
                    default: throw new IllegalStateException("Unrecognized operator type " + boolOp.getOperatorType());
                }
            default: throw new IllegalStateException("Unrecognized node type " + formula.getNodeType());
        }
    }

    /// atom visitors
    @Nonnull
    abstract protected T visitFalse(@Nonnull LiteralFalse literalFalse);
    @Nonnull
    abstract protected T visitTrue(@Nonnull LiteralTrue literalTrue);
    @Nonnull
    abstract protected T visitVariable(@Nonnull BooleanVariable var);

    /// operator visitors
    @Nonnull
    abstract protected T visitAnd(@Nonnull AndOperator and);
    @Nonnull
    abstract protected T visitIFF(@Nonnull IFFOperator iff);
    @Nonnull
    abstract protected T visitImplies(@Nonnull ImpliesOperator impl);
    @Nonnull
    abstract protected T visitNot(@Nonnull NotOperator not);
    @Nonnull
    abstract protected T visitOr(@Nonnull OrOperator or);
}
