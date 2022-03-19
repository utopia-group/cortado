package edu.utexas.cs.utopia.cortado.util.soot.atomics;

import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.util.graph.GuavaExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.PostDominatorTreeCache;
import soot.*;
import soot.jimple.FieldRef;
import soot.toolkits.graph.DominatorNode;
import soot.toolkits.graph.DominatorTree;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Used to identify {@link PotentialAtomicOperation}s for
 * a primitive field which could be converted to an atomic one.
 */
public class PotentialAtomicField
{
    final private SootField nonAtomicField;
    final private SootField atomicField;
    final private SootClass atomicClass;
    private final List<PotentialAtomicOperation> potentialAtomicOperations;

    /**
     * @param field the field which would be converted to atomic.
     *              Should be an integer, long, or boolean type.
     *              It should be a non-static field with a declared
     *              class.
     */
    private PotentialAtomicField(@Nonnull SootField field)
    {
        if(!isPotentialAtomicField(field))
        {
            throw new IllegalArgumentException("field " + field.getType() + " must be " +
                    "an integer/long/boolean type, not of type " + field.getType());
        }
        this.atomicClass = getPotentialAtomicFieldClass(field);
        assert atomicClass != null;
        // store field, and make an atomic version
        this.nonAtomicField = field;
        this.atomicField = new SootField(field.getName(), atomicClass.getType());
        this.potentialAtomicOperations = new ArrayList<>();
    }

    /**
     * @param field the field
     * @return true iff field is a potential atomic field
     *          as described in {@link #PotentialAtomicField(SootField)}
     */
    public static boolean isPotentialAtomicField(@Nonnull SootField field)
    {
        return getPotentialAtomicFieldClass(field) != null;
    }

