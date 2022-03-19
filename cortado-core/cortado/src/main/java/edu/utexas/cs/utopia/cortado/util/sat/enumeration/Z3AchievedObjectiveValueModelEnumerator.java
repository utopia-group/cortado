package edu.utexas.cs.utopia.cortado.util.sat.enumeration;

import com.microsoft.z3.*;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3Converter;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormulaBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

class Z3AchievedObjectiveValueModelEnumerator extends AchievedObjectiveValueModelEnumerator
{
    private final Context ctx;
    private final Z3Converter z3Converter;
    private final Solver solver;
    private List<PartialInterpretation> interpretations;
    final private Set<PropositionalFormula> allVariables = new HashSet<>();
    private PropositionalFormulaBuilder builder = null;

    Z3AchievedObjectiveValueModelEnumerator(@Nonnull Context ctx)
    {
        this.ctx = ctx;
        this.z3Converter = new Z3Converter(ctx);
        this.solver = ctx.mkSolver();
    }

    @Override
    void addConstraint(@Nonnull PropositionalFormula formula)
    {
        allVariables.addAll(formula.getAllVariables());
        solver.add(z3Converter.convertToZ3(formula));
        if(builder == null)
        {
            builder = formula.getBuilder();
        }
        else if(builder != formula.getBuilder())
        {
            throw new IllegalArgumentException("Cannot use formulas with different builders");
        }
    }

    @Override
    void requireExactlyKAreTrue(@Nonnull List<PropositionalFormula> formulas, int k)
    {
        if(k > formulas.size())
        {
            throw new IllegalArgumentException("k must be at most formulas.size()");
        }
        if(builder == null && !formulas.isEmpty())
        {
            builder = formulas.stream().findFirst().get().getBuilder();
        }
        if(formulas.stream().map(PropositionalFormula::getBuilder).anyMatch(b -> b != builder))
        {
            throw new IllegalStateException("All formulas must use same builder");
        }

        formulas.stream()
                .map(PropositionalFormula::getAllVariables)
                .forEach(allVariables::addAll);

        final ArithExpr sumOfFormulas = formulas.stream()
                .map(z3Converter::convertToZ3)
                .map(f -> (ArithExpr) ctx.mkITE(f, ctx.mkInt(1), ctx.mkInt(0)))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        l -> ctx.mkAdd(l.toArray(new ArithExpr[0]))
                ));
        solver.add(ctx.mkEq(sumOfFormulas, ctx.mkInt(k)));
    }

    @Nonnull
    @Override
    protected EnumerationStatus enumerate(@Nullable Collection<PropositionalFormula> nonAuxiliaryVariables,
                                          long timeoutInMs, int maxSolves)
    {
        if(builder == null)
        {
            throw new IllegalStateException("No constraints have been set");
        }

        // set timeout
        final Params solverParams = ctx.mkParams();
        if(timeoutInMs != (int) timeoutInMs)
        {
            throw new IllegalArgumentException("timeoutInMs of " + timeoutInMs + " is not convertible to an int.");
        }
        solverParams.add("timeout", (int) timeoutInMs);
        solver.setParameters(solverParams);

        // figure out variables (so we can cross out models as we find them)
        if(nonAuxiliaryVariables == null)
        {
            nonAuxiliaryVariables = allVariables;
        }

        // repeatedly solve
        Status solverStatus;
        int numSolves = 0;
        interpretations = new ArrayList<>();
        List<Model> foundModels = new ArrayList<>();
        do
        {
            ++numSolves;
            solverStatus = solver.check();
            if(solverStatus == Status.SATISFIABLE)
            {
                // eliminate this model as a possibility
                final Model model = solver.getModel();
                for(Model otherModel : foundModels)
                {
                    final Z3PartialInterpretation myInterp = new Z3PartialInterpretation(model, z3Converter);
                    final Z3PartialInterpretation otherInterp = new Z3PartialInterpretation(otherModel, z3Converter);
                    final List<PropositionalFormula> differingInterps = nonAuxiliaryVariables.stream()
                            .filter(myInterp::isInterpreted)
                            .filter(otherInterp::isInterpreted)
                            .filter(v -> myInterp.interpret(v) != otherInterp.interpret(v))
                            .collect(Collectors.toList());
                    assert !differingInterps.isEmpty();
                }
                foundModels.add(model);

                final PartialInterpretation partialInterpretation = new Z3PartialInterpretation(model, z3Converter);
                final PropositionalFormula notThisModel = nonAuxiliaryVariables.stream()
                        .filter(partialInterpretation::isInterpreted)
                        .map(var -> {
                            if(partialInterpretation.interpret(var))
                            {
                                return builder.mkNOT(var);
                            }
                            return var;
                        })
                        .reduce(builder::mkOR)
                        .orElseThrow(() -> new RuntimeException("Formula is trivially satisfiable"));
                solver.add(z3Converter.convertToZ3(notThisModel));
                // record the interpretation
                interpretations.add(partialInterpretation);
            }
        }
        while(numSolves < maxSolves && solverStatus == Status.SATISFIABLE);

        EnumerationStatus status;
        if(numSolves >= maxSolves && solverStatus == Status.SATISFIABLE)
        {
            status = EnumerationStatus.MAX_SOLVES_REACHED;
        }
        else if(solverStatus == Status.UNKNOWN)
        {
            status = EnumerationStatus.TIMEOUT;
        }
        else
        {
            status = EnumerationStatus.COMPLETE;
        }

        return status;
    }

    @Nonnull
    @Override
    List<PartialInterpretation> getInterpretations()
    {
        if(interpretations == null)
        {
            throw new IllegalStateException("Must call enumerateSolutions first");
        }
        return interpretations;
    }
}
