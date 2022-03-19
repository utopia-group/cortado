package edu.utexas.cs.utopia.cortado.util.sat.enumeration;

import com.microsoft.z3.*;
import edu.utexas.cs.utopia.cortado.util.sat.backends.Z3Converter;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class Z3WeightedPartialMaxSatEnumerator extends WeightedPartialMaxSatEnumerator
{
    private final Context ctx;
    private final Optimize optimizer;
    private static final Logger log = LoggerFactory.getLogger(Z3WeightedPartialMaxSatEnumerator.class.getName());
    private final Z3Converter z3Converter;

    final List<PartialInterpretation> interpretations = new ArrayList<>();
    Map<String, Optimize.Handle> objectiveHandles = new HashMap<>();
    final private Map<PartialInterpretation, Map<String, Integer>> interpretationToObjectiveValues = new HashMap<>();

    // store the constraints so that we can enumerate the pareto front more completely
    final private List<PropositionalFormula> constraints = new ArrayList<>();
    final private Map<String, List<PropositionalFormula>> softConstraints = new HashMap<>();

    Z3WeightedPartialMaxSatEnumerator(@Nonnull Context ctx)
    {
        this.ctx = ctx;
        this.z3Converter = new Z3Converter(ctx);
        this.optimizer = ctx.mkOptimize();
    }

    @Override
    public void addConstraint(@Nonnull PropositionalFormula constraint)
    {
        BoolExpr z3constraint = z3Converter.convertToZ3(constraint);
        this.optimizer.Assert(z3constraint);
        this.constraints.add(constraint);
    }

    @Override
    public void addObjectiveClause(@Nonnull PropositionalFormula formula, int weight, @Nonnull String objectiveName)
    {
        if(weight < 0) {
            throw new IllegalArgumentException("negative weight");
        }
        BoolExpr z3formula = z3Converter.convertToZ3(formula);
        objectiveHandles.put(objectiveName, this.optimizer.AssertSoft(z3formula, weight, objectiveName));
        if(!this.softConstraints.containsKey(objectiveName))
        {
            this.softConstraints.put(objectiveName, new ArrayList<>());
        }
        this.softConstraints.get(objectiveName).add(formula);
    }

    @Override
    EnumerationStatus enumerate(@Nonnull MultiObjectiveOptimizationPriority multiObjectiveOptimizationPriority,
                                Collection<PropositionalFormula> nonAuxiliaryVariables,
                                long timeoutInMs,
                                int maxSolves)
    {
        assert getEnumerationStatus() == EnumerationStatus.NOT_STARTED;
        // set timeout
        final Params params = ctx.mkParams();
        if(timeoutInMs != (int) timeoutInMs)
        {
            throw new IllegalArgumentException("timeoutInMs of " + timeoutInMs + " is not convertible to an int.");
        }
        params.add("timeout", (int) timeoutInMs);
        String optPriority;
        switch (multiObjectiveOptimizationPriority)
        {
            case INDEPENDENT:
                optPriority = "independent";
                break;
            case LEXICOGRAPHIC:
                optPriority = "lexicographic";
                break;
            case PARETO_FRONT:
                optPriority = "pareto";
                break;
            default:
                throw new IllegalStateException("Unrecognized strategy " + multiObjectiveOptimizationPriority);
        }
        params.add("opt.priority", optPriority);
        this.optimizer.setParameters(params);
        // perform enumeration
        Status solverZ3Status;

        do
        {
            solverZ3Status = this.optimizer.Check();
            if (solverZ3Status == Status.SATISFIABLE)
            {
                log.debug("Identified a model");
                final Map<String, Integer> objectiveValues = objectiveHandles.entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                handle -> Integer.parseInt(handle.getValue().toString())
                        ));
                // now identify all other models which achieve these objective values
                final AchievedObjectiveValueModelEnumerator achievedObjectiveValueModelEnumerator = new Z3AchievedObjectiveValueModelEnumerator(ctx);
                constraints.forEach(achievedObjectiveValueModelEnumerator::addConstraint);
                softConstraints.forEach((objectiveGroup, constraints) -> {
                    final Optimize.Handle handle = objectiveHandles.get(objectiveGroup);
                    final int value = Integer.parseInt(handle.getValue().toString());
                    // (Z3 optimizes, but the value it gives is the number of False, not the number of True)
                    final List<PropositionalFormula> negatedConstraints = constraints.stream()
                            .map(c -> c.getBuilder().mkNOT(c))
                            .collect(Collectors.toList());
                    achievedObjectiveValueModelEnumerator.requireExactlyKAreTrue(negatedConstraints, value);
                });
                AchievedObjectiveValueModelEnumerator.EnumerationStatus status = null;
                try
                {
                    status = achievedObjectiveValueModelEnumerator.enumerateSolutions(nonAuxiliaryVariables,
                            timeoutInMs,
                            maxSolves);
                } catch (EnumerationAlreadyAttemptedException ignored) { }
                if(status == AchievedObjectiveValueModelEnumerator.EnumerationStatus.TIMEOUT)
                {
                    log.warn("Enumeration of a pareto-front representative's equivalence class timed out");
                }
                else if(status == AchievedObjectiveValueModelEnumerator.EnumerationStatus.MAX_SOLVES_REACHED)
                {
                    log.warn("Max solves reached on a pareto-front representative's equivalence class");
                }
                final List<PartialInterpretation> interpretations = achievedObjectiveValueModelEnumerator.getInterpretations();
                log.debug(interpretations.size() + " members identified at one point on the pareto front");
                assert !interpretations.isEmpty();
                interpretations.forEach(interp ->  interpretationToObjectiveValues.put(interp, objectiveValues));
                this.interpretations.addAll(interpretations);
            }
        }
        while (objectiveHandles.size() > 1 && solverZ3Status == Status.SATISFIABLE);

        EnumerationStatus status;
        if(solverZ3Status == Status.UNKNOWN)
        {
            status = EnumerationStatus.TIMEOUT;
            log.debug("Enumeration timed out");
        }
        else
        {
            status = EnumerationStatus.COMPLETE;
            log.debug("Enumeration completed");
        }
        return status;
    }

    @Nonnull
    @Override
    public List<PartialInterpretation> getInterpretations()
    {
        if(enumerationStatus == EnumerationStatus.NOT_STARTED)
        {
            throw new IllegalStateException("Enumeration not started");
        }
        if(enumerationStatus == EnumerationStatus.TIMEOUT)
        {
            log.warn("Requesting interpretations from an enumeration which timed out");
        }
        return interpretations;
    }

    @Nonnull
    @Override
    public Map<PartialInterpretation, Map<String, Integer>> getObjectiveValues()
    {
        return interpretationToObjectiveValues;
    }

    @Override
    public void close()
    {
        ctx.close();
    }
}
