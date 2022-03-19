package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.staticanalysis.CommutativityAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.RaceConditionAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Checks lock assignments for correctness.
 * Also can be used to build correctness constraints
 */
@SuppressWarnings("ALL")
public class LockAssignmentChecker
{
    private static final Logger log = LoggerFactory.getLogger(LockAssignmentChecker.class);

    private final FragmentedMonitor fragmentedMonitor;
    private final RaceConditionAnalysis raceConditionAnalysis;
    private final CommutativityAnalysis commutativityAnalysis;

    public LockAssignmentChecker(@Nonnull FragmentedMonitor fragmentedMonitor,
                                 @Nonnull RaceConditionAnalysis raceConditionAnalysis,
                                 @Nonnull CommutativityAnalysis commutativityAnalysis,
                                 Map<SootMethod, Set<Unit>> preChecks)
    {
        this.fragmentedMonitor = fragmentedMonitor;
        this.raceConditionAnalysis = raceConditionAnalysis;
        this.commutativityAnalysis = commutativityAnalysis;
    }

    /**
     * @return the underlying fragmented monitor
     */
    @Nonnull
    public FragmentedMonitor getFragmentedMonitor()
    {
        return fragmentedMonitor;
    }

    /**
     * Check if lockAssignment
     * - prevents race conditions
     * - prevents atomicity violations
     * - obtains locks in order
     * - allows a consistent assignment from predicates
     *   to the lowest lock held by that predicate
     *
     * @param lockAssignment the lock assignment
     * @return true iff lockAssignment satisfies the correctness check
     */
    @SuppressWarnings("UnstableApiUsage")
    public boolean check(@Nonnull LockAssignment lockAssignment)
    {
        // make sure the lock assignment prohibits race conditions
        boolean allowsRaceConditions = !getPossibleRaces().stream()
                                                          .allMatch(possibleRace -> possibleRace.mutexedUnderAssignment(lockAssignment));
        if(allowsRaceConditions) return false;
        // make sure the lock assignment prohibits atomicity violations
        boolean allowsInvInterleaving = !getInvalidInterleavings().stream().allMatch(interleav -> interleav.mutexedUnderAssignment(lockAssignment));
        boolean nonAtomicCCR = !getCCRFragSets().stream().allMatch(fragSet -> fragSet.mutexedUnderAssignment(lockAssignment));
        if(allowsInvInterleaving || nonAtomicCCR) return false;
        // now make sure that the locks are always obtained in order
        Comparator<Integer> lockOrder = lockAssignment.getLockOrder();
        boolean locksNotObtainedInOrder = fragmentedMonitor.getFragmentsCFG()
                                                           .edges()
                                                           .stream()
                                                           .anyMatch(edge -> {
                                                               Set<Integer> sourceLocks = lockAssignment.getLocksInOrder(edge.nodeU());
                                                               Set<Integer> destLocks = lockAssignment.getLocksInOrder(edge.nodeV());
                                                               Set<Integer> locksHeldOnEdge = Sets.intersection(sourceLocks, destLocks);
                                                               Set<Integer> locksObtained = Sets.difference(destLocks, sourceLocks);
                                                               // locks are not obtained in order if the first lock obtained
                                                               // is less than the greatest lock already held
                                                               return !locksHeldOnEdge.isEmpty() && !locksObtained.isEmpty() &&
                                                                      Collections.max(locksHeldOnEdge, lockOrder) > Collections.min(locksObtained, lockOrder);
                                                           });
        if(locksNotObtainedInOrder) return false;
        // make sure that predicates are associated to the same
        // lowest lock
        ImmutableSortedSet<Integer> locks = lockAssignment.getLocks();
        int noLockValue = locks.isEmpty() ? -1 : Collections.min(locks) - 1;
        return fragmentedMonitor.getCCRs()
                                .stream()
                                // group the CCRs by guard
                                .filter(CCR::hasGuard)
                                .collect(Collectors.groupingBy(CCR::getGuard))
                                .entrySet()
                                .stream()
                                .allMatch(entry -> {
                                    Set<Integer> lowestLocksHeld = entry.getValue()
                                                                        .stream()
                                                                        .map(CCR::getWaituntilFragment)
                                                                        .map(lockAssignment::getLocksInOrder)
                                                                        .map(locksInOrder -> locksInOrder.isEmpty() ? noLockValue : locksInOrder.first())
                                                                        .collect(Collectors.toSet());
                                    return lowestLocksHeld.size() == 1 && !lowestLocksHeld.contains(noLockValue);
                                });
    }