    // TODO: this needs some serious cleanup.
    @Nullable
    public static PotentialAtomicField allOperationsCanBeAtomic(@Nonnull SootField field, Map<SootMethod, Set<SootField>> nonReadOnlyFldsForPreds, Map<SootMethod, Set<Unit>> predPreChecks)
    {
        PotentialAtomicField atomicField = new PotentialAtomicField(field);

        Map<SootField, Set<SootMethod>> predFieldsToPredsMap = new HashMap<>();

        for (SootMethod meth : nonReadOnlyFldsForPreds.keySet())
        {
            for (SootField fld : nonReadOnlyFldsForPreds.get(meth))
            {
                if (!predFieldsToPredsMap.containsKey(fld))
                    predFieldsToPredsMap.put(fld, new HashSet<>());

                predFieldsToPredsMap.get(fld).add(meth);
            }
        }

        Map<Unit, SootMethod> preCheckUnitToPred = new HashMap<>();
        for (SootMethod pred : predPreChecks.keySet())
        {
            for (Unit u : predPreChecks.get(pred))
                preCheckUnitToPred.put(u, pred);
        }

        Set<SootMethod> predWithPreChecksForField = new HashSet<>();
        // If we are trying to convert a field involved in a predicate with "prechecks", make sure that's the only field
        if (predFieldsToPredsMap.containsKey(field))
        {
            Set<SootMethod> predsWithPreCheckForFld = predPreChecks.keySet();

            if (!Collections.disjoint(predsWithPreCheckForFld, predFieldsToPredsMap.get(field)))
            {
                predWithPreChecksForField.addAll(predsWithPreCheckForFld.stream()
                                                                  .filter(pred -> predFieldsToPredsMap.get(field).contains(pred))
                                                                  .collect(Collectors.toSet()));

                // Make sure that only fld is the only field involved.
                if (!predWithPreChecksForField.stream().allMatch(pred -> nonReadOnlyFldsForPreds.get(pred).size() == 1))
                    return null;
            }
        }

        // Compute the potential atomic operations for this field
        SootClass sootClass = field.getDeclaringClass();
        // for each method which is non-static, not a constructor,
        // and has an active body, figure out the atomic replacements
        for (SootMethod m : sootClass.getMethods())
        {
            if (m.hasActiveBody() && !m.isConstructor() && !m.isStatic())
            {
                Body body = m.getActiveBody();

                Set<Unit> fldDefs = new HashSet<>();
                Set<Unit> preChecks = body.getUnits()
                                          .stream()
                                          .filter(u -> preCheckUnitToPred.containsKey(u) && predWithPreChecksForField.contains(preCheckUnitToPred.get(u)))
                                          .collect(Collectors.toSet());
                // get all the units which use/def the field
                List<Unit> unitsWhichUseOrDefField = body.getUnits()
                                                         .stream()
                                                         .filter(ut -> ut.getUseAndDefBoxes()
                                                                         .stream()
                                                                         .filter(valueBox -> valueBox.getValue() instanceof FieldRef)
                                                                         .map(valueBox -> (FieldRef) valueBox.getValue())
                                                                         .anyMatch(fieldRef -> Objects.equals(fieldRef.getField(), field)))
                                                         .collect(Collectors.toList());

                Set<Unit> matchedUnits = new HashSet<>();
                // TODO: we can generalize this to arbitrary functions, not only +/-
                // first try to match plus-equals
                for (Unit ut : unitsWhichUseOrDefField)
                {
                    if (matchedUnits.contains(ut)) continue;
                    List<Unit> potentialPlusEquals = PotentialAtomicPlusEquals.tryToMatchWithTailOfPlusEquals(ut, field, m);
                    if (potentialPlusEquals != null)
                    {
                        fldDefs.add(potentialPlusEquals.get(potentialPlusEquals.size() - 1));
                        matchedUnits.addAll(potentialPlusEquals);
                        PotentialAtomicPlusEquals potentialAtomicPlusEquals = new PotentialAtomicPlusEquals(potentialPlusEquals, m, atomicField);
                        atomicField.potentialAtomicOperations.add(potentialAtomicPlusEquals);
                    }
                }

                unitsWhichUseOrDefField.removeAll(matchedUnits);
                List<PotentialAtomicGet> atomicGets = new ArrayList<>();
                List<PotentialAtomicSet> atomicSets = new ArrayList<>();
                // Then try to match get/set
                for (Unit ut : unitsWhichUseOrDefField)
                {
                    // if matches get, match it
                    if (PotentialAtomicGet.isPotentialAtomicGet(ut, field))
                    {
                        matchedUnits.add(ut);
                        atomicGets.add(new PotentialAtomicGet(ut, m, atomicField));
                    }
                    // if matches set, match if
                    else if (PotentialAtomicSet.isPotentialAtomicSet(ut, field))
                    {
                        fldDefs.add(ut);
                        matchedUnits.add(ut);
                        atomicSets.add(new PotentialAtomicSet(ut, m, atomicField));
                    }
                }

                // make sure we matched all units
                unitsWhichUseOrDefField.removeAll(matchedUnits);
                if (!unitsWhichUseOrDefField.isEmpty())
                {
                    atomicField.potentialAtomicOperations.clear();
                    return null;
                }

                DominatorTree<Unit> pdomTree = PostDominatorTreeCache.getInstance().getOrCompute(m);
                // make sure that none of the atomic gets dominates any of the atomic sets.
                for (PotentialAtomicGet atomGet : atomicGets)
                {
                    Unit getU = atomGet.getNonAtomicImplementation().get(0);
                    DominatorNode<Unit> getDode = pdomTree.getDode(getU);
                    for (PotentialAtomicSet atomSet : atomicSets)
                    {
                        Unit setU = atomSet.getNonAtomicImplementation().get(0);
                        if (pdomTree.isDominatorOf(getDode, pdomTree.getDode(setU)))
                        {
                            atomicField.potentialAtomicOperations.clear();
                            return null;
                        }

                    }
                }

                // Make sure that preChecks can actually be converted to an atomic operation.
                if (!preChecks.isEmpty())
                {
                    @SuppressWarnings("UnstableApiUsage")
                    final ImmutableGraph<Unit> cfg = GuavaExceptionalUnitGraphCache.getInstance().getOrCompute(m);
                    //noinspection UnstableApiUsage
                    final boolean someFieldDefMayReachAnotherFieldDef = fldDefs.stream()
                            .anyMatch(def -> fldDefs.stream()
                                    .filter(otherDef -> def != otherDef)
                                    .anyMatch(Graphs.reachableNodes(cfg, def)::contains)
                            );
                    if (someFieldDefMayReachAnotherFieldDef)
                    {
                        atomicField.potentialAtomicOperations.clear();
                        return null;
                    }
                }

                atomicField.potentialAtomicOperations.addAll(atomicGets);
                atomicField.potentialAtomicOperations.addAll(atomicSets);
            }
        }

        return atomicField;
    }

    /**
     * @param field the field
     * @return the soot class of the atomic implementation of field
     *          (e.g. {@link AtomicInteger}) or null if there is
     *          no such one.
     */
    @Nullable
    private static SootClass getPotentialAtomicFieldClass(@Nonnull SootField field)
    {
        if (field.isStatic() || !field.isDeclared())
        {
            return null;
        }
        // figure out the atomic version of field
        if (field.getType().equals(BooleanType.v()))
        {
            return Scene.v().getSootClass(AtomicBoolean.class.getName());
        } else if (field.getType().equals(IntType.v()))
        {
            return Scene.v().getSootClass(AtomicInteger.class.getName());
        } else if (field.getType().equals(LongType.v()))
        {
            return Scene.v().getSootClass(AtomicLong.class.getName());
        }
        return null;
    }

    /**
     * @return the potential atomic operations
     */
    @Nonnull
    public List<PotentialAtomicOperation> getPotentialAtomicOperations()
    {
        return potentialAtomicOperations;
    }

    /**
     * @return the primitive, non-atomic field
     */
    @Nonnull
    public SootField getNonAtomicField()
    {
        return nonAtomicField;
    }

    /**
     * @return the atomic version of the field
     */
    @Nonnull
    SootField getAtomicField()
    {
        return atomicField;
    }

    /**
     * @return the atomic class
     */
    @Nonnull
    SootClass getAtomicClass()
    {
        return atomicClass;
    }
}
