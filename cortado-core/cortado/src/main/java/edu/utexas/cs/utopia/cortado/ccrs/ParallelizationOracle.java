package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.base.Objects;

import java.util.*;

/**
 * A collection of pairs of fragments which should be
 * parallelized
 *
 * @author Ben_Sepanski
 */
public class ParallelizationOracle
{
    private final List<Fragment> fragments = new ArrayList<>();
    private final Map<CCR, List<Fragment>> ccr2fragments = new HashMap<>();
    private final Map<Fragment, Set<Fragment>> parallelFragments = new HashMap<>();

    /**
     * Add a fragment to this parallelization oracle
     *
     * @param frag the fragment
     * @param ccr  the CCR containing this fragment
     */
    public void addFragment(Fragment frag, CCR ccr)
    {
        if (!Objects.equal(frag.getEnclosingBody(), ccr.getAtomicSection().getActiveBody()))
        {
            throw new IllegalArgumentException(
                    "frag and ccr must reside in the same method body");
        }

        this.fragments.add(frag);
        if (!this.ccr2fragments.containsKey(ccr))
        {
            this.ccr2fragments.put(ccr, new ArrayList<>());
        }
        this.ccr2fragments.get(ccr).add(frag);
        this.parallelFragments.put(frag, new HashSet<>());
    }

    /**
     * Add (frag1, frag2) as a parallel pair
     * <p>
     * frag1 and frag2 must have been added to this oracle already
     * (via the addFragment method)
     *
     * @param frag1 a fragment that can run in parallel with frag2
     * @param frag2 a fragment that can run in parallel with frag1
     */
    public void addParallelPair(Fragment frag1, Fragment frag2)
    {
        if (!this.fragments.contains(frag1) || !this.fragments.contains(frag2))
        {
            throw new IllegalArgumentException(
                    "frag1 and frag2 must be added to this oracle before"
                            + " being made a parallel pair (see addFragment method)");
        }
        this.parallelFragments.get(frag1).add(frag2);
        this.parallelFragments.get(frag2).add(frag1);
    }

    /**
     * @return the set of fragments in this oracle
     */
    public List<Fragment> getFragments()
    {
        return fragments;
    }

    /**
     * @return the mapping from CCRs to the set of fragments they enclose
     */
    public Map<CCR, List<Fragment>> getCCR2fragments()
    {
        return ccr2fragments;
    }

    /**
     * @return A map from f1->{f2 : (f1,f2) is a parallel pair}
     */
    public Map<Fragment, Set<Fragment>> getParallelFragments()
    {
        return parallelFragments;
    }

}
