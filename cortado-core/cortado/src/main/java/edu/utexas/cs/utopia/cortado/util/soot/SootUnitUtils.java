package edu.utexas.cs.utopia.cortado.util.soot;

import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils;
import edu.utexas.cs.utopia.cortado.util.graph.GuavaExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.graph.StronglyConnectedComponents;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Commonly performed operations on {@link soot.Unit}s
 */
public class SootUnitUtils
{
    public static InvokeExpr dupInvokeExpr(InvokeExpr expr, SootMethod newMeth, List<Value> args)
    {
        Jimple j = Jimple.v();
        SootMethodRef methRef = newMeth.makeRef();
        if (expr instanceof StaticInvokeExpr)
        {
            return j.newStaticInvokeExpr(methRef, args);
        }
        else
        {
            assert expr instanceof InstanceInvokeExpr;
            InstanceInvokeExpr instInvExpr = (InstanceInvokeExpr) expr;
            Local base = (Local) instInvExpr.getBase();

            if (expr instanceof InterfaceInvokeExpr)
            {
                return j.newInterfaceInvokeExpr(base, methRef, args);
            }
            else if (expr instanceof VirtualInvokeExpr)
            {
                return j.newVirtualInvokeExpr(base, methRef, args);
            }
            else if (expr instanceof SpecialInvokeExpr)
            {
                return j.newSpecialInvokeExpr(base, methRef, args);
            }
        }

        throw new UnsupportedOperationException("Unsupported invoke expression: " + expr);
    }

    /**
     * A utility function, returns true if ut is waiting
     * on a condition (or yielding)
     *
     * @param ut the unit
     * @return true iff ut is yielding
     */
    public static boolean unitIsWaiting(@Nonnull Unit ut)
    {
        if(ut instanceof InvokeStmt)
        {
            final SootMethod invokedMethod = ((InvokeStmt) ut).getInvokeExpr().getMethod();
            return Objects.equals(invokedMethod, MonitorInterfaceUtils.getYieldMethod())
                    || Objects.equals(invokedMethod, MonitorInterfaceUtils.getConditionAwaitMethod());
        }
        return false;
    }

    /**
     * Return the first unit in units whose left-hand-side matches lhs
     *
     * @param units the units to search through, in order
     * @param lhs the desired left-hand side
     * @return the first unit in units which matches lhs, or null
     *         if none match
     */
    public static Unit findFirstUnitWithLHS(UnitPatchingChain units, String lhs)
    {
        for (Unit u : units)
        {
            if (u instanceof AssignStmt)
            {
                AssignStmt assignStmt = (AssignStmt) u;
                if (assignStmt.getLeftOp().toString().equals(lhs))
                    return u;
            }
        }

        return null;
    }

    /**
     * Get all explicit exits from method. Namely, any
     * returns or uncaught throws.
     *
     * @param method a method with an active body
     * @return the set of explicit exit units
     */
    @Nonnull
    public static Set<Unit> getExplicitExitUnits(@Nonnull SootMethod method) {
        assert method.hasActiveBody();
        final IsExplicitExitUnit explicitExitChecker = new IsExplicitExitUnit(method);
        return method.getActiveBody()
                .getUnits()
                .stream()
                .filter(ut -> {
                    ut.apply(explicitExitChecker);
                    return explicitExitChecker.isExplicitExitUnit();
                }).collect(Collectors.toSet());
    }

    /**
     * A {@link AbstractStmtSwitch} which returns true if the statement
     * is an explicit exit unit, i.e. an uncaught exception or a return
     */
    private static class IsExplicitExitUnit extends AbstractStmtSwitch {
        private final SootMethod method;
        private boolean result;

        public boolean isExplicitExitUnit() {return result;}

        public IsExplicitExitUnit(@Nonnull SootMethod method) {
            this.method = method;
        }

        @Override public void caseRetStmt(RetStmt ret) {result = true;}
        @Override public void caseReturnStmt(ReturnStmt ret) {result = true;}
        @Override public void caseReturnVoidStmt(ReturnVoidStmt ret) {result = true;}
        @Override public void caseThrowStmt(ThrowStmt throwStmt) {
            final ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(method);
            result = cfg.getExceptionalSuccsOf(throwStmt).isEmpty();
        }

        @Override
        public void defaultCase(Object v) {
            this.result = false;
        }
    }

