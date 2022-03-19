package edu.utexas.cs.utopia.cortado.util.soot.atomics;

import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Objects;

/**
 * A potential replacement of
 *
 * local := fieldRef
 *
 * with local := atomicFieldRef
 */
class PotentialAtomicGet extends PotentialAtomicOperation
{
    private Unit atomicGet = null;

    /**
     * @param nonAtomicImplementation the non-atomic implementation of the get
     * @param inMethod the method containing the non-atomic implementation
     * @param potentialAtomicField the potential atomic field
     */
    PotentialAtomicGet(@Nonnull Unit nonAtomicImplementation,
                       @Nonnull SootMethod inMethod,
                       @Nonnull PotentialAtomicField potentialAtomicField)
    {
        super(Collections.singletonList(nonAtomicImplementation), inMethod, potentialAtomicField);
        if(!isPotentialAtomicGet(nonAtomicImplementation, getNonAtomicField()))
        {
            throw new IllegalArgumentException(nonAtomicImplementation + " is not a potential atomic get for field " + getNonAtomicField());
        }
    }

    /**
     * @param ut the unit
     * @param nonAtomicField the non-atomic field
     * @return if the unit is a non-atomic implementation of
     *          getting the non-atomic field nonAtomicField
     */
    static boolean isPotentialAtomicGet(@Nonnull Unit ut, @Nonnull SootField nonAtomicField)
    {
        return ut instanceof AssignStmt
                && ((AssignStmt) ut).getLeftOp() instanceof Local
                && ((AssignStmt) ut).getRightOp() instanceof FieldRef
                && Objects.equals(((FieldRef) ((AssignStmt) ut).getRightOp()).getField(), nonAtomicField);
    }

    @Override
    void replaceImplementationWithAtomicOperation()
    {
        final UnitPatchingChain units = getMethod().getActiveBody().getUnits();
        assert getNonAtomicImplementation().size() == 1;
        AssignStmt get = (AssignStmt) getNonAtomicImplementation().get(0);
        String getSubSignature = getNonAtomicField().getType() + " get()";
        InvokeExpr atomicGetInvk = Jimple.v().newVirtualInvokeExpr(
                getOrAddAtomicFieldAlias(),
                getAtomicClass().getMethod(getSubSignature).makeRef()
        );
        atomicGet = Jimple.v().newAssignStmt(get.getLeftOp(), atomicGetInvk);
        units.swapWith(get, atomicGet);
    }

    @Nonnull
    @Override
    public Unit getAtomicImplementationStart()
    {
        if(atomicGet == null)
        {
            throw new IllegalStateException("Must call replaceImplementationWithAtomicOperation() first.");
        }
        return atomicGet;
    }

    @Override
    public Unit getAtomicImplementationEnd()
    {
        if(atomicGet == null)
        {
            throw new IllegalStateException("Must call replaceImplementationWithAtomicOperation() first.");
        }
        return atomicGet;
    }

    @Override
    public void recordOperationInProfiler(@Nonnull CortadoMonitorProfiler profiler) {
        profiler.incrementValue("numAtomicGet", 0);
    }
}
