package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.collect.Sets;
import edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils;
import edu.utexas.cs.utopia.cortado.util.soot.PostDominatorTreeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.DominatorNode;
import soot.toolkits.graph.DominatorTree;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;
import java.util.stream.Collectors;

/*
 *
 * USER PARALLELIZATION ORACLE INTERPRETER:
 * 	- The user parallelization oracle looks for calls to
 * 	  {@link #edu.utexas.cs.utopia.cortado.mockclasses.ir.Fragment.beginFragment beginFragment}
 *     and
 * 	  {@link #edu.utexas.cs.utopia.cortado.mockclasses.ir.Fragment.endFragment endFragment}
 *   - records their fragments in the user oracle
 *   - removes the calls (replacing them each with a nop)
 *   - uses the argument passed to begin/end as the name of the fragments.
 *   - If multiple fragments share a name, they are added as parallel pairs.
 *
 *  Fragments must
 *  		- appear inside CCRs
 *  		- have matching begin/end calls
 *  		- be disjoint
 *  		- satisfy the post-domination requirement (i.e. lastUnit postdominates firstUnit)
 *
 *  the last three points are validated, and thrown an exception otherwise.
 *  Any fragments indicated by the user which appear outside of CCRs
 *  are ignored.
 */
public class UserParallelizationOracleTranslator extends BodyTransformer
{
    // User acts as the ParallelizationOracle
    private ParallelizationOracle userOracle = null;
    // Frag -> its CCR
    private final Map<Fragment, CCR> frag2CCR = new HashMap<>();
    // Map fragments to user annotations
    private final Map<Fragment, Set<String>> frag2IDs = new HashMap<>(),
        frag2parFragIDs = new HashMap<>();
    // bodies to a list of CCRs in the body. Taken as input
    private final Map<Body, CCR> bodies2ccrs;
    private final Logger log = LoggerFactory.getLogger(UserParallelizationOracleTranslator.class.getName());

