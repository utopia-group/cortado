package edu.utexas.cs.utopia.cortado.staticanalysis;

import com.google.common.graph.EndpointPair;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import soot.SootMethod;

import javax.annotation.Nonnull;


/**
 * Use a {@link RaceConditionAnalysis} to check commutativity
 */
public class RaceConditionBasedCommutativityAnalysis implements CommutativityAnalysis
{
    private final RaceConditionAnalysis raceConditionAnalysis;

    /**
     * @param raceConditionAnalysis the {@link RaceConditionAnalysis} to use
     */
    public RaceConditionBasedCommutativityAnalysis(RaceConditionAnalysis raceConditionAnalysis)
    {
        this.raceConditionAnalysis = raceConditionAnalysis;
    }

    /*
     * NOTE: During commutativity checks, make sure to
     * temporarily stop ignoring race conditions for ignored fields
     * (a set to an atomic field and a get to an atomic field do not have
     *   race, but still won't commute)
     */

    @Override
    public boolean commutes(@Nonnull Fragment frag1, @Nonnull Fragment frag2)
    {
        return raceConditionAnalysis.getRaces(frag1, frag2).isEmpty();
    }

    @Override
    public boolean commutes(@Nonnull CCR ccr1, @Nonnull CCR ccr2)
    {
        SootMethod atomMeth1 = ccr1.getAtomicSection(), atomMeth2 = ccr2.getAtomicSection();
        return ccr1.getUnits()
                   .stream()
                   .allMatch(u1 -> ccr2.getUnits()
                                       .stream()
                                       .allMatch(u2 -> raceConditionAnalysis.getRaces(u1, atomMeth1, u2, atomMeth2).isEmpty()));
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public boolean preservesEdgeCondition(Fragment frag, EndpointPair<Fragment> edge)
    {
        return raceConditionAnalysis.getRaces(frag, edge.source()).isEmpty();
    }
}
