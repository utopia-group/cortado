package edu.utexas.cs.utopia.cortado.util.visualization;

import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.staticanalysis.CommutativityAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.RaceConditionAnalysis;
import edu.utexas.cs.utopia.cortado.util.graph.GuavaExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.graph.StronglyConnectedComponents;
import soot.Body;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class which can generate visualizations using dot.
 *
 * This is primarily used to help developers see fragments and
 * fragment interactions
 */
public class DotFactory {

    /**
     * Make dot files to show the fragment partition of
     * the given CCR
     *
     * @param ccr the CCR
     * @param condense_fragments if true, only show first/last units
     *        of fragments and unify edges between
     *        fragments
     * @throws IllegalArgumentException if b is not partitioned by
     *         fragmentedMonitor
     * @return A string of .dot code to draw the provided method
     *         with fragments as clusters
     */
    static public String drawPartitionedCCR(@Nonnull CCR ccr,
                                            boolean condense_fragments) {
        HashMap<Fragment, Integer> frag2id = new HashMap<>();
        HashMap<Unit, Integer> unit2id = new HashMap<>();
        return drawPartitionedCCR(
                ccr,
                frag2id,
                unit2id,
                condense_fragments,
                false);
    }

    /**
     * Get string for the graphviz node (or cluster) label of a frag
     *
     * @param frag2id Map from fragment to its id
     * @param frag the fragment
     * @param condense_fragments are we condensing fragments?
     * @return The node label for the fragment
     */
    static private String fragNodeString(Map<Fragment, Integer> frag2id, Fragment frag, boolean condense_fragments) {
        assert frag2id.containsKey(frag);
        // Get format string
        String formatString;
        if(condense_fragments) {
            formatString = "frag%d";
        }
        else {
            formatString = "cluster_frag%d";
        }
        // Substitute fragId
        return String.format(formatString, frag2id.get(frag));
    }

    /**
     * Get string for the graphviz node label of a unit
     *
     * @param unit2id Map from unit to its id
     * @param ut the unit
     * @return The node label for the unit
     */
    static private String unitNodeString(Map<Unit, Integer> unit2id, Unit ut) {
        assert unit2id.containsKey(ut);
        return String.format("unit%d", unit2id.get(ut));
    }

    /**
     * Get string for graphviz edge between fragments
     *
     * @param frag2id Map from fragment to its id
     * @param unit2id map units to its id
     * @param frag1 the first fragment
     * @param frag2 the second fragment
     * @param condense_fragments are we condensing fragments?
     * @return Graphviz code for an edge between fragments
     *   (with no semicolon/newline) at the end
     */
    static private String frag2fragString(Map<Fragment, Integer> frag2id,
                                   Map<Unit, Integer> unit2id,
                                   Fragment frag1,
                                   Fragment frag2,
                                   String color,
                                   boolean condense_fragments) {
        assert frag2id.containsKey(frag1);
        assert frag2id.containsKey(frag2);
        String frag1node = fragNodeString(frag2id, frag1, condense_fragments),
                frag2node = fragNodeString(frag2id, frag2, condense_fragments);
        // If we are condensing fragments, then frags are nodes!
        if(condense_fragments) {
            String formatString = "%s -> %s [dir=none,color="+ color + "]";
            return String.format(formatString, frag1node, frag2node);
        }
        // Otherwise, fragments are clusters!
        // We have to put edges between units and tell the graph to point
        // them at the fragment
        Unit ut1 = frag1.getEntryUnit(), ut2 = frag2.getEntryUnit();
        String ut1node = unitNodeString(unit2id, ut1),
                ut2node = unitNodeString(unit2id, ut2);
        // Be careful with self-edges: make a dummy node
        String formatString;
        if(Objects.equals(frag1, frag2)) {
            String dummyNode = String.format("dummy%s%s", frag1node, frag2node);
            formatString = dummyNode +  " [style=invis,shape=point,width=0.0,height=0.0]\n" +
                        "%s -> " + dummyNode + " [dir=none,color=" + color + ",ltail=%s]\n" +
                        dummyNode + " -> %s [dir=none,color=" + color + ",lhead=%s]";
            return String.format(formatString, ut1node, frag1node, ut2node, frag2node);
        }
        // Otherwise, do obvious thing
        formatString = "%s -> %s [dir=none,color=" + color + ",ltail=%s,lhead=%s]";
        return String.format(formatString, ut1node, ut2node, frag1node, frag2node);
    }

