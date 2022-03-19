package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.ImmutableSortedSet;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootMethodUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils.*;
import static edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils.*;
import static edu.utexas.cs.utopia.cortado.util.soot.SootFieldUtils.getOrAddField;
import static edu.utexas.cs.utopia.cortado.util.soot.SootFieldUtils.getOrAddLocalAliasOfField;

/**
 * Insert locks according to a given map from fragments
 * to locks
 *
 * @author Ben_Sepanski
 */
public class LockInserter extends BodyTransformer
{

    private final Map<Body, CCR> body2ccr;
    private final ImmutableLockAssignment lockAssignment;

    /**
     * Build a LockInserter
     *
     * @param ccrs        the ccrs
     * @param lockAssignment  a lock assignment
     */
    public LockInserter(@Nonnull List<CCR> ccrs, @Nonnull ImmutableLockAssignment lockAssignment)
    {
        this.body2ccr = ccrs.stream()
                .collect(Collectors.toMap(
                        ccr -> ccr.getAtomicSection().getActiveBody(),
                        Function.identity())
                );
        this.lockAssignment = lockAssignment;
    }

    /**
     * Insert locks, or perform NOP if b not in {@link #body2ccr}.
     *
     * Insert
     * - lock before first unit
     * - unlock on edges to explicit exit
     * - on edges frag1 -> frag2, unlock locks held by frag1 but not frag2,
     *   and lock locks held by frag2 but not frag1.
     *
     * @param b the body to insert locks for
     */
    @Override
    protected void internalTransform(@Nonnull Body b, @Nonnull String phaseName, @Nonnull Map<String, String> options)
    {
        if(b.getMethod().isStatic())
        {
            return;
        }
        // If no CCRs, nothing to do! Otherwise, get the CCRs
        if (!body2ccr.containsKey(b))
        {
            return;
        }
        CCR ccr = body2ccr.get(b);
        // if CCR is empty, nothing to do
        if(ccr.getFragments().isEmpty())
        {
            return;
        }
        // Grab locks at beginning of entry fragment
        final ImmutableSortedSet<Integer> entryLocks = lockAssignment.getLocksInOrder(ccr.getEntryFragment());
        final Stmt firstUnit = ((JimpleBody) b).getFirstNonIdentityStmt();
        final Unit predOfFirst = b.getUnits().getPredOf(firstUnit);
        assert predOfFirst != null;
        final List<Integer> emptyList = new ArrayList<>();
        insertLocks(emptyList, entryLocks, predOfFirst, firstUnit, b);

        // figure out all the exit units
        final Set<Unit> exitUnits = SootUnitUtils.getExplicitExitUnits(b.getMethod());
        // store unit to fragment map
        final Map<Unit, Fragment> unitFragmentMap = ccr.computeUnitToFragmentMap();

        // Now handle exits from fragments
        ccr.getFragments().forEach(srcFrag -> {
            // get the locks held by the source frag, in reverse order
            // (since we want to release in reverse order)
            final ImmutableSortedSet<Integer> srcLocks = lockAssignment.getLocksInOrder(srcFrag)
                    .descendingSet();
            // look at all successors of the source
            final ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance()
                    .getOrCompute(b.getMethod());

            // for each dest
            srcFrag.getExitUnits().forEach(src ->
                cfg.getSuccsOf(src).forEach(dest -> {
                    // destination locks are either: no locks
                    // if the dest is an exit unit, or the locks its fragment holds
                    // otherwise
                    ImmutableSortedSet<Integer> destLocks = ImmutableSortedSet.of();
                    if(!exitUnits.contains(dest) &&
                            // method insertLocks might introduce new goto statements.
                            unitFragmentMap.containsKey(dest))
                    {
                        Fragment destFrag = unitFragmentMap.get(dest);
                        assert destFrag != null;

                        if(srcFrag == destFrag)
                            return;

                        // get the dest-locks in order, since we want to obtain locks in order
                        destLocks = lockAssignment.getLocksInOrder(destFrag);
                    }
                    // insert the locks
                    insertLocks(srcLocks, destLocks, src, dest, b);
                })
            );
        });
    }