    List<CCRFragmentSet> getCCRFragSets()
    {
        return fragmentedMonitor.getCCRs()
                                .stream()
                                .map(ccr -> new CCRFragmentSet(ccr.getFragments().toArray(new Fragment[0])))
                                .collect(Collectors.toList());
    }

    /**
     * @return the list of possible race conditions or predicate violations in the monitor
     */
    @Nonnull
    List<PossibleRace> getPossibleRaces()
    {
        final List<Fragment> fragments = getFragmentedMonitor().getFragments();
        // For each pair of fragments frag1, frag2
        return getAllFragmentPais().parallelStream()
                                   .map(fragPair -> new PossibleRace(fragPair.f1, fragPair.f2, raceConditionAnalysis.getRaces(fragPair.f1, fragPair.f2)))
                                   .filter(race -> !race.memLocs.isEmpty())
                                   .collect(Collectors.toList());
    }

    List<FragmentPair> fragmentPairsWithoutRaces()
    {
        return getAllFragmentPais().stream()
                                   .filter(fragPair -> raceConditionAnalysis.getRaces(fragPair.f1, fragPair.f2).isEmpty())
                                   // No need to parallelize fragments that just evaluate predicates for signalling.
                                   .filter(fragPair -> (!fragPair.f1.isSignalPredicateEvalFragment() && !fragPair.f2.isSignalPredicateEvalFragment()))
                                   // Do not parallelize fragements that do not access any monitor state.
                                   .filter(fragPair -> (fragPair.f1.accessesMonitorState() && fragPair.f2.accessesMonitorState()))
                                   .collect(Collectors.toList());
    }

    /**
     * @return the list of possible atomicity violations in the monitor
     */
    @SuppressWarnings("UnstableApiUsage")
    @Nonnull
    List<PossibleAtomicityViolation> getInvalidInterleavings()
    {
        return getFragmentedMonitor().getFragmentsCFG()
                                     .edges()
                                     .parallelStream()
                                     // Ignore self-edges
                                     .filter(e -> !(e.source().equals(e.target())))
                                     // Ignore egdes where the target just evaluates predicates for signalling.
                                     // Losing atomicity for those edges does not affect correctness.
                                     .filter(e -> !e.target().isSignalPredicateEvalFragment())
                                     .flatMap(edge -> getFragmentedMonitor().getFragments()
                                                                            .parallelStream()
                                                                            .filter(frag -> isNotLinearizableInterleaving(frag, edge))
                                                                            .map(frag -> new PossibleAtomicityViolation(frag, edge)))
                                     .collect(Collectors.toList());
    }

    private static class isNotLinearEdgeInterleavingQuery
    {
        private final Fragment fragi;

        private final EndpointPair<Fragment> edge;

        public isNotLinearEdgeInterleavingQuery(Fragment fragi, EndpointPair<Fragment> edge)
        {
            this.fragi = fragi;
            this.edge = edge;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LockAssignmentChecker.isNotLinearEdgeInterleavingQuery that = (LockAssignmentChecker.isNotLinearEdgeInterleavingQuery) o;
            return fragi.equals(that.fragi) && edge.equals(that.edge);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(fragi, edge);
        }
    }

    private ConcurrentHashMap<LockAssignmentChecker.isNotLinearEdgeInterleavingQuery, Boolean> isNotLinearEdgeInterleavingQuery = new ConcurrentHashMap<>();

