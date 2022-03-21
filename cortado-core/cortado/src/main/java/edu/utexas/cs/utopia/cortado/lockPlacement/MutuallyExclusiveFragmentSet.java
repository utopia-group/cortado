package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * A set of {@link edu.utexas.cs.utopia.cortado.ccrs.Fragment}s
 * which must mutually exclude each other
 */
abstract public class MutuallyExclusiveFragmentSet
{
    private final ImmutableSet<Fragment> fragments;

    MutuallyExclusiveFragmentSet(@Nonnull Fragment... fragments)
    {
        this.fragments = ImmutableSet.<Fragment>builder().add(fragments).build();
    }

    /**
     * @return the fragments which mutually exclude each other
     */
    ImmutableSet<Fragment> getFragments()
    {
        return fragments;
    }

    /**
     * @param lockAssignment the lock assignment
     * @return true iff there exist some lock held by all
     *          the fragments under lockAssignment
     */
    boolean mutexedUnderAssignment(@Nonnull LockAssignment lockAssignment)
    {
        return !fragments.stream()
                         .map(lockAssignment::getLocksInOrder)
                         .map(s -> (Set<Integer>) s)  // cast so that reduction type checks
                         .reduce(Sets::intersection)
                         .orElse(Collections.emptySet())
                         .isEmpty();
    }

    @Override
    public int hashCode()
    {
        return fragments.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof MutuallyExclusiveFragmentSet)) return false;
        return Objects.equals(fragments, ((MutuallyExclusiveFragmentSet) other).fragments);
    }
}
