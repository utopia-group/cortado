package edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import soot.SootField;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Representation of an abstract memory location from some analysis
 */
public interface MemoryLocations
{
    /**
     * @param m another abstract memory location
     * @return true iff the memory locations intersect
     */
    boolean intersect(@Nonnull MemoryLocations m);

    @Nonnull
    Collection<Expr> getExprsIn(@Nonnull Set<Expr> freeVars);

    /**
     * Build an abstract memory location from all the
     * concrete memory locations in this object except for
     * sootField.
     *
     * This must be a sound construction, but may be imprecise.
     * In particular, it is okay to return any
     * abstract memory location which contains all the concrete
     * memory locations of this object, except possibly sootField.
     *
     * @param sootField the field
     * @return the new memory locations object
     */
    @Nonnull
    MemoryLocations overApproximateWithoutField(@Nonnull SootField sootField);

    /**
     * @param locs1 a collection of memory locations
     * @param locs2 a collection of memory locations
     * @return true iff some memory locations in loc1 have a non-empty
     *          intersection with some memory locations in loc2
     */
    static boolean mayIntersectAny(@Nonnull Collection<MemoryLocations> locs1, @Nonnull Collection<MemoryLocations> locs2)
    {
        return locs1.stream()
                .anyMatch(m1 -> locs2.stream().anyMatch(m1::intersect));
    }

    static Set<MemoryLocations> intersectingLocs(@Nonnull Collection<MemoryLocations> locs1, @Nonnull Collection<MemoryLocations> locs2)
    {
        Set<MemoryLocations> intersectingLocs = new HashSet<>();
        for (MemoryLocations m1 : locs1)
        {
            for (MemoryLocations m2 : locs2)
            {
                if (m1.intersect(m2))
                {
                    intersectingLocs.add(m1);
                    intersectingLocs.add(m2);
                }
            }
        }

        return intersectingLocs;
    }

    /**
     * @param locs the memory locations
     * @param ignoredFields the fields to ignore
     * @return the memory locations in locs, each over-approximated after
     *          ignoring the fields in ignoredFields
     */
    @Nonnull
    static Collection<MemoryLocations> overApproximateWithoutFields(@Nonnull Collection<MemoryLocations> locs,
                                                                    @Nonnull Collection<SootField> ignoredFields)
    {
        if(ignoredFields.isEmpty()) return locs;
        return locs.stream().map(memLocs -> {
            for(SootField ignoredField : ignoredFields)
            {
                memLocs = memLocs.overApproximateWithoutField(ignoredField);
            }
            return memLocs;
        }).collect(Collectors.toList());
    }
}
