package edu.utexas.cs.utopia.cortado.staticanalysis;

import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MemoryLocations;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Holds the results of an analysis which
 * checks whether two fragments must not have a race condition
 *
 */
public interface RaceConditionAnalysis {
    /**
     * Check whether two fragments must not have a race condition.
     *
     * @param frag1 The first fragment
     * @param frag2 The second fragment
     * @throws IllegalArgumentException if frag1 or frag2 is not present in underlying
     *      analysis.
     * @return True iff frag1 and frag2 must not have a race condition (i.e.
     *       the writeSet of frag1 never intersects the readWriteSet of frag2,
     *       and vice versa)
     */
    Set<MemoryLocations> getRaces(@Nonnull Fragment frag1, @Nonnull Fragment frag2);

    /**
     * Check whether two units must not have a race condition.
     *
     * @param unit1 The first unit
     * @param method1 the method whose active body contains the first unit
     * @param unit2 The second unit
     * @param method2 the method whose active body contains the second unit
     * @throws IllegalArgumentException if unit1 or unit2 is not present in underlying
     *      analysis.
     * @return True iff unit1 and unit2 must not have a race condition (i.e.
     *       the writeSet of unit1 never intersects the readWriteSet of unit2,
     *       and vice versa)
     */
    Set<MemoryLocations> getRaces(@Nonnull Unit unit1, @Nonnull SootMethod method1,
                                  @Nonnull Unit unit2, @Nonnull SootMethod method2);
}