    /**
     * The composite statement defined by start and exitUnits,
     * i.e. all statements reachable from start which can also
     * reach exitUnits.
     *
     *
     * @param start the start unit
     * @param exitUnits the exitUnits unit. Must post-dominate the start unit
     * @param inMethod the method containing start unit and exitUnits unit
     * @return all units in the composite statement
     */
    @Nonnull
    static public Set<Unit> getCompositeStatement(@Nonnull Unit start, @Nonnull Set<Unit> exitUnits, @Nonnull SootMethod inMethod)
    {
        if(!inMethod.hasActiveBody())
        {
            throw new IllegalArgumentException("inMethod has no active body.");
        }
        final UnitPatchingChain units = inMethod.getActiveBody().getUnits();
        if(!units.contains(start))
        {
            throw new IllegalArgumentException("start unit " + start + " not contained in inMethod's active body.");
        }
        if(!units.containsAll(exitUnits))
        {
            throw new IllegalArgumentException("some exit unit is not contained in inMethod's active body.");
        }

        //TODO: figure out what's the proper check here. Our current partitioner is based on SCCs, so we are fine.
//        final DominatorTree<Unit> pdomTree = PostDominatorTreeCache.getInstance().getOrCompute(inMethod);
//        if(!exitUnits.stream().allMatch(exit -> pdomTree.isDominatorOf(pdomTree.getDode(exit), pdomTree.getDode(start))))
//        {
//            throw new IllegalArgumentException("some exit unit does not post-dominate start unit " + start);
//        }
        // DFS from start, to build visited
        final ImmutableGraph<Unit> cfg = GuavaExceptionalUnitGraphCache.getInstance().getOrCompute(inMethod);
        StronglyConnectedComponents<Unit> unitSCCs = new StronglyConnectedComponents<>(cfg);

        Map<Unit, List<Unit>> unitToSCCMap = new HashMap<>();
        for (List<Unit> scc : unitSCCs.getComponents())
        {
            scc.forEach(u -> unitToSCCMap.put(u, scc));
        }

        final Deque<Unit> toVisit = new ArrayDeque<>(Collections.singleton(start));
        final Set<Unit> visited = new HashSet<>(Collections.singleton(start));
        while(!toVisit.isEmpty())
        {
            Unit current = toVisit.pop();
            if(exitUnits.contains(current))
            {
                // This is mainly to capture loops.
                if (cfg.successors(current).size() > 1)
                {
                    cfg.successors(current)
                       .stream()
                       .filter(succUnit -> unitToSCCMap.get(current) == unitToSCCMap.get(succUnit))
                       .peek(toVisit::addFirst)
                       .forEach(visited::add);
                }
            }
            else
            {
                cfg.successors(current)
                   .stream()
                   .filter(succUnit -> !visited.contains(succUnit))
                   .peek(toVisit::addFirst)
                   .forEach(visited::add);
            }
        }
        assert visited.containsAll(exitUnits) && visited.contains(start);
        return visited;
    }

    /**
     * Like {@link soot.UnitPatchingChain#insertOnEdge(Collection, Unit, Unit)}, but
     * searches for and fixes the bug described in
     * https://github.com/soot-oss/soot/pull/1742
     */
    public static void insertOnEdge(@Nonnull Collection<Unit> toInsert,
                                    @Nonnull Unit src,
                                    @Nonnull Unit tgt,
                                    @Nonnull Body body)
    {
        final UnitPatchingChain units = body.getUnits();
        Unit oldPredecessorOfTarget = units.getPredOf(tgt);
        units.insertOnEdge(toInsert, src, tgt);
        if(!oldPredecessorOfTarget.fallsThrough() && !(oldPredecessorOfTarget instanceof GotoStmt))
        {
            Unit possibleDeadCode = units.getSuccOf(oldPredecessorOfTarget);
            if(possibleDeadCode.getBoxesPointingToThis().isEmpty())
            {
                units.remove(possibleDeadCode);
            }
        }
    }

    /**
     * See {@link #insertOnEdge(Collection, Unit, Unit, Body)}
     */
    public static void insertOnEdge(@Nonnull Unit toInsert,
                                    @Nonnull Unit src,
                                    @Nonnull Unit tgt,
                                    @Nonnull Body body)
    {
        SootUnitUtils.insertOnEdge(Collections.singletonList(toInsert), src, tgt, body);
    }
}
