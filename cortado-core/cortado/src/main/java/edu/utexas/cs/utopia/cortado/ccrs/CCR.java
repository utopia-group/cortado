package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootAnnotationUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.IdentityStmt;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import soot.jimple.toolkits.annotation.purity.DirectedCallGraph;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.tagkit.AnnotationTag;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A critical-code region. The atomic section is represented
 * as a {@link SootMethod}. If the atomic section should only
 * be executed once a guard holds true, that guard is also
 * represented as a {@link SootMethod}.
 */
public class CCR
{
    // atomic section and guard
    final SootMethod atomicSection, ccrGuard;
    // annotation info
    private static final Class<Atomic> atomicAnnotationClass = Atomic.class;
    private static final String waituntilGuardParameterName = "waituntil";
    private Fragment waituntilFragment = null;
    // all fragments and special fragments
    private ImmutableList<Fragment> fragments = null;
    private Fragment entryFragment = null;
    // fragment control-flow
    @SuppressWarnings("UnstableApiUsage")
    private Graph<Fragment> fragmentCFG = null;
    // logger
    private final static Logger log = LoggerFactory.getLogger(CCR.class.getName());

    /**
     * Extract and return the {@link AnnotationTag} corresponding
     * to an {@link Atomic} annotation.
     *
     * @param method the method to get the annotation from
     * @return the annotation, or null if there is no annotation.
     */
    @Nullable
    private static AnnotationTag getCCRAnnotation(@Nonnull SootMethod method)
    {
        return SootAnnotationUtils.getAnnotation(method, atomicAnnotationClass);
    }

    /**
     * Get the name of the guard method.
     * Empty string represents no guard method.
     *
     * @param atomicAnnotation the annotation to extract the guard method name from
     * @return the guard method name.
     */
    @Nonnull
    private static String getGuard(@Nonnull AnnotationTag atomicAnnotation)
    {
        String waituntilGuard = SootAnnotationUtils.getStringParameter(atomicAnnotation, waituntilGuardParameterName);
        // if no waituntil guard parameter, get the default value
        if(waituntilGuard == null)
        {
            try
            {
                // get the method and extract the default value
                final Method method = atomicAnnotationClass.getDeclaredMethod(waituntilGuardParameterName);
                waituntilGuard = (String) method.getDefaultValue();
            } catch (NoSuchMethodException e)
            {
                // This just means we got the names wrong.
                throw new RuntimeException(waituntilGuardParameterName
                                + "() method not found in atomic annotation class "
                                + atomicAnnotationClass);
            }
        }
        return waituntilGuard;
    }

    /**
     * Returns true iff method has an {@link #atomicAnnotationClass} annotation.
     *
     * @param method the method
     * @return true iff method is annotated as atomic.
     */
    static public boolean isCCRMethod(@Nonnull SootMethod method)
    {
        return getCCRAnnotation(method) != null;
    }

    static private boolean hasGuard(@Nonnull SootMethod method)
    {
        return !getGuard(Objects.requireNonNull(getCCRAnnotation(method))).equals("");
    }

    /**
     * Figure out if method may invoke a CCR-method.
     * If {@link soot} has no call graph (see {@link Scene#hasCallGraph()})
     * just returns true and logs a warning.
     *
     * @param method the method
     * @return The CCR-method which {@link Scene#getCallGraph()} indicates
     *          method may call. null if there is no method.
     */
    @Nullable
    static private SootMethod mayCallCCRMethod(@Nonnull SootMethod method)
    {
        // If we have a call graph, make sure we don't call any other CCR methods
        if(Scene.v().hasCallGraph())
        {
            final CallGraph cg = Scene.v().getCallGraph();
            final DirectedCallGraph dcg = new DirectedCallGraph(
                    cg,
                    // Ignore static initializers, it's OK for our context.
                    m -> !m.getName().contains("<clinit>"),
                    Collections.singleton(method).iterator(),
                    false);
            for(SootMethod reachableMethod : dcg)
            {
                if(CCR.isCCRMethod(reachableMethod) &&
                   hasGuard(reachableMethod)        &&
                   // allow reentrancy
                   !reachableMethod.equals(method))
                {
                    // only count this method as reachable if it is reachable through
                    // a series of other invocations
                    if(!Objects.equals(reachableMethod, method) || !dcg.getPredsOf(method).isEmpty())
                    {
                        return reachableMethod;
                    }
                }
            }
        } // Otherwise, warn user we are not verifying
        else {
            log.warn("Soot has no call-graph, please manually verify " +
                    "that no CCR methods may be invoked by other CCR methods.");
        }
        return null;
    }

