package edu.utexas.cs.utopia.cortado.util.soot.atomics;

import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
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
 * fieldRef := value
 *
 * with atomicFieldRef.set(value)
 */
class PotentialAtomicSet extends PotentialAtomicOperation
{
    private Unit atomicSet = null;

    /**
     * @param nonAtomicImplementation the non-atomic implementation of the set
     * @param inMethod the method containing the non-atomic implementation
     * @param potentialAtomicField the potential atomic field
     */
    PotentialAtomicSet(@Nonnull Unit nonAtomicImplementation, @Nonnull SootMethod inMethod, @Nonnull PotentialAtomicField potentialAtomicField)
    {
        super(Collections.singletonList(nonAtomicImplementation), inMethod, potentialAtomicField);
        if(!isPotentialAtomicSet(nonAtomicImplementation, getNonAtomicField()))
        {
            throw new IllegalArgumentException(nonAtomicImplementation + " is not a potential atomic set for field " + getNonAtomicField());
        }
    }

    /**
     * @param ut the unit
     * @param nonAtomicField the non-atomic field
     * @return if the unit is a non-atomic implementation of
     *          setting the non-atomic field nonAtomicField
     */
    static boolean isPotentialAtomicSet(@Nonnull Unit ut, @Nonnull SootField nonAtomicField)
    {
        return ut instanceof AssignStmt
                && ((AssignStmt) ut).getLeftOp() instanceof FieldRef
                && Objects.equals(((FieldRef) ((AssignStmt) ut).getLeftOp()).getField(), nonAtomicField);
    }

    @Override
    void replaceImplementationWithAtomicOperation()
    {
        Body body = getMethod().getActiveBody();
        final UnitPatchingChain units = body.getUnits();
        assert getNonAtomicImplementation().size() == 1;
        AssignStmt set = (AssignStmt) getNonAtomicImplementation().get(0);
        SootField nonAtomicField = getNonAtomicField();
        String setSubSignature = nonAtomicField.getType() + " getAndSet(" + nonAtomicField.getType() + ")";
        InvokeExpr atomicSetInvk = Jimple.v().newVirtualInvokeExpr(
                getOrAddAtomicFieldAlias(),
                getAtomicClass().getMethod(setSubSignature).makeRef(),
                set.getRightOp()
        );
        Local oldFldVal = SootLocalUtils.getOrAddLocal("old_" + nonAtomicField.getName() + "_val", nonAtomicField.getType(), body);
        atomicSet = Jimple.v().newAssignStmt(oldFldVal, atomicSetInvk);
        units.swapWith(set, atomicSet);
    }

    @Nonnull
    @Override
    public Unit getAtomicImplementationStart()
    {
        if(atomicSet == null)
        {
            throw new IllegalStateException("Must call replaceImplementationWithAtomicOperation() first");
        }
        return atomicSet;
    }

    @Override
    public Unit getAtomicImplementationEnd()
    {
        if(atomicSet == null)
        {
            throw new IllegalStateException("Must call replaceImplementationWithAtomicOperation() first");
        }
        return atomicSet;
    }

    @Override
    public void recordOperationInProfiler(@Nonnull CortadoMonitorProfiler profiler) {
        profiler.incrementValue("numAtomicGetAndSet", 0);
    }
}
