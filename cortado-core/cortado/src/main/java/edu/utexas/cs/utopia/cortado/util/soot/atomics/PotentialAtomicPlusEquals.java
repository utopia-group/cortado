package edu.utexas.cs.utopia.cortado.util.soot.atomics;

import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SimpleLiveLocalsSootAnalysisCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A potential replacement of
 *
 * local0 := fieldRef
 * local1 := local0 +/- delta
 * fieldRef := local1
 *
 * with increment/decrement/addAndGet/getAndAdd
 */
class PotentialAtomicPlusEquals extends PotentialAtomicOperation
{
    private Unit atomicPlusEquals = null;

    private Unit oldValSet = null;

    /**
     * @param nonAtomicImplementation the non-atomic implementation of the plus-equals
     * @param inMethod the method containing the non-atomic implementation
     * @param potentialAtomicField the potential atomic field
     */
    PotentialAtomicPlusEquals(@Nonnull List<Unit> nonAtomicImplementation, @Nonnull SootMethod inMethod, @Nonnull PotentialAtomicField potentialAtomicField)
    {
        super(nonAtomicImplementation, inMethod, potentialAtomicField);
        if(isNotPotentialAtomicPlusEquals(nonAtomicImplementation, getNonAtomicField()))
        {
            throw new IllegalArgumentException(nonAtomicImplementation + " is not a potential atomic plus-equals for field " + getNonAtomicField());
        }
    }

    /**
     * @param nonAtomicImplementation the potential plus-equals
     * @param nonAtomicField the non-atomic field
     * @return true iff nonAtomicImplementation matches the pattern of
     *          adding something to the non-atomic field nonAtomicField
     */
    static private boolean isNotPotentialAtomicPlusEquals(@Nonnull List<Unit> nonAtomicImplementation, @Nonnull SootField nonAtomicField)
    {
        // must be an int or a long
        if(!(nonAtomicField.getType().equals(IntType.v()) || nonAtomicField.getType().equals(LongType.v())))
        {
            return true;
        }
        // Must be exactly three units
        if(nonAtomicImplementation.size() != 3) return true;
        // grab the presumed load, plusEquals, and store
        Unit load = nonAtomicImplementation.get(0),
                plusEquals = nonAtomicImplementation.get(1),
                store = nonAtomicImplementation.get(2);
        // make sure load is an assignment from the field to a local
        if(!(load instanceof AssignStmt)) return true;
        AssignStmt temp0Assignment = (AssignStmt) load;
        if(!(temp0Assignment.getRightOp() instanceof FieldRef && temp0Assignment.getLeftOp() instanceof Local))
        {
            return true;
        }
        if(!nonAtomicField.equals(((FieldRef) temp0Assignment.getRightOp()).getField())) return true;
        Local temp0 = (Local) temp0Assignment.getLeftOp();
        // make sure plus-equals is an addition/subtraction involving being stored
        // to a local
        if(!(plusEquals instanceof AssignStmt)) return true;
        AssignStmt temp1Assignment = (AssignStmt) plusEquals;
        if(!(temp1Assignment.getLeftOp() instanceof Local)) return true;
        Local temp1 = (Local) temp1Assignment.getLeftOp();
        if(!(temp1Assignment.getRightOp() instanceof AddExpr || temp1Assignment.getRightOp() instanceof SubExpr))
        {
            return true;
        }
        BinopExpr addOrSub = (BinopExpr) temp1Assignment.getRightOp();
        // make sure that exactly one argument of the add/sub is temp0
        if(Objects.equals(addOrSub.getOp1(), temp0) == Objects.equals(addOrSub.getOp2(), temp0))
        {
            return true;
        }
        // make sure the store is an assignment of temp1 to the field
        if(!(store instanceof AssignStmt)) return true;
        AssignStmt fieldAssignment = (AssignStmt) store;
        if(!Objects.equals(fieldAssignment.getRightOp(), temp1)) return true;
        if(!(fieldAssignment.getLeftOp() instanceof FieldRef)) return true;
        // this is a plus-equals, as long as the last assignment is to the desired field!
        return !Objects.equals(((FieldRef) fieldAssignment.getLeftOp()).getField(), nonAtomicField);
    }

