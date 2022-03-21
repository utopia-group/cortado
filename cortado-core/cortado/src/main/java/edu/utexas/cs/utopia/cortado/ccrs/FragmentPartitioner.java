package edu.utexas.cs.utopia.cortado.ccrs;

import soot.Body;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Extend the fragments of a ParallelizationOracle
 * into a fragment partition of CCRs.
 * <p>
 * Note that the fragments in parOracle, the CCRs,
 * the post-domination trees, and the control-flow graphs
 * the post-domination trees were built from all must be
 * consistent with one another.
 *
 * @author Ben_Sepanski
 */
public abstract class FragmentPartitioner
{
    // provided by constructor
    protected final ParallelizationOracle parOracle;
    // ccrs -> partition
    private final Map<CCR, List<Fragment>> ccr2fragPartition = new HashMap<>();
    // units -> frag
    private final Map<Unit, Fragment> unit2frags = new HashMap<>();

    /**
     * Create a fragment partition with no parOracle
     */
    public FragmentPartitioner()
    {
        this(new ParallelizationOracle());
    }

    /**
     * Create a fragment partition respecting parOracle
     *
     * @param parOracle     the parallelization oracle which the fragment
     *                      partition must respect. Note we assume
     *                      all fragments in parOracle are disjoint
     */
    public FragmentPartitioner(@Nonnull ParallelizationOracle parOracle)
    {
        this.parOracle = parOracle;
        // Record the units of any fragments for the parOracle
        parOracle.getFragments()
                .forEach(frag -> frag.getAllUnits()
                        .forEach(ut -> this.unit2frags.put(ut, frag))
                );
    }

    /**
     * Partition ccr into fragments (and sets the fragments for ccr)
     *
     * @param ccr the ccr
     */
    public void partitionIntoFragments(CCR ccr)
    {
        // if already partitioned, nothing to do!
        if (this.ccr2fragPartition.containsKey(ccr))
        {
            return;
        }
        List<Fragment> part = this.partition(ccr);
        ccr.setFragments(part);
        // Sort fragments by their first unit (this is used by
        // the to-dot files function, just a prettifier really)
        final Body b = ccr.getAtomicSection().getActiveBody();
        part.sort((frag1, frag2) -> {
            if (Objects.equals(frag1, frag2))
            {
                return 0;
            }
            boolean one_follows_two = frag2.getExitUnits().stream().allMatch(exit -> b.getUnits().follows(frag1.getEntryUnit(),exit));
            return one_follows_two ? 1 : -1;
        });
        // store the partition
        this.ccr2fragPartition.put(ccr, part);
        // get the cfg of the body of the fragment
        // record the map units -> frags for the fragCFG method
        part.forEach(frag -> frag.getAllUnits().forEach(ut -> this.unit2frags.put(ut, frag)));
    }

    /**
     * Return a fragment partition of ccr respecting parOracle
     * <p>
     * The list is a fragment partition of ccr if each unit
     * of the ccr appears in exactly one fragment in the list.
     * <p>
     * The list respects parOracle if, whenever a fragment
     * inside ccr appears in parOracle, it also appears in the list.
     * <p>
     * Note: It is guaranteed that the ccr body appears in body2pdomTree.
     *
     * @param ccr the CCR to partition into fragments
     * @return a list of fragments which is a fragment partition respecting parOracle
     */
    abstract List<Fragment> partition(CCR ccr);

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder("fragments = [\n");
        for (Entry<CCR, List<Fragment>> entry : this.ccr2fragPartition.entrySet())
        {
            str.append("CCR:\n").append(entry.getKey().toString());
            str.append("FRAGS:\n[");
            for (Fragment frag : entry.getValue())
            {
                str.append(frag.toString()).append(";").append("\n");
            }
            str.append("]\n");
        }
        return str.toString();
    }

    /**
     * map units to the oracle fragment they are contained in (if any)
     *
     * @return A map from units to the fragment containing the unit
     */
    protected Map<Unit, Fragment> getUnit2Frags() { return this.unit2frags; }

    /**
     * @return the map from ccrs to fragment partitions
     */
    public Map<CCR, List<Fragment>> getCCR2fragPartition()
    {
        return ccr2fragPartition;
    }
}