    /**
     * Return true iff we cannot prove fragi can interleave between
     * u and v along the control-flow edge u -> v
     *
     * @param fragi the interleaving fragment
     * @param edge the edge between fragments during
     *             which fragi might execute
     * @return true iff we cannot prove fragi can safely execute between u and v
     */
    @SuppressWarnings("UnstableApiUsage")
    private boolean isNotLinearizableInterleaving(@Nonnull Fragment fragi, @Nonnull EndpointPair<Fragment> edge)
    {
        return Streams.concat(Streams.stream(Collections.singleton(edge)),
                        Streams.stream(fragmentedMonitor.getPredecessorFragments(edge.source()))
                                .flatMap(pred -> fragmentedMonitor.getFragmentsCFG()
                                        .predecessors(pred)
                                        .stream()
                                        .map(predOfPred -> EndpointPair.ordered(predOfPred, pred))
                                ),
                        Streams.stream(fragmentedMonitor.getSuccessorFragments(edge.target()))
                                .flatMap(succ -> fragmentedMonitor.getFragmentsCFG()
                                        .successors(succ)
                                        .stream()
                                        .map(succOfSucc -> EndpointPair.ordered(succ, succOfSucc))
                                )
                )
                .parallel()
                // Filter out self-edges of the waituntil fragment!
                // REASONING:
                // We know the waituntil fragment only reads from monitor.
                // If we write to these values, it's either right before a yield() ( no problem there )
                // or right after returning from a yield(), in which case we can think of
                // the operation commuting to before the yield
                .filter(e -> !(e.source().isWaitUntilFrag() && e.source().equals(e.target())))
                .filter(e -> !e.target().isSignalPredicateEvalFragment())
                .anyMatch(e -> isNotLinearEdgeInterleaving(fragi, e));
    }

    /**
     * A fragment interleaving along an edge (u, v) is a linear edge interleaving iff
     *
     * (1) The fragment preserves the edge condition of edge (see {@link edu.utexas.cs.utopia.cortado.vcgen.CommutativityChecker#preservesEdgeCondition(Fragment, EndpointPair)}
     * (2) All monitor fields read by the fragment are not written by u
     * (3) The fragment commutes with both u and v
     *
     * @param fragi the fragment
     * @param edge the edge
     * @return true iff fragi is a linear edge interleaving along edge
     */
    @SuppressWarnings("UnstableApiUsage")
    private boolean isNotLinearEdgeInterleaving(@Nonnull Fragment fragi, @Nonnull EndpointPair<Fragment> edge)
    {
        return isNotLinearEdgeInterleavingQuery.computeIfAbsent(new isNotLinearEdgeInterleavingQuery(fragi, edge), (k) -> {
            final Fragment u = edge.source();
            final Fragment v = edge.target();

            boolean fragiMustNotReadFromEdgeSourceWrite =
                    raceConditionAnalysis.getRaces(fragi, u).isEmpty()
                            || !MemoryLocations.mayIntersectAny(fragi.getMonitorReadSet(), u.getMonitorWriteSet());

            return !(fragiMustNotReadFromEdgeSourceWrite
                    && commutativityAnalysis.preservesEdgeCondition(fragi, edge)
                    && (u.isWaitUntilFrag() || commutativityAnalysis.commutes(fragi, u))
                    && (v.isWaitUntilFrag() || commutativityAnalysis.commutes(fragi, v))
            );
        });
    }