    /**
     * Try to match tail as the last-instruction in a non-atomic implementation
     * of plus-equals.
     *
     * @param tail the tail
     * @param nonAtomicField the non-atomic field
     * @param inMethod the method containing tail
     * @return the non-atomic implementation of the plus-equals,
     *          if matched. Otherwise null
     */
    @Nullable
    static List<Unit> tryToMatchWithTailOfPlusEquals(@Nonnull Unit tail, @Nonnull SootField nonAtomicField, @Nonnull SootMethod inMethod)
    {
        UnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(inMethod);
        // if tail has two unique predecessors, and matches, return it!
        // otherwise return null
        if(cfg.getPredsOf(tail) == null || cfg.getPredsOf(tail).size() != 1) return null;
        Unit pred = cfg.getPredsOf(tail).get(0);
        if(cfg.getPredsOf(pred) == null || cfg.getPredsOf(pred).size() != 1) return null;
        List<Unit> potentialPlusEquals = Arrays.asList(cfg.getPredsOf(pred).get(0), pred, tail);
        if(isNotPotentialAtomicPlusEquals(potentialPlusEquals, nonAtomicField)) return null;
        return potentialPlusEquals;
    }

    @Override
    void replaceImplementationWithAtomicOperation()
    {
        Body body = getMethod().getActiveBody();
        final UnitPatchingChain units = body.getUnits();
        assert getNonAtomicImplementation().size() == 3;
        AssignStmt load = (AssignStmt) getNonAtomicImplementation().get(0),
            plusEquals = (AssignStmt) getNonAtomicImplementation().get(1),
            store = (AssignStmt) getNonAtomicImplementation().get(2);
        // make sure they are still the unique predecessors/successors of each other
        final ExceptionalUnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(getMethod());
        final List<Unit> succsOfLoad = cfg.getSuccsOf(load);
        if(succsOfLoad == null || succsOfLoad.size() != 1 || !succsOfLoad.contains(plusEquals))
        {
            throw new IllegalStateException(plusEquals + " is no longer unique successor of " + load);
        }
        final List<Unit> succsOfPlusEquals = cfg.getSuccsOf(plusEquals);
        if(succsOfPlusEquals == null || succsOfPlusEquals.size() != 1 || !succsOfPlusEquals.contains(store))
        {
            throw new IllegalStateException(store + " is no longer unique successor of " + plusEquals);
        }
        assert cfg.getPredsOf(store).size() == 1;
        assert cfg.getPredsOf(plusEquals).size() == 1;
        // Now figure out which locals are live after, so we know whether
        // to get the value before/after adding
        SimpleLiveLocals simpleLiveLocals = SimpleLiveLocalsSootAnalysisCache.getInstance().getOrCompute(getMethod());
        Local temp0 = (Local) load.getLeftOp();
        Local temp1 = (Local) plusEquals.getLeftOp();
        boolean temp0IsLive = simpleLiveLocals.getLiveLocalsAfter(store).contains(temp0);
        boolean temp1IsLive = simpleLiveLocals.getLiveLocalsAfter(store).contains(temp1);
        boolean getBeforePlusEquals = !temp1IsLive;
        boolean bothLive = temp1IsLive && temp0IsLive;
        // Now figure out what we are adding to temp0
        assert plusEquals.getRightOp() instanceof BinopExpr;
        final BinopExpr addOrSubExpr = (BinopExpr) plusEquals.getRightOp();
        boolean isAdd = addOrSubExpr instanceof AddExpr;
        assert isAdd || addOrSubExpr instanceof SubExpr;
        Value delta;
        if(temp0.equals(addOrSubExpr.getOp1()))
        {
            delta = addOrSubExpr.getOp2();
        }
        else
        {
            assert temp0.equals(addOrSubExpr.getOp2());
            delta = plusEquals.getLeftOp();
        }
        // Figure out if we can increment/decrement!
        boolean isIncrement = false, isDecrement = false;
        Jimple j = Jimple.v();
        if(delta instanceof LongConstant)
        {
            final long value = ((LongConstant) delta).value;
            isIncrement = (isAdd && value == 1) || (!isAdd && value == -1);
            isDecrement = (isAdd && value == -1) || (!isAdd && value == 1);
        }
        else if(delta instanceof IntConstant)
        {
            final int value = ((IntConstant) delta).value;
            isIncrement = (isAdd && value == 1) || (!isAdd && value == -1);
            isDecrement = (isAdd && value == -1) || (!isAdd && value == 1);
        }
        // if we subtract, just negate delta.
        else if (!isAdd)
        {
            Local deltaTmp = SootLocalUtils.getOrAddLocal("delta_tmp", delta.getType(), body);
            units.insertBefore(j.newAssignStmt(deltaTmp, j.newNegExpr(delta)), load);
            delta = deltaTmp;
        }
        // Now get the appropriate atomic method
        SootMethod atomicPlusEqualsMethod;
        SootClass atomicClass = getAtomicClass();
        String primTypeString = getNonAtomicField().getType().toString();
        if(isIncrement)
        {
            if(getBeforePlusEquals)
            {
                atomicPlusEqualsMethod = atomicClass.getMethod(primTypeString + " getAndIncrement()");
            }
            else
            {
                atomicPlusEqualsMethod = atomicClass.getMethod(primTypeString + " incrementAndGet()");
            }
        }
        else if(isDecrement)
        {
            if(getBeforePlusEquals)
            {
                atomicPlusEqualsMethod = atomicClass.getMethod(primTypeString + " getAndDecrement()");
            }
            else
            {
                atomicPlusEqualsMethod = atomicClass.getMethod(primTypeString + " decrementAndGet()");
            }
        }
        else
        {
            if(getBeforePlusEquals)
            {
                String addOrSub = isAdd ? "Add" : "Sub";
                atomicPlusEqualsMethod = atomicClass.getMethod(primTypeString + " getAndAdd(" + primTypeString + ")");
            }
            else
            {
                String addOrSub = isAdd ? "add" : "sub";
                atomicPlusEqualsMethod = atomicClass.getMethod(primTypeString + " " + "addAndGet" + "(" + primTypeString + ")");
            }
        }
        // build an invocation of that method
        InvokeExpr atomicPlusEqualsInvk;
        Local atomicFieldAlias = getOrAddAtomicFieldAlias();
        if(isIncrement || isDecrement)
        {
            atomicPlusEqualsInvk = j.newVirtualInvokeExpr(atomicFieldAlias, atomicPlusEqualsMethod.makeRef());
        }
        else
        {
            atomicPlusEqualsInvk = j.newVirtualInvokeExpr(atomicFieldAlias, atomicPlusEqualsMethod.makeRef(), delta);
        }
        // replace the load, plus-equals, store with the atomic version
        Local assignee = getBeforePlusEquals ? temp0 : temp1;
        atomicPlusEquals = j.newAssignStmt(assignee, atomicPlusEqualsInvk);
        units.insertBefore(atomicPlusEquals, load);
        units.remove(load);

        if (assignee == temp0)
        {
            SootField nonAtomicField = getNonAtomicField();
            Local oldFldVal = SootLocalUtils.getOrAddLocal("old_" + nonAtomicField.getName() + "_val", nonAtomicField.getType(), body);
            oldValSet = j.newAssignStmt(oldFldVal, temp0);
            units.insertAfter(oldValSet, atomicPlusEquals);
        }
        else
        {
            // COMMENT (Kostas): Don't have time to redesign the Atomic conversion, putting this here as a safeguard.
            throw new IllegalStateException("Potentially cannot convert conditional signalling to atomic");
        }
        // if both the original value and the incremented value are live
        // afterwards, don't remove the plus-equals
        if(!bothLive)
        {
            units.remove(plusEquals);
        }
        units.remove(store);
    }

    @Nonnull
    @Override
    public Unit getAtomicImplementationStart()
    {
        if(atomicPlusEquals == null)
        {
            throw new IllegalStateException("Must be called after replaceImplementationWithAtomicOperation()");
        }
        return atomicPlusEquals;
    }

    @Override
    public Unit getAtomicImplementationEnd()
    {
        if (oldValSet == null)
        {
            throw new IllegalStateException("Must be called after replaceImplementationWithAtomicOperation()");
        }
        return oldValSet;
    }

    @Override
    public void recordOperationInProfiler(@Nonnull CortadoMonitorProfiler profiler) {
        profiler.incrementValue("numAtomicGetAndSet", 0);
    }
}
