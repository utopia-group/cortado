package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.*;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.SootMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.util.soot.SootClassReplicator;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;
import soot.*;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.util.Chain;
import soot.util.HashChain;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getMemoryLocationsPerFields;

/**
 * A partition of a monitor into fragments, along
 * with the associated static analysis
 */
@SuppressWarnings("UnstableApiUsage")
public class FragmentedMonitor {
    private final SootClass monitor;
    private List<CCR> allCCRs;
    private Graph<Fragment> allFragsControlFlowGraph;
    private List<Fragment> allFragments;
    private final Map<CCR, SootMethod> ccrPredicate;

    private Set<SootField> readOnlyFields = null;

    private Map<SootMethod, Set<SootField>> nonReadOnlyPredicateFields = null;
    private final Deque<StashedFragmentedMonitor> stash = new ArrayDeque<>();

    /**
     * @param monitor the monitor
     * @param ccrs the ccrs
     */
    public FragmentedMonitor(@Nonnull SootClass monitor, @Nonnull List<CCR> ccrs)
    {
        this.monitor = monitor;
        this.allCCRs = ccrs;
        // initialize fragment info
        this.initializeFragmentInfo();

        ccrPredicate = allCCRs.stream()
                              .filter(CCR::hasGuard)
                              .collect(Collectors.toMap(Function.identity(), CCR::getGuard));
    }

    /**
     * Requires {@link #allCCRs} to be set.
     * Initializes {@link #allFragments} and {@link #allFragsControlFlowGraph}
     * using {@link #allCCRs}.
     */
    private void initializeFragmentInfo()
    {
        // collect all fragments
        this.allFragments = allCCRs.stream()
                .map(CCR::getFragments)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        // Get massive fragment graph
        final MutableGraph<Fragment> allFragCFG = GraphBuilder
                .directed()
                .allowsSelfLoops(true)
                .build();
        allCCRs.stream()
                .map(CCR::getFragmentsCFG)
                .map(Graph::edges)
                .flatMap(Collection::stream)
                .forEach(allFragCFG::putEdge);

        allCCRs.stream()
               .map(CCR::getFragmentsCFG)
               .map(Graph::nodes)
               .flatMap(Collection::stream)
               .forEach(allFragCFG::addNode);

        this.allFragsControlFlowGraph = allFragCFG;
    }

    /**
     * Get the monitor as a soot class
     *
     * @return the monitor class
     */
    public SootClass getMonitor() {
        return this.monitor;
    }

    /**
     * Get all the CCRs in the monitor
     *
     * @return a list of all the ccrs in the monitor
     */
    public List<CCR> getCCRs() {
        return this.allCCRs;
    }

    /**
     * Get the fragments in the monitor
     *
     * @return A list of all the fragments in the monitor
     */
    public List<Fragment> getFragments() {
        return this.allFragments;
    }

    /**
     * @return The number of fragments
     */
    public int numFragments() {
        return this.getFragments().size();
    }

    /**
     * Get the fragments in the monitor (as a control-flow graph)
     *
     * @return A Guava graph whose node set is all the fragments
     *      in the monitor, and whose edges indicate intra-CCR
     *      control-flow between fragments. More precisely,
     *      there is an edge f1 -> f2 if and only if f1 and f2
     *      are in the same CCR, and there is a control-flow
     *      edge from a statement in f1 to a statement in f2.
     */
    public Graph<Fragment> getFragmentsCFG() {
        return this.allFragsControlFlowGraph;
    }

    /**
     * Get fragments pred in the CCR of frag which there exists
     * a control-flow path pred ->* frag (includes frag itself)
     *
     * @param frag the fragment
     * @throws IllegalArgumentException if frag is not in this.fragments()
     * @return An iterable over the predecessors of frag
     */
    public Iterable<Fragment> getPredecessorFragments(Fragment frag) {
        Graph<Fragment> fragCFG = this.getFragmentsCFG();
        return getSuccessorOrPredecessorFragments(frag, fragCFG::predecessors);
    }

    /**
     * Get fragments succ in the CCR of frag which there exists
     * a control-flow path frag ->* succ (includes frag itself)
     *
     * @param frag the fragment
     * @throws IllegalArgumentException if frag is not in this.fragments()
     * @return An iterable over the successors of frag
     */
    public Iterable<Fragment> getSuccessorFragments(Fragment frag) {
        Graph<Fragment> fragCFG = this.getFragmentsCFG();
        assert fragCFG.nodes().contains(frag);
        return getSuccessorOrPredecessorFragments(frag, fragCFG);
    }