    /**
     * Same as the public method, but allows us to reuse fragment
     * labels for edges between method bodies
     *
     * @param frag2id the map from fragment to ids
     * @param unit2id the map from units to ids
     * @param as_cluster If true, draw as a cluster. Otherwise, draw as a digraph
     */
    @SuppressWarnings({"ConstantConditions", "UnstableApiUsage"})
    static private String drawPartitionedCCR(CCR ccr,
                                             Map<Fragment, Integer> frag2id,
                                             Map<Unit, Integer> unit2id,
                                             boolean condense_fragments,
                                             boolean as_cluster)
    {
        Body b = ccr.getAtomicSection().getActiveBody();
        // Now get CFG
        final ImmutableGraph<Unit> cfg = GuavaExceptionalUnitGraphCache.getInstance().getOrCompute(b.getMethod());
        StronglyConnectedComponents<Unit> unitSCCs = new StronglyConnectedComponents<>(cfg);

        Map<Unit, List<Unit>> unitToSCCMap = new HashMap<>();
        for (List<Unit> scc : unitSCCs.getComponents())
        {
            scc.forEach(u -> unitToSCCMap.put(u, scc));
        }

        // we will need to recover maps unit -> fragment, and to give
        // each unit and fragment a unique id
        Map<Unit, Fragment> unit2frag = new HashMap<>();
        // start building our graphviz string
        String methodName = b.getMethod().getName(),
                className = b.getMethod().getDeclaringClass().getName();
        StringBuilder dot_text = new StringBuilder();
        if(as_cluster) {
            dot_text.append(String.format("subgraph cluster_%s {\n", methodName));
        }
        else {
            dot_text.append("Digraph D {\ncompound=true;\n");
        }
        dot_text.append(String.format("label=\"%s.%s\"\n", className, methodName))
                .append("labelloc=t // put title at top\n")
                .append("fontsize=20  // title font-size\n");
        if (condense_fragments)
        {
            dot_text.append("// FRAG NODES\n");
        }
        for (Fragment frag : ccr.getFragments())
        {
            frag2id.put(frag, frag2id.size());
            if (condense_fragments)
            {
                dot_text.append(String.format(
                        "%s [shape=box, color=blue, label=\"%s\\n%s\"]\n",
                        fragNodeString(frag2id, frag, condense_fragments),
                        frag.getEntryUnit().toString().replace("\"", "\\\""),
                        frag.getExitUnits().toString().replace("\"", "\\\"")
                ));
            }
        }
        // Figure out which fragments each node is in
        for (Fragment frag : ccr.getFragments())
        {
            if (!condense_fragments)
            {
                dot_text.append(String.format(
                        "// NEXT FRAG\nsubgraph %s {\ncolor=blue\nlabel=\"Fragment\"\n",
                        fragNodeString(frag2id, frag, condense_fragments)));
                if (!unit2id.containsKey(frag.getEntryUnit()))
                {
                    unit2id.put(frag.getEntryUnit(), unit2id.size());
                    dot_text.append(String.format("unit%d [shape=box, label=\"%s\"]\n",
                            unit2id.get(frag.getEntryUnit()),
                            frag.getEntryUnit().toString().replace("\"", "\\\"")));
                }
            }
            // Find all reachable units so that we know
            // explicitly who is in each fragment
            Queue<Unit> toVisit = new LinkedList<>();
            toVisit.add(frag.getEntryUnit());
            unit2frag.put(frag.getEntryUnit(), frag);
            while (!toVisit.isEmpty())
            {
                Unit current = toVisit.poll();
                // make sure current gets a unit id (if we're not condensing fragments)
                if (!condense_fragments && !unit2id.containsKey(current))
                {
                    unit2id.put(current, unit2id.size());
                    dot_text.append(String.format("%s [shape=box, label=\"%s\"]\n",
                            unitNodeString(unit2id, current),
                            current.toString().replace("\"", "\\\"")));
                }

                Set<Unit> succsToConsider = cfg.successors(current);
                // In case of a loop visit successors that the last unit dominates
                if (frag.getExitUnits().contains(current))
                {
                    if (succsToConsider.size() > 1)
                    {
                        succsToConsider = succsToConsider.stream()
                                                         .filter(succ -> unitToSCCMap.get(current) == unitToSCCMap.get(succ))
                                                         .collect(Collectors.toSet());
                    }
                    else
                    {
                        continue;
                    }
                }

                for (Unit nbr : succsToConsider)
                {
                    if (!unit2frag.containsKey(nbr))
                    {
                        unit2frag.put(nbr, frag);
                        toVisit.add(nbr);
                    }
                    // if not condensing fragments, put edges in graph
                    if (!condense_fragments)
                    {
                        // make sure nbr gets a unit id (if we're not condensing fragments)
                        if (!unit2id.containsKey(nbr))
                        {
                            unit2id.put(nbr, unit2id.size());
                            dot_text.append(String.format("%s [shape=box, label=\"%s\"]\n",
                                    unitNodeString(unit2id, nbr),
                                    nbr.toString().replace("\"", "\\\"")));
                        }
                        dot_text.append(String.format("%s -> %s\n",
                                unitNodeString(unit2id, current),
                                unitNodeString(unit2id, nbr)));
                    }
                }
            }
            // if not condensing, end the fragment cluster
            if (!condense_fragments)
            {
                dot_text.append("}\n\n");
            }
        }

        // Now figure out edges between fragments
        dot_text.append("// BETWEEN FRAG EDGES\n");
        for (Fragment frag1 : ccr.getFragments())
        {
            Set<Fragment> knownEdges = new HashSet<>();
            // connect frag1 to any fragment reachable from its last unit
            for (Unit exit : frag1.getExitUnits())
            {
                for (Unit nbr : cfg.successors(exit))
                {
                    Fragment frag2 = unit2frag.get(nbr);
                    if (!knownEdges.contains(frag2) && condense_fragments)
                    {
                        if (frag2 == null) continue;
                        knownEdges.add(frag2);
                        String frag1node = fragNodeString(frag2id, frag1, condense_fragments),
                                frag2node = fragNodeString(frag2id, frag2, condense_fragments);
                        dot_text.append(String.format("%s -> %s\n", frag1node, frag2node));
                    }
                    else if (!condense_fragments)
                    {
                        if (!unit2id.containsKey(nbr))
                        {
                            unit2id.put(nbr, unit2id.size());
                            dot_text.append(String.format("%s [shape=box, label=\"%s\"]\n",
                                                          unitNodeString(unit2id, nbr),
                                                          nbr.toString().replace("\"", "\\\"")));
                        }

                        dot_text.append(String.format("%s -> %s\n",
                                                      unitNodeString(unit2id, exit),
                                                      unitNodeString(unit2id, nbr)));
                    }
                }
            }
        }
        // Close graph
        dot_text.append("}\n");
        // Return the dot code
        return dot_text.toString();
    }

