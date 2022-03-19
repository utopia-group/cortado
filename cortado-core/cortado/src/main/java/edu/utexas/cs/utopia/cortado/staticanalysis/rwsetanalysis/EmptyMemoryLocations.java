package edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import soot.SootField;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * An abstract memory location containing no concrete memory locations
 */
class EmptyMemoryLocations implements MemoryLocations
{
    @Override
    public boolean intersect(@Nonnull MemoryLocations m)
    {
        return false;
    }

    @Nonnull
    @Override
    public Collection<Expr> getExprsIn(@Nonnull Set<Expr> freeVars)
    {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public MemoryLocations overApproximateWithoutField(@Nonnull SootField sootField)
    {
        return new EmptyMemoryLocations();
    }
}
