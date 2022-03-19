package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Streams;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.SootMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.util.naming.SATNamingUtils;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PartialInterpretation;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormula;
import edu.utexas.cs.utopia.cortado.util.sat.formula.PropositionalFormulaBuilder;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getMemoryLocationsPerFields;
import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getThisMemoryLocs;

/**
 * Tool for lock allocation/assignment
 *
 * Used to build constraints and objective functions
 * for the lock-related sat/max-sat solving
 */
class LockAssignmentSatEncoder {
    private static final Logger log = LoggerFactory.getLogger(LockAssignmentSatEncoder.class);

    // our monitor
    private final FragmentedMonitor fragmentedMonitor;
    private final PropositionalFormulaBuilder formulaBuilder = new PropositionalFormulaBuilder();
    // Boolean variables
    private final PropositionalFormula[][] fragIsAssignedLock;
    private final int numLocksUpperBound;
    private final Map<Fragment, Integer> frag2index;
    // Used to throw exceptions when optionals are missing
    private final Supplier<IllegalStateException> missingOptional = () -> new IllegalStateException("Missing optional");
    private final LockAssignmentChecker lockAssignmentChecker;

    // Atomics optimization
    private final Map<SootField, PotentialAtomicField> potentialAtomicFields;

    private final BiMap<SootField, PropositionalFormula> atomicFldsVars = HashBiMap.create();

    private final Map<Unit, SootMethod> preCheckToPred = new HashMap<>();

    private final FragmentWeightStrategy fragmentWeightStrategy;

    ///////////////////////////////////////////////////////////////////////////

    /**
     * Build constraints which ensure monitor correctness
     *  @param lockAssignmentChecker the lock-assignment checker used to generate
     *                               correctness constraints
     * @param numLocksUpperBound upper bound on number of locks required
     * @param fragmentWeightStrategy the fragment weight strategy to use
     * @throws IllegalArgumentException if numLocksUpperBound is non-positive
     */
    LockAssignmentSatEncoder(@Nonnull LockAssignmentChecker lockAssignmentChecker, Map<SootMethod, Set<Unit>> predPreChecks, int numLocksUpperBound, FragmentWeightStrategy fragmentWeightStrategy)
    {
        // grab the lock assignment checker and its associated fragmented monitor
        this.lockAssignmentChecker = lockAssignmentChecker;
        this.fragmentedMonitor = lockAssignmentChecker.getFragmentedMonitor();
        this.fragmentWeightStrategy = fragmentWeightStrategy;
        // Build map from frag to index
        this.frag2index = IntStream.range(0, fragmentedMonitor.numFragments())
                .boxed()
                .collect(Collectors.toMap(
                        index -> fragmentedMonitor.getFragments().get(index),
                        Function.identity()
                ));

        predPreChecks.forEach((pred, units) -> units.forEach(u -> preCheckToPred.put(u, pred)));
        this.potentialAtomicFields = fragmentedMonitor.getMonitor()
                                                      .getFields()
                                                      .stream()
                                                       .map((SootField originalField) -> fragmentedMonitor.asPotentialAtomicField(originalField, predPreChecks))
                                                      .filter(Objects::nonNull)
                                                      .collect(Collectors.toMap(PotentialAtomicField::getNonAtomicField, Function.identity()));

        // Get upper bound on number of locks
        this.numLocksUpperBound = numLocksUpperBound;
        // Sanity check
        if(numLocksUpperBound <= 0) {
            throw new IllegalArgumentException("numLocksUpperBound is non-positive: " + numLocksUpperBound);
        }
        // build our variables
        fragIsAssignedLock = new PropositionalFormula[fragmentedMonitor.numFragments()][numLocksUpperBound];
        for(int f = 0; f < fragmentedMonitor.numFragments(); ++f) {
            for(int ell = 0; ell < numLocksUpperBound; ++ell) {
                fragIsAssignedLock[f][ell] = formulaBuilder.mkFreshVar("f" + f + "L" + ell);
            }
        }
    }

    private PropositionalFormula getAtomicFieldVar(SootField fld)
    {
        if (!atomicFldsVars.containsKey(fld))
            atomicFldsVars.put(fld, formulaBuilder.mkFreshVar("a" + atomicFldsVars.size()));

        return atomicFldsVars.get(fld);
    }

