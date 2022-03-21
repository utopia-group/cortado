package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.Maps;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * A lock assignment which can be changed
 */
public class MutableLockAssignment extends LockAssignment
{
    /**
     * Makes a mutable copy of lockAssignment
     *
     * @param lockAssignment the lock assignment to copy
     */
    public MutableLockAssignment(@Nonnull LockAssignment lockAssignment)
    {
        super(getFragToSortedLocks(lockAssignment.fragmentToLockSet, lockAssignment.getLockOrder()),
                lockAssignment.getLockOrder(), lockAssignment.getFieldsToConvertToAtomics());
    }

    /**
     * @param fragToLockSet the map from fragments to locks
     * @param lockOrder the lock order
     * @return a map from fragments to sorted sets of locks (made from copies of the original sets)
     */
    @Nonnull
    private static Map<Fragment, SortedSet<Integer>> getFragToSortedLocks(@Nonnull Map<Fragment, ? extends Set<Integer>> fragToLockSet,
                                                                          @Nonnull Comparator<Integer> lockOrder)
    {
        return Maps.toMap(fragToLockSet.keySet(),
                k -> new TreeSet<Integer>(lockOrder){{addAll(fragToLockSet.get(k));}}
        );
    }

    /**
     * Add lock to frag's set of locks
     *
     * @param frag the fragment
     * @param lock the lock to add
     */
    public void addLock(@Nonnull Fragment frag, @Nonnull Integer lock)
    {
        final SortedSet<Integer> fragLocks = fragmentToLockSet.get(frag);
        if(fragLocks == null)
        {
            throw new IllegalArgumentException("Unrecognized fragment " + frag);
        }
        fragLocks.add(lock);
    }

    /**
     * Remove lock from frag's set of locks.
     *
     * @param frag the fragment
     * @param lock the lock to remove.
     * @throws IllegalArgumentException if frag does not hold lock
     */
    void removeLock(@Nonnull Fragment frag, @Nonnull Integer lock)
    {
        final SortedSet<Integer> fragLocks = fragmentToLockSet.get(frag);
        if(fragLocks == null)
        {
            throw new IllegalArgumentException("Unrecognized fragment " + frag);
        }
        if(!fragLocks.contains(lock))
        {
            throw new IllegalArgumentException("Fragment does not hold lock.");
        }
        fragLocks.remove(lock);
    }
}