    // TODO: We should also require that no constructors are CCRs,
    //       and that no CCRs may be called by constructors
    /**
     * Create a CCR
     *
     * @param atomicSection the method representing the atomic section
     * @throws IllegalArgumentException if atomicSection is not a CCR method
     *          (see {@link #isCCRMethod(SootMethod)}), is static, or has
     *          no active body
     * @throws IllegalArgumentException if atomicSection (or its guard, if it has one)
     *          may invoke another CCR method. This check is only performed
     *          if soot has a call-graph (see {@link Scene#hasCallGraph()}).
     * @throws IllegalArgumentException If guard is invalid. See {@link Atomic#waituntil()}
     *          for details.
     */
    CCR(@Nonnull SootMethod atomicSection)
    {
        // make sure atomicSection is a CCR method, non-static, and with an active
        // body
        if(!isCCRMethod(atomicSection))
        {
            throw new IllegalArgumentException("method " + atomicSection +
                    " is missing annotation " + Atomic.class.getName());
        }
        if(atomicSection.isStatic())
        {
            throw new IllegalArgumentException("method " + atomicSection + " is static.");
        }
        if(!atomicSection.hasActiveBody())
        {
            throw new IllegalArgumentException("method " + atomicSection + " has no active body.");
        }
        // make sure the atomic section won't invoke other CCR methods
        if(mayCallCCRMethod(atomicSection) != null)
        {
            throw new IllegalArgumentException("CCR method " + mayCallCCRMethod(atomicSection)
                    + " is reachable from CCR method " + atomicSection);
        }
        // figure out the guard, if there is one
        final AnnotationTag atomicTag = getCCRAnnotation(atomicSection);
        assert atomicTag != null;
        final String guardMethodName = getGuard(atomicTag);
        // special value of empty string indicates no guard
        if(!"".equals(guardMethodName))
        {
            final List<SootMethod> possibleGuards = atomicSection.getDeclaringClass()
                    .getMethods()
                    .stream()
                    .filter(method -> method.getName().equals(guardMethodName))
                    .collect(Collectors.toList());
            // make sure we have exactly one guard method
            if(possibleGuards.isEmpty())
            {
                throw new IllegalArgumentException("There is no method " + guardMethodName
                        + " of atomic method " + atomicSection + " in declaring class "
                        + atomicSection.getDeclaringClass() + " to act as a guard.");
            }
            if(possibleGuards.size() > 1)
            {
                throw new IllegalArgumentException("Multiple candidate guard methods of name "
                        + guardMethodName + " of atomic method " + atomicSection + " in declaring class "
                        + atomicSection.getDeclaringClass());
            }
            SootMethod ccrGuard = possibleGuards.get(0);
            // make sure the guard is private
            if(!ccrGuard.isPrivate())
            {
                throw new IllegalArgumentException("Guard method " + ccrGuard + " is not private.");
            }
            // make sure the guard returns a boolean
            if(!BooleanType.v().equals(ccrGuard.getReturnType()))
            {
                throw new IllegalArgumentException("Guard method " + ccrGuard
                        + " must return a boolean, not " + ccrGuard.getReturnType());
            }
            // make sure we have either 0 arguments or the same
            // arguments as the atomic section
            final List<Type> guardParamTypes = ccrGuard.getParameterTypes();
            if(!Objects.equals(guardParamTypes, atomicSection.getParameterTypes()) && !guardParamTypes.isEmpty())
            {
                throw new IllegalArgumentException("Guard method " + ccrGuard + " must have no arguments, " +
                        " or the same argument types as its atomic section " + atomicSection);
            }
            // make sure the ccrGuard throws no explicit exceptions
            if(!ccrGuard.getExceptions().isEmpty())
            {
                throw new IllegalArgumentException("Guard method " + ccrGuard + " throws an exception.");
            }
            // make sure the ccrGuard is not a CCR method
            if(CCR.isCCRMethod(ccrGuard))
            {
                throw new IllegalArgumentException("Guard method " + ccrGuard + " is declared Atomic.");
            }
            // Make sure the guard won't invoke any CCR methods
            if(mayCallCCRMethod(ccrGuard) != null)
            {
                throw new IllegalArgumentException("CCR method " + mayCallCCRMethod(ccrGuard)
                        + " is reachable from CCR guard " + ccrGuard);
            }
            // now store the guard, and its parameters
            this.ccrGuard = ccrGuard;
        } else {
            this.ccrGuard = null;
        }
        this.atomicSection = atomicSection;
    }

    /**
     * @return the soot method whose body is the atomic section
     */
    @Nonnull
    public SootMethod getAtomicSection() {
        return atomicSection;
    }

    /**
     * Get the units of the {@link #atomicSection}'s
     * active body.
     *
     * @return the units
     */
    @Nonnull
    public UnitPatchingChain getUnits() {
        return getAtomicSection().getActiveBody().getUnits();
    }