    /**
     * Get fragments which are either successors or predecessors
     * of frag
     *
     * @param frag the fragment
     * @param successorFunction the successor function
     * @throws IllegalArgumentException if frag is not in this.fragments()
     * @return An iterable over its successors/predecessors
     */
    private Iterable<Fragment> getSuccessorOrPredecessorFragments(Fragment frag, SuccessorsFunction<Fragment> successorFunction) {
        if(!this.getFragments().contains(frag)) {
            throw new IllegalArgumentException("Unrecognized fragment");
        }
        Traverser<Fragment> fragCFGTraverser = Traverser.forGraph(successorFunction);
        return fragCFGTraverser.depthFirstPreOrder(frag);
    }

    /**
     * Get all the predicates in the monitor
     *
     * @return a list of methods which represent monitor predicates
     */
    public Collection<SootMethod> getCcrPredicates() {
        return this.ccrPredicate.values();
    }

    /**
     * Get all fragments which wait on the given predicate
     *
     * @param predicate the predicate
     * @return the set of fragments waiting on the predicate
     * @throws IllegalArgumentException if predicate not in this.getPredicates()
     */
    public Set<Fragment> getFragmentsWaitingOnPredicate(@Nonnull SootMethod predicate)
    {
        return allCCRs.stream()
                .filter(CCR::hasGuard)
                .filter(ccr -> ccr.getGuard().equals(predicate))
                .map(CCR::getWaituntilFragment)
                .collect(Collectors.toSet());
    }

    public Map<SootMethod, Set<SootField>> getNonReadOnlyPredicateFields()
    {
        if (nonReadOnlyPredicateFields == null)
        {
            nonReadOnlyPredicateFields = new HashMap<>();

            Set<SootField> readOnlyFields = getReadOnlyFields();

            MayRWSetAnalysis mayRWSetAnalysis = CachedMayRWSetAnalysis.getInstance().getMayRWSetAnalysis();

            for (SootMethod pred : getCcrPredicates())
            {
                Set<SootField> flds = new HashSet<>();
                flds.addAll(mayRWSetAnalysis.readSet(pred)
                                            .stream()
                                            .filter(mLocs -> mLocs instanceof SootMayRWSetAnalysis.FieldMemoryLocations)
                                            .map(mLocs -> ((SootMayRWSetAnalysis.FieldMemoryLocations)mLocs).memObject)
                                            .filter(fld -> !readOnlyFields.contains(fld))
                                            .collect(Collectors.toSet()));
                flds.addAll(mayRWSetAnalysis.writeSet(pred)
                                            .stream()
                                            .filter(mLocs -> mLocs instanceof SootMayRWSetAnalysis.FieldMemoryLocations)
                                            .map(mLocs -> ((SootMayRWSetAnalysis.FieldMemoryLocations)mLocs).memObject)
                                            .filter(fld -> !readOnlyFields.contains(fld))
                                            .collect(Collectors.toSet()));

                nonReadOnlyPredicateFields.put(pred, flds);
            }

        }

        return nonReadOnlyPredicateFields;
    }

    // Returns set of monitor fields that are only read in atomic methods
    public Set<SootField> getReadOnlyFields()
    {
        if (readOnlyFields == null)
        {
            MayRWSetAnalysis mayRWSetAnalysis = CachedMayRWSetAnalysis.getInstance().getMayRWSetAnalysis();
            this.readOnlyFields = monitor.getFields()
                                         .stream()
                                         .filter(fld -> !fld.isStatic())
                                         .collect(Collectors.toSet());

            Map<SootField, Set<MemoryLocations>> fldMemLocs = MayRWSetAnalysis.getMemoryLocationsPerFields(monitor);

            monitor.getMethods()
                   .stream()
                   .filter(m -> !m.isConstructor())
                   .filter(m -> !m.isStatic())
                   .filter(SootMethod::isPublic)
                   .filter(SootMethod::hasActiveBody)
                   .forEach(m -> readOnlyFields.removeAll(fldMemLocs.entrySet()
                                                                    .stream()
                                                                    .filter(e -> MemoryLocations.mayIntersectAny(mayRWSetAnalysis.writeSet(m), e.getValue()))
                                                                    .map(Map.Entry::getKey)
                                                                    .collect(Collectors.toSet())));
        }

        return readOnlyFields;
    }

