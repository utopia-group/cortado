package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * An immutable assignment of fragments to locks, with an
 * associated lock order.
 */
public class ImmutableLockAssignment extends LockAssignment
{
    /**
     * Create the lock-assignment
     *
     * @param fragmentToLockSet the map from fragments to sets of locks.
     *                          Each set of locks must be non-null.
     * @param lockOrder the order on locks
     */
    public ImmutableLockAssignment(@Nonnull Map<Fragment, ? extends Set<Integer>> fragmentToLockSet,
                                   @Nonnull Comparator<Integer> lockOrder,
                                   Set<PotentialAtomicField> fieldsToMakeAtomics)
    {
        super(buildImmutableFragToLockSet(fragmentToLockSet, lockOrder), lockOrder, fieldsToMakeAtomics);
    }

    /**
     * @param fragmentToLockSet the map from fragments to lock sets
     * @param lockOrder the lock order
     *
     * @return an immutable map to the sorted sets of locks
     */
    @Nonnull
    private static ImmutableMap<Fragment, SortedSet<Integer>> buildImmutableFragToLockSet(
            @Nonnull Map<Fragment, ? extends Set<Integer>> fragmentToLockSet,
            @Nonnull Comparator<Integer> lockOrder)
    {
        return fragmentToLockSet.entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(
                        Map.Entry::getKey,
                        entry -> ImmutableSortedSet.copyOf(lockOrder, entry.getValue())
                ));
    }

    @Nonnull
    public static ImmutableLockAssignment copyOf(@Nonnull LockAssignment lockAssignment)
    {
        return new ImmutableLockAssignment(ImmutableMap.copyOf(lockAssignment.fragmentToLockSet),
                                           lockAssignment.getLockOrder(),
                                           ImmutableSet.copyOf(lockAssignment.getFieldsToConvertToAtomics()));
    }
}