    /// Translate solution ////////////////////////////////////////////////////

    /**
     * Translate a model of the weighted max-sat problem
     * into a map fragments -> set of locks.
     *
     * Renames the locks so that they start at 0, preserving order
     * (e.g. if locks used are 2, 7, 9, rename 2 -> 0, 7 -> 1, 9 -> 2).
     *
     * @param interp the satisfying interpretation
     * @return a lock assignment from each fragment to its lock set
     */
    @Nonnull
    ImmutableLockAssignment getLockAssignment(@Nonnull PartialInterpretation interp) {
        // get map fragment to set of locks
        final Map<Fragment, Set<Integer>> fragToLockSet = getFragments().stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        fragID -> IntStream.range(0, numLocksUpperBound)
                                .filter(lock -> interp.interpret(isAssignedLock(fragID, lock)))
                                .boxed()
                                .collect(Collectors.toSet())
                ));
        // rename locks
        final ArrayList<Integer> sortedLocks = fragToLockSet.values()
                .stream()
                .flatMap(Collection::stream)
                .distinct()
                .sorted(Integer::compare)
                .collect(Collectors.toCollection(ArrayList::new));
        // get lock map with renamed locks
        final Map<Fragment, Set<Integer>> fragToRelabeledLockSet = fragToLockSet.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue()
                                .stream()
                                .map(sortedLocks::indexOf)
                                .collect(Collectors.toSet())
                ));

        Set<PotentialAtomicField> fieldsToConvertToAtomics = new HashSet<>();

        for (PropositionalFormula aFldVar : atomicFldsVars.values())
        {
            if (interp.interpret(aFldVar))
            {
                SootField fld = atomicFldsVars.inverse().get(aFldVar);
                fieldsToConvertToAtomics.add(potentialAtomicFields.get(fld));
            }
        }

        return new ImmutableLockAssignment(fragToRelabeledLockSet, Integer::compare, fieldsToConvertToAtomics);
    }

    ///////////////////////////////////////////////////////////////////////////

    /// build an objective function ///////////////////////////////////////////

    public static class EncodingWeightPair
    {
        public final PropositionalFormula formula;

        public final int weight;

        public EncodingWeightPair(PropositionalFormula formula, int weight)
        {
            this.formula = formula;
            this.weight = weight;
        }
    }

    /**
     * Build a list of formulas with one entry for each pair
     * of fragments. That entry is satisfied iff they share no
     * locks, i.e. can run in parallel
     *
     * @return the list of formulas
     */
    @Nonnull
    List<EncodingWeightPair> computeEncodingsOfParallelPairs()
    {
        List<LockAssignmentChecker.FragmentPair> fragmentPairs = lockAssignmentChecker.fragmentPairsWithoutRaces();
        Map<CCR, Set<Fragment>> parFrags = parallelFragsPerCCR(fragmentPairs);

        return fragmentPairs.stream()
                            .filter(fragPair -> !fragPair.f1.getEnclosingCCR().equals(fragPair.f2.getEnclosingCCR()))
                            .map(fragPair -> new EncodingWeightPair(formulaBuilder.mkNOT(mutexConstraint(getFragmentID(fragPair.f1), getFragmentID(fragPair.f2))),
                                                                    getWeightForFragPair(parFrags, fragPair)))
                            .collect(Collectors.toList());
    }

    private Map<CCR, Set<Fragment>> parallelFragsPerCCR(List<LockAssignmentChecker.FragmentPair> parallelPairs)
    {
        Set<Fragment> parallelizableFrags = parallelPairs.stream()
                                                         .flatMap(pair -> Stream.of(pair.f1, pair.f2))
                                                         .collect(Collectors.toSet());
        Map<CCR, Set<Fragment>> rv = new HashMap<>();

        for (Fragment f : parallelizableFrags)
        {
            CCR ccr = f.getEnclosingCCR();
            if (!rv.containsKey(ccr))
                rv.put(ccr, new HashSet<>());

            rv.get(ccr).add(f);
        }

        return rv;
    }

    private int getWeightForFragPair(@Nonnull Map<CCR, Set<Fragment>> parallelizableFrags,
                                     @Nonnull LockAssignmentChecker.FragmentPair fragPair) {
        switch (fragmentWeightStrategy) {
            case UNIFORM: return 1;
            case PARALLELIZATION_OPPORTUNITIES:
                return Stream.of(fragPair.f1, fragPair.f2)
                        .map(frag -> getWeightForFragBasedOnParallelizationOpportunities(parallelizableFrags, frag))
                        .reduce(Math::min)
                        .get();
            default:
                throw new IllegalStateException("Unrecognized fragment weight strategy " + fragmentWeightStrategy);
        }
    }

    private int getWeightForFragBasedOnParallelizationOpportunities(Map<CCR, Set<Fragment>> parallelizableFrags, Fragment frag)
    {
        if (frag.isSignalPredicateEvalFragment())
            return 1;

        if (frag.getMonitorWriteSet().isEmpty())
            return 1;

        final CCR ccr = frag.getEnclosingCCR();
        int parallelFragsInCCR = parallelizableFrags.get(ccr).size();
        int totalFrag = ccr.getFragments().size();

        return ((double)parallelFragsInCCR / totalFrag) >= 0.7 ? 8 : 4;
    }

    public List<PropositionalFormula> computeEncodingOfUnusedAtomics()
    {
        return atomicFldsVars.values()
                             .stream()
                             .map(formulaBuilder::mkNOT)
                             .collect(Collectors.toList());
    }

    public List<PropositionalFormula> minimizeLocksPerCCR()
    {
        List<PropositionalFormula> formulas = new ArrayList<>();

        fragmentedMonitor.getCCRs()
                         .forEach(ccr -> formulas.addAll(IntStream.range(0, numLocksUpperBound)
                                                                  .mapToObj(lID -> noFragsHoldLock(ccr.getFragments(), lID))
                                                                  .collect(Collectors.toList()))
                         );

        return formulas;
    }

    ///////////////////////////////////////////////////////////////////////////

    /// Correctness constraints ///////////////////////////////////////////////

    /**
     * Compute a list of constraints which, when all hold,
     * ensures correctness of the locking implementation by guaranteeing:
     * (1) any two fragments which may have a race condition share a lock
     * (2) any interleaving of fragments is equivalent to a sequential one
     * (3) there exists an implementable lock order
     * (4) shared predicates may be associated to a single lock
     *
     * @return the list of correctness constraints
     */
    @Nonnull
    List<PropositionalFormula> computeCorrectnessConstraints()
    {
        // Add correctness constraints
        log.debug("Building correctness constraints");
        final List<PropositionalFormula> correctnessConstraints = new ArrayList<>(requireFragsWithRaceMutex());
        correctnessConstraints.addAll(requireNoAtomicityViolations());
        correctnessConstraints.addAll(requireLockOrdering());
        correctnessConstraints.addAll(requireConsistentPredicateLockAssociation());
        log.debug("Correctness constraints built");
        return correctnessConstraints;
    }

    /**
     * @return constraints which make sure that, if two fragments cannot run in parallel,
     *         they share a lock
     */
    @Nonnull
    private List<PropositionalFormula> requireFragsWithRaceMutex() {
        return lockAssignmentChecker.getPossibleRaces()
                .stream()
                .map(this::mutexOrAtomicFldConstraint)
                .collect(Collectors.toList());
    }

    private PropositionalFormula mutexOrAtomicFldConstraint(PossibleRace race)
    {
        return formulaBuilder.mkOR(mutexConstraint(race.getFragments()), convertFieldToAtomic(race));
    }

    private PropositionalFormula convertFieldToAtomic(PossibleRace race)
    {
        Set<MemoryLocations> memLocs = race.memLocs;
        boolean allFields = memLocs.stream()
                                   .allMatch(locs -> locs instanceof SootMayRWSetAnalysis.FieldMemoryLocations);

        if (allFields)
        {
            Set<SootField> fldsForRace = memLocs.stream()
                                                .map(locs -> ((SootMayRWSetAnalysis.FieldMemoryLocations)locs).memObject)
                                                .collect(Collectors.toSet());

            if (fldsForRace.size() == 1)
            {
                SootField racyFld = fldsForRace.iterator().next();

                if (potentialAtomicFields.containsKey(racyFld))
                {
                    boolean fragsCanBeImplWithAtomics = race.getFragments()
                                                            .stream()
                                                            .allMatch(f -> canFragBeAtomicWithAtomicField(f, racyFld));

                    if (fragsCanBeImplWithAtomics)
                    {
                        return getAtomicFieldVar(racyFld);
                    }
                }
            }
        }

        return formulaBuilder.mkFALSE();
    }

    private boolean canFragBeAtomicWithAtomicField(Fragment frag, SootField racyFld)
    {
        return atomicFieldForFrag(frag, racyFld) != null;
    }

    private List<SootField> atomicFieldForFrag(Fragment frag, SootField racyFld)
    {
        SootMethod ccrMeth = frag.getEnclosingCCR().getAtomicSection();
        MayRWSetAnalysis mayRWSetAnalysis = CachedMayRWSetAnalysis.getInstance().getMayRWSetAnalysis();
        Map<SootField, Set<MemoryLocations>> locsPerFld = getMemoryLocationsPerFields(fragmentedMonitor.getMonitor());
        Set<MemoryLocations> mtrThisLocs = getThisMemoryLocs(fragmentedMonitor.getMonitor());
        Set<SootField> readOnlyFields = fragmentedMonitor.getReadOnlyFields();

        Map<SootField, Integer> fragReads = new HashMap<>(), fragWrites = new HashMap<>();

        frag.getAllUnits()
            .forEach(u -> {
                // Ignore atomic pre checks
                if (!canBeAtomicPreCheck(u) &&
                    // Predicate evaluations is purely for signaling, it's ok to read a new predicate value.
                    !Fragment.isSignalPredEvalUnit(u))
                {
                    Collection<MemoryLocations> rSet = mayRWSetAnalysis.readSet(ccrMeth, u),
                            wSet = mayRWSetAnalysis.writeSet(ccrMeth, u);

                    locsPerFld.entrySet()
                              .stream()
                              .filter(e -> !readOnlyFields.contains(e.getKey()))
                              .filter(e -> racyFld == null || racyFld.equals(e.getKey()))
                              .forEach(e ->
                                       {
                                           // We no longer make use of the checker to optimize atomics, so it's fine for the two to be out of sync.
                                           SootField fld = e.getKey();
                                           Set<MemoryLocations> fldLocs = locsPerFld.get(fld);
                                           if (MemoryLocations.mayIntersectAny(fldLocs, rSet))
                                               fragReads.put(fld, fragReads.getOrDefault(fld, 0) + 1);

                                           if (MemoryLocations.mayIntersectAny(fldLocs, wSet))
                                               fragWrites.put(fld, fragWrites.getOrDefault(fld, 0) + 1);
                                           // This is to handle stores.
                                           else if (u instanceof AssignStmt)
                                           {
                                               AssignStmt assignStmt = (AssignStmt) u;
                                               Value leftOp = assignStmt.getLeftOp();
                                               if (leftOp instanceof InstanceFieldRef)
                                               {
                                                   InstanceFieldRef fldRef = (InstanceFieldRef) leftOp;
                                                   if (fldRef.getField().equals(fld) && MemoryLocations.mayIntersectAny(mtrThisLocs, wSet))
                                                   {
                                                       fragWrites.put(fld, fragWrites.getOrDefault(fld, 0) + 1);
                                                   }
                                               }
                                           }
                                       });
                }
            });

        // If more than read or write operation return false
        Integer nReads = fragReads.values()
                                  .stream()
                                  .reduce(0, Integer::sum);
        Integer nWrites = fragWrites.values()
                                    .stream()
                                    .reduce(0, Integer::sum);
        if (nReads > 1 || nWrites > 1)
            return null;

        Set<SootField> fldAccesses = Stream.concat(fragReads.keySet().stream(), fragWrites.keySet().stream())
                                           .collect(Collectors.toSet());

        if (nReads + nWrites == 1 && readOnlyFields.containsAll(fldAccesses))
            return Collections.emptyList();

        if (potentialAtomicFields.keySet().containsAll(fldAccesses))
        {
            assert fldAccesses.size() != 0 || racyFld != null;
            SootField atomicFld = racyFld != null ? racyFld : fldAccesses.iterator().next();
            return Collections.singletonList(atomicFld);
        }

        return null;
    }

    private boolean canBeAtomicPreCheck(Unit u)
    {
        if (!preCheckToPred.containsKey(u))
            return false;

        SootMethod predForPreCheck = preCheckToPred.get(u);
        Map<SootMethod, Set<SootField>> predNonReadOnlyFlds = fragmentedMonitor.getNonReadOnlyPredicateFields();

        // It's guaranteed that only one field would match.
        return predNonReadOnlyFlds.get(predForPreCheck)
                                  .stream()
                                  .anyMatch(potentialAtomicFields::containsKey);
    }

    /**
     * @return constraints which ensure that, if fragment f can run between f1 -> f2,
     *         and has successors which don't commute with predecessors of f1
     *         (or predecessors which don't commute with successors of f2),
     *         then f, f1, f2 share a lock.
     */
    @Nonnull
    private List<PropositionalFormula> requireNoAtomicityViolations()
    {
        return lockAssignmentChecker.getInvalidInterleavings()
                                    .stream()
                                    .map(MutuallyExclusiveFragmentSet::getFragments)
                                    .map(this::mutexConstraint)
                                    .collect(Collectors.toList());
    }

    /**
     * @return constraints which enforce that if i < j,
     *         and lock i is obtained when moving from predFrag -> succFrag
     *         then lock j is not held in both predFrag and succFrag
     */
    @Nonnull
    @SuppressWarnings("UnstableApiUsage")
    private List<PropositionalFormula> requireLockOrdering() {
        return IntStream.range(0, numLocksUpperBound - 1)
                .mapToObj(lowerLock -> IntStream.range(lowerLock + 1, numLocksUpperBound)
                        .mapToObj(upperLock -> fragmentedMonitor.getFragmentsCFG().edges()
                                .stream()
                                .map(edge -> formulaBuilder.mkNOT(
                                        formulaBuilder.mkAND(
                                                isNotAssignedLock(edge.nodeU(), lowerLock),
                                                isAssignedLock(edge.nodeV(), lowerLock),
                                                isAssignedLock(edge.nodeU(), upperLock),
                                                isAssignedLock(edge.nodeV(), upperLock))
                                        )
                                ).collect(Collectors.toList())
                        ).flatMap(Collection::stream)
                        .collect(Collectors.toList())
                ).flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * @return constraints to guarantee that each predicate
     *         can be associated to a lock. In particular, we must associate
     *         a predicate to the lowest lock held at the time we wait
     *         on the predicate. If a predicate is waited upon in
     *         multiple places, this lowest lock must be consistent.
     */
    @Nonnull
    private List<PropositionalFormula> requireConsistentPredicateLockAssociation() {
        final List<PropositionalFormula> fragsWaitingOnPredMutex = fragmentedMonitor.getCcrPredicates()
                .stream()
                .map(fragmentedMonitor::getFragmentsWaitingOnPredicate)
                .map(this::mutexConstraint)
                .collect(Collectors.toList());
        final List<PropositionalFormula> fragsWaitingOnPredHoldSameLockSet = fragmentedMonitor.getCcrPredicates()
                .stream()
                .map(fragmentedMonitor::getFragmentsWaitingOnPredicate)
                .flatMap(fragsWaitingOnPred -> IntStream.range(0, numLocksUpperBound)
                        .mapToObj(lock -> formulaBuilder.mkOR(
                                allFragsHoldLock(fragsWaitingOnPred, lock),
                                noFragsHoldLock(fragsWaitingOnPred, lock))
                        )
                ).collect(Collectors.toList());
        return Streams.concat(
                    fragsWaitingOnPredMutex.stream(),
                    fragsWaitingOnPredHoldSameLockSet.stream()
                ).collect(Collectors.toList());
    }

    ///////////////////////////////////////////////////////////////////////////

    /// Constraints for enumeration ///////////////////////////////////////////

    /**
     * Say two lock maps A, A' are isomorphic if there exists some
     * reordering of locks which makes A = A'
     *
     * This function computes constraints which are satisfied by
     * exactly one member of each equivalence class of isomorphic solutions
     *
     * @return the list of constraints
     */
    @Nonnull
    List<PropositionalFormula> computeSymmetryBreakingConstraints()
    {
        assert numFragments() > 0;
        final List<PropositionalFormula> symmetryBreakingConstraints = new ArrayList<>();
        // easier to write
        final PropositionalFormulaBuilder fb = formulaBuilder;
        // For each pair of locks l < u
        for(int lowerLock = 0; lowerLock < numLocksUpperBound - 1; lowerLock++)
        {
            for(int upperLock = lowerLock + 1; upperLock < numLocksUpperBound; ++upperLock)
            {
                // make copy of variables so that we can use inside a stream
                final int finalLowerLock = lowerLock;
                final int finalUpperLock = upperLock;
                // Build variables distBefore[i]
                final List<PropositionalFormula> distBefore = IntStream.range(0, numFragments())
                        .mapToObj(fragID -> SATNamingUtils.getVarNameForDistinguishedBefore(finalLowerLock, finalUpperLock, fragID))
                        .map(fb::mkFreshVar)
                        .collect(Collectors.toList());
                // distBefore[0] is always false
                distBefore.set(0, fb.mkFALSE());
                // Now make sure distBefore[i] is true iff
                // l and u have been distinguished by some fragment j < i
                // (i.e. frag[j] holds l but not u, or vice versa)
                //
                // First, distBefore[i-1] => distBefore[i]
                IntStream.range(1, numFragments())
                        .mapToObj(i -> fb.mkIMPLIES(distBefore.get(i-1), distBefore.get(i)))
                        .forEach(symmetryBreakingConstraints::add);
                // Second, NOT(frag[i-1] holds l IFF frag[i-1] holds u) => distBefore[i]
                for(int i = 1; i < numFragments(); ++i)
                {
                    // does prev fragment distinguish locks?
                    PropositionalFormula prevFragDist = fragDistinguishesLocks(i - 1, lowerLock, upperLock);
                    // if prev frag distinguishes, then l and u have been distinguished before i
                    symmetryBreakingConstraints.add(fb.mkIMPLIES(prevFragDist, distBefore.get(i)));
                }
                // Now require that, for each fragment, either
                // l and u have already been distinguished, or u => l
                // (i.e. if this is the first fragment to distinguish l from u,
                //  then it holds l and does not hold u)
                for(int i = 0; i < numFragments(); ++i)
                {
                    PropositionalFormula upImpliesLow = fb.mkIMPLIES(
                            isAssignedLock(i, upperLock),
                            isAssignedLock(i, lowerLock));
                    symmetryBreakingConstraints.add(fb.mkOR(distBefore.get(i), upImpliesLow));
                }
            } // end for each upper lock
        } // end for each lower lock
        return symmetryBreakingConstraints;
    }

    /**
     * @return the variables which uniquely define a lock mapping
     */
    @Nonnull
    List<PropositionalFormula> getLockMappingVars()
    {
        return Stream.of(fragIsAssignedLock)
                .flatMap(Stream::of)
                .collect(Collectors.toList());
    }

    ///////////////////////////////////////////////////////////////////////////

    /// Convenient functions for building boolean formulas ////////////////////

    /**
     * Get the indicator for whether frag is assigned lock
     *
     * @param fragID the fragment identifier of frag
     * @param lock the lock
     * @return a boolean formula true iff frag holds lock
     */
    private PropositionalFormula isAssignedLock(int fragID, int lock) {
        return this.fragIsAssignedLock[fragID][lock];
    }

    /**
     * Get the indicator for whether frag is assigned lock
     *
     * @param frag the fragment
     * @param lock the lock
     * @return a boolean formula true iff frag holds lock
     */
    @Nonnull
    private PropositionalFormula isAssignedLock(@Nonnull Fragment frag, int lock) {
        return this.isAssignedLock(getFragmentID(frag), lock);
    }

    /**
     * Get the indicator for whether frag is not assigned lock
     *
     * @param fragID the fragment identifier of frag
     * @param lock the lock
     * @return a boolean formula true iff frag does not hold lock
     */
    @Nonnull
    private PropositionalFormula isNotAssignedLock(int fragID, int lock) {
        return formulaBuilder.mkNOT(isAssignedLock(fragID, lock));
    }

    /**
     * Get the indicator for whether frag is not assigned lock
     *
     * @param frag the fragment
     * @param lock the lock
     * @return a boolean formula true iff frag does not hold lock
     */
    private PropositionalFormula isNotAssignedLock(@Nonnull Fragment frag, int lock) {
        return this.isNotAssignedLock(getFragmentID(frag), lock);
    }

    /**
     * Build a constraint that ensures there is at least one lock
     * shared by all the fragments
     *
     * @param fragments the fragments
     * @throws IllegalArgumentException if given fewer than 2 fragments
     * @return the desired constraint
     */
    @Nonnull
    private PropositionalFormula mutexConstraint(@Nonnull Fragment... fragments) {
        Integer[] fragIDs = Stream.of(fragments)
                .map(this::getFragmentID).toArray(Integer[]::new);
        return mutexConstraint(fragIDs);
    }

    /**
     * Build a constraint that ensures there is at least one lock
     * shared by all the fragments with identifiers in fragIDs
     *
     * @param fragIDs the identifiers of the fragments
     * @throws IllegalArgumentException if given fewer than 2 fragments
     * @return the desired constraint
     */
    @Nonnull
    private PropositionalFormula mutexConstraint(@Nonnull Integer... fragIDs) {
        // if 0 or 1 fragments, this trivially holds
        if(fragIDs.length < 1) {
            throw new IllegalArgumentException("Attempt to build mutex constraint for < 1 fragments");
        }
        // For at least one lock ell
        // all the frag ids must hold lock ell
        return IntStream.range(0, numLocksUpperBound)
                        // all the frag ids must hold lock ell
                        .mapToObj(lock -> Stream.of(fragIDs)
                                                .map(fragID -> isAssignedLock(fragID, lock))
                                                .reduce(this::mkAndOrSelf)
                                                .orElseThrow(missingOptional)
                        )
                        .reduce(formulaBuilder::mkOR)
                        .orElseThrow(missingOptional);
    }

    private PropositionalFormula mkAndOrSelf(PropositionalFormula... args)
    {
        assert args.length > 0;
        return args.length == 1 ? args[0] : formulaBuilder.mkAND(args);
    }

    /**
     * Just like {@link #mutexConstraint(Fragment...)}
     */
    @Nonnull
    private PropositionalFormula mutexConstraint(@Nonnull Collection<Fragment> frags)
    {
        return mutexConstraint(frags.toArray(new Fragment[0]));
    }

    /**
     * Return a formula which is satisfied iff the specified fragment
     * holds one, but not both, of lock1 and lock2
     *
     * @param fragID the fragment
     * @param lock1 the first lock
     * @param lock2 the second lock
     * @return the formula
     */
    @Nonnull
    private PropositionalFormula fragDistinguishesLocks(int fragID, int lock1, int lock2)
    {
        return formulaBuilder.mkNOT(
                formulaBuilder.mkIFF(
                        isAssignedLock(fragID, lock1),
                        isAssignedLock(fragID, lock2)
                )
        );
    }

    /**
     * Build a formula which holds true iff all fragments in frags
     * hold the given lock
     *
     * @param frags the fragments
     * @param lock the lock
     * @return a boolean formula tru iff all specified fragments hold the lock
     */
    @Nonnull
    private PropositionalFormula allFragsHoldLock(@Nonnull Collection<Fragment> frags, int lock) {
        return frags.stream()
                .map(frag -> isAssignedLock(frag, lock))
                .reduce(formulaBuilder::mkAND)
                // If no fragments, trivially true
                .orElse(formulaBuilder.mkTRUE());
    }

    /**
     * Build a formula which holds true iff no fragments in frags
     * hold the given lock
     *
     * @param frags the fragments
     * @param lock the lock
     * @return a boolean formula tru iff no specified fragments hold the lock
     */
    @Nonnull
    private PropositionalFormula noFragsHoldLock(@Nonnull Collection<Fragment> frags, int lock) {
        return frags.stream()
                .map(frag -> isNotAssignedLock(frag, lock))
                .reduce(formulaBuilder.mkTRUE(), formulaBuilder::mkAND);
    }

    /// Basic utility /////////////////////////////////////////////////////////

    /**
     * @return list of all fragments
     */
    @Nonnull
    private List<Fragment> getFragments() {
        return this.fragmentedMonitor.getFragments();
    }

    /**
     * Get fragment identifier
     *
     * @param frag the fragment
     * @return the fragment identifier
     */
    private int getFragmentID(@Nonnull Fragment frag) {
        // sanity check
        assert frag2index.containsKey(frag);
        return this.frag2index.get(frag);
    }

    /**
     * @return the number of fragments
     */
    private int numFragments() {
        return this.fragmentedMonitor.numFragments();
    }
    ///////////////////////////////////////////////////////////////////////////
}
