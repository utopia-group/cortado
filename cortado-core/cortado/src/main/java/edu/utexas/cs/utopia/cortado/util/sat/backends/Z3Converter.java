package edu.utexas.cs.utopia.cortado.util.sat.backends;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import edu.utexas.cs.utopia.cortado.util.sat.formula.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Convert {@link PropositionalFormula}s to {@link BoolExpr}s.
 */
public class Z3Converter
{
    private final Context ctx;
    private final Z3FormulaConverter converter;
    private final Map<PropositionalFormula, BoolExpr> varToZ3Var = new HashMap<>();
    private final Set<String> z3VarNames = new HashSet<>();
    // logging
    private static final Logger log = LoggerFactory.getLogger(Z3Converter.class.getName());

    /**
     * @param ctx the context to use
     */
    public Z3Converter(Context ctx)
    {
        this.ctx = ctx;
        this.converter = new Z3FormulaConverter();
    }

    // TODO: Check that there are not multiple variables with the same name!
    //       Or redesign the boolean variables to enforce it
    /**
     * NOTE: boolean constants are defined uniquely
     *      by their name in this conversion.
     *      Using a {@link PropositionalFormulaBuilder} will
     *      ensure that different variables have different
     *      names
     *
     * @param formula the formula to convert
     * @return an equivalent formula in Z3
     */
    public BoolExpr convertToZ3(@Nonnull PropositionalFormula formula)
    {
        return converter.visit(formula);
    }

    /**
     * @return the z3 context.
     */
    public Context getContext()
    {
        return ctx;
    }

    /**
     * Visitor which enacts z3 conversion
     */
    private class Z3FormulaConverter extends PropositionalFormulaVisitor<BoolExpr>
    {
        @Nonnull
        @Override
        protected BoolExpr visitFalse(@Nonnull LiteralFalse literalFalse)
        {
            return ctx.mkFalse();
        }

        @Nonnull
        @Override
        protected BoolExpr visitTrue(@Nonnull LiteralTrue literalTrue)
        {
            return ctx.mkTrue();
        }

        @Nonnull
        @Override
        protected BoolExpr visitVariable(@Nonnull BooleanVariable var)
        {
            if(!varToZ3Var.containsKey(var))
            {
                if(z3VarNames.contains(var.toString()))
                {
                    log.warn("Different variable objects have same name " + var);
                }
                z3VarNames.add(var.toString());
                varToZ3Var.put(var, ctx.mkBoolConst(var.toString()));
            }
            return varToZ3Var.get(var);
        }

        @Nonnull
        @Override
        protected BoolExpr visitAnd(@Nonnull AndOperator and)
        {
            return ctx.mkAnd(visit(and.getLHS()), visit(and.getRHS()));
        }

        @Nonnull
        @Override
        protected BoolExpr visitIFF(@Nonnull IFFOperator iff)
        {
            return ctx.mkIff(visit(iff.getLHS()), visit(iff.getRHS()));
        }

        @Nonnull
        @Override
        protected BoolExpr visitImplies(@Nonnull ImpliesOperator impl)
        {
            return ctx.mkImplies(visit(impl.getLHS()), visit(impl.getRHS()));
        }

        @Nonnull
        @Override
        protected BoolExpr visitNot(@Nonnull NotOperator not)
        {
            return ctx.mkNot(visit(not.getOperand()));
        }

        @Nonnull
        @Override
        protected BoolExpr visitOr(@Nonnull OrOperator or)
        {
            return ctx.mkOr(visit(or.getLHS()), visit(or.getRHS()));
        }
    }
}
