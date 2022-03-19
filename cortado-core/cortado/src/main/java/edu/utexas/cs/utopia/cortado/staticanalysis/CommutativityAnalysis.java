package edu.utexas.cs.utopia.cortado.staticanalysis;

import com.google.common.graph.EndpointPair;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;

import javax.annotation.Nonnull;

/**
 * Interface for testing commutativity.
 * This is an under-approximation of the commutes relation,
 * i.e. it only returns true if it can prove commutativity,
 * and just returns false otherwise.
 */
public interface CommutativityAnalysis {
    /**
     * @param frag1 first fragment
     * @param frag2 second fragment
     * @return true iff we can prove frag1 and frag2 commute
     */
    boolean commutes(@Nonnull Fragment frag1, @Nonnull Fragment frag2);

    /**
     * @param ccr1 first CCR
     * @param ccr2 second CCR
     * @return true iff we can prove ccr1 and ccr2 commute
     */
    boolean commutes(@Nonnull CCR ccr1, @Nonnull CCR ccr2);

    @SuppressWarnings("UnstableApiUsage")
    boolean preservesEdgeCondition(Fragment frag, EndpointPair<Fragment> edge);
}
