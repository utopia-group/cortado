package edu.utexas.cs.utopia.cortado.lockPlacement;

import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.util.sat.formula.Interpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.MaxSatSolverFailException;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.SolveAlreadyAttemptedException;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.WeightedPartialMaxSatSolver;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.WeightedPartialMaxSatSolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solve for a lock assignment using partial weighted max-sat.
 *
 * The current objective function is
 * weight1 * numberOfLocksUnusedPerCCR + weight2 * numberOfFragsWhichShareNoLocks
 */
public class LockAssignmentMaxSatSolver {
    private final static Logger log = LoggerFactory.getLogger(LockAssignmentMaxSatSolver.class.getName());
    private final WeightedPartialMaxSatSolverFactory wpmsFactory;
    private final LockAssignmentChecker lockAssignmentChecker;
    private final Map<SootMethod, Set<Unit>> predPreChecks;
    private final FragmentWeightStrategy fragmentWeightStrategy;

    /**
     * Build a max-sat solver for the given problem
     *  @param wpmsFactory the weighted partial max-sat solver to use
     * @param lockAssignmentChecker the lock-assignment checker used to build constraints
     * @param predPreChecks the predicate pre-checks for the flip predicate optimization
     * @param fragmentWeightStrategy the fragment weight assignment strategy to use
     */
    public LockAssignmentMaxSatSolver(@Nonnull WeightedPartialMaxSatSolverFactory wpmsFactory,
                                      @Nonnull LockAssignmentChecker lockAssignmentChecker,
                                      @Nonnull Map<SootMethod, Set<Unit>> predPreChecks, FragmentWeightStrategy fragmentWeightStrategy)
    {
        this.wpmsFactory = wpmsFactory;
        this.lockAssignmentChecker = lockAssignmentChecker;
        this.predPreChecks = predPreChecks;
        this.fragmentWeightStrategy = fragmentWeightStrategy;
    }

    /**
     * compute a locking by solving the max-sat problem
     *
     * @param numLocksUpperBound upper bound on the number of locks
     * @param timeoutInMS the timeout (in milliseconds)
     *
     * @return A lock assignment which is correct and maximizes the objective
     *          function, or null if the problem is UNSAT
     * @throws MaxSatSolverFailException if the solve fails
     */
    @Nullable
    private LockAssignmentObjValPair computeLocking(int numLocksUpperBound, long timeoutInMS)
            throws MaxSatSolverFailException
    {
        LockAssignmentSatEncoder satEncoder = new LockAssignmentSatEncoder(lockAssignmentChecker, predPreChecks, numLocksUpperBound, fragmentWeightStrategy);

        // build our encodings
        List<PropositionalFormula> isCorrect = satEncoder.computeCorrectnessConstraints();

        // get objectives
        List<PropositionalFormula> lockIsOffPerCCR = satEncoder.minimizeLocksPerCCR();
        List<PropositionalFormula> atomicFldIsOff = satEncoder.computeEncodingOfUnusedAtomics();
        List<LockAssignmentSatEncoder.EncodingWeightPair> fragsAreParallel = satEncoder.computeEncodingsOfParallelPairs();

        try(WeightedPartialMaxSatSolver solver = wpmsFactory.getSolver())
        {
            isCorrect.forEach(solver::addConstraint);
            // Make array of integers to allow lambda capture
            Integer[] lockIsOffPerCCRWeight = new Integer[1], atomicFldIsOffWeight = new Integer[1];
            switch (fragmentWeightStrategy) {
                case UNIFORM:
                    lockIsOffPerCCRWeight[0] = 1;
                    atomicFldIsOffWeight[0] = 1;
                    break;
                case PARALLELIZATION_OPPORTUNITIES:
                    lockIsOffPerCCRWeight[0] = 8;
                    atomicFldIsOffWeight[0] = 3;
                    break;
                default:
                    throw new IllegalStateException("Unrecognized fragmentWeightStrategy " + fragmentWeightStrategy);
            }
            lockIsOffPerCCR.forEach(c -> solver.addObjectiveClause(c, lockIsOffPerCCRWeight[0], "obj"));
            atomicFldIsOff.forEach(c -> solver.addObjectiveClause(c, atomicFldIsOffWeight[0], "obj"));
            fragsAreParallel.forEach(c -> solver.addObjectiveClause(c.formula, c.weight, "obj"));

            // solve the problem
            try
            {
                solver.maximizeObjective(timeoutInMS);
            } catch (SolveAlreadyAttemptedException ignored)
            {
            }

            WeightedPartialMaxSatSolver.SolverResult solverResult = solver.getSolverResult();
            switch (solverResult)
            {
                case SAT:
                    final Interpretation model = solver.getModel();
                    return new LockAssignmentObjValPair(satEncoder.getLockAssignment(model), solver.getObjectiveVal());
                case UNSAT:
                    return null;
                case SOLVER_FAILED:
                    throw new MaxSatSolverFailException();
                default:
                    throw new IllegalStateException("Unexpected solver result " + solverResult);
            }
        }
    }