    /**
     * @return the guard of this CCR
     * @throws IllegalStateException if this CCR has no guard (see {@link #hasGuard()})
     */
    @Nonnull
    public SootMethod getGuard() {
        if(!hasGuard()) {
            throw new IllegalStateException("CCR method " + getAtomicSection() + " has no guard.");
        }
        return ccrGuard;
    }

    /**
     * @return true iff this CCR has a guard
     */
    public boolean hasGuard() {
        return ccrGuard != null;
    }

    void setWaituntilFragment(@Nonnull Fragment waituntilFragment)
    {
        if(this.waituntilFragment != null)
        {
            throw new IllegalArgumentException("waituntilFragment has already been set for " + this);
        }
        if(!hasGuard())
        {
            throw new IllegalArgumentException("Cannot set waituntil fragment for a CCR with no guard");
        }
        this.waituntilFragment = waituntilFragment;
    }

    /**
     * Set fragments of the CCR.
     * Should only be used internally by {@link FragmentPartitioner}.
     *
     * The CCR's {@link #waituntilFragment} is automatically added to the partition
     * by this method.
     *
     * @param fragments the fragments of this CCRs
     * @throws IllegalStateException if this CCR {@link #hasGuard()}, but {@link #setWaituntilFragment(Fragment)} has
     *                               not been called
     * @throws IllegalArgumentException if any of the fragments intersect,
     *      if any non-exit unit (except {@link IdentityStmt})s of the atomic section
     *      is not included in the fragments,
     *      if any {@link IdentityStmt} or exit unit is contained in a fragment,
     *      or if any fragment is not contained in this CCR
     */
    void setFragments(@Nonnull List<Fragment> fragments) {
        if(hasGuard() && waituntilFragment == null)
        {
            throw new IllegalStateException("Attempt to setFragments() of a CCR with a guard before calling setWaituntilFragment()");
        }
        // set fragments and clear fragmentCFG
        if(hasGuard())
        {
            fragments = new ArrayList<>(fragments);
            fragments.add(getWaituntilFragment());
        }
        this.fragments = ImmutableList.copyOf(fragments);
        this.fragmentCFG = null;
        // make sure fragments are disjoint
        final Map<Unit, Fragment> unitToFrag = computeUnitToFragmentMap();
        for(Fragment frag : fragments) {
            for(Unit ut : frag.getAllUnits()) {
                assert unitToFrag.containsKey(ut);
                if(!Objects.equals(frag, unitToFrag.get(ut))) {
                    throw new IllegalArgumentException("Non-disjoint fragments: " +
                            " fragments " + frag + " and " + unitToFrag.get(ut) +
                            " meet at unit " + ut);
                }
            }
        }
        // Make units are in a fragment iff they are not an identity stmt or exit unit
        final UnitPatchingChain allUnits = getUnits();
        final Set<Unit> exitUnits = SootUnitUtils.getExplicitExitUnits(getAtomicSection());
        for(Unit ut : allUnits)
        {
            boolean unitIsIdentity = ut instanceof IdentityStmt;
            boolean unitIsExit = exitUnits.contains(ut);
            boolean unitInFrag = unitToFrag.containsKey(ut);
            if(unitIsIdentity && unitInFrag)
            {
                throw new IllegalArgumentException("IdentityStmt in fragment.");
            } else if(unitIsExit && unitInFrag)
            {
                throw new IllegalArgumentException("Exit unit in fragment.");
            }
            else if(!unitIsIdentity && !unitIsExit && !unitInFrag)
            {
                throw new IllegalArgumentException("Unit " + ut + " not contained in any fragment.");
            }
        }
        // make sure all fragments are in the CCR
        boolean allFragsInCCR = fragments.stream()
                .map(Fragment::getEnclosingCCR)
                .allMatch(this::equals);
        if(!allFragsInCCR){
            throw new IllegalArgumentException("Not all fragments contained in CCR.");
        }
        // record the entry fragment
        final Stmt firstUnit = ((JimpleBody) getAtomicSection().getActiveBody()).getFirstNonIdentityStmt();
        entryFragment = unitToFrag.get(firstUnit);
        assert entryFragment != null || fragments.isEmpty();
    }

    /**
     * @return the fragments of this monitor.
     * @throws IllegalStateException if {@link #setFragments(List)} has not been called yet
     */
    @Nonnull
    public List<Fragment> getFragments() {
        if(fragments == null) {
            throw new IllegalStateException("Call to getFragments() before any call to setFragments()");
        }
        return fragments;
    }

