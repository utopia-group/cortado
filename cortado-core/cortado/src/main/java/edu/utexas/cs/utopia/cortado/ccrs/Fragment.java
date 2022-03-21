package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.util.graph.GuavaExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.GotoStmt;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getMemoryLocationsForFields;
import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getThisMemoryLocs;

/**
 * A Fragment is a set of code points in a method body.
 *
 * There must be exactly one unit which is either
 * (1) an entry unit in the CCR
 * (2) the target of a control-flow edge from outside the fragment
 * which we call the entry unit.
 *
 * All units must be reachable from the entry unit and contained
 * in the enclosing CCR
 *
 * @author Ben_Sepanski
 */
public class Fragment
{
    private final CCR enclosingCCR;
    private final ImmutableSet<Unit> allUnits;
    private final Unit entryUnit;
    private final ImmutableSet<Unit> exitUnits;
    private final boolean containsLoop;

    private final Set<MemoryLocations> monitorReadSet = new HashSet<>();

    private final Set<MemoryLocations> monitorWriteSet = new HashSet<>();

    private void calculateMonitorReadWriteSets()
    {
        SootMethod ccrMeth = enclosingCCR.getAtomicSection();
        SootClass mtrClass = ccrMeth.getDeclaringClass();
        Set<MemoryLocations> mtrMemLocs = getMemoryLocationsForFields(mtrClass);
        mtrMemLocs.addAll(getThisMemoryLocs(mtrClass));

        final CachedMayRWSetAnalysis mayRWSetAnalysisCache = CachedMayRWSetAnalysis.getInstance();
        if(!mayRWSetAnalysisCache.hasMayRWSetAnalysis())
        {
            mayRWSetAnalysisCache.setMayRWSetAnalysis(mtrClass);
        }
        MayRWSetAnalysis mayRWSetAnalysis = mayRWSetAnalysisCache.getMayRWSetAnalysis();

        allUnits.forEach(u -> {
            Collection<MemoryLocations> uWriteSet = mayRWSetAnalysis.writeSet(ccrMeth, u);
            Collection<MemoryLocations> uReadSet = mayRWSetAnalysis.readSet(ccrMeth, u);

            if (MemoryLocations.mayIntersectAny(uWriteSet, mtrMemLocs))
                monitorWriteSet.addAll(uWriteSet);

            if (MemoryLocations.mayIntersectAny(uReadSet, mtrMemLocs))
                monitorReadSet.addAll(uReadSet);
        });
    }

    /**
     * Build a singleton fragment
     *
     * @param enclosingCCR the ccr
     * @param unit the unit
     */
    public Fragment(@Nonnull CCR enclosingCCR, @Nonnull Unit unit)
    {
        this(enclosingCCR, Collections.singleton(unit));
    }

    /**
     * Define a fragment as all units reachable from the entry unit
     * which can reach at least on exit unit
     *
     * @param enclosingCCR the ccr
     * @param entryUnit the entry unit
     * @param exitUnits the exit units
     */
    public Fragment(@Nonnull CCR enclosingCCR, @Nonnull Unit entryUnit, @Nonnull Collection<Unit> exitUnits)
    {
        this(enclosingCCR, SootUnitUtils.getCompositeStatement(entryUnit, ImmutableSet.copyOf(exitUnits), enclosingCCR.getAtomicSection()));
    }

