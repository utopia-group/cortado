package edu.utexas.cs.utopia.cortado.staticanalysis;

import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.StoreExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.PostOrderExprVisitor;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.SootMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.vcgen.CachedStrongestPostConditionCalculatorFactory;
import edu.utexas.cs.utopia.cortado.vcgen.MonitorInvariantGenerator;
import edu.utexas.cs.utopia.cortado.vcgen.StrongestPostConditionCalculator;
import edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link RaceConditionAnalysis} based on a {@link MayRWSetAnalysis}
 */
public class RWSetRaceConditionAnalysis implements RaceConditionAnalysis
{
    private static final ExprFactory eFac = CachedExprFactory.getInstance();

    private static final ConcurrentHashMap<Set<Fragment>, Set<MemoryLocations>> cachedQueries = new ConcurrentHashMap<>();

    private final MayRWSetAnalysis mayRWSetAnalysis;

    /**
     * clear the cache
     */
    public static void clear()
    {
        cachedQueries.clear();
        CachedMayRWSetAnalysis.getInstance().clear();
    }

    /**
     * Build this object
     */
    public RWSetRaceConditionAnalysis() {
        this.mayRWSetAnalysis = CachedMayRWSetAnalysis.getInstance().getMayRWSetAnalysis();
    }

    @Override
    public  Set<MemoryLocations> getRaces(@Nonnull Unit unit1, @Nonnull SootMethod method1,
                                          @Nonnull Unit unit2, @Nonnull SootMethod method2)
    {
        return conflictingAccesses(unit1, method1, unit2, method2);
    }

    private Set<MemoryLocations> conflictingAccesses(@Nonnull Unit ut1, @Nonnull SootMethod sm1, @Nonnull Unit ut2, @Nonnull SootMethod sm2)
    {
        Set<MemoryLocations> conflictingLocs = new HashSet<>();

        // Get R/W sets for ut1 and ut2.
        Collection<MemoryLocations> writeSet1 = this.mayRWSetAnalysis.writeSet(sm1, ut1),
                writeSet2 = this.mayRWSetAnalysis.writeSet(sm2, ut2),
                readSet1 = this.mayRWSetAnalysis.readSet(sm1, ut1),
                readSet2 = this.mayRWSetAnalysis.readSet(sm2, ut2);

        // the units must not have a race condition if they
        // it is not possible for one to write to the read/write set of
        // the other.
        conflictingLocs.addAll(MemoryLocations.intersectingLocs(writeSet1, readSet2));
        conflictingLocs.addAll(MemoryLocations.intersectingLocs(readSet1, writeSet2));
        conflictingLocs.addAll(MemoryLocations.intersectingLocs(writeSet1, writeSet2));

        return conflictingLocs;
    }

    @Override
    public Set<MemoryLocations> getRaces(@Nonnull Fragment frag1, @Nonnull Fragment frag2)
    {
        return cachedQueries.computeIfAbsent(new HashSet<>(Arrays.asList(frag1, frag2)), (k) -> {
            Set<MemoryLocations> conflictingAccesses = new HashSet<>();

            SootMethod m1 = frag1.getEnclosingBody().getMethod();
            SootMethod m2 = frag2.getEnclosingBody().getMethod();
            for (Unit u1 : frag1.getAllUnits())
            {
                for (Unit u2 : frag2.getAllUnits())
                {
                    conflictingAccesses.addAll(conflictingAccesses(u1, m1, u2, m2));
                }
            }

            if (conflictingAccesses.isEmpty())
                return conflictingAccesses;


            if (!frag1.containsLoop() && !frag2.containsLoop() &&
                    conflictingAccesses.stream().allMatch(a -> a instanceof SootMayRWSetAnalysis.ArrayMemoryLocations))
            {
                assert m1.getDeclaringClass().equals(m2.getDeclaringClass());
                return areArrayAccessesDisjoint(frag1, frag2) ? Collections.emptySet() : conflictingAccesses;
            }

            return conflictingAccesses;
        });
    }

    private boolean areArrayAccessesDisjoint(Fragment frag1, Fragment frag2)
    {
        SootMethod frag1CCR = frag1.getEnclosingCCR().getAtomicSection();
        SootMethod frag2CCR = frag2.getEnclosingCCR().getAtomicSection();

        CachedStrongestPostConditionCalculatorFactory spCalcFactory = CachedStrongestPostConditionCalculatorFactory.getInstance();
        StrongestPostConditionCalculator ccr1SpCalc = spCalcFactory.getOrCompute(frag1CCR);
        StrongestPostConditionCalculator ccr2SpCalc = spCalcFactory.getOrCompute(frag2CCR);

        SootClass mtrClass = frag1CCR.getDeclaringClass();
        MonitorInvariantGenerator mtrInvGen = MonitorInvariantGenerator.getInstance(mtrClass);

        Expr ccr1Encoding = ccr1SpCalc.getMethodEncoding(null, mtrInvGen.getInvariant(), true);
        Set<Expr> ccr1Arrays = getArrayLoads(ccr1Encoding);
        Set<Expr> ccr1Indices = getArrayIndices(ccr1Encoding, ccr1Arrays);

        Expr ccr2Encoding = ccr2SpCalc.getMethodEncoding(null, StrongestPostConditionCalculator.encodeConditionToPostState(mtrInvGen.getInvariant(), ccr1Encoding), true);
        Set<Expr> ccr2Arrays = getArrayLoads(ccr2Encoding);
        Set<Expr> ccr2Indices = getArrayIndices(ccr1Encoding, ccr2Arrays);

        Expr disjointArrayAccessCond = eFac.mkTRUE();
        for (Expr idx1 : ccr1Indices)
            for (Expr idx2 : ccr2Indices)
                disjointArrayAccessCond = eFac.mkAND(disjointArrayAccessCond, eFac.mkNEG(eFac.mkEQ(idx1, idx2)));

        return ExprUtils.isUnsat(eFac.mkNEG(eFac.mkIMPL(eFac.mkAND(ccr1Encoding, ccr2Encoding),
                                                        disjointArrayAccessCond)));
    }

    private Set<Expr> getArrayLoads(Expr e)
    {
        Set<Expr> rv = new HashSet<>();
        PostOrderExprVisitor arrayLoadCollector = new PostOrderExprVisitor()
        {
            @Override
            public void visit(SelectExpr e)
            {
                Expr arrayExpr = e.getArrayExpr();
                if (arrayExpr instanceof FunctionApp && VCGenUtils.isSymbolicHeapArrayForArrayType((FunctionApp) arrayExpr))
                    rv.add(e);
                super.visit(e);
            }
        };
        e.accept(arrayLoadCollector);

        return rv;
    }

    private Set<Expr> getArrayIndices(Expr e, Set<Expr> loadedArrays)
    {
        Set<Expr> rv = new HashSet<>();
        PostOrderExprVisitor arrayIndicesCollector = new PostOrderExprVisitor()
        {
            @Override
            public void visit(SelectExpr e)
            {
                if (loadedArrays.contains(e.getArrayExpr()))
                    rv.add(e.getIndexExpr());

                super.visit(e);
            }

            @Override
            public void visit(StoreExpr e)
            {
                if (loadedArrays.contains(e.getArrayExpr()))
                    rv.add(e.getIndexExpr());

                super.visit(e);
            }
        };
        e.accept(arrayIndicesCollector);

        return rv;
    }
}
