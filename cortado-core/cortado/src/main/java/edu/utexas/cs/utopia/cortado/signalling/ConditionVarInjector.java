package edu.utexas.cs.utopia.cortado.signalling;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.lockPlacement.ImmutableLockAssignment;
import edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils;
import soot.*;
import soot.jimple.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils.*;
import static edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils.*;
import static edu.utexas.cs.utopia.cortado.util.soot.SootFieldUtils.getOrAddField;
import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.getOrAddLocal;
import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.retrieveLocal;
import static edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils.findFirstUnitWithLHS;

/**
 * Creates condition vars
 */
public class ConditionVarInjector extends BodyTransformer
{
    private final Map<CCR, SootMethod> ccrWithPreds;
    private final Map<SootMethod, CCR> methodToCCRWithPred;
    private final ImmutableMap<SootMethod, Integer> predToLockID;
    private final SootClass inClass;
    private final List<CCR> ccrs;
    private final ImmutableLockAssignment lockAssignment;
    private final Map<Unit, Unit> yieldToAwait = new HashMap<>();

    public Map<Unit, Unit> getYieldToAwait()
    {
        return yieldToAwait;
    }

    public ConditionVarInjector(@Nonnull SootClass inClass,
                                @Nonnull List<CCR> ccrs,
                                @Nonnull ImmutableLockAssignment lockAssignment)
    {
        this.inClass = inClass;
        this.ccrs = ccrs;
        this.lockAssignment = lockAssignment;

        this.ccrWithPreds = ccrWithPredicates();
        final ConditionVarLockAssigner conditionVarLockAssigner = new ConditionVarLockAssigner(ccrs, lockAssignment);
        this.predToLockID = conditionVarLockAssigner.getGuardToLock();

        this.methodToCCRWithPred = this.ccrWithPreds.keySet()
                                                   .stream()
                                                   .collect(Collectors.toMap(CCR::getAtomicSection, Function.identity()));

        addConditionVarFields();
    }

    /**
     * Add a condition variable for each predicate in {@link #inClass}.
     */
    private void addConditionVarFields()
    {
        // for each constructor
        inClass.getMethods()
               .stream()
               .filter(SootMethod::hasActiveBody)
               .filter(SootMethod::isConstructor)
               .forEach(constr -> {
                   Body constrBody = constr.getActiveBody();
                   UnitPatchingChain constrUnits = constrBody.getUnits();

                   SootClass condVarClass = getConditionClass();
                   SootClass lockClass = getLockClass();

                   // For every condVar
                   for (SootMethod pred : predToLockID.keySet())
                   {
                       Integer lockIDForPred = predToLockID.get(pred);

                       // Add field
                       String condVarFldName = getConditionVarFieldName(lockIDForPred, pred);
                       SootField condVarFld = getOrAddField(condVarFldName, condVarClass.getType(), inClass);

                       // Locate initialization point.
                       Local thisLocal = constrBody.getThisLocal();
                       String lockFieldInitLHS = thisLocal.toString() + "." + getOrAddField(getLockFieldName(lockIDForPred), lockClass.getType(), inClass).toString();
                       Unit lockFieldInitializer = findFirstUnitWithLHS(constrUnits, lockFieldInitLHS);
                       Local lockInitLocal = getOrAddLocal(getInitLocalNameForLock(lockIDForPred), lockClass.getType(), constrBody);

                       assert lockFieldInitializer != null : "failed to find lock field initializer";

                       // Initialize Condition Variable
                       String condVarInitLocalName = getInitLocalNameForConditionVar(lockIDForPred, pred);
                       Local condVarInitLocal = getOrAddLocal(condVarInitLocalName, condVarClass.getType(), constrBody);

                       Jimple jimpleFactory = Jimple.v();
                       AssignStmt newCondCall = jimpleFactory.newAssignStmt(condVarInitLocal, jimpleFactory.newVirtualInvokeExpr(lockInitLocal, lockClass.getMethod("newCondition", Collections.emptyList()).makeRef()));
                       AssignStmt initCondVarFld = jimpleFactory.newAssignStmt(jimpleFactory.newInstanceFieldRef(thisLocal, condVarFld.makeRef()), condVarInitLocal);

                       constrUnits.insertAfter(Arrays.asList(newCondCall, initCondVarFld), lockFieldInitializer);
                   }
               });
    }

    /**
     * Filter out CCRs without predicates (i.e. waituntil(true))
     * and build a map
     * from the remaining CCRs to their predicates
     *
     * @return A map which takes each ccr to its predicate (if it has one)
     */
    private Map<CCR, SootMethod> ccrWithPredicates()
    {
        return ccrs.stream()
                   .filter(CCR::hasGuard)
                   .collect(Collectors.toMap(Function.identity(), CCR::getGuard));
    }

