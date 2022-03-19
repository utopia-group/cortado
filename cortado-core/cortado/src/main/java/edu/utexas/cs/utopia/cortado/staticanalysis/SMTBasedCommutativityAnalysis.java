package edu.utexas.cs.utopia.cortado.staticanalysis;

import com.google.common.graph.EndpointPair;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.vcgen.CommutativityChecker;
import soot.SootClass;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SMTBasedCommutativityAnalysis implements CommutativityAnalysis {
    private final CommutativityChecker smtChecker;
    private final RaceConditionBasedCommutativityAnalysis raceCondBasedChecker;
    private final ConcurrentHashMap<Set<Fragment>, Boolean> fragsCommute = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<List<Fragment>, Boolean> preserveEdges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Set<CCR>, Boolean> ccrsCommute = new ConcurrentHashMap<>();
    // some summary statistics
    private final AtomicInteger raceProvedFragCommutes = new AtomicInteger(0),
                                raceProvedCCRCommutes = new AtomicInteger(0),
                                raceFailedToProveFragCommutes = new AtomicInteger(0),
                                raceFailedToProveCCRCommutes = new AtomicInteger(0),
                                smtFailedToProveFragCommutes = new AtomicInteger(0),
                                smtFailedToProveCCRCommutes = new AtomicInteger(0);

    /**
     * @param raceConditionAnalysis a {@link RaceConditionAnalysis}
     */
    public SMTBasedCommutativityAnalysis(SootClass monitor, RaceConditionAnalysis raceConditionAnalysis) {
        this.raceCondBasedChecker = new RaceConditionBasedCommutativityAnalysis(raceConditionAnalysis);
        this.smtChecker = new CommutativityChecker(monitor);
    }

    @Override
    public boolean commutes(@Nonnull Fragment frag1, @Nonnull Fragment frag2) {
        Set<Fragment> fragPair = new HashSet<Fragment>(){{add(frag1); add(frag2);}};
        // If we haven't already computed this query, compute it!
        return fragsCommute.computeIfAbsent(fragPair, (pair) -> {
            if (raceCondBasedChecker.commutes(frag1, frag2)) {
                raceProvedFragCommutes.getAndIncrement();
                return true;
            }
            else if (smtChecker.check(frag1, frag2)) {
                raceFailedToProveFragCommutes.getAndIncrement();
                return true;
            }
            else {
                smtFailedToProveFragCommutes.getAndIncrement();
                return false;
            }
        });
    }

    @Override
    public boolean commutes(@Nonnull CCR ccr1, @Nonnull CCR ccr2) {
        Set<CCR> ccrPair = new HashSet<CCR>(){{add(ccr1); add(ccr2);}};
        // If we haven't already computed this query, compute it!
        return ccrsCommute.computeIfAbsent(ccrPair, (pair) -> {
            if (raceCondBasedChecker.commutes(ccr1, ccr2)) {
                raceProvedCCRCommutes.getAndIncrement();
                return true;
            }
            else if (smtChecker.check(ccr1.getAtomicSection(), ccr2.getAtomicSection())) {
                raceFailedToProveCCRCommutes.getAndIncrement();
                return true;
            }
            else {
                smtFailedToProveCCRCommutes.getAndIncrement();
                return false;
            }
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public boolean preservesEdgeCondition(Fragment frag, EndpointPair<Fragment> edge)
    {
        return preserveEdges.computeIfAbsent(Arrays.asList(frag, edge.source(), edge.target()), (k) -> {
            if (raceCondBasedChecker.preservesEdgeCondition(frag, edge))
                return true;
            else
                return smtChecker.preservesEdgeCondition(frag, edge);
        });
    }

    /**
     * Record some information about how/how many commutativity
     * checks were proven to the current monitor profiler
     * (see {@link CortadoProfiler#getCurrentMonitorProfiler()}).
     */
    public void recordProfilingInfo()
    {
        final CortadoMonitorProfiler monitorProfiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
        monitorProfiler.recordProfileInfo("numCommutingFragsProvedByRaceFree", raceProvedFragCommutes.get());
        monitorProfiler.recordProfileInfo("numCommutingFragsProvedBySMT", raceFailedToProveFragCommutes.get());
        monitorProfiler.recordProfileInfo("numCommutingFragProofFailures", smtFailedToProveFragCommutes.get());
        monitorProfiler.recordProfileInfo("numCommutingCCRsProvedByRaceFree", raceProvedCCRCommutes.get());
        monitorProfiler.recordProfileInfo("numCommutingCCRsProvedBySMT", raceFailedToProveCCRCommutes.get());
        monitorProfiler.recordProfileInfo("numCommutingCCRProofFailures", smtFailedToProveCCRCommutes.get());
    }
}
