package edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis;

import edu.utexas.cs.utopia.cortado.vcgen.ModelImporter;
import soot.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementations must guarantee the following:
 * <p>
 * if a unit may read/write from an abstract location M,
 * M must be in readSet(u)/writeSet(u)
 */
public interface MayRWSetAnalysis extends RWSetAnalysis
{
    ConcurrentHashMap<SootClass, Set<MemoryLocations>> mtrFldLocs = new ConcurrentHashMap<>();

    ConcurrentHashMap<SootClass, Set<MemoryLocations>> thisLocs = new ConcurrentHashMap<>();

    ModelImporter models = ModelImporter.getInstance();

    static Set<MemoryLocations> getMemoryLocationsForFields(SootClass mtr)
    {
        return mtrFldLocs.computeIfAbsent(mtr,
                                          (k) -> getMemoryLocationsPerFields(mtr).values().stream()
                                                                                 .flatMap(Collection::stream)
                                                                                 .collect(Collectors.toSet()));
    }

    static Set<MemoryLocations> getThisMemoryLocs(SootClass mtr)
    {
        return thisLocs.computeIfAbsent(mtr, (k) -> {
            PointsToAnalysis ptAnalysis = Scene.v().getPointsToAnalysis();
            Set<Local> ctrThisLocal = getThisLocals(mtr);

            return ctrThisLocal.stream()
                               .map(l -> new SootMayRWSetAnalysis.SootMemoryLocations(ptAnalysis.reachingObjects(l)))
                               .collect(Collectors.toSet());
        });
    }

    static Map<SootField, Set<MemoryLocations>> getMemoryLocationsPerFields(SootClass mtr)
    {
        PointsToAnalysis ptAnalysis = Scene.v().getPointsToAnalysis();

        Map<SootField, Set<MemoryLocations>> mtrFldMemLocs;
        Set<Local> ctrThisLocal = getThisLocals(mtr);

        // Regular fields
        mtrFldMemLocs = mtr.getFields()
                           .stream()
                           .filter(f -> !f.isStatic())
                           .collect(Collectors.toMap(Function.identity(), f -> ctrThisLocal.stream()
                                                                                           .flatMap(l -> getAllReachableStateFromField(f, ptAnalysis.reachingObjects(l)).stream())
                                                                                           .collect(Collectors.toSet()))
                           );

        return mtrFldMemLocs;
    }

    static Set<Local> getThisLocals(SootClass mtr)
    {
        return mtr.getMethods()
                  .stream()
                  .filter(SootMethod::isConstructor)
                  .filter(SootMethod::hasActiveBody)
                  .map(SootMethod::getActiveBody)
                  .map(Body::getThisLocal)
                  .collect(Collectors.toSet());
    }

    static Set<MemoryLocations> followFieldsFromClass(SootClass cl, PointsToSet base)
    {
        return cl.getFields()
                 .stream()
                 .filter(fld -> !fld.isStatic())
                 .filter(fld -> !(fld.getType() instanceof RefType) || (models.hasModeledFields(((RefType)fld.getType()).getSootClass()) &&
                         models.getModeledFields(((RefType)fld.getType()).getSootClass()).contains(fld)))
                 .flatMap(fld -> getAllReachableStateFromField(fld, base).stream())
                 .collect(Collectors.toSet());
    }

    static Set<MemoryLocations> getAllReachableStateFromField(SootField fld, PointsToSet base)
    {
        PointsToAnalysis ptAnalysis = Scene.v().getPointsToAnalysis();

        Set<MemoryLocations> rv = new HashSet<>();

        boolean isPrimitive = fld.getType() instanceof PrimType;

        rv.add(new SootMayRWSetAnalysis.FieldMemoryLocations(fld, base, isPrimitive));

        if (!isPrimitive)
        {
            PointsToSet reachThroughFld = ptAnalysis.reachingObjects(base, fld);
            rv.add(new SootMayRWSetAnalysis.FieldMemoryLocations(fld, reachThroughFld, false));

            reachThroughFld.possibleTypes()
                           .forEach(t -> {
                               if (t instanceof RefType)
                               {
                                   RefType refTy = (RefType) t;
                                   SootClass refClass = refTy.getSootClass();
                                   if (models.hasModeledFields(refClass))
                                   {
                                       models.getModeledFields(refClass)
                                             .forEach(nestFld ->
                                                 rv.addAll(getAllReachableStateFromField(nestFld, reachThroughFld))
                                             );
                                   }
                               }
                               else if (t instanceof ArrayType)
                               {
                                   ArrayType arrTy = (ArrayType) t;
                                   rv.add(new SootMayRWSetAnalysis.ArrayMemoryLocations(reachThroughFld, arrTy.getElementType() instanceof PrimType));
                               }
                           });
        }

        return rv;
    }
}
