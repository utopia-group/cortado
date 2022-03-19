package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.Streams;
import edu.utexas.cs.utopia.cortado.util.sat.enumeration.WeightedPartialMaxSatEnumerator;
import edu.utexas.cs.utopia.cortado.util.sat.enumeration.WeightedPartialMaxSatEnumeratorFactory;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.SolveAlreadyAttemptedException;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.WeightedPartialMaxSatSolver;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.WeightedPartialMaxSatSolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.util.sat.enumeration.WeightedPartialMaxSatEnumerator.EnumerationStatus.NOT_STARTED;
import static edu.utexas.cs.utopia.cortado.util.sat.enumeration.WeightedPartialMaxSatEnumerator.MultiObjectiveOptimizationPriority.PARETO_FRONT;

/**
 * Used to enumerate lock assignments along the pareto front
 */
public class LockAssignmentMaxSatEnumerator
{
    private final static Logger log = LoggerFactory.getLogger(LockAssignmentMaxSatEnumerator.class.getName());
    private final WeightedPartialMaxSatEnumeratorFactory wpmseFactory;
    private final LockAssignmentChecker lockAssignmentChecker;
    private final Map<SootMethod, Set<Unit>> predPreChecks;
    private final WeightedPartialMaxSatSolverFactory wpmsFactory;
    private final FragmentWeightStrategy fragmentWeightStrategy;

    /**
     * Build an enumerator for the given problem
     *  @param wpmseFactory the weighted partial max-sat enumerator to use
     * @param wpmsFactory the weighted partial max-sat solver to use (for determining lock upper bound)
     * @param lockAssignmentChecker the lock-assignment checker used to build constraints
     * @param predPreChecks the predicate pre-checks for the flip predicate optimization
     */
    public LockAssignmentMaxSatEnumerator(@Nonnull WeightedPartialMaxSatEnumeratorFactory wpmseFactory,
                                          @Nonnull WeightedPartialMaxSatSolverFactory wpmsFactory,
                                          @Nonnull LockAssignmentChecker lockAssignmentChecker,
                                          @Nonnull Map<SootMethod, Set<Unit>> predPreChecks,
                                          @Nonnull FragmentWeightStrategy fragmentWeightStrategy)
    {
        this.wpmseFactory = wpmseFactory;
        this.wpmsFactory = wpmsFactory;
        this.lockAssignmentChecker = lockAssignmentChecker;
        this.predPreChecks = predPreChecks;
        this.fragmentWeightStrategy = fragmentWeightStrategy;
    }

    /**
     * enumerate lockings along pareto front between
     * (# locks off per CCR + # fields not kept atomic) vs. (# fragments in parallel)
     * by solving the max-sat problem.
     *
     * Picks # of locks by increasing number of locks until a maximal value of (# fragments in parallel)
     * is reached.
     *
     * @param timeoutInMS the timeout (in milliseconds)
     * @param timeoutInMSForUpperBound the timeout (in milliseconds) for determining the lock upper bound
     *
     * @return the enumerated partial interpretations
     */
    @Nonnull
    public List<ImmutableLockAssignment> enumerateLockings(long timeoutInMS, long timeoutInMSForUpperBound)
    {
        // figure out number of locks to use
        log.debug("Attempting to compute lock upper bound");
        int numLocksUpperBound = computeLockUpperBound(timeoutInMSForUpperBound);
        LockAssignmentSatEncoder satEncoder = new LockAssignmentSatEncoder(
                lockAssignmentChecker,
                predPreChecks,
                numLocksUpperBound,
                fragmentWeightStrategy);
        log.debug("Lock upper bound of " + numLocksUpperBound + " determined.");

        // build our enumerator
        try (WeightedPartialMaxSatEnumerator enumerator = wpmseFactory.getEnumerator())
        {
            // build our constraints
            satEncoder.computeCorrectnessConstraints()
                    .forEach(enumerator::addConstraint);
            satEncoder.computeSymmetryBreakingConstraints()
                    .forEach(enumerator::addConstraint);

            // get objectives
            List<PropositionalFormula> lockIsOffPerCCR = satEncoder.minimizeLocksPerCCR();
            List<PropositionalFormula> atomicFldIsOff = satEncoder.computeEncodingOfUnusedAtomics();
            List<LockAssignmentSatEncoder.EncodingWeightPair> fragsAreParallel = satEncoder.computeEncodingsOfParallelPairs();

            // set objectives for our enumerator
            final String LOCK_IS_OFF_GROUP = "LOCK_IS_OFF",
                    FRAG_IS_PARALLEL_GROUP = "FRAG_IS_PARALLEL";
            lockIsOffPerCCR.forEach(c -> enumerator.addObjectiveClause(c, 2, LOCK_IS_OFF_GROUP));
            atomicFldIsOff.forEach(c -> enumerator.addObjectiveClause(c, 1, LOCK_IS_OFF_GROUP));
            fragsAreParallel.forEach(c -> enumerator.addObjectiveClause(c.formula, 1, FRAG_IS_PARALLEL_GROUP));

            // solve the problem
            log.debug("Beginning enumeration");
            try
            {
                enumerator.enumerateMaximalSolutions(
                        PARETO_FRONT,
                        satEncoder.getLockMappingVars(),
                        timeoutInMS,
                        1000);  // TODO: Make this a parameter
            } catch (SolveAlreadyAttemptedException ignored)
            {
            }
            switch (enumerator.getEnumerationStatus())
            {
                case NOT_STARTED:
                    throw new IllegalStateException("enumerator in state " + NOT_STARTED + " after already started.");
                case TIMEOUT:
                    log.warn("Enumeration timed out. Returned partial interpretations may be incomplete");
                case COMPLETE:
                    log.debug("enumeration complete");
                    //noinspection UnstableApiUsage
                    return enumerator.getInterpretations()
                            .stream()
                            .map(pi -> pi.getCompletions(satEncoder.getLockMappingVars()))
                            .flatMap(Streams::stream)
                            .map(satEncoder::getLockAssignment)
                            .collect(Collectors.toList());
                default:
                    throw new IllegalStateException("Unrecognized enumeration state " + enumerator.getEnumerationStatus());
            }
        }
    }

