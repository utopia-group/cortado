package edu.utexas.cs.utopia.cortado.util.soot.atomics;

import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootMethodUtils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Used to transform a potential atomic field to an atomic field
 */
public class FieldToAtomicTransformer extends BodyTransformer
{
    private final PotentialAtomicField potentialAtomicField;

    /**
     * @param potentialAtomicField the potential atomic field
     */
    public FieldToAtomicTransformer(@Nonnull PotentialAtomicField potentialAtomicField)
    {
        // add the atomic field to the class
        final SootField nonAtomicField = potentialAtomicField.getNonAtomicField();
        final SootField atomicField = potentialAtomicField.getAtomicField();
        nonAtomicField.getDeclaringClass().addField(atomicField);
        // store the potential atomic field
        this.potentialAtomicField = potentialAtomicField;
    }

    /**
     * Should be called after the transform is applied
     * to finalize the removal of the non-atomic field
     */
    public void finalizeTransform()
    {
        final SootField nonAtomicField = potentialAtomicField.getNonAtomicField();
        nonAtomicField.getDeclaringClass().removeField(nonAtomicField);
    }

    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map)
    {
        // get profiler
        CortadoMonitorProfiler monitorProfiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();

        // handle insertion of atomic operations
        final List<PotentialAtomicOperation> potentialAtomicOperations = potentialAtomicField
                .getPotentialAtomicOperations()
                .stream()
                .filter(p -> Objects.equals(p.getMethod().getActiveBody(), body))
                .collect(Collectors.toList());
        if(!potentialAtomicOperations.isEmpty())
        {
            assert !body.getMethod().isStatic() && !body.getMethod().isConstructor();
            potentialAtomicOperations
                    .stream()
                    .peek(monitorProfiler::recordAtomicInvocation)
                    .forEach(PotentialAtomicOperation::replaceImplementationWithAtomicOperation);
        }
        // handle instantiation of constructor
        else if(body.getMethod().isConstructor())
        {
            final SootMethod constructor = body.getMethod();
            // Get the call to super
            final Unit superInvocation = SootMethodUtils.getObjectInitializationInvocation(constructor);
            final SootField atomicField = potentialAtomicField.getAtomicField();
            // initialize the atomic field after the call to super
            final String initLocalName = SootNamingUtils.getInitLocalNameForField(atomicField);
            final Local initLocal = SootLocalUtils.addNewLocal(initLocalName, atomicField.getType(), body);
            final AssignStmt allocToInitLocal = Jimple.v().newAssignStmt(
                    initLocal,
                    Jimple.v().newNewExpr((RefType) initLocal.getType())
            );
            SootMethod initMethod = Scene.v()
                    .getSootClass(initLocal.getType().toString())
                    .getMethod("void <init>()");
            final InvokeStmt initializeInitLocal = Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(initLocal, initMethod.makeRef())
            );
            final AssignStmt assignInitLocalToField = Jimple.v().newAssignStmt(
                    Jimple.v().newInstanceFieldRef(body.getThisLocal(), atomicField.makeRef()),
                    initLocal
            );
            body.getUnits().insertAfter(
                    Arrays.asList(allocToInitLocal, initializeInitLocal, assignInitLocalToField),
                    superInvocation
            );

            // try to match gets in the constructor
            final SootField nonAtomicField = potentialAtomicField.getNonAtomicField();
            final List<PotentialAtomicGet> potentialAtomicGets = body.getUnits()
                    .stream()
                    .filter(ut -> PotentialAtomicGet.isPotentialAtomicGet(ut, nonAtomicField))
                    .map(ut -> new PotentialAtomicGet(ut, constructor, potentialAtomicField))
                    .collect(Collectors.toList());
            potentialAtomicGets.stream()
                    .peek(monitorProfiler::recordAtomicInvocation)
                    .forEach(PotentialAtomicOperation::replaceImplementationWithAtomicOperation);
            // try to match sets in the constructor
            final List<PotentialAtomicSet> potentialAtomicSets = body.getUnits()
                    .stream()
                    .filter(ut -> PotentialAtomicSet.isPotentialAtomicSet(ut, nonAtomicField))
                    .map(ut -> new PotentialAtomicSet(ut, constructor, potentialAtomicField))
                    .collect(Collectors.toList());
            potentialAtomicSets.stream()
                    .peek(monitorProfiler::recordAtomicInvocation)
                    .forEach(PotentialAtomicOperation::replaceImplementationWithAtomicOperation);
            // make sure that we replaced all references to the non-atomic field!
            assert body.getUnits().stream().noneMatch(ut -> ut.getUseAndDefBoxes()
                    .stream()
                    .map(ValueBox::getValue)
                    .anyMatch(v -> v instanceof FieldRef && ((FieldRef) v).getField().equals(nonAtomicField))
            );
        }
    }
}