    /**
     * We upper bound number of locks by number of fragments.
     *
     * We also upper bound the number of locks
     * by the minimal number of locks required so that
     * - Every pair of fragments which {@link RaceConditionAnalysis#getRaces(Fragment, Fragment)}
     *   shares no lock.
     * - Every pair of fragments which cannot run in parallel shares a lock.
     * Under this framework, the set of fragments holding each lock
     * is a clique in the graph G := (vertices=fragments, edges=u -> v iff u cannot run in parallel with v),
     * so we use upper bounds on the intersection number (https://en.wikipedia.org/wiki/Intersection_number_(graph_theory))
     * of this graph.
     *
     * @return An upper bound on the number of locks
     */
    @SuppressWarnings("UnstableApiUsage")
    public int getNumLocksUpperBound()
    {
        FragmentedMonitor fragmentedMonitor = getFragmentedMonitor();
        if(fragmentedMonitor.numFragments() <= 0)
        {
            throw new IllegalStateException("No fragments present.");
        }
        int upperBound = fragmentedMonitor.numFragments();

        // build graph of fragments
        // with edges between fragments which cannot run
        // in parallel
        final MutableGraph<Fragment> cannotRunInParallelGraph = GraphBuilder.undirected()
                .allowsSelfLoops(false)
                .expectedNodeCount(fragmentedMonitor.numFragments())
                .build();
        List<Fragment> frags = fragmentedMonitor.getFragments();
        frags.forEach(cannotRunInParallelGraph::addNode);
        for (int i = 0; i < frags.size(); i++)
        {
            Fragment frag1 = frags.get(i);
            frags.stream()
                 .skip(i + 1)
                 .filter(frag2 -> frag2 != frag1)
                 .filter(frag2 -> !raceConditionAnalysis.getRaces(frag1, frag2).isEmpty())
                 .forEach(frag2 -> cannotRunInParallelGraph.putEdge(frag1, frag2));
        }
        // Remove isolated vertices
        final int numExtraLocks = (int) frags.stream()
                                             .filter(frag -> cannotRunInParallelGraph.degree(frag) <= 0)
                                             .filter(frag -> !raceConditionAnalysis.getRaces(frag, frag).isEmpty())
                                             .count();
        frags.stream()
             .filter(frag -> cannotRunInParallelGraph.degree(frag) <= 0)
             .forEach(cannotRunInParallelGraph::removeNode);
        // use upper bounds from
        // https://en.wikipedia.org/wiki/Intersection_number_(graph_theory)#Upper_bounds
        //
        // We have to add in extra locks for isolated vertices with self-loops
        final int nVerts = cannotRunInParallelGraph.nodes().size(),
                nEdges = cannotRunInParallelGraph.edges().size();
        // upper bounds based on number of edges/vertices
        upperBound = Math.min(upperBound, nEdges + numExtraLocks);
        upperBound = Math.min(upperBound, nVerts * nVerts / 4 + numExtraLocks);
        // upper bound based on number of non-incident pairs
        int numPairsNotIncident = 0;
        for(int i = 0; i < fragmentedMonitor.numFragments() - 1; ++i)
        {
            Fragment fragi = frags.get(i);
            for(int j = i + 1; j < fragmentedMonitor.numFragments(); ++j)
            {
                Fragment fragj = frags.get(j);
                if(cannotRunInParallelGraph.hasEdgeConnecting(fragi, fragj))
                {
                    numPairsNotIncident++;
                }
            }
        }
        int t = 0;
        while((t-1) * t <= numPairsNotIncident)
        {
            t++;
        }
        t--;
        assert numPairsNotIncident < t * (t+1);
        upperBound = Math.min(upperBound, numPairsNotIncident + t + numExtraLocks);
        // get good upper bound for dense graphs
        final int minDegree = cannotRunInParallelGraph.nodes()
                .stream()
                .map(cannotRunInParallelGraph::degree)
                .reduce(Math::min)
                .orElse(0);
        int maxDegreeInComplement = nVerts - 1 - minDegree;
        final double complementOfSparseUpperBound = 2 * Math.exp(2) *
                (maxDegreeInComplement + 1) * (maxDegreeInComplement + 1) *
                Math.log(nVerts);
        upperBound = Math.min(upperBound, (int) complementOfSparseUpperBound + numExtraLocks);

        // sanity check
        assert upperBound > 0;
        // return our upper bound
        return upperBound;
    }

    private List<FragmentPair> getAllFragmentPais()
    {
        List<Fragment> fragments = getFragmentedMonitor().getFragments();

        return Streams.mapWithIndex(fragments.stream(), (frag1, index) -> fragments.stream()
                                                                                   .skip(index)
                                                                                   .map(frag2 -> new FragmentPair(frag1, frag2))
                                                                                   .collect(Collectors.toList()))
                      .flatMap(Collection::stream)
                      .collect(Collectors.toList());
    }

    static class FragmentPair
    {
        Fragment f1, f2;

        public FragmentPair(Fragment f1, Fragment f2)
        {
            this.f1 = f1;
            this.f2 = f2;
        }
    }
}