    /**
     * @param enclosingCCR the ccr
     * @param units the units in the fragment
     */
    @SuppressWarnings("UnstableApiUsage")
    public Fragment(@Nonnull CCR enclosingCCR, @Nonnull Collection<Unit> units)
    {
        final SootMethod ccrMethod = enclosingCCR.getAtomicSection();
        if(!ccrMethod.hasActiveBody())
        {
            throw new IllegalArgumentException("CCR " + enclosingCCR + " atomic section has no active body");
        }

        final Body ccrBody = ccrMethod.getActiveBody();
        if(!ccrBody.getUnits().containsAll(units))
        {
            throw new IllegalArgumentException("CCR " + enclosingCCR + " does not contain all units");
        }

        // make sure we have a well-defined entry unit
        final ImmutableGraph<Unit> ccrCfg = GuavaExceptionalUnitGraphCache.getInstance().getOrCompute(ccrMethod);
        final List<Unit> entryUnits = units.stream()
                .filter(ut -> ccrCfg.predecessors(ut).isEmpty() || !units.containsAll(ccrCfg.predecessors(ut)))
                .collect(Collectors.toList());
        assert !entryUnits.isEmpty();
        if(entryUnits.size() > 1)
        {
            throw new IllegalArgumentException("units contains more than one entry unit");
        }
        final Unit entryUnit = entryUnits.get(0);

        // make sure the entry unit can reach all other units in the fragment
        if(!Graphs.reachableNodes(ccrCfg, entryUnit).containsAll(units))
        {
            throw new IllegalArgumentException("Some units are unreachable from the entry unit");
        }

        this.enclosingCCR = enclosingCCR;
        this.allUnits = ImmutableSet.copyOf(units);
        this.entryUnit = entryUnit;
        this.exitUnits = units.stream()
                .filter(ut -> !units.containsAll(ccrCfg.successors(ut)))
                .collect(Collectors.collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
        this.containsLoop = Graphs.hasCycle(
                Graphs.inducedSubgraph(ccrCfg, units)
        );

        calculateMonitorReadWriteSets();
    }

    /**
     * Equivalent to {@link #getEnclosingCCR()}.{@link CCR#getAtomicSection()}.{@link SootMethod#getActiveBody()}
     *
     * @return the body of the method enclosing this fragment
     */
    @Nonnull
    public Body getEnclosingBody()
    {
        return enclosingCCR.getAtomicSection().getActiveBody();
    }

    /**
     * @return the enclosing CCR
     */
    @Nonnull
    public CCR getEnclosingCCR()
    {
        return enclosingCCR;
    }

    /**
     * @return the first unit
     */
    @Nonnull
    public Unit getEntryUnit()
    {
        return entryUnit;
    }

    /**
     * @return the last unit
     */
    @Nonnull
    public ImmutableSet<Unit> getExitUnits()
    {
        return exitUnits;
    }

    @Nonnull
    @Override
    public String toString()
    {
        // singleton case
        if(allUnits.size() == 1)
        {
            return this.allUnits.iterator().next().toString();
        }
        String exitUnitsStr = String.join(",", exitUnits.stream().map(Object::toString).collect(Collectors.toSet()));
        return "(" + this.getEntryUnit() + ", {" + exitUnitsStr + "})";
    }

    /**
     * Run computeUnits on a soot ExceptionalUnitGraph
     * created from the enclosing body of this method
     *
     * @return as described in other computeUnits method
     */
    @Nonnull
    public ImmutableSet<Unit> getAllUnits()
    {
        return allUnits;
    }

    public Set<MemoryLocations> getMonitorReadSet()
    {
        return monitorReadSet;
    }

    public Set<MemoryLocations> getMonitorWriteSet()
    {
        return monitorWriteSet;
    }

    public boolean accessesMonitorState()
    {
        return monitorReadSet.size() + monitorWriteSet.size() > 0;
    }

    public boolean isSignalPredicateEvalFragment()
    {
        return allUnits.stream()
                       .allMatch(u -> u instanceof GotoStmt || isSignalPredEvalUnit(u));
    }

    /**
     * @return true iff this is the waituntil fragment of its {@link #enclosingCCR}
     */
    public boolean isWaitUntilFrag()
    {
        return getEnclosingCCR().hasGuard() && getEnclosingCCR().getWaituntilFragment().equals(this);
    }

    public boolean containsLoop()
    {
        return containsLoop;
    }

    @Override
    public int hashCode()
    {
        return java.util.Objects.hash(enclosingCCR, entryUnit, exitUnits);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (!(obj instanceof Fragment))
            return false;
        Fragment other = (Fragment) obj;
        return java.util.Objects.equals(enclosingCCR, other.enclosingCCR)
                && java.util.Objects.equals(entryUnit, other.entryUnit)
                && java.util.Objects.equals(exitUnits, other.exitUnits);
    }

    public static boolean isSignalPredEvalUnit(Unit u)
    {
        if (!(u instanceof AssignStmt))
            return false;

        AssignStmt stmt = (AssignStmt) u;

        Value leftOp = stmt.getLeftOp();
        return leftOp instanceof Local && SootNamingUtils.isPredEvalLocal((Local) leftOp);
    }
}
