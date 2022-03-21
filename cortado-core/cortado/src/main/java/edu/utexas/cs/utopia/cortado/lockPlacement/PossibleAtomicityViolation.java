package edu.utexas.cs.utopia.cortado.lockPlacement;

import com.google.common.graph.EndpointPair;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * A possible atomicity violation occurs when a {@link edu.utexas.cs.utopia.cortado.ccrs.Fragment}
 * interleaves between two other fragments in a non-sequentially-consistent
 * way
 */
@SuppressWarnings("UnstableApiUsage")
class PossibleAtomicityViolation extends MutuallyExclusiveFragmentSet
{
    private final Fragment interleavingFragment;
    private final EndpointPair<Fragment> controlFlowEdge;

    /**
     * Build an object representing a possible atomicity violation
     * which would occur if interleavingFragment appeared in
     * an interleaving between the source and destination
     * of controlFlowEdge
     *
     * @param interleavingFragment the fragment
     * @param controlFlowEdge the control-flow edge
     */
    PossibleAtomicityViolation(@Nonnull Fragment interleavingFragment, @Nonnull EndpointPair<Fragment> controlFlowEdge)
    {
        super(interleavingFragment, controlFlowEdge.nodeU(), controlFlowEdge.nodeV());
        this.interleavingFragment = interleavingFragment;
        this.controlFlowEdge = controlFlowEdge;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(interleavingFragment, controlFlowEdge);
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other) return true;
        if(!(other instanceof PossibleAtomicityViolation)) return false;
        PossibleAtomicityViolation that = (PossibleAtomicityViolation) other;
        return Objects.equals(this.interleavingFragment, that.interleavingFragment)
                && Objects.equals(this.controlFlowEdge, that.controlFlowEdge);
    }
}