    /**
     *
     * @param originalField the original field
     * @param predPreChecks predicate pre-checks
     * @return the potential atomic field, if originalField can be converted to atomic. Null otherwise
     */
    @Nullable
    public PotentialAtomicField asPotentialAtomicField(@Nonnull SootField originalField, @Nonnull Map<SootMethod, Set<Unit>> predPreChecks) {
        PotentialAtomicField potentialAtomicField = null;
        if(!getReadOnlyFields().contains(originalField) && PotentialAtomicField.isPotentialAtomicField(originalField)) {
            Map<SootField, Set<MemoryLocations>> fieldToMemoryLocations = getMemoryLocationsPerFields(getMonitor());
            Set<MemoryLocations> originalFieldMemoryLocations = fieldToMemoryLocations.get(originalField);
            Set<MemoryLocations> otherNonReadOnlyPossiblyAtomicFieldMemoryLocations = getMonitor().getFields()
                    .stream()
                    .filter(fld -> !getReadOnlyFields().contains(fld))
                    .filter(PotentialAtomicField::isPotentialAtomicField)
                    .filter(nonReadOnlyFld -> !Objects.equals(nonReadOnlyFld, originalField))
                    .map(fieldToMemoryLocations::get)
                    .reduce(new HashSet<>(), Sets::union);

            boolean noFragWritesToOriginalFieldAndRWAnotherPossiblyAtomicField = getFragments().stream()
                    .filter(frag -> MemoryLocations.mayIntersectAny(frag.getMonitorWriteSet(), originalFieldMemoryLocations))
                    .noneMatch(frag -> MemoryLocations.mayIntersectAny(
                            Sets.union(frag.getMonitorReadSet(), frag.getMonitorWriteSet()),
                            otherNonReadOnlyPossiblyAtomicFieldMemoryLocations)
                    );

            // If other non-read-only fields can be accessed
            if (noFragWritesToOriginalFieldAndRWAnotherPossiblyAtomicField) {
                potentialAtomicField = PotentialAtomicField.allOperationsCanBeAtomic(originalField, getNonReadOnlyPredicateFields(), predPreChecks);
                // Make sure that each atomic operation is contained in a single fragment
                if(potentialAtomicField != null) {
                    boolean atomicOpsCanBeContainedInFragments = potentialAtomicField.getPotentialAtomicOperations()
                            .stream()
                            .allMatch(potentialAtomicOperation -> getCCRs()
                                    .stream()
                                    .filter(ccr -> ccr.getAtomicSection().equals(potentialAtomicOperation.getMethod()))
                                    .map(CCR::getFragments)
                                    .flatMap(Collection::stream)
                                    .map(Fragment::getAllUnits)
                                    .filter(fragUnits -> potentialAtomicOperation.getNonAtomicImplementation()
                                            .stream()
                                            .anyMatch(fragUnits::contains)
                                    ).count() <= 1
                            );
                    if (!atomicOpsCanBeContainedInFragments) {
                        potentialAtomicField = null;
                    }
                }
            }
        }
        return potentialAtomicField;
    }

    public static class FragmentedMonitorToReplica
    {
        private final FragmentedMonitor replica;
        private final ImmutableMap<CCR, CCR> ccrToReplica;
        private final ImmutableMap<Fragment, Fragment> fragToReplica;
        private final SootClassReplicator replicator;

        public FragmentedMonitorToReplica(@Nonnull FragmentedMonitor replica,
                                          @Nonnull SootClassReplicator replicator,
                                          @Nonnull Map<CCR, CCR> ccrToReplica,
                                          @Nonnull Map<Fragment, Fragment> fragToReplica)
        {
            this.replica = replica;
            this.replicator = replicator;
            this.ccrToReplica = ImmutableMap.copyOf(ccrToReplica);
            this.fragToReplica = ImmutableMap.copyOf(fragToReplica);
        }

        @Nonnull
        public FragmentedMonitor getFragmentedMonitorReplica()
        {
            return replica;
        }

        @Nonnull
        public ImmutableMap<CCR, CCR> getCcrToReplicaMap()
        {
            return ccrToReplica;
        }

        @Nonnull
        public SootClassReplicator getReplicator()
        {
            return replicator;
        }