    /**
     * Compute upper bound on locks by repeatedly solving problem with more locks
     * until no more fragments can run in parallel
     * @param timeoutInMS the timeout per solve
     * @return the upper bound on number of locks
     */
    int computeLockUpperBound(long timeoutInMS)
    {
        Integer numLocksUpperBound = null;
        Integer prevValue = null;
        Integer currentValue = null;
        boolean solveFailed = false;
        // even though we're maximizing, Z3's optimization handle returns MAX - (achieved objective value)
        // so we want currentValue to be less than prevValue
        while((prevValue == null || currentValue < prevValue) && !solveFailed)
        {
            prevValue = currentValue;

            int candidateNumLocksUpperBound;
            if(numLocksUpperBound == null)
            {
                candidateNumLocksUpperBound = 1;
            }
            else
            {
                candidateNumLocksUpperBound = numLocksUpperBound + 1;
            }
            LockAssignmentSatEncoder satEncoder = new LockAssignmentSatEncoder(lockAssignmentChecker,
                    predPreChecks,
                    candidateNumLocksUpperBound, fragmentWeightStrategy);

            // build our encodings
            List<PropositionalFormula> isCorrect = satEncoder.computeCorrectnessConstraints();

            // get objectives
            List<LockAssignmentSatEncoder.EncodingWeightPair> fragsAreParallel = satEncoder.computeEncodingsOfParallelPairs();
            try(WeightedPartialMaxSatSolver solver = wpmsFactory.getSolver())
            {
                isCorrect.forEach(solver::addConstraint);
                fragsAreParallel.forEach(c -> solver.addObjectiveClause(c.formula, 1, "parallel"));

                // solve
                try
                {
                    solver.maximizeObjective(timeoutInMS);
                } catch (SolveAlreadyAttemptedException ignored) { }
                assert solver.getSolverResult() != WeightedPartialMaxSatSolver.SolverResult.NO_SOLVE_ATTEMPTED;
                if(solver.getSolverResult() == WeightedPartialMaxSatSolver.SolverResult.SAT)
                {
                    currentValue = solver.getObjectiveVal();
                    if(!currentValue.equals(prevValue))
                    {
                        assert prevValue == null || currentValue < prevValue;
                        numLocksUpperBound = candidateNumLocksUpperBound;
                    }
                }
                else if(solver.getSolverResult() == WeightedPartialMaxSatSolver.SolverResult.UNSAT)
                {
                    throw new IllegalStateException("Constraints were UNSAT");
                }
                else
                {
                    assert solver.getSolverResult() == WeightedPartialMaxSatSolver.SolverResult.SOLVER_FAILED;
                    solveFailed = true;
                }
            }
        }
        if(numLocksUpperBound == null)
        {
            throw new IllegalStateException("Solve failed with upper bound of 1 lock");
        }
        return numLocksUpperBound;
    }

}
