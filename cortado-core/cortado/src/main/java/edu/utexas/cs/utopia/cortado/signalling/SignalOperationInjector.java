package edu.utexas.cs.utopia.cortado.signalling;

import com.google.common.collect.ImmutableSortedSet;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.lockPlacement.ImmutableLockAssignment;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils.*;
import static edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils.*;
import static edu.utexas.cs.utopia.cortado.util.soot.SootFieldUtils.getOrAddField;
import static edu.utexas.cs.utopia.cortado.util.soot.SootFieldUtils.getOrAddLocalAliasOfField;
import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.getOrAddLocal;
import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.retrieveLocal;
import static edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils.getExplicitExitUnits;

public class SignalOperationInjector extends BodyTransformer
{
    // Signaling map.
    private final Map<CCR, Set<SignalOperation>> sigOpsPerCCR;

    // Locking-related maps.

    // Locks for Fragments.
    private final ImmutableLockAssignment lockAssignment;
    // Locks for predicates.
    private final Map<SootMethod, Integer> predToLockID;

    private final Map<Unit, Unit> yieldToAwait;

    /**
     *
     * @param sigOpsPerCCR map from CCRs to the signal operations which need to be performed
     * @param lockAssignment the lock assignment
     * @param predToLockID the association from each guard to its lock
     */
    public SignalOperationInjector(@Nonnull Map<CCR, Set<SignalOperation>> sigOpsPerCCR,
                                   @Nonnull ImmutableLockAssignment lockAssignment,
                                   @Nonnull Map<SootMethod, Integer> predToLockID,
                                   Map<Unit, Unit> yieldToAwait)
    {
        this.sigOpsPerCCR = sigOpsPerCCR;
        this.lockAssignment = lockAssignment;
        this.predToLockID = predToLockID;
        this.yieldToAwait = yieldToAwait;
    }

    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map)
    {
        // If body not a CCR method, this is a NOP
        final SootMethod inMethod = body.getMethod();
        if(!CCR.isCCRMethod(inMethod)) {
            return;
        }

        yieldToAwait.forEach((yield, await) -> {
            UnitPatchingChain uts = body.getUnits();
            if (uts.contains(yield))
                uts.swapWith(yield, await);
        });

        // Otherwise, see if its CCR needs to do any signalling
        final List<CCR> ccrs = sigOpsPerCCR.keySet()
                .stream()
                .filter(ccr -> inMethod.equals(ccr.getAtomicSection()))
                .collect(Collectors.toList());
        assert ccrs.size() <= 1;
        // if not, nothing to do
        if(ccrs.isEmpty()) {
            return;
        }
        // get soot objects
        final UnitPatchingChain units = body.getUnits();
        final ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(inMethod);
        final Jimple j = Jimple.v(); // just a convenient alias

        // get the exit units
        final Set<Unit> explicitExitUnits = getExplicitExitUnits(inMethod);

        // get CCR
        final CCR ccr = ccrs.get(0);
        final Map<Unit, Fragment> unitFragmentMap = ccr.computeUnitToFragmentMap();

        // go ahead and grab the lock and condition class/types
        SootClass lockClass = getLockClass();
        RefType lockType = lockClass.getType();
        // For each exiting fragment
        for(Unit exitUnit : explicitExitUnits)
        {
            // For each edge to an exit Unit
            for(Unit exitUnitPred : cfg.getPredsOf(exitUnit))
            {
                // Figure out fragment of predecessor.
                // We may have to iterate because of unlocks inserted on this edge
                Fragment exitPredFrag = unitFragmentMap.get(exitUnitPred);
                Unit predOfExitUnitPred = exitUnitPred;
                while(exitPredFrag == null)
                {
                    predOfExitUnitPred = units.getPredOf(predOfExitUnitPred);
                    assert predOfExitUnitPred != null;
                    exitPredFrag = unitFragmentMap.get(predOfExitUnitPred);
                }
                // locks held at the predecessor.
                final ImmutableSortedSet<Integer> fragLocks = lockAssignment.getLocksInOrder(exitPredFrag);
                // lock -> signal operations on that lock we are performing
                final Map<Integer, List<SignalOperation>> lockToSigOp = sigOpsPerCCR.get(ccr)
                        .stream()
                        .collect(Collectors.groupingBy(sigOp -> predToLockID.get(sigOp.getPredicate())));

                // Handle signal operations we already hold the lock of
                final Map<Integer, List<SignalOperation>> signalsOnLocksWeHold = lockToSigOp.entrySet()
                        .stream()
                        .filter(lockSigOpsPair -> fragLocks.contains(lockSigOpsPair.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                for(Map.Entry<Integer, List<SignalOperation>> lockSigOpsPair : signalsOnLocksWeHold.entrySet())
                {
                    final Integer lockID = lockSigOpsPair.getKey();
                    assert lockID != null;
                    // find the unlock statement of this lock
                    Unit unlockStmt = exitUnitPred;
                    while (!isUnlock(body, unlockStmt, lockID))
                    {
                        // We should be able to find the unlock statement by just going up
                        // the units from the predecessor
                        assert !(unlockStmt == ((JimpleBody) body).getFirstNonIdentityStmt());
                        unlockStmt = units.getPredOf(unlockStmt);
                    }
                    for(SignalOperation sigOp : lockSigOpsPair.getValue())
                    {
                        final List<Unit> signalOps = buildSignal(body, sigOp, lockID);
                        assert !signalOps.isEmpty();
                        units.insertBefore(signalOps, unlockStmt);
                        final List<Unit> skipSignalIfNotCond = buildSignalConditionCheck(body, sigOp, unlockStmt, false);
                        if(skipSignalIfNotCond != null)
                        {
                            assert !skipSignalIfNotCond.isEmpty();
                            units.insertBefore(skipSignalIfNotCond, signalOps.get(0));
                        }
                    }
                } // End for each signal operation we already hold the lock for

                // Handle signal operations we need to acquire the lock of
                final Map<Integer, List<SignalOperation>> signalsOnLocksWeMustAcquire = lockToSigOp.entrySet()
                        .stream()
                        .filter(lockSigOpsPair -> !fragLocks.contains(lockSigOpsPair.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                for(Map.Entry<Integer, List<SignalOperation>> lockSigOpsPair : signalsOnLocksWeMustAcquire.entrySet())
                {
                    final Integer lockID = lockSigOpsPair.getKey();
                    final List<SignalOperation> sigOps = lockSigOpsPair.getValue();
                    assert lockID != null;

                    // Build a lock(); unlock(); statement on the edge

                    // Not safe to assume LockInserter has created and instantiated
                    // these variables since it only builds locks for ones
                    // which are obtained in this method.
                    final String lockFieldName = SootNamingUtils.getLockFieldName(lockID);
                    final SootField lockField = inMethod.getDeclaringClass().getField(lockFieldName, lockType);
                    final String lockLocalName = SootNamingUtils.getAliasingLocalNameForLock(lockID);
                    final Local lockLocal = getOrAddLocalAliasOfField(lockLocalName, lockField, inMethod);
                    // build the lock and unlock
                    final InvokeStmt lockStmt = j.newInvokeStmt(
                            j.newVirtualInvokeExpr(lockLocal, getLockMethod().makeRef())
                    );
                    final InvokeStmt unlockStmt = j.newInvokeStmt(
                            j.newVirtualInvokeExpr(lockLocal, getUnlockMethod().makeRef())
                    );
                    final NopStmt landingPadAfterUnlock = Jimple.v().newNopStmt();
                    SootUnitUtils.insertOnEdge(Arrays.asList(lockStmt, unlockStmt, landingPadAfterUnlock),
                            exitUnitPred,
                            exitUnit,
                            body);

                    // Insert each signal operation inside the lock acquire
                    boolean allSignalsAreConditional = true;
                    for(SignalOperation sigOp : sigOps)
                    {
                        final List<Unit> signalOps = buildSignal(body, sigOp, lockID);
                        assert !signalOps.isEmpty();
                        units.insertBefore(signalOps, unlockStmt);
                        final List<Unit> skipSignalIfNotCond = buildSignalConditionCheck(body, sigOp, unlockStmt, false);
                        // If only signalling one condition on this lock,
                        // don't need the condition inside the lock(); unlock() since the condition will also
                        // be used to guard the lock acquire
                        if(skipSignalIfNotCond != null && sigOps.size() > 1)
                        {
                            assert !skipSignalIfNotCond.isEmpty();
                            units.insertBefore(skipSignalIfNotCond, signalOps.get(0));
                        }
                        else if(skipSignalIfNotCond == null)
                        {
                            allSignalsAreConditional = false;
                        }
                    }
                    // If all signal operations are conditional, avoid grabbing the lock
                    // if all conditions are false
                    if(allSignalsAreConditional)
                    {
                        final GotoStmt skipLockAcquire = j.newGotoStmt(landingPadAfterUnlock);
                        SootUnitUtils.insertOnEdge(skipLockAcquire, exitUnitPred, lockStmt, body);
                        Unit succOfExitUnitPred = skipLockAcquire;
                        for(SignalOperation sigOp : sigOps)
                        {
                            final List<Unit> acquireLockIfCond = buildSignalConditionCheck(body, sigOp, lockStmt, true);
                            assert acquireLockIfCond != null;
                            assert !acquireLockIfCond.isEmpty();
                            SootUnitUtils.insertOnEdge(acquireLockIfCond, exitUnitPred, succOfExitUnitPred, body);
                            succOfExitUnitPred = acquireLockIfCond.get(0);
                        }
                    }
                    // update predecessor
                    exitUnitPred = landingPadAfterUnlock;
                } // End for each signal operation we already hold the lock for
            } // End for each predecessor of exit unit
        } // End for each exiting fragment
    }

    /**
     * Builds if ((predCondition && !preCheck) == truthValue) goto target.
     *
     * (where predCondition is true if sigOp has no condition, and preCheck is
     *  false if the flipPredOpt is not applied to this sigOp)
     *
     * @param body the body to build in
     * @param sigOp the signal operation to build a condition for
     * @param target the unit to jump to if the condition matches truthValue
     * @param truthValue the desired truth value
     * @return a list of units, or null if there is no condition for the signal
     */
    @Nullable
    private List<Unit> buildSignalConditionCheck(@Nonnull Body body, @Nonnull SignalOperation sigOp, @Nonnull Unit target, boolean truthValue)
    {
        boolean performFlipPredOpt = sigOp.performFlipPredOpt();
        if(!sigOp.isConditional() && !performFlipPredOpt)
        {
            return null;
        }
        // Build condition if (predCondition && !preCheck) goto target (if truthValue is true)
        //                 (!predCondition || preCheck) goto target (if truthValue is false)
        final IntConstant falseConstant = IntConstant.v(0);
        final IntConstant trueConstant = IntConstant.v(1);

        if(performFlipPredOpt && !sigOp.isConditional())
        {
            final IntConstant desiredTruthValue = truthValue ? falseConstant : trueConstant;
            return Collections.singletonList(gotoTargetIf(body, sigOp, target, "_pre", desiredTruthValue));
        }
        else if(!performFlipPredOpt && sigOp.isConditional())
        {
            final IntConstant desiredTruthValue = truthValue ? trueConstant : falseConstant;
            return Collections.singletonList(gotoTargetIf(body, sigOp, target, "", desiredTruthValue));
        }
        else {
            assert performFlipPredOpt && sigOp.isConditional();
            Local predEvalLocal = retrieveLocal(getLocalForPredEval(sigOp), BooleanType.v(), body);
            Local prePredEvalLocal = retrieveLocal(getLocalForPredEval(sigOp) + "_pre", BooleanType.v(), body);
            assert predEvalLocal != null;
            assert prePredEvalLocal != null;
            if(truthValue)
            {
                return gotoTargetIfShortCircuitAnd(predEvalLocal, true, prePredEvalLocal, false, target);
            }
            else
            {
                return gotoTargetIfShortCircuitOr(predEvalLocal, false, prePredEvalLocal, true, target);
            }
        }
    }

    /**
     * Short circuit (l0 == desiredTruthValue0) && (l1 == desiredTruthValue1)
     *
     * @param l0 first local
     * @param desiredTruthValue0 desired truth value of first local
     * @param l1 second local
     * @param desiredTruthValue1 desired truth value of second local
     * @param target the target if and evaluates to true
     * @return a list of units to evaluate the short-circuited and
     */
    @Nonnull
    private List<Unit> gotoTargetIfShortCircuitAnd(@Nonnull Local l0, boolean desiredTruthValue0,
                                                   @Nonnull Local l1, boolean desiredTruthValue1,
                                                   @Nonnull Unit target)
    {
        final Jimple j = Jimple.v();
        final NopStmt nopStmt = j.newNopStmt();
        final IfStmt gotoNopIfLocal0IsFalse = j.newIfStmt(
                j.newEqExpr(l0, IntConstant.v(desiredTruthValue0 ? 0 : 1)),
                nopStmt
        );
        final IfStmt gotoNopIfLocal1IsFalse = j.newIfStmt(
                j.newEqExpr(l1, IntConstant.v(desiredTruthValue1 ? 0 : 1)),
                nopStmt
        );
        final GotoStmt gotoTarget = j.newGotoStmt(target);
        return Arrays.asList(
                gotoNopIfLocal0IsFalse,
                gotoNopIfLocal1IsFalse,
                gotoTarget,
                nopStmt
        );
    }

    /**
     * Short circuit (l0 == desiredTruthValue0) || (l1 == desiredTruthValue1)
     *
     * @param l0 first local
     * @param desiredTruthValue0 desired truth value of first local
     * @param l1 second local
     * @param desiredTruthValue1 desired truth value of second local
     * @param target the target if and evaluates to true
     * @return a list of units to evaluate the short-circuited or
     */
    @Nonnull
    private List<Unit> gotoTargetIfShortCircuitOr(@Nonnull Local l0, boolean desiredTruthValue0,
                                                   @Nonnull Local l1, boolean desiredTruthValue1,
                                                   @Nonnull Unit target)
    {
        final Jimple j = Jimple.v();
        final IfStmt gotoTargetIfLocal0IsTrue = j.newIfStmt(
                j.newEqExpr(l0, IntConstant.v(desiredTruthValue0 ? 1 : 0)),
                target
        );
        final IfStmt gotoTargetIfLocal1IsTrue = j.newIfStmt(
                j.newEqExpr(l1, IntConstant.v(desiredTruthValue1 ? 1 : 0)),
                target
        );
        return Arrays.asList(gotoTargetIfLocal0IsTrue, gotoTargetIfLocal1IsTrue);
    }

    /**
     * Build a signalling operation
     *
     * @param body the soot body
     * @param sigOp the signal operation
     * @param lockID the lock of the signal. This lock must have a local alias in the method
     *               (see {@link SootNamingUtils#getAliasingLocalNameForLock(int)}).
     * @return a list of straight-line units which signals (does not include any conditions, just the signal/broadcast)
     */
    @Nonnull
    private List<Unit> buildSignal(@Nonnull Body body, @Nonnull SignalOperation sigOp, @Nonnull Integer lockID)
    {
        // the soot classes and types we'll need to build signals/locks
        final SootClass condVarClass = getConditionClass();
        final SootClass inClass = body.getMethod().getDeclaringClass();
        final RefType condVarType = condVarClass.getType();
        final RefType lockType = getLockClass().getType();

        // Build a local to hold the condition variable
        final SootMethod pred = sigOp.getPredicate();
        Local condVarInit = getOrAddLocal(getInitLocalNameForConditionVar(lockID, pred), condVarType, body);
        SootField condVarFld = getOrAddField(getConditionVarFieldName(lockID, pred), condVarType, inClass);
        SootMethod sigOpMethod = condVarClass.getMethod(sigOp.isBroadCast() ? "signalAll" : "signal", Collections.emptyList());

        // we can assume this local already got inserted
        Local lockLocal = retrieveLocal(getAliasingLocalNameForLock(lockID), lockType, body);
        assert lockLocal != null;
        // initialize the condVar local
        final Jimple j = Jimple.v();
        final AssignStmt initCondVarLocal = j.newAssignStmt(
                condVarInit,
                j.newInstanceFieldRef(body.getThisLocal(), condVarFld.makeRef())
        );
        // build the signal
        return Arrays.asList(
                        // Initialize CondVar local
                        initCondVarLocal,
                        // Perform signal operation
                        j.newInvokeStmt(j.newInterfaceInvokeExpr(condVarInit, sigOpMethod.makeRef()))
                );
    }

    /**
     * Return an {@link IfStmt} which jumps to target
     * if op does not need to signal
     *
     * @param body the body that op is signalling in
     * @param op the signal operation
     * @param target where to jump if op does not need to signal
     * @return the {@link IfStmt}
     */
    private IfStmt gotoTargetIf(Body body, SignalOperation op, Unit target, String localSuffix, IntConstant truthVal)
    {
        Jimple j = Jimple.v();
        Local predEvalLocal = retrieveLocal(getLocalForPredEval(op) + localSuffix, BooleanType.v(), body);
        return j.newIfStmt(j.newEqExpr(predEvalLocal, truthVal), target);
    }

    /**
     * @param body the body holding unit
     * @param unit the unit
     * @param lockID the lock id
     * @return true iff unit is a statement unlocking the lock with id lockID
     *      (specifically, using the lock local of name {@link SootNamingUtils#getAliasingLocalNameForLock(int)}).
     */
    private boolean isUnlock(Body body, Unit unit, int lockID)
    {
        if(unit instanceof InvokeStmt)
        {
            InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
            if(invokeExpr.getMethod().equals(getUnlockMethod()))
            {
                assert invokeExpr instanceof InstanceInvokeExpr;
                final InstanceInvokeExpr unlockInvocation = (InstanceInvokeExpr) invokeExpr;
                String lockLocalName = SootNamingUtils.getAliasingLocalNameForLock(lockID);
                Local lockLocal = getOrAddLocal(lockLocalName, getLockClass().getType(), body);
                return unlockInvocation.getBase().equals(lockLocal);
            }
        }
        return false;
    }
}
