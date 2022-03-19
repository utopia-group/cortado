package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterators;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An assignment from fragments to locks.
 */
public abstract class LockAssignment
{
    final Map<Fragment, SortedSet<Integer>> fragmentToLockSet;
    private final Comparator<Integer> lockOrder;

    final Set<PotentialAtomicField> fieldsToConvertToAtomics;

    /**
     * Create the lock-assignment
     *
     * @param fragmentToLockSet the map from fragments to sorted sets of locks.
     *                          Each set of locks must be non-null, and
     *                          use lockOrder as its order
     * @param lockOrder the lock-order.
     */
    LockAssignment(@Nonnull Map<Fragment, SortedSet<Integer>> fragmentToLockSet,
                   @Nonnull Comparator<Integer> lockOrder,
                   Set<PotentialAtomicField> fieldsToConvertToAtomics)
    {
        if(fragmentToLockSet.isEmpty())
        {
            throw new IllegalArgumentException("fragmentToLockSet is empty.");
        }
        if(fragmentToLockSet.values().stream().anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException("Fragment mapped to null lock-set");
        }
        boolean someFragUsesDifferentLockOrder = fragmentToLockSet.values()
                .stream()
                .map(SortedSet::comparator)
                .anyMatch(comp -> !lockOrder.equals(comp));
        if(someFragUsesDifferentLockOrder)
        {
            throw new IllegalArgumentException("All lock-sets must be sorted using lockOrder.");
        }
        this.fragmentToLockSet = fragmentToLockSet;
        this.lockOrder = lockOrder;
        this.fieldsToConvertToAtomics = fieldsToConvertToAtomics;
    }

    /**
     * Get the locks this assignment maps frag to, sorted
     * according to the global lock order
     *
     * @param frag the fragment. Must be mapped to some set of locks
     * @return the locks, sorted in order
     */
    @Nonnull
    public ImmutableSortedSet<Integer> getLocksInOrder(@Nonnull Fragment frag)
    {
        final SortedSet<Integer> locks = fragmentToLockSet.get(frag);
        if(locks == null)
        {
            throw new IllegalArgumentException("fragment " + frag + " is not mapped to any locks.");
        }
        return ImmutableSortedSet.copyOf(locks);
    }

    /**
     * @param frag the fragment. Must be mapped to some set of locks
     * @param lock the lock
     * @return true iff frag holds lock
     */
    public boolean holdsLock(@Nonnull Fragment frag, int lock)
    {
        final SortedSet<Integer> locks = fragmentToLockSet.get(frag);
        if(locks == null)
        {
            throw new IllegalArgumentException("fragment " + frag + " is not mapped to any locks.");
        }
        return locks.contains(lock);
    }

    /**
     * @return the lock order
     */
    @Nonnull
    public Comparator<Integer> getLockOrder()
    {
        return lockOrder;
    }

    public Set<PotentialAtomicField> getFieldsToConvertToAtomics()
    {
        return fieldsToConvertToAtomics;
    }

    /**
     * @return the number of locks used by this assignment
     */
    public int numLocks()
    {
        return (int) fragmentToLockSet.values()
                .stream()
                .flatMap(Collection::stream)
                .distinct()
                .count();
    }

    /**
     * @return the number of
     */
    public int numAtomics()
    {
        return fieldsToConvertToAtomics.size();
    }

    /**
     * @return the fragments mapped to lock-sets
     */
    @Nonnull
    public ImmutableSet<Fragment> getFragments()
    {
        return ImmutableSet.copyOf(fragmentToLockSet.keySet());
    }

    @Override
    public int hashCode()
    {
        return fragmentToLockSet.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == this) return true;
        if(!(other instanceof LockAssignment)) return false;
        LockAssignment that = (LockAssignment) other;
        return this.fragmentToLockSet.keySet().equals(that.fragmentToLockSet.keySet())
                && fragmentToLockSet.keySet()
                    .stream()
                    .allMatch(frag -> Iterators.elementsEqual(
                            this.getLocksInOrder(frag).iterator(),
                            that.getLocksInOrder(frag).iterator()
                    ));
    }

    @Override
    public String toString()
    {
        return fragmentToLockSet.toString();
    }

    /**
     * @return all locks used by this assignment, in order
     */
    @Nonnull
    ImmutableSortedSet<Integer> getLocks()
    {
        return fragmentToLockSet.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.collectingAndThen(
                        Collectors.toSet(),
                        s -> ImmutableSortedSet.copyOf(getLockOrder(), s)
                ));
    }
}
