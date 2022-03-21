package edu.utexas.cs.utopia.cortado.ccrs;

import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.Unit;
import soot.jimple.IdentityStmt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coarsest possible fragment partitioner
 *
 * @author Ben_Sepanski
 */
public class CoarseFragmentPartitioner extends FragmentPartitioner
{
    public CoarseFragmentPartitioner()
    {
        super();
    }

    public CoarseFragmentPartitioner(ParallelizationOracle parOracle)
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

        List<Unit> unitsInFrag = ccr.getAtomicSection()
                .getActiveBody()
                .getUnits()
                .stream()
                .filter(ut -> !unitsInOracleFragments.contains(ut))
                .filter(ut -> !explicitExitUnits.contains(ut))
                .filter(ut -> !(ut instanceof IdentityStmt))
                .filter(ut -> !(ccr.hasGuard() && ccr.getWaituntilFragment().getAllUnits().contains(ut)))
                .collect(Collectors.toList());

        List<Fragment> fragments = new ArrayList<>();
        if(!unitsInFrag.isEmpty()) {
            fragments.add(new Fragment(ccr, unitsInFrag));
        }
        return fragments;
    }

}
