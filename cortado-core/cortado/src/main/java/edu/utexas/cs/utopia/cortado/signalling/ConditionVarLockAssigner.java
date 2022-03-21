package edu.utexas.cs.utopia.cortado.signalling;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.lockPlacement.LockAssignment;
import soot.SootMethod;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Figure out which lock to associate to each condition variable
 */
public class ConditionVarLockAssigner
{
    private final ImmutableMap<SootMethod, Integer> guardToLock;

    public ConditionVarLockAssigner(@Nonnull List<CCR> ccrs, @Nonnull LockAssignment lockAssignment)
    {
        final Map<SootMethod, Set<ImmutableSortedSet<Integer>>> guardToLockSetsHeldDuringYield = ccrs.stream()
                .filter(CCR::hasGuard)
                .collect(Collectors.groupingBy(CCR::getGuard,
                        Collectors.mapping(CCR::getWaituntilFragment,
                                Collectors.mapping(lockAssignment::getLocksInOrder, Collectors.toSet())
                        ))
                );
        assert guardToLockSetsHeldDuringYield.values().stream().noneMatch(Collection::isEmpty);
        final boolean waituntilFragsHaveSameGuardButDiffLocks = guardToLockSetsHeldDuringYield.values()
                .stream()
                .anyMatch(sets -> sets.size() != 1);
        if(waituntilFragsHaveSameGuardButDiffLocks)
        {
            throw new IllegalArgumentException("Some waituntil fragments share a lock, but do not have identical lock-sets");
        }
        final Map<SootMethod, ImmutableSortedSet<Integer>> predToLockSet = guardToLockSetsHeldDuringYield.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iterator().next()));
        if(predToLockSet.values().stream().anyMatch(Collection::isEmpty))
        {
            throw new IllegalArgumentException("Some predicate yielded on while no lock is held");
        }
        guardToLock = ImmutableMap.copyOf(predToLockSet
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue()
                                .stream()
                                .min(lockAssignment.getLockOrder())
                                .orElseThrow(() -> new IllegalArgumentException("Missing optional"))
                        )
                )
        );
    }

    /**
     * @return the map from guards to locks
     */
    @Nonnull
    public ImmutableMap<SootMethod, Integer> getGuardToLock()
    {
        return guardToLock;
    }
}