    /**
     * @return the waituntil fragment
     * @throws IllegalStateException if the CCR has no guard, or if {@link #setWaituntilFragment(Fragment)} has not been called yet
     */
    @Nonnull
    public Fragment getWaituntilFragment()
    {
        if(!hasGuard())
        {
            throw new IllegalStateException("A CCR with no guard has no waituntil fragment!");
        }
        if(waituntilFragment == null)
        {
            throw new IllegalStateException("Call to getWaituntilFragment() before call to setWaituntilFragment");
        }
        return waituntilFragment;
    }

    /**
     * @return the entry fragment
     * @throws IllegalStateException if setFragments() has not been called yet.
     */
    @Nonnull
    public Fragment getEntryFragment() {
        if(fragments == null) {
            assert entryFragment == null;
            throw new IllegalStateException("Call to getEntryFragment() before any call to setFragments()");
        }
        assert entryFragment != null;
        return entryFragment;
    }

    /**
     * Fragments must have been set
     *
     * @return a control-flow graph between fragments
     */
    @SuppressWarnings("UnstableApiUsage")
    public Graph<Fragment> getFragmentsCFG() {
        if(fragmentCFG == null) {
            if (fragments == null) {
                throw new IllegalStateException("Attempt to compute fragments CFG before setting fragments.");
            }
            // get unit-to-fragment map
            final Map<Unit, Fragment> unitToFragmentMap = computeUnitToFragmentMap();
            // get exit units
            final Set<Unit> exitUnits = SootUnitUtils.getExplicitExitUnits(atomicSection);
            // Build an empty graph to hold the fragment control-flow
            // graph in
            MutableGraph<Fragment> fragCFG = GraphBuilder
                    .directed()
                    .expectedNodeCount(fragments.size())
                    .allowsSelfLoops(true)
                    .build();
            // Get unit control-flow-graph
            ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(atomicSection);
            MHGDominatorsFinder<Unit> domTrees = new MHGDominatorsFinder<>(cfg);
            // DFS from each non-identity, non-exit node
            Stack<Unit> toVisit = new Stack<>();
            Set<Unit> visited = new HashSet<>();
            for (Unit ut : atomicSection.getActiveBody().getUnits()) {
                if (visited.contains(ut) || ut instanceof IdentityStmt || exitUnits.contains(ut)) {
                    continue;
                }
                visited.add(ut);
                toVisit.push(ut);
                // run DFS
                while (!toVisit.isEmpty()) {
                    // get current unit and fragment
                    Unit cur = toVisit.pop();
                    Fragment srcFrag = unitToFragmentMap.get(cur);
                    // Since we don't look past the end of the CCR, all units
                    // should have fragments!
                    assert srcFrag != null;
                    // make sure frag gets a node
                    if (!fragCFG.nodes().contains(srcFrag)) {
                        fragCFG.addNode(srcFrag);
                    }
                    List<Unit> curDoms = domTrees.getDominators(cur);
                    // Visit successors
                    for (Unit nbr : cfg.getSuccsOf(cur)) {
                        // add any edges between fragments.
                        Fragment destFrag = unitToFragmentMap.get(nbr);
                        // all discovered units should EITHER have an associated fragment,
                        // or be an exit unit
                        assert destFrag != null ^ exitUnits.contains(nbr);
                        // Record the edge, if it exists
                        if(destFrag != null)
                        {
                            if (srcFrag != destFrag ||
                                // Only add self-loop if fragment contains a loop
                                curDoms.contains(nbr))
                                fragCFG.putEdge(srcFrag, destFrag);
                            // continue with dfs
                            if (!visited.contains(nbr)) {
                                visited.add(nbr);
                                toVisit.push(nbr);
                            }
                        }
                    }
                } // End DFS from current unit
            } // End DFS over all units
            fragmentCFG = ImmutableGraph.copyOf(fragCFG);
        }
        return fragmentCFG;
    }

    /**
     * compute a map from units in the atomic section
     * to the fragment containing it.
     *
     * @return the map
     */
    @Nonnull
    public Map<Unit, Fragment> computeUnitToFragmentMap() {
        Map<Unit, Fragment> unitToFragmentMap = new HashMap<>();
        for(Fragment frag : getFragments())
        {
            frag.getAllUnits().forEach(ut -> unitToFragmentMap.put(ut, frag));
        }
        return unitToFragmentMap;
    }

    @Override
    public String toString() {
        String stringRep = "atomic: " + getAtomicSection();
        if(hasGuard()) {
            stringRep = "waituntil(" + getGuard() + "); " + stringRep;
        }
        return stringRep;
    }

    @Override
    public int hashCode()
    {
        return java.util.Objects.hash(atomicSection);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (!(obj instanceof CCR))
            return false;
        CCR other = (CCR) obj;
        return java.util.Objects.equals(atomicSection, other.atomicSection);
    }
}
