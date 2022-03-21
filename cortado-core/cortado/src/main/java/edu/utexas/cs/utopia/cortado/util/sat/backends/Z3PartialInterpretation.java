package edu.utexas.cs.utopia.cortado.util.sat.backends;

import com.google.common.collect.Sets;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import edu.utexas.cs.utopia.cortado.util.sat.formula.FormulaUtils;
import edu.utexas.cs.utopia.cortado.util.sat.formula.Interpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A partial interpretation build from a z3 model.
 */
public class Z3PartialInterpretation implements PartialInterpretation
{
    private final Model model;
    private final Z3Converter z3Converter;
    private final boolean enableModelCompletion;

    /**
     * Build a interpretation from the model (without model completion).
     *
     * @param model the model to build this from
     * @param z3Converter the converter to use
     */
    public Z3PartialInterpretation(@Nonnull Model model, @Nonnull Z3Converter z3Converter) {
        this(model, z3Converter, false);
    }

    /**
     * Build a interpretation from the model
     *
     * @param model the model to build this from
     * @param z3Converter the converter to use
     * @param enableModelCompletion if true, complete the model to a complete interpretation
     */
    Z3PartialInterpretation(@Nonnull Model model, @Nonnull Z3Converter z3Converter, boolean enableModelCompletion)
    {
        this.model = model;
        this.z3Converter = z3Converter;
        this.enableModelCompletion = enableModelCompletion;
    }

    /**
     * @param z3Formula the z3 formula
     * @return true iff z3formula is interpreted
     */
    private boolean isInterpreted(@Nonnull BoolExpr z3Formula)
    {
        final Expr evaluatedExpr = model.evaluate(z3Formula, enableModelCompletion);
        return evaluatedExpr.isTrue() || evaluatedExpr.isFalse();
    }

    @Override
    public boolean isInterpreted(@Nonnull PropositionalFormula f)
    {
        return isInterpreted(z3Converter.convertToZ3(f));
    }

    @Override
    public boolean interpret(@Nonnull PropositionalFormula formula)
    {
        final BoolExpr z3formula = z3Converter.convertToZ3(formula);
        // make sure formula is interpreted
        if(!isInterpreted(z3formula))
        {
            throw new IllegalArgumentException("formula is uninterpreted.");
        }
        Expr result = this.model.evaluate(z3formula, false);
        return result.isTrue();
    }

    @Override
    @Nonnull
    public Iterator<Interpretation> getCompletions(@Nonnull Collection<PropositionalFormula> nonAuxiliaryVariables)
    {
        return new Iterator<Interpretation>()
        {
            final Set<PropositionalFormula> uninterpretedNonAuxiliaryVariables = nonAuxiliaryVariables.stream()
                    .filter(var -> !isInterpreted(var))
                    .collect(Collectors.toSet());
            // the uninterpreted variables are "free"
            // iterate over each which set of variables is assigned to true
            final Iterator<Set<PropositionalFormula>> uninterpretedVarsPowerSet = Sets.powerSet(uninterpretedNonAuxiliaryVariables).iterator();

            @Override
            public boolean hasNext()
            {
                return uninterpretedVarsPowerSet.hasNext();
            }

            @Override
            public Interpretation next()
            {
                Set<PropositionalFormula> trueSet = uninterpretedVarsPowerSet.next();
                return new Interpretation()
                {
                    @Override
                    public boolean isInterpreted(@Nonnull PropositionalFormula f)
                    {
                        return Z3PartialInterpretation.this.isInterpreted(f) || uninterpretedNonAuxiliaryVariables.contains(f);
                    }

                    @Override
                    public boolean interpret(@Nonnull PropositionalFormula f)
                    {
                        // substitute values for uninterpreted vars
                        for(PropositionalFormula var : uninterpretedNonAuxiliaryVariables)
                        {
                            // substitute true for var iff it is contained in the true-set
                            f = FormulaUtils.setVar(f, var, trueSet.contains(var));
                        }
                        // interpret remaining formula
                        return Z3PartialInterpretation.this.interpret(f);
                    }
                };
            }
        };
    }
}