        @Nonnull
        public ImmutableMap<Fragment, Fragment> getFragToReplica()
        {
            return fragToReplica;
        }
    }

    /**
     * Build a replica of the fragmented monitor on a new soot class of name newName
     * (see {@link SootClassReplicator})
     *
     * @param newName name of the new soot class
     * @return the replicated monitor
     */
    @Nonnull
    public FragmentedMonitorToReplica buildReplica(@Nonnull String newName)
    {
        final SootClassReplicator monitorReplicator = new SootClassReplicator(getMonitor(), newName);
        final SootClass monitorReplica = monitorReplicator.getReplicaClass();

        // replicate CCRs
        final ImmutableMap<SootMethod, SootMethod> methodToReplicaMap = monitorReplicator.getMethodToReplicaMap();
        final Map<CCR, CCR> ccrToReplica = getCCRs().stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ccr -> new CCR(methodToReplicaMap.get(ccr.getAtomicSection())
                )));

        // replicate fragments
        final ImmutableMap<Unit, Unit> unitToReplicaMap = monitorReplicator.getUnitToReplicaMap();
        final Map<Fragment, Fragment> fragToReplica = new HashMap<>();
        ccrToReplica.forEach((ccr, ccrReplica) -> {
            if(ccr.hasGuard())
            {
                assert ccrReplica.hasGuard();
                final Fragment waituntilFragment = ccr.getWaituntilFragment();
                final List<Unit> replicatedUnits = waituntilFragment.getAllUnits()
                        .stream()
                        .map(unitToReplicaMap::get)
                        .collect(Collectors.toList());
                assert replicatedUnits.stream().noneMatch(Objects::isNull);
                final Fragment waituntilFragmentReplica = new Fragment(ccrReplica, replicatedUnits);
                ccrReplica.setWaituntilFragment(waituntilFragmentReplica);
                fragToReplica.put(waituntilFragment, waituntilFragmentReplica);
            }
            final List<Fragment> replicatedFragments = ccr.getFragments()
                    .stream()
                    .filter(f -> !f.isWaitUntilFrag())
                    .map(frag -> {
                        final List<Unit> replicatedUnits = frag.getAllUnits()
                                .stream()
                                .map(unitToReplicaMap::get)
                                .collect(Collectors.toList());
                        assert replicatedUnits.stream().noneMatch(Objects::isNull);
                        final Fragment fragReplica = new Fragment(ccrReplica, replicatedUnits);
                        fragToReplica.put(frag, fragReplica);
                        return fragReplica;
                    })
                    .collect(Collectors.toList());
            ccrReplica.setFragments(replicatedFragments);
        });

        final List<CCR> ccrReplicas = getCCRs()
                .stream()
                .map(ccrToReplica::get)
                .collect(Collectors.toList());
        final FragmentedMonitor replica = new FragmentedMonitor(monitorReplica, ccrReplicas);
        return new FragmentedMonitorToReplica(replica, monitorReplicator, ccrToReplica, fragToReplica);
    }

    /**
     * An object which stashes the fragmented monitor's bodies,
     * CCRs, and fragments, then replaces them with a copy.
     */
    private class StashedFragmentedMonitor
    {
        final private Chain<SootField> originalMonitorFields;
        final private ImmutableMap<SootMethod, Body> monitorMethodToOriginalBody;
        private final ImmutableList<CCR> oldCCRs;
        private final Map<CCR, CCR> oldCCRToNewCCR = new HashMap<>();

        /**
         * Stash the monitor method active {@link Body}s, {@link CCR}s,
         * and {@link Fragment}s. Then, replace them with a copy.
         */
        StashedFragmentedMonitor()
        {
            // store the fields so that any added fields can be removed
            // upon restoration
            originalMonitorFields = new HashChain<>(getMonitor().getFields());
            // store the active bodies so they can be restored
            monitorMethodToOriginalBody = getMonitor().getMethods()
                    .stream()
                    .filter(SootMethod::hasActiveBody)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(Function.identity(), SootMethod::getActiveBody),
                            ImmutableMap::copyOf)
                    );
            // Make copies of active bodies of the monitor, and set the copies
            // as the new active bodies
            getMonitor().getMethods()
                    .stream()
                    .filter(SootMethod::hasActiveBody)
                    .forEach(m -> {
                        final JimpleBody bodyCopy = Jimple.v().newBody(m);
                        bodyCopy.importBodyContentsFrom(m.getActiveBody());
                        m.setActiveBody(bodyCopy);
                    });
            // store the old CCRs, and build new ones on the new bodies
            oldCCRs = ImmutableList.copyOf(getCCRs());
            // now store new values into fragmented monitor
            FragmentedMonitor.this.allCCRs = oldCCRs.stream()
                    .map(oldCCR -> {
                        // build new CCR
                        CCR newCCR = new CCR(oldCCR.getAtomicSection());
                        // get map from new units to old units
                        final Map<Unit, Unit> originalUnitToNewUnit = new HashMap<>();
                        Streams.forEachPair(monitorMethodToOriginalBody.get(oldCCR.getAtomicSection()).getUnits().stream(),
                                newCCR.getAtomicSection().getActiveBody().getUnits().stream(),
                                originalUnitToNewUnit::put
                        );
                        // build new fragments and associate to new CCR
                        final List<Fragment> newFragments = oldCCR.getFragments()
                                .stream()
                                .map(oldFrag -> new Fragment(
                                        oldCCR,
                                        oldFrag.getAllUnits()
                                                .stream()
                                                .map(originalUnitToNewUnit::get)
                                                .collect(Collectors.toList()))
                                )
                                .collect(Collectors.toList());
                        newCCR.setFragments(newFragments);
                        // copy over any info about ccr-to-predicate associations
                        if(ccrPredicate.containsKey(oldCCR))
                        {
                            ccrPredicate.put(newCCR, ccrPredicate.get(oldCCR));
                        }
                        // return the ccr
                        oldCCRToNewCCR.put(oldCCR, newCCR);
                        return newCCR;
                    }).collect(Collectors.toList());
            // set up fragment info using the new CCRs
            FragmentedMonitor.this.initializeFragmentInfo();
            // remove old ccrs from ccr-to-predicate map
            oldCCRs.forEach(ccrPredicate::remove);
        }

        /**
         * Restore the stashed active {@link Body}s, {@link CCR}s, and {@link Fragment}s.
         */
        public void restore()
        {
            // remove any added fields
            final List<SootField> fieldsToRemove = getMonitor().getFields()
                    .stream()
                    .filter(field -> !originalMonitorFields.contains(field))
                    .collect(Collectors.toList());
            fieldsToRemove.forEach(getMonitor()::removeField);
            // restore the active bodies
            monitorMethodToOriginalBody.forEach(SootMethod::setActiveBody);
            // restore ccr-to-predicate map
            assert FragmentedMonitor.this.allCCRs.size() == oldCCRs.size();
            for(int i = 0; i < oldCCRs.size(); ++i)
            {
                CCR oldCCR = oldCCRs.get(i);
                CCR newCCR = FragmentedMonitor.this.allCCRs.get(i);
                if(ccrPredicate.containsKey(newCCR))
                {
                    ccrPredicate.put(oldCCR, ccrPredicate.get(newCCR));
                    ccrPredicate.remove(newCCR);
                }
            }
            // restore the previous CCRs
            FragmentedMonitor.this.allCCRs = oldCCRs;
            FragmentedMonitor.this.initializeFragmentInfo();
        }

        @Nonnull
        public Map<CCR, CCR> getOldCCRToNewCCR()
        {
            return oldCCRToNewCCR;
        }
    }

    /**
     * Stash the fragmented monitor's method's active {@link Body}s,
     * {@link CCR}s, and {@link Fragment}s. Then, replace them all
     * with copies.
     *
     * @return a map from the original CCRs to their copies
     */
    @Nonnull
    public Map<CCR, CCR> stash()
    {
        final StashedFragmentedMonitor stashedFragmentedMonitor = new StashedFragmentedMonitor();
        stash.push(stashedFragmentedMonitor);
        return stashedFragmentedMonitor.getOldCCRToNewCCR();
    }

    /**
     * Restore the most recently stashed fragmented monitor (see {@link #stash()}).
     * There must be at least one stashed monitor.
     */
    public void stashPop()
    {
        if(stash.isEmpty())
        {
            throw new IllegalStateException("No stashed fragmented monitor.");
        }
        StashedFragmentedMonitor stashedFragmentedMonitor = stash.pop();
        stashedFragmentedMonitor.restore();
    }
}