    public UserParallelizationOracleTranslator(Map<Body, CCR> bodies2ccrs)
    {
        this.bodies2ccrs = bodies2ccrs;
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options)
    {
        log.warn("Using deprecated class. May experience unexpected results.");
        // Get the CCRs, (if there are none, there's nothing to do!)
        CCR ccr = bodies2ccrs.getOrDefault(b, null);
        if (ccr == null) return;

        /// Collect the fragments and replace them with nops //////////////////

        // These will be used to hold fragments
        List<Unit> fragFirstUnit = new ArrayList<>(),
                fragLastUnit = new ArrayList<>();
        // Record the fragment identifiers, parFrag identifiers, and commuting
        // pairs identifiers, (can think of each list as mapping
        //                     from frag index to IDs)
        List<Set<String>> allFragIDs = new ArrayList<>(),
                allParFragIDs = new ArrayList<>();

        // Get body units and fragment begin/end methods
        UnitPatchingChain units = b.getUnits();
        SootClass fragClass = Scene.v().getSootClass(MonitorInterfaceUtils.FRAGMENT_MOCK_CLASS_CLASSNAME);
        SootMethod beginInitFragMeth = fragClass.getMethod(
                "void beginInitialFragment(java.lang.String,java.lang.String,java.lang.String)"),
                beginFragMeth = fragClass.getMethod(
        "void beginFragment(java.lang.String,java.lang.String,java.lang.String)"),
                endFragMeth = fragClass.getMethod("void endFragment()");

        // Get the CFG
        ExceptionalUnitGraph cfg = new ExceptionalUnitGraph(b);
        boolean seenAtLeastOneFrag = false;

        boolean insideFrag = false;
        // iterate through the units of CCRs, looking for a fragment
        Unit firstUnit = ((JimpleBody) ccr.getAtomicSection().getActiveBody()).getFirstNonIdentityStmt();
        Stack<Unit> toVisit = new Stack<>();
        Set<Unit> visited = new HashSet<>();
        toVisit.push(firstUnit);
        visited.add(firstUnit);
        // We DFS through the CCR to look for fragment begin/end pairs.
        // If placed properly, this is correct (since the end
        // must post-dominate the beginning).
        while (!toVisit.empty())
        {
            Unit curUnit = toVisit.pop();
            // Record neighbors of this unit
            for (Unit nbr : cfg.getSuccsOf(curUnit))
            {
                if (!visited.contains(nbr))
                {
                    toVisit.push(nbr);
                    visited.add(nbr);
                }
            }
            // See if we began/ended a fragment
            if (curUnit instanceof InvokeStmt)
            {
                InvokeStmt invk = (InvokeStmt) curUnit;
                InvokeExpr invkExpr = invk.getInvokeExpr();
                // beginFragment() case
                if (Objects.equals(invkExpr.getMethod(), beginFragMeth)
                        || Objects.equals(invkExpr.getMethod(), beginInitFragMeth))
                {
                    // Make sure we're not inside a fragment yet,
                    // and that we're passed a valid name. Get that name.
                    if (insideFrag)
                    {
                        throw new IllegalArgumentException(
                                "unexpected beginFragment() encountered before end"
                                        + " of current fragment");
                    }
                    insideFrag = true;
                    // sanity check: should take IDs, parFrags and commIDs as argument
                    assert (invkExpr.getArgCount() == 3);
                    Value idsArg = invkExpr.getArg(0),
                          parFragArg = invkExpr.getArg(1),
                          commPairsArg = invkExpr.getArg(2);
                    // Make sure we got string constants
                    String errorMsg = String.format(
                        "argument passed to beginFragment() must be a " +
                        " StringConstant in method %s.", b.getMethod().getName());
                    if (!(idsArg instanceof StringConstant)) {
                        throw new IllegalArgumentException("first " + errorMsg);
                    }
                    if (!(parFragArg instanceof StringConstant)) {
                        throw new IllegalArgumentException("second " + errorMsg);
                    }
                    if (!(commPairsArg instanceof StringConstant)) {
                        throw new IllegalArgumentException("third " + errorMsg);
                    }
                    // Now get and validate the id lists
                    Set<String> ids, parFragIDs;
                    try {
                        ids = getIDs((StringConstant) idsArg, false);
                    } catch(IllegalArgumentException e) {
                        System.err.println("Failed to parse ids argument in method " +
                                b.getMethod().getName());
                        throw e;
                    }
                    // get the parFrags
                    try {
                        parFragIDs = getIDs((StringConstant) parFragArg, true);
                    } catch(IllegalArgumentException e) {
                        System.err.println("Failed to parse parFragIDs argument in method " +
                                b.getMethod().getName());
                        throw e;
                    }
                    // Now replace with a nop
                    NopStmt replacement = Jimple.v().newNopStmt();
                    units.swapWith(curUnit, replacement);
                    // Handle initial fragment case to figure out first unit
                    boolean isInitialFrag = Objects.equals(invkExpr.getMethod(), beginInitFragMeth);
                    if (isInitialFrag && seenAtLeastOneFrag) {
                        throw new IllegalArgumentException(
                                "beginInitialFragment() call must be the first fragment"
                                        + " in the CCR");
                    }
                    seenAtLeastOneFrag = true;
                    fragFirstUnit.add(replacement);
                    // Record names, parfrags
                    allFragIDs.add(ids);
                    allParFragIDs.add(parFragIDs);
                }
                // endFragment() case
                else if (Objects.equals(invkExpr.getMethod(), endFragMeth))
                {
                    if (!insideFrag)
                    {
                        throw new IllegalArgumentException(
                                "Unexpected endFragment() while not inside fragment");
                    }
                    insideFrag = false;
                    // sanity check, endFragment() should take no arguments
                    assert (invkExpr.getArgCount() == 0);
                    // Now replace with a nop
                    NopStmt replacement = Jimple.v().newNopStmt();
                    units.swapWith(curUnit, replacement);
                    // Finally, record the nop as the lastUnit
                    // (or, if it is immediately before the end of the CCR,
                    //  make the end of the CCR the lastUnit)
                        fragLastUnit.add(replacement);
                }
            }
        }
        if (fragLastUnit.size() != fragFirstUnit.size())
        {
            throw new IllegalArgumentException("Unmatched beginFragment(), "
                                                       + "did you remember to call endFragment()?");
        }
        ///////////////////////////////////////////////////////////////////////

        /// Now build the fragments, and make sure lastUnits //////////////////
        /// post-dominate firstUnits //////////////////////////////////////////

        // Get post-dominator tree
        final DominatorTree<Unit> pdomTree = PostDominatorTreeCache.getInstance().getOrCompute(b.getMethod());

        for (int fragIndex = 0; fragIndex < fragFirstUnit.size(); ++fragIndex) {
            // make sure lastUnitInFrag dominates firstUnitInFrag
            Unit firstUnitInFrag = fragFirstUnit.get(fragIndex),
                    lastUnitInFrag = fragLastUnit.get(fragIndex);
            DominatorNode<Unit> firstUnitNode = pdomTree.getDode(firstUnitInFrag),
                    lastUnitNode = pdomTree.getDode(lastUnitInFrag);
            if (!pdomTree.isDominatorOf(lastUnitNode, firstUnitNode)) {
                throw new IllegalArgumentException(
                        "User-declared endFragment() does not post-dominate"
                                + " beginFragment()");
            }

            // Build the fragment
            Fragment frag = new Fragment(ccr, firstUnitInFrag, Collections.singleton(lastUnitInFrag));
            // record its ccr
            this.frag2CCR.put(frag, ccr);
            // Record fragment ids
            this.frag2IDs.put(frag, allFragIDs.get(fragIndex));
            this.frag2parFragIDs.put(frag, allParFragIDs.get(fragIndex));
        }
        ///////////////////////////////////////////////////////////////////////
    }

