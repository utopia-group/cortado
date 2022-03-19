package edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis;

import soot.SootMethod;
import soot.Unit;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Interface for read-write set analyses.
 */
public interface RWSetAnalysis
{
    /**
     * @throws IllegalArgumentException if analysis has not been performed on the given unit
     */
    Collection<MemoryLocations> readSet(SootMethod sm, Unit ut);

    /**
     * @throws IllegalArgumentException if analysis has not been performed on the given unit
     */
    Collection<MemoryLocations> writeSet(SootMethod sm, Unit ut);

    default Collection<MemoryLocations> readSet(SootMethod m)
    {
        if (!m.hasActiveBody())
            throw new IllegalArgumentException("method has no active body");

        Set<MemoryLocations> rSet = new HashSet<>();
        m.getActiveBody()
         .getUnits()
         .forEach(u -> rSet.addAll(readSet(m, u)));

        return rSet;
    }

    default Collection<MemoryLocations> writeSet(SootMethod m)
    {
        if (!m.hasActiveBody())
            throw new IllegalArgumentException("method has no active body");

        Set<MemoryLocations> wSet = new HashSet<>();
        m.getActiveBody()
         .getUnits()
         .forEach(u -> wSet.addAll(writeSet(m, u)));

        return wSet;
    }
}
