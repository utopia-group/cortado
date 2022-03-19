package edu.utexas.cs.utopia.cortado.util.sat.satsolver;

import com.microsoft.z3.*;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3Converter;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link SATSolver} based on {@link com.microsoft.z3}.
 */
class Z3SatSolver implements SATSolver
{
    private final Context ctx;
    private final Z3Converter z3Converter;
    private final Solver solver;

    Z3SatSolver(@Nonnull Context ctx)
    {
        this.ctx = ctx;
        this.z3Converter = new Z3Converter(ctx);
        this.solver = ctx.mkSolver();
    }

    @Override
    public void addConstraint(@Nonnull PropositionalFormula f)
    {
        solver.add(z3Converter.convertToZ3(f));
    }

    @Nullable
    @Override
    public PartialInterpretation solve(long timeoutInMs) throws SatSolveFailedException
    {
        // setup timeout
        final Params params = ctx.mkParams();
        // z3 needs timeout as an integer
        int timeoutInMsAsInt = (int) timeoutInMs;
        if(timeoutInMsAsInt != timeoutInMs)
        {
            throw new IllegalArgumentException("timeoutInMs cannot be converted to an integer.");
        }
        params.add("timeout", timeoutInMsAsInt);
        solver.setParameters(params);
        // make sure all constraints remain after solve
        solver.push();
        // solve
        final Status result = solver.check();

        // handle unknown/unsat/sat cases
        switch(result)
        {
            case UNKNOWN: throw new SatSolveFailedException(solver.getReasonUnknown());
            case UNSATISFIABLE: return null;
            case SATISFIABLE:
                final Model model = solver.getModel();
                return new Z3PartialInterpretation(model, z3Converter);
            default: throw new IllegalStateException("Unrecognized solver result " + result);
        }
    }
}
