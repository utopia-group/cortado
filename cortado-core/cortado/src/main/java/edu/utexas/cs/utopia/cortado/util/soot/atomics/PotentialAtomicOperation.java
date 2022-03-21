package edu.utexas.cs.utopia.cortado.util.soot.atomics;

import com.google.common.collect.ImmutableList;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootFieldUtils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * A sequence of units which could potentially be
 * implemented with a single call to an atomic function
 * from {@link java.util.concurrent.atomic.AtomicInteger},
 * {@link java.util.concurrent.atomic.AtomicLong}, or {@link java.util.concurrent.atomic.AtomicBoolean}
 */
public abstract class PotentialAtomicOperation
{
    private final ImmutableList<Unit> nonAtomicImplementation;
    private final SootMethod inMethod;
    private final PotentialAtomicField potentialAtomicField;
    private boolean atomicFieldAliasHasBeenAdded = false;

    /**
     * @param nonAtomicImplementation the non-atomic implementation
     * @param inMethod the method whose body contains the non-atomic implementation
     * @param potentialAtomicField the potentially-atomic field
     */
    PotentialAtomicOperation(@Nonnull List<Unit> nonAtomicImplementation,
                             @Nonnull SootMethod inMethod,
                             @Nonnull PotentialAtomicField potentialAtomicField)
    {
        if(!inMethod.hasActiveBody())
        {
            throw new IllegalArgumentException("inMethod " + inMethod.getName() + " has no active body.");
        }
        if(!bodyContainsStraightLineSequence(inMethod.getActiveBody(), nonAtomicImplementation))
        {
            throw new IllegalArgumentException("inMethod " + inMethod.getName() + " does not contain non-atomic implementation.");
        }
        this.nonAtomicImplementation = ImmutableList.copyOf(nonAtomicImplementation);
        this.inMethod = inMethod;
        this.potentialAtomicField = potentialAtomicField;
    }

    /**
     * @param b the body
     * @param units the straight-line sequence of code
     * @return true iff b contains units as a straight-line
     *          subset of its units.
     */
    private static boolean bodyContainsStraightLineSequence(@Nonnull Body b, @Nonnull List<Unit> units)
    {
        if(units.isEmpty())
        {
            return true;
        }
        if(!b.getUnits().contains(units.get(0)))
        {
            throw new IllegalArgumentException("body does not contain non-atomic implementation.");
        }
        final ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(b.getMethod());
        for(int i = 1; i < units.size(); ++i)
        {
            Unit pred = units.get(i - 1),
                    succ = units.get(i);
            if(cfg.getSuccsOf(pred).size() != 1 || !Objects.equals(cfg.getSuccsOf(pred).get(0), succ))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @param units the units
     * @return true iff all units are contained in the non-atomic implementation
     */
    public boolean containsAll(@Nonnull Collection<Unit> units)
    {
        return getNonAtomicImplementation().containsAll(units);
    }

    /**
     * @return the non-atomic implementation.
     */
    @Nonnull
    public List<Unit> getNonAtomicImplementation()
    {
        return new ArrayList<>(nonAtomicImplementation);
    }

    /**
     * @return the method containing this operation
     */
    @Nonnull
    public SootMethod getMethod()
    {
        return inMethod;
    }

    /**
     * @return the field which, if implemented as atomic, results in this operation
     */
    @Nonnull
    public PotentialAtomicField getPotentialAtomicField()
    {
        return potentialAtomicField;
    }

    /**
     * Must be called after {@link #replaceImplementationWithAtomicOperation()}
     *
     * @return the unit which initializes the local alias of the atomic field.
     */
    @Nonnull
    public Unit getLocalAtomicAliasInitialization()
    {
        if(!atomicFieldAliasHasBeenAdded)
        {
            throw new IllegalStateException("Must call replaceImplementationWithAtomicOperation() first.");
        }
        Local atomicFieldAlias = getOrAddAtomicFieldAlias();
        final List<AssignStmt> aliasAssignments = getMethod().getActiveBody()
                .getUnits()
                .stream()
                .filter(ut -> ut instanceof AssignStmt)
                .map(ut -> (AssignStmt) ut)
                .filter(assignStmt -> Objects.equals(atomicFieldAlias, assignStmt.getLeftOp()))
                .collect(Collectors.toList());
        if(aliasAssignments.isEmpty())
        {
            throw new IllegalStateException("alias " + atomicFieldAlias + " is never initialized in method " + getMethod());
        }
        if(aliasAssignments.size() > 1)
        {
            throw new IllegalStateException("alias " + atomicFieldAlias + " is assigned multiple times in method " + getMethod());
        }
        return aliasAssignments.get(0);
    }

    /**
     * @return the non-atomic version of the field
     */
    @Nonnull
    SootField getNonAtomicField()
    {
        return potentialAtomicField.getNonAtomicField();
    }

    /**
     * @return the atomic class
     */
    @Nonnull
    SootClass getAtomicClass()
    {
        return potentialAtomicField.getAtomicClass();
    }

    /**
     * Gets the local alias of the atomic field,
     * creating it if it does not exist.
     *
     * @return the local alias of the atomic field in b
     */
    @Nonnull
    Local getOrAddAtomicFieldAlias()
    {
        // record that the atomic alias has been added
        atomicFieldAliasHasBeenAdded = true;
        // build the alias and add it to the body
        final SootField atomicField = potentialAtomicField.getAtomicField();
        final String aliasName = SootNamingUtils.getAliasingLocalNameForField(atomicField);
        return SootFieldUtils.getOrAddLocalAliasOfField(aliasName, atomicField, getMethod());
    }

    /**
     * Replace the non-atomic implementation of this operation
     * with the atomic one.
     */
    abstract void replaceImplementationWithAtomicOperation();

    /**
     * Must be called after {@link #replaceImplementationWithAtomicOperation()}
     *
     * @return the atomic implementation which replaced the
     *          non-atomic implementation
     */
    @Nonnull
    public abstract Unit getAtomicImplementationStart();

    public abstract Unit getAtomicImplementationEnd();

    public abstract void recordOperationInProfiler(@Nonnull CortadoMonitorProfiler profiler);
}
