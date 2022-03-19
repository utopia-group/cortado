package edu.utexas.cs.utopia.cortado.lockPlacement;

import edu.utexas.cs.utopia.cortado.ccrs.Fragment;

import javax.annotation.Nonnull;

public class CCRFragmentSet extends MutuallyExclusiveFragmentSet
{
    public CCRFragmentSet(@Nonnull Fragment... fragments)
    {
        super(fragments);
    }
}
