package edu.utexas.cs.utopia.cortado.util.sat.maxsat;

import com.microsoft.z3.*;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3Converter;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3Interpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.Interpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class Z3WeightedPartialMaxSatSolver extends WeightedPartialMaxSatSolver {
    private final Context ctx;
    private final Optimize optimizer;
    private static final Logger log = LoggerFactory.getLogger(Z3WeightedPartialMaxSatSolver.class.getName());
    private final Z3Converter z3Converter;

    private Model bestModel;
    Map<String, Optimize.Handle> objectiveHandles = new HashMap<>();

    /**
     * @param ctx A Z3 computing {@link Context}
     */
    public Z3WeightedPartialMaxSatSolver(Context ctx)
    {
        this.ctx = ctx;
        this.z3Converter = new Z3Converter(ctx);
        this.optimizer = ctx.mkOptimize();
    }

    @Override
    public void addConstraint(PropositionalFormula constraint) {
        BoolExpr z3constraint = z3Converter.convertToZ3(constraint);
        this.optimizer.Assert(z3constraint);
    }

    @Override
    public void addObjectiveClause(PropositionalFormula formula, int weight, String objectiveName) {
        if(weight < 0) {
            throw new IllegalArgumentException("negative weight");
        }
        BoolExpr z3formula = z3Converter.convertToZ3(formula);
        objectiveHandles.put(objectiveName, this.optimizer.AssertSoft(z3formula, weight, objectiveName));
    }

    @Nonnull
    @Override
    SolverResult solveWPMaxSat(long timeoutInMs) {
        assert getSolverResult() == SolverResult.NO_SOLVE_ATTEMPTED;
        // set timeout
        final Params timeoutParams = ctx.mkParams();
        if(timeoutInMs != (int) timeoutInMs)
        {
            throw new IllegalArgumentException("timeoutInMs of " + timeoutInMs + " is not convertible to an int.");
        }
        timeoutParams.add("timeout", (int) timeoutInMs);
//        timeoutParams.add("opt.priority", "pareto");
        this.optimizer.setParameters(timeoutParams);
        // perform solve
        Status solverZ3Status;

        Model bestModel = null;
        int bestObjComb = -1;

        do
        {
            solverZ3Status = this.optimizer.Check();
            if (solverZ3Status == Status.SATISFIABLE)
            {
                int objComb = this.objectiveHandles.values()
                                                   .stream()
                                                   .mapToInt(handle -> Integer.parseInt(handle.getValue().toString()))
                                                   .sum();

                Model model = this.optimizer.getModel();

                if (log.isDebugEnabled() && bestModel != null)
                {
                    this.objectiveHandles
                        .forEach((obj, value) -> System.out.println(obj + " = " + value));
                    System.out.println(lockAssignmentToStr(model));
                }

                if (bestModel == null || objComb < bestObjComb)
                {
                    bestModel = model;
                    bestObjComb = objComb;
                }
            }
        }
        while (objectiveHandles.size() > 1 && solverZ3Status != Status.UNSATISFIABLE);

        this.bestModel = bestModel;

        if (log.isDebugEnabled() && bestModel != null)
        {
            log.debug("Picked lock assignment\n" + lockAssignmentToStr(bestModel));
            log.debug("Optimizer statistics:\n" + this.optimizer.getStatistics());
        }

        return solverZ3Status == Status.SATISFIABLE ? SolverResult.SAT :
                solverZ3Status == Status.UNSATISFIABLE ? SolverResult.UNSAT : SolverResult.SOLVER_FAILED;
    }

    @Override
    public Interpretation getModel() {
        if(this.getSolverResult() != SolverResult.SAT) {
            throw new IllegalStateException("this.getSolverResult() != SAT");
        }
        return new Z3Interpretation(bestModel, z3Converter);
    }

    @Override
    public int getObjectiveVal()
    {
        return objectiveHandles.values()
                               .stream()
                               .map(handle -> Integer.valueOf(handle.toString()))
                               .reduce(0, Integer::sum);
    }

    @Override
    public void close()
    {
        this.ctx.close();
    }

    // mainly for debugging
    private String lockAssignmentToStr(Model model)
    {
        StringBuilder s = new StringBuilder();

        Map<String, Set<String>> lockSets = new HashMap<>();
        for (FuncDecl d : model.getDecls())
        {
            if (model.getConstInterp(d).toString().equals("true")
                && d.getName().toString().matches("f\\d+L\\d+"))
            {
                String dName = d.getName().toString();
                String frag = dName.substring(1, dName.indexOf("L"));
                String lock = dName.substring(dName.indexOf("L") + 1);
                if (!lockSets.containsKey(frag))
                    lockSets.put(frag, new HashSet<>());

                lockSets.get(frag).add(lock);
            }
        }

        for (String frag : lockSets.keySet().stream().sorted().collect(Collectors.toList()))
        {
            s.append(frag)
             .append(" -> {")
             .append(String.join(",", lockSets.get(frag)))
             .append("}\n");
        }

        return s.toString();
    }
}