    /**
     * @return predicate -> associated lock
     */
    public Map<SootMethod, Integer> getPredToLockID()
    {
        return predToLockID;
    }

    /**
     * For each CCR that yields on a predicate, replace
     * the yield with waiting on the proper predicate.
     *
     * Unlocks any locks not associated to the predicate to avoid deadlock.
     *
     * Note that this invalidates {@link Fragment}s, {@link FragmentedMonitor}s,
     * and {@link Fragment}s of {@link CCR}s in b.
     *
     * @param body the body we're transforming
     * @param s ignored
     * @param map ignored
     */
    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map)
    {
        SootMethod inMethod = body.getMethod();
        CCR ccr = methodToCCRWithPred.get(inMethod);
        // if not a ccr, or has no predicate, continue!
        if(ccr == null) return;
        assert ccr.hasGuard() && ccr.getAtomicSection().equals(inMethod);

        // Get the waituntil fragment, the yield statement, and its locks
        Fragment waituntilFrag = ccr.getWaituntilFragment();
        Unit yieldStmt = waituntilFrag.getAllUnits()
                          .stream()
                          .filter(u -> u instanceof InvokeStmt)
                          .filter(u -> ((InvokeStmt) u).getInvokeExpr().getMethod() == getYieldMethod())
                          .findFirst()
                          .orElseThrow(() -> new IllegalStateException("Missing yield method in yield frag."));

        ImmutableSortedSet<Integer> locksForFragInReverse = lockAssignment
                .getLocksInOrder(waituntilFrag)
                .descendingSet();

        UnitPatchingChain units = body.getUnits();
        Local thisLocal = body.getThisLocal();
        SootClass inClass = inMethod.getDeclaringClass();
        Jimple j = Jimple.v();

        // Call unlock/lock before/after yield.
        SootClass lockClass = getLockClass();
        RefType lockClassType = lockClass.getType();
        for(int lID : locksForFragInReverse.headSet(locksForFragInReverse.last()))
        {
            // Safe to assume that edu.utexas.cs.utopia.cortado.lockPlacement.LockInserter
            // created a local which aliases the lock field
            Local lockLocal = getOrAddLocal(getAliasingLocalNameForLock(lID), lockClassType, body);

            // Call unlock before yield.
            units.insertBefore(j.newInvokeStmt(j.newVirtualInvokeExpr(lockLocal, lockClass.getMethod("unlock", Collections.emptyList()).makeRef())), yieldStmt);

            // Call lock after yield.
            units.insertAfter(j.newInvokeStmt(j.newVirtualInvokeExpr(lockLocal, lockClass.getMethod("lock", Collections.emptyList()).makeRef())), yieldStmt);
        }

        /// Replace yield with call to await. /////////////////////

        SootClass condVarClass = getConditionClass();
        RefType condVarClassType = condVarClass.getType();
        SootMethod predMeth = ccrWithPreds.get(ccr);

        Integer condVarLockID = predToLockID.get(predMeth);
        String condVarAliasingLocalName = getAliasingLocalNameForConditionVar(condVarLockID, predMeth);

        // If the local does not exist yet in the method, we need to initialize it!
        Local condVarAliasingLocal = retrieveLocal(condVarAliasingLocalName, condVarClassType, body);
        if(condVarAliasingLocal == null) {
            condVarAliasingLocal = getOrAddLocal(condVarAliasingLocalName, condVarClassType, body);
            // Get field, and alias at beginning of the method
            SootField condVarFld = getOrAddField(getConditionVarFieldName(condVarLockID, predMeth), condVarClassType, inClass);
            Unit firstUnit = ((JimpleBody) body).getFirstNonIdentityStmt();
            AssignStmt initAliasingLocal = j.newAssignStmt(condVarAliasingLocal, j.newInstanceFieldRef(thisLocal, condVarFld.makeRef()));
            units.insertBefore(initAliasingLocal, firstUnit);
        }

        // build the await() invocation
        SootMethod awaitMethod = MonitorInterfaceUtils.getConditionAwaitMethod();
        InvokeExpr awaitInvkExpr = Jimple.v().newInterfaceInvokeExpr(condVarAliasingLocal, awaitMethod.makeRef());
        InvokeStmt awaitInvkStmt = Jimple.v().newInvokeStmt(awaitInvkExpr);
        yieldToAwait.put(yieldStmt, awaitInvkStmt);

        ///////////////////////////////////////////////////////////

        // TODO: add exception handler for InterruptedException
    }
}