    private static class LockAssignmentObjValPair
    {
        final ImmutableLockAssignment lockAssignment;

        final int objVal;

        public LockAssignmentObjValPair(ImmutableLockAssignment lockAssignment, int objVal)
        {
            this.lockAssignment = lockAssignment;
            this.objVal = objVal;
        }
    }

    /**
     * As {computeLocking(int, int, int, long)}, but on
     * a failure retry with a lower upper bound on the number of locks.
     *
     * The rate of decreases is:
     * - cut upper bound in half if it is greater than 8
     * - reduce upper bound by 1 otherwise
     *
     * @return as {computeLocking(int, int, int, long)}. If the
     *          problem is UNSAT for *any* upper bound on the number
     *          of locks, null is returned.
     * @throws MaxSatSolverFailException If the solver fails with
     *      an upper bound of 1.
     */
    @Nullable
    public ImmutableLockAssignment computeLockingWithRetries(long timeoutInMS)
            throws MaxSatSolverFailException
    {
        // grab the current profiler
        final CortadoMonitorProfiler profiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
        int numLocksUpperBound = lockAssignmentChecker.getNumLocksUpperBound();
        log.debug("Starting weighted max-sat problem with " + numLocksUpperBound + " locks.");
        profiler.recordInitialLockUpperBound(numLocksUpperBound);
        profiler.pushEvent("weightedMaxSat");

        ImmutableLockAssignment lockAssignment;
        try
        {
            if (numLocksUpperBound < 15)
            {
                lockAssignment = topDownSearch(numLocksUpperBound, timeoutInMS);
            }
            else
            {
                lockAssignment = bottomUpSearch(numLocksUpperBound, timeoutInMS);
            }


            if (lockAssignment != null)
            {
                log.debug("max-sat solve successful with " + lockAssignment.numLocks() + " of possible " + numLocksUpperBound + " locks used.");
                profiler.recordNumLocksUsed(lockAssignment.numLocks());
                profiler.recordNumAtomicsUsed(lockAssignment.numAtomics());
            }
            return lockAssignment;
        }
        finally
        {
            profiler.popEvent();
        }
    }

    private ImmutableLockAssignment bottomUpSearch(int numLocksUpperBound, long timeoutInMS)
    {
        CortadoMonitorProfiler profiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
        LockAssignmentObjValPair bestLockAssignment = null;

        int i = 1;
        while (i <= numLocksUpperBound)
        {
            try
            {
                LockAssignmentObjValPair lockAssignObjVal = computeLocking(i, timeoutInMS);
                // if problem was unsat, break early
                if (lockAssignObjVal == null)
                {
                    log.debug("max-sat constraints are unsatisfiable.");
                    return null;
                }
                else if (bestLockAssignment != null && lockAssignObjVal.objVal >= bestLockAssignment.objVal)
                {
                    return bestLockAssignment.lockAssignment;
                }
                else
                {
                    bestLockAssignment = lockAssignObjVal;
                }
            } catch (MaxSatSolverFailException e)
            {
                log.warn("weighted max-sat solve failed with " + numLocksUpperBound + " locks.");
                profiler.recordWeightedMaxSatTimeout();
                return bestLockAssignment != null ? bestLockAssignment.lockAssignment : null;
            }
            i++;
        }

        assert bestLockAssignment != null;
        return bestLockAssignment.lockAssignment;
    }

    private ImmutableLockAssignment topDownSearch(int numLocksUpperBound, long timeoutInMS) throws MaxSatSolverFailException
    {
        CortadoMonitorProfiler profiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();

        // initialize our lockAssignment and lock upper bound
        LockAssignmentObjValPair lockAssignObjVal = null;
        boolean solveComplete = false;
        // repeatedly try to solve until number of locks upper
        // bound is 1, or we successfully solved
        while (numLocksUpperBound >= 1 && !solveComplete)
        {
            solveComplete = true;
            try
            {
                lockAssignObjVal = computeLocking( numLocksUpperBound, timeoutInMS);
                // if problem was unsat, break early
                if (lockAssignObjVal == null)
                {
                    log.debug("max-sat constraints are unsatisfiable.");
                    return null;
                }
            } catch (MaxSatSolverFailException e)
            {
                log.warn("weighted max-sat solve failed with " + numLocksUpperBound + " locks.");
                solveComplete = false;
                profiler.recordWeightedMaxSatTimeout();
                if (numLocksUpperBound <= 1) throw e;
                numLocksUpperBound = numLocksUpperBound > 8 ? numLocksUpperBound / 2 : numLocksUpperBound - 1;
                log.warn("Retrying max-sat solve with " + numLocksUpperBound + " locks.");
            }
        }
        assert lockAssignObjVal != null;

        // return the solver
        return lockAssignObjVal.lockAssignment;
    }
}
