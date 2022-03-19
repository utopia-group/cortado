package edu.utexas.cs.utopia.cortado.ccrs;

import edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.Edge;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * This transformer
 *      (1) validates that all implicit CCRs are valid (see {@link CCR#CCR(SootMethod)})
 *      (2) validates that there are no
 *          yield calls, or {@link MonitorInterfaceUtils#getConditionAwaitMethod()}
 *          calls already in the code.
 *      (4) Inserts a while(!guard()) yield() statement at the beginning
 *          of CCRs which have a guard.
 *
 * @author Ben_Sepanski
 */
public class CCRMarker extends BodyTransformer
{
    private final List<CCR> ccrs = new ArrayList<>();

    private final Map<Body, Unit> originalCCRStartInstr = new HashMap<>();

    public Map<Body, Unit> getOriginalCCRStartInstr()
    {
        return originalCCRStartInstr;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
        // if b is not a CCR method, this is a NOP
        if(!CCR.isCCRMethod(b.getMethod())) return;
        // make sure there are no waiting statements doesn't wait
        UnitPatchingChain units = b.getUnits();
        final boolean anyWaitingInAtomicSection = units.stream()
                                                       .anyMatch(SootUnitUtils::unitIsWaiting);
        if(anyWaitingInAtomicSection) {
            throw new IllegalArgumentException("atomic section " + b.getMethod().getName()
                    + " contains a unit which may block.");
        }

        Unit firstNonIdentityUnit = ((JimpleBody) b).getFirstNonIdentityStmt();
        originalCCRStartInstr.put(b, firstNonIdentityUnit);

        // make sure b is the body of CCR, and figure out its guard
        CCR ccr = new CCR(b.getMethod());
        ccrs.add(ccr);
        // If the CCR has no guard, we're done
        if (!ccr.hasGuard()) {
            return;
        }

        if (!(firstNonIdentityUnit instanceof InvokeStmt) || !((InvokeStmt)firstNonIdentityUnit).getInvokeExpr().getMethod().equals(ccr.getGuard()))
            throw new IllegalStateException("first statement of CCR must be a call to the guard assigned to a local");

        originalCCRStartInstr.put(b, units.getSuccOf(firstNonIdentityUnit));

        InvokeExpr guardCall = ((InvokeStmt) firstNonIdentityUnit).getInvokeExpr();

        // Otherwise, make sure the guard doesn't yield/block
        final boolean anyWaitingInGuard = ccr.getGuard()
                                             .getActiveBody()
                                             .getUnits()
                                             .stream()
                                             .anyMatch(SootUnitUtils::unitIsWaiting);

        if(anyWaitingInGuard) {
            throw new IllegalArgumentException("CCR guard " + ccr.getGuard().getName()
                    + " contains a unit which may block.");
        }

        /// if the ccr has a guard, insert a while(!condition) yield() loop /////////////

        // figure out the first unit
        //noinspection ConstantConditions
        assert b instanceof JimpleBody;
        // handle case of empty body
        if(units.isEmpty())
        {
            ((JimpleBody) b).insertIdentityStmts();
            units.addLast(Jimple.v().newNopStmt());
        }

        // setup evaluation of guard, and jumping to method if true
        final String guardLocalName = SootNamingUtils.getLocalForCCRGuardEvaluation(ccr);
        final Local guardLocal = SootLocalUtils.getOrAddLocal(guardLocalName, BooleanType.v(), b);
        assert ccr.getGuard().isPrivate();
        final AssignStmt evaluateGuard = Jimple.v().newAssignStmt(
                guardLocal,
                guardCall
        );
        final IfStmt startMethodIfGuard = Jimple.v().newIfStmt(
                Jimple.v().newNeExpr(guardLocal, IntConstant.v(0)),
                firstNonIdentityUnit
        );
        // otherwise, we'll yield(), and then goto the guard evaluation again
        final InvokeStmt yieldStmt = MonitorInterfaceUtils.newYieldInvk();
        final GotoStmt gotoGuardEval = Jimple.v().newGotoStmt(evaluateGuard);
        // Now add everything to the body
        units.insertBeforeNoRedirect(evaluateGuard, firstNonIdentityUnit);
        units.insertAfter(
                Arrays.asList(startMethodIfGuard, yieldStmt, gotoGuardEval),
                evaluateGuard
        );
        // remove original call to guard
        units.remove(firstNonIdentityUnit);
        // update call graph if present
        if(Scene.v().hasCallGraph())
        {
            // TODO: FIX CONTEXT SENSITIVITY ISSUE IN WSDATALISTENER
//            final MethodOrMethodContext callingContext = MethodContext.v(ccr.getAtomicSection(), evaluateGuard);
            final SootMethod callingContext = ccr.getAtomicSection();
            final Edge guardInvkEdge = new Edge(callingContext, evaluateGuard, ccr.getGuard());
            Scene.v().getCallGraph().addEdge(guardInvkEdge);
            assert Scene.v().getCallGraph().edgesOutOf(evaluateGuard).hasNext();
            assert !Scene.v().getCallGraph().edgesOutOf(evaluateGuard).next().isInvalid();
            assert Scene.v().getCallGraph().edgesOutOf(callingContext).hasNext();
            assert !Scene.v().getCallGraph().edgesOutOf(callingContext).next().isInvalid();
        }
        // record the instrumented waituntil fragment
        ccr.setWaituntilFragment(new Fragment(ccr, Arrays.asList(evaluateGuard, startMethodIfGuard, yieldStmt, gotoGuardEval)));
    }

    /**
     * @return the list of ccrs
     */
    @Nonnull
    public List<CCR> getCCRs()
    {
        return ccrs;
    }
}