    /**
     * @return the user-specified ParallelizationOracle
     */
    public ParallelizationOracle getUserOracle()
    {
        // If haven't computed user oracle yet, compute it!
        if(this.userOracle == null) {
            this.userOracle = new ParallelizationOracle();
            // Add all the fragments to the user oracle
            frag2IDs.keySet().forEach(frag -> this.userOracle.addFragment(frag, frag2CCR.get(frag)));

            /// Record any parallel pairs //////////////////////////////////////////
            for(Map.Entry<Fragment, Set<String>> fragAndIDs : frag2IDs.entrySet()) {
                Fragment frag = fragAndIDs.getKey();
                Set<String> fragIDs = fragAndIDs.getValue();
                Set<String> parFragIDs = frag2parFragIDs.get(frag);
                // Can assert this because par frag ids were validated
                assert(!parFragIDs.isEmpty());
                // Temporarily put SELF in ids
                fragIDs.add("SELF");
                // Add all parallel fragments
                if(parFragIDs.contains("ALL")) {
                    frag2IDs.keySet()
                            .forEach(otherFrag -> this.userOracle.addParallelPair(frag, otherFrag));
                }
                else if(parFragIDs.contains("NOT")) {
                    frag2IDs.keySet().stream()
                            .filter(otherFrag -> {
                                Set<String> otherIDs = frag2IDs.get(otherFrag);
                                return Collections.disjoint(parFragIDs, otherIDs);
                            })
                            .forEach(otherFrag -> this.userOracle.addParallelPair(frag, otherFrag));
                }
                else if(!parFragIDs.contains("NONE")) {
                    frag2IDs.keySet().stream()
                            .filter(otherFrag -> {
                                Set<String> otherIDs = frag2IDs.get(otherFrag);
                                return !Collections.disjoint(parFragIDs, otherIDs);
                            })
                            .forEach(otherFrag -> this.userOracle.addParallelPair(frag, otherFrag));
                }
                // Remove SELF from current fragment
                fragIDs.remove("SELF");
            }
            ///////////////////////////////////////////////////////////////////////
        }
        return userOracle;
    }

    /**
     * Return a list of the IDs if valid, otherwise throw exception
     *
     * @throws IllegalArgumentException if invalid
     * @param sc the string constant
     * @param keywordsAllowed are keywords allowed?
     * @return the list of IDs, if valid
     */
    private Set<String> getIDs(StringConstant sc, boolean keywordsAllowed) {
        List<String> ids = Arrays.stream(sc.value.split(","))
                .collect(Collectors.toList());
        validateIDs(ids, keywordsAllowed);
        return new HashSet<>(ids);
    }

    /**
     * Validate the ids: do nothing if valid, otherwise throw exception.
     *
     * Valid if does not have empty string as ID, has at least one ID,
     * if no keywords allowed then has no keywords, if
     * ALL or NONE is used then they are the only keyword (respectively),
     * and if NOT is used then it is used exactly once and comes first
     *
     * @throws IllegalArgumentException if invalid
     * @param ids the ids
     * @param keywordsAllowed are keywords allowed
     */
    private void validateIDs(List<String> ids, boolean keywordsAllowed) {
        if(ids.isEmpty()) {
            throw new IllegalArgumentException("At least one ID must be provided");
        }
        // No empty string
        if(ids.contains("")) {
            throw new IllegalArgumentException("Empty string is not an allowed ID");
        }
        // make sure no keywords if not allowed
        if(!keywordsAllowed) {
            Set<String> keywords = new HashSet<>(Arrays.asList("ALL", "NONE", "NOT", "SELF"));
            if(!Collections.disjoint(ids, keywords)) {
                throw new IllegalArgumentException("Cannot have reserved keyword(s) " +
                        Sets.intersection(new HashSet<>(ids), keywords) + " as an id.");
            }
        } // Otherwise, make sure keywords are used properly
        else {
            // If use ALL or NONE, must be only member of list
            for (String keyword : Arrays.asList("ALL", "NONE")) {
                if (ids.contains(keyword) && ids.size() > 1) {
                    throw new IllegalArgumentException("When keyword " +
                            keyword + " is present in ID-list, it must be the only" +
                            "ID.");
                }
            }
            // If use NOT, must be first and have other IDs
            if (ids.contains("NOT")) {
                if(ids.size() < 2) {
                    throw new IllegalArgumentException("When keyword NOT" +
                            " is present in ID-list, it must not be the only ID");
                }
                if(ids.lastIndexOf("NOT") != 0) {
                    throw new IllegalArgumentException("Keyword NOT may appear " +
                            "at most once in ID-list, and must be the first ID");
                }
            }
        }
    }
}
