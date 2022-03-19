package edu.utexas.cs.utopia.cortado.lockPlacement;

import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * two {@link edu.utexas.cs.utopia.cortado.ccrs.Fragment}s
 * which may have a race condition or predicate violation
 */
class PossibleRace extends MutuallyExclusiveFragmentSet
{
    final Set<MemoryLocations> memLocs;

    /**
     * @param frag1 the first fragment in the race condition
     * @param frag2 the second fragment in the race condition
     */
    PossibleRace(@Nonnull Fragment frag1, @Nonnull Fragment frag2, Set<MemoryLocations> memLocs)
    {
        super(frag1, frag2);
        this.memLocs = memLocs;
    }
}
