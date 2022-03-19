package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.util.graph.SootGraphConverter;
import edu.utexas.cs.utopia.cortado.util.graph.StronglyConnectedComponents;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.Unit;
import soot.jimple.IdentityStmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The finest fragment partition
 * <p>
 * Takes as fragments
 * (1) user-defined fragments
 * (2) Strongly connected components - user-defined fragments
 *
 * @author Ben_Sepanski
 */
public class FinestFragmentPartitioner extends FragmentPartitioner
{
    public FinestFragmentPartitioner()
    {
        super();
    }

    public FinestFragmentPartitioner(ParallelizationOracle parOracle)
    {
        super(parOracle);
    }

    @Override
    List<Fragment> partition(CCR ccr)
    {
        // go ahead and figure out the exit units
        Set<Unit> explicitExitUnits = SootUnitUtils.getExplicitExitUnits(ccr.getAtomicSection());
        // Record any fragments in the oracle
        List<Fragment> oracleFragments = new ArrayList<>();
        if (this.parOracle.getCCR2fragments().containsKey(ccr))
        {
            // add all fragments from the oracle
            List<Fragment> frags = this.parOracle.getCCR2fragments().get(ccr);
            oracleFragments.addAll(frags);
        }
        Set<Unit> unitsInOracleFragments = oracleFragments.stream()
                .map(Fragment::getAllUnits)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        // Group fragments into SCCs
        ExceptionalUnitGraphCache cfgCache = ExceptionalUnitGraphCache.getInstance();
        @SuppressWarnings("UnstableApiUsage")
        ImmutableGraph<Unit> cfg = SootGraphConverter.convertToGuavaGraph(cfgCache.getOrCompute(ccr.getAtomicSection()));
        StronglyConnectedComponents<Unit> sccs = new StronglyConnectedComponents<>(cfg);
        // Next, put each non-Identity, non-exit unit which is not already in a fragment into
        // a new fragment containing exactly its strongly connected component
        List<Fragment> sccFragments = sccs.getComponents()
                .stream()
                .map(scc -> scc.stream()
                        .filter(ut -> !unitsInOracleFragments.contains(ut))
                        .filter(ut -> !explicitExitUnits.contains(ut))
                        .filter(ut -> !(ut instanceof IdentityStmt))
                        .filter(ut -> !(ccr.hasGuard() && ccr.getWaituntilFragment().getAllUnits().contains(ut)))
                        .collect(Collectors.toList())
                )
                .filter(units -> !units.isEmpty())
                .map(units -> new Fragment(ccr, units))
                .collect(Collectors.toList());
        return Streams.concat(oracleFragments.stream(), sccFragments.stream())
                .collect(Collectors.toList());
    }
}