    /**
     * Draw the fragmented monitor
     *
     * @param fragmentedMonitor the monitor to draw, with edges between
     *                          fragments which cannot run in parallel
     * @param raceConditionAnalysis the race-condition analysis
     * @param condense_fragments As used by {@link #drawPartitionedCCR(CCR, boolean)}
     * @return dot text drawing the entire monitor
     */
    static public String drawFragmentedMonitor(FragmentedMonitor fragmentedMonitor,
                                               RaceConditionAnalysis raceConditionAnalysis,
                                               CommutativityAnalysis commutativityAnalysis,
                                               boolean condense_fragments) {
        // build prologue
        StringBuffer dot_text = new StringBuffer();
        String className = fragmentedMonitor.getMonitor().getName();
        dot_text.append("Digraph D {\ncompound=true;\n")
                .append(String.format("label=\"%s, Red edges represent", className))
                .append(" race conditions/predicate invalidations. Blue edges connect fragments that do not commute. Self-edges are not drawn\"\n")
                .append("labelloc=t // put title at top\n")
                .append("fontsize=20  // title font-size\n\n");
        // build each method
        Map<Fragment, Integer> frag2id = new HashMap<>();
        Map<Unit, Integer> unit2id = new HashMap<>();
        for(CCR ccr : fragmentedMonitor.getCCRs()) {
            String b_dot = drawPartitionedCCR(
                    ccr,
                    frag2id,
                    unit2id,
                    condense_fragments,
                    true);
            dot_text.append(b_dot).append("\n");
        }

        List<Fragment> fragments = fragmentedMonitor.getFragments();
        int nFrags = fragments.size();
        for(int i = 0; i < nFrags; ++i) {
            Fragment frag1 = fragments.get(i);
            fragments.stream()
                     .skip(i + 1)
                     .parallel()
                     .forEach(frag2 -> {
                         if(!raceConditionAnalysis.getRaces(frag1, frag2).isEmpty()) {
                             dot_text.append(String.format("%s\n", frag2fragString(frag2id, unit2id, frag1, frag2,
                                                                                   "red", condense_fragments)));
                         }

                         if (!frag1.getEnclosingCCR().equals(frag2.getEnclosingCCR()) &&
                             !commutativityAnalysis.commutes(frag1, frag2))
                         {
                             dot_text.append(String.format("%s\n", frag2fragString(frag2id, unit2id, frag1, frag2,
                                                                                   "blue", condense_fragments)));
                         }
                     });
        }
        // close graph
        dot_text.append("}\n");
        return dot_text.toString();
    }
}