    /**
     * Release all locks in src but not dest than grab all locks
     * in dest but not src, inserted on the edge from src to dest
     *
     * These operations are performed in the order that locks
     * appear in src/dest locks
     *
     * @param srcLocks locks held at source
     * @param destLocks locks held at dest
     * @param src source of control-flow edge
     * @param dest destination of control-flow edge
     * @param b the body
     */
    private void insertLocks(@Nonnull Collection<Integer> srcLocks,
                             @Nonnull Collection<Integer> destLocks,
                             @Nonnull Unit src,
                             @Nonnull Unit dest,
                             @Nonnull Body b)
    {
        // get profiler
        CortadoMonitorProfiler monitorProfiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();

        // we'll release any locks in src, but not dest.
        List<Integer> toRelease = new ArrayList<>(srcLocks);
        toRelease.removeAll(destLocks);
        monitorProfiler.recordNumUnlockInvocations(toRelease.size());
        // Insert unlocks on edge
        for (Integer lockID : toRelease)
        {
            InvokeStmt unlockInvk = buildLockInvocation(lockID, b, getUnlockMethod());
            // Sanity check: identity statements aren't really executed,
            // we should not be unlocking after one
            //
            // Experience says this is error-prone, so put it in a try-catch block
            // to print more informative error messages in case this code is
            // incorrect.
            try {
                assert !(src instanceof IdentityStmt);
                SootUnitUtils.insertOnEdge(unlockInvk, src, dest, b);
                src = unlockInvk;
            } catch(RuntimeException e) {
                System.err.printf(
                        "Unlock insertion failed on edge %s -> %s in method %s.%n",
                        src, dest, b.getMethod().getName());
                throw e;
            }
        }
        // we'll need to grab any locks in dest, but not src.
        List<Integer> toGrab = new ArrayList<>(destLocks);
        toGrab.removeAll(srcLocks);
        monitorProfiler.recordNumLockInvocations(toGrab.size());
        // Insert locks on edge
        for (Integer lockID : toGrab)
        {
            InvokeStmt lockInvk = buildLockInvocation(lockID, b, getLockMethod());
            // even though IdentityStmt s appear in the exceptional unit graph,
            // no edge is found when you try to b.getUnits().insertOnEdge(),
            // so we have to handle identity stmts separately.
            //
            // Experience says this is error-prone, so put it in a try-catch block
            // to print more informative error messages in case this code is incorrect
            try {
                if (src instanceof IdentityStmt) {
                    b.getUnits().insertBeforeNoRedirect(lockInvk, dest);
                } else {
                    SootUnitUtils.insertOnEdge(lockInvk, src, dest, b);
                }
                src = lockInvk;
            } catch(RuntimeException e) {
                System.err.printf(
                        "Lock insertion failed on edge %s -> %s in method %s.%n",
                        src, dest, b.getMethod().getName());
                throw e;
            }
        }
    }

    /**
     * If not already done,
     * build a lock local that alias the specified lock.
     * Then, return a statement which invokes lockOrUnlockMethod
     * on the lock local
     *
     * @param lockID the lock identifier
     * @param b the body to build the local in
     * @param lockOrUnlockMethod a method to invoke on the lock local
     * @return A statement which invokes lockOrUnlockMethod
     *      on the lock local
     */
    private InvokeStmt buildLockInvocation(
            int lockID,
            @Nonnull Body b,
            @Nonnull SootMethod lockOrUnlockMethod)
    {
        // Get the lock field, and a local that aliases it
        SootField lockField = getOrAddInitializedLockField(
                lockID,
                b.getMethod().getDeclaringClass());
        Local lockLocal = getOrAddLocalAliasOfField(
                getAliasingLocalNameForLock(lockID),
                lockField,
                b.getMethod()
        );
        // Build a statement to lock/unlock the aliased lock
        InvokeExpr lockOrUnlockInvkExpr = Jimple.v()
                .newVirtualInvokeExpr(lockLocal,lockOrUnlockMethod.makeRef());
        return Jimple.v().newInvokeStmt(lockOrUnlockInvkExpr);
    }

    /**
     * Either retrieve a field for the given lockID, or make a new one.
     * Make sure every constructor of cls initializes this field
     *
     * @param lockID the lock identifier
     * @param cls the class this lock is associated to
     * @return the lock field
     */
    @Nonnull
    private SootField getOrAddInitializedLockField(int lockID, @Nonnull SootClass cls) {
        // add the field to the class
        RefType lockType = getLockClass().getType();
        SootField lockField = getOrAddField(
                getLockFieldName(lockID),
                lockType,
                cls
        );
        String lockInitLocalName = getInitLocalNameForLock(lockID);

        // for each constructor, make sure the field gets initialized
        cls.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .filter(SootMethod::isConstructor)
                .forEach(constr -> {

                    // Get the body and locals
                    Body constrBody = constr.getActiveBody();

                    // If the initializer already exists, assume the field gets initialized
                    Local lockInitLocal = SootLocalUtils.retrieveLocal(lockInitLocalName, lockType, constrBody);
                    if(lockInitLocal != null) return;
                    // Otherwise, make the initializer local and initialize it
                    lockInitLocal = SootLocalUtils.addNewLocal(lockInitLocalName, lockType, constrBody);
                    // Create a statement to invoke new
                    // set local lock to a new lock
                    AssignStmt localLockNew = Jimple.v().newAssignStmt(lockInitLocal,
                                    Jimple.v().newNewExpr(getLockClass().getType()));
                    // Create a statement to initialize that new lock
                    SootMethodRef lockInitMethod = getLockClass().getMethod("void <init>()").makeRef();
                    SpecialInvokeExpr localLockInitExpr =
                            Jimple.v().newSpecialInvokeExpr(lockInitLocal, lockInitMethod);
                    InvokeStmt localInitStmt = Jimple.v().newInvokeStmt(localLockInitExpr);
                    // Create a statement which assigns the field to that initialized local
                    InstanceFieldRef lockFieldRef =
                            Jimple.v().newInstanceFieldRef(constrBody.getThisLocal(),
                                    lockField.makeRef());
                    AssignStmt assignField = Jimple.v().newAssignStmt(lockFieldRef, lockInitLocal);
                    // Now add the these statements to the method body
                    List<Stmt> toInsert = Arrays.asList(localLockNew,
                            localInitStmt,
                            assignField);
                    if (constrBody.getUnits().size() > 0)
                    {
                        // grab the call to super()/another constructor
                        final Unit superInitInvocation = SootMethodUtils.getObjectInitializationInvocation(constr);
                        // insert the lock instantiation after this statement
                        constrBody.getUnits().insertAfter(toInsert, superInitInvocation);
                    }
                    else
                    {
                        throw new IllegalStateException("Expected monitor to have a constructor.");
                    }
                    constrBody.validate();
                });
        return lockField;
    }
}
