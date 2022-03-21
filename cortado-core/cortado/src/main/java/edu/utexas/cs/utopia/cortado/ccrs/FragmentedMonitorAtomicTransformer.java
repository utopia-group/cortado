package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.util.graph.GuavaExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootTransformUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.FieldToAtomicTransformer;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.util.soot.SootTransformUtils.registerTransformer;

/**
 * Designed to apply a {@link edu.utexas.cs.utopia.cortado.util.soot.atomics.FieldToAtomicTransformer}
 * and then fix fragments/lock assignments on a {@link FragmentedMonitor}
 */
public class FragmentedMonitorAtomicTransformer
{
    private final static Logger log = LoggerFactory.getLogger(FragmentedMonitorAtomicTransformer.class.getName());

    private final FragmentedMonitor fragmentedMonitor;

    private final Collection<PotentialAtomicField> fieldsToMakeAtomic;

    private final Map<SootMethod, Set<Unit>> preChecks;

    /**
     * Transform the fieldsToMakeAtomic into atomics
     *  @param fragmentedMonitor the monitor.
     *                          It must be the case that each non-atomic implementation replaced
     *                          by an atomic implementation is either a fragment, or the
     *                          union of fragments. (i.e. fragments have been either totally
     *                          replaced, or not replaced at all). This can be guaranteed by using the
     *                          {@link FragmentedMonitorShatterer}.
     * @param fieldsToMakeAtomic the fields to convert to atomics
     * @param preChecks the units which check a predicate for the flip-predicate optimization
     **/
    public FragmentedMonitorAtomicTransformer(@Nonnull FragmentedMonitor fragmentedMonitor,
                                              @Nonnull Collection<PotentialAtomicField> fieldsToMakeAtomic,
                                              Map<SootMethod, Set<Unit>> preChecks)
    {
        this.fragmentedMonitor = fragmentedMonitor;
        this.fieldsToMakeAtomic = fieldsToMakeAtomic;
        this.preChecks = preChecks;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void atomicTransformation()
    {
        // convert fields to atomic
        final SootClass monitorClass = fragmentedMonitor.getMonitor();

        // For predicates that have pre-checks and contain a field that is going to be atomic, create a clone of the
        // predicate method to implement the checks atomically.
        Set<SootMethod> predsWithPreChecks = preChecks.keySet();
        Map<SootMethod, Set<SootField>> nonReadOnlyPredicateFields = fragmentedMonitor.getNonReadOnlyPredicateFields();

        Set<SootField> originalFldsToBeConverted = fieldsToMakeAtomic.stream().map(PotentialAtomicField::getNonAtomicField).collect(Collectors.toSet());
        Set<SootMethod> predsWithAtomicFields = nonReadOnlyPredicateFields.entrySet()
                                                                         .stream()
                                                                         .filter(e -> !Collections.disjoint(e.getValue(), originalFldsToBeConverted))
                                                                         .map(Map.Entry::getKey)
                                                                         .collect(Collectors.toSet());

        predsWithAtomicFields.stream()
                .filter(predsWithPreChecks::contains)
                .forEach(pred -> {
                    List<SootField> fldToBeAtomic = nonReadOnlyPredicateFields.get(pred)
                            .stream()
                            .filter(fld -> fieldsToMakeAtomic.stream()
                                    .anyMatch(aFld -> aFld.getNonAtomicField().equals(fld)))
                            .collect(Collectors.toList());

                    assert fldToBeAtomic.size() == 1;
                    // add type of field to become atomic as an extra argument
                    ArrayList<Type> paramTypes = new ArrayList<>(pred.getParameterTypes());
                    Type fldTy = fldToBeAtomic.get(0).getType();
                    paramTypes.add(fldTy);
                    SootMethod predForPreCheck = new SootMethod(pred.getName() + "$pre_check", paramTypes, pred.getReturnType(), pred.getModifiers(), pred.getExceptions());
                    Body clonedBody = (Body) pred.getActiveBody().clone();
                    predForPreCheck.setActiveBody(clonedBody);
                    monitorClass.addMethod(predForPreCheck);

                    // Now alter the new body, so it reads the value from the new argument.
                    Jimple j = Jimple.v();
                    UnitPatchingChain units = clonedBody.getUnits();
                    Unit firstNonIdentity = ((JimpleBody)clonedBody).getFirstNonIdentityStmt();

                    Local oldFldVal = SootLocalUtils.getOrAddLocal("old_fld_val", fldTy, clonedBody);
                    units.insertBeforeNoRedirect(j.newIdentityStmt(oldFldVal, j.newParameterRef(fldTy, paramTypes.size()-1)), firstNonIdentity);

                    units.stream()
                         .flatMap(u -> u.getUseBoxes().stream())
                         .filter(vb -> vb.getValue() instanceof InstanceFieldRef && ((InstanceFieldRef)vb.getValue()).getField().equals(fldToBeAtomic.get(0)))
                         .forEach(vb -> vb.setValue(oldFldVal));
                });

        // Convert operations to atomic ones
        fieldsToMakeAtomic.forEach(potentialAtomicField -> {
            log.debug("Converting " + potentialAtomicField.getNonAtomicField() + " to an atomic field.");
            final FieldToAtomicTransformer fieldToAtomicTransformer = new FieldToAtomicTransformer(potentialAtomicField);
            final Transform fieldToAtomicTransformerT = registerTransformer(
                    PackManager.v().getPack("jtp"),
                    monitorClass.getName() + "." + potentialAtomicField.getNonAtomicField().getName(),
                    fieldToAtomicTransformer
            );
            SootTransformUtils.applyTransform(monitorClass, fieldToAtomicTransformerT);
            // finalize removal
            fieldToAtomicTransformer.finalizeTransform();
        });

        // Finally, handle pre-checks for predicates with atomic fields.
        Map<Unit, SootMethod> preCheckToPred = new HashMap<>();
        for (SootMethod pred : preChecks.keySet())
        {
            for (Unit preCheck : preChecks.get(pred))
                preCheckToPred.put(preCheck, pred);
        }
        Set<Unit> allPreChecks = preChecks.values()
                                          .stream()
                                          .flatMap(Collection::stream)
                                          .collect(Collectors.toSet());

        Map<CCR, Set<Unit>> newPreChecks = new HashMap<>();
        fragmentedMonitor.getCCRs()
                .forEach(ccr -> {
                    Body body = ccr.getAtomicSection().getActiveBody();
                    UnitPatchingChain units = body.getUnits();

                    Set<Unit> preChecksToReplace = units.stream()
                            .filter(allPreChecks::contains)
                            .filter(u -> predsWithPreChecks.contains(preCheckToPred.get(u)))
                            .filter(u -> predsWithAtomicFields.contains(preCheckToPred.get(u)))
                            .collect(Collectors.toSet());

                    final ImmutableGraph<Unit> cfg = GuavaExceptionalUnitGraphCache.getInstance().getOrCompute(ccr.getAtomicSection());
                    final Set<Unit> explicitExitUnits = SootUnitUtils.getExplicitExitUnits(ccr.getAtomicSection());
                    preChecksToReplace.forEach(preCheck -> {
                        SootMethod predForCheck = preCheckToPred.get(preCheck);
                        // This must be unique
                        SootField fldForPre = nonReadOnlyPredicateFields.get(predForCheck)
                                .stream()
                                .filter(originalFldsToBeConverted::contains)
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Missing optional."));

                        Local oldFldVal = SootLocalUtils.retrieveLocal("old_" + fldForPre.getName() + "_val", fldForPre.getType(), body);
                        assert oldFldVal != null;
                        List<Unit> fldUpdates = units.stream()
                                .filter(u -> u.getDefBoxes()
                                        .stream()
                                        .anyMatch(def -> def.getValue().equals(oldFldVal)))
                                .collect(Collectors.toList());

                        SootMethod pred = preCheckToPred.get(preCheck);
                        SootMethod preCheckPred = monitorClass.getMethodByName(pred.getName() + "$pre_check");

                        Jimple j = Jimple.v();

                        for(Unit fldUpdate : fldUpdates)
                        {
                            SpecialInvokeExpr originalInvExpr = (SpecialInvokeExpr) ((Stmt) preCheck).getInvokeExpr();
                            ArrayList<Value> args = new ArrayList<>(originalInvExpr.getArgs());
                            args.add(oldFldVal);
                            AssignStmt newPreCheck = j.newAssignStmt(((AssignStmt) preCheck).getLeftOp(), j.newSpecialInvokeExpr((Local) originalInvExpr.getBase(), preCheckPred.makeRef(), args));
                            units.insertAfter(newPreCheck, fldUpdate);

                            if (!newPreChecks.containsKey(ccr))
                                newPreChecks.put(ccr, new HashSet<>());

                            newPreChecks.get(ccr).add(newPreCheck);
                        }
                        final Graph<Unit> cfgWithoutUpdates = Graphs.inducedSubgraph(cfg, cfg.nodes()
                                        .stream()
                                        .filter(n -> !fldUpdates.contains(n))
                                        .collect(Collectors.toList())
                        );
                        final boolean preCheckDominatedByFieldUpdates = Graphs.reachableNodes(cfgWithoutUpdates, preCheck)
                                .stream()
                                .noneMatch(explicitExitUnits::contains);
                        if(preCheckDominatedByFieldUpdates)
                        {
                            units.remove(preCheck);
                        }
                    });
                });
    }
}
