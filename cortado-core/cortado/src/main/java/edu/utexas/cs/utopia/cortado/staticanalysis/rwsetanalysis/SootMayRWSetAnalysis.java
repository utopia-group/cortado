package edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.vcgen.IgnoreListsParser;
import edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Stmt;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.TransitiveTargets;
import soot.jimple.toolkits.pointer.RWSet;
import soot.jimple.toolkits.pointer.SideEffectAnalysis;
import soot.options.Options;
import soot.util.queue.QueueReader;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getMemoryLocationsForFields;
import static edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis.getThisMemoryLocs;

/**
 * A {@link MayRWSetAnalysis} based on {@link soot}'s {@link SideEffectAnalysis}
 */
public class SootMayRWSetAnalysis implements MayRWSetAnalysis
{
    private static final Logger log = LoggerFactory.getLogger(SootMayRWSetAnalysis.class);

    private static FastHierarchy typeHierarchy = Scene.v().getOrMakeFastHierarchy();

    private static SootClass throwableClass = Scene.v().getSootClass(Throwable.class.getName());

    private static IgnoreListsParser ignListParserInst = IgnoreListsParser.getInstance();
    private QueueReader<Edge> callGraphListener;

    /**
     * Clear and re-initialize class-wide info
     */
    public static void clear()
    {
        Scene.v().releaseFastHierarchy();
        typeHierarchy = Scene.v().getOrMakeFastHierarchy();
        throwableClass = Scene.v().getSootClass(Throwable.class.getName());
        ignListParserInst = IgnoreListsParser.getInstance();
        SootMemoryLocations.intersectQueries.clear();
    }

    /**
     * Clear object-specific caches
     */
    public void clearCaches()
    {
        writeCache.clear();
        readCache.clear();
    }

    private final PointsToAnalysis pointsToAnalysis;

    private final SideEffectAnalysis sideEffectAnalysis;

    private final SootClass monitorClass;

    private final Set<MemoryLocations> monitorState;

    private TransitiveTargets mtrTransTrgs;

    private final ConcurrentHashMap<SootMethod, Boolean> methodsWithComputedTransTargets = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<SootMethod, ConcurrentHashMap<Unit, Collection<MemoryLocations>>> readCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<SootMethod, ConcurrentHashMap<Unit, Collection<MemoryLocations>>> writeCache = new ConcurrentHashMap<>();

    public static class SootMemoryLocations implements MemoryLocations
    {
        static private final ConcurrentHashMap<SootMemoryLocations, ConcurrentHashMap<SootMemoryLocations, Boolean>> intersectQueries = new ConcurrentHashMap<>();

        public final PointsToSet memLocs;

        public SootMemoryLocations(PointsToSet memLocs)
        {
            this.memLocs = memLocs;
        }

        @Override
        public boolean intersect(@Nonnull MemoryLocations m)
        {
            if(m instanceof EmptyMemoryLocations) return false;
            if (!(m instanceof SootMemoryLocations))
            {
                throw new IllegalArgumentException("invalid argument, only objects of type SootMemoryLocations are accepted");
            }

            if (m instanceof FieldMemoryLocations)
            {
                FieldMemoryLocations fldMem = (FieldMemoryLocations) m;

                Type fldTy = fldMem.memObject.getType();
                if (fldMem.isPrimitive || memLocs.possibleTypes().stream().noneMatch(t -> typeHierarchy.canStoreType(t, fldTy)))
                    return false;
            }
            else if (m instanceof ArrayMemoryLocations)
            {
                ArrayMemoryLocations arrayMem = (ArrayMemoryLocations) m;

                Set<Type> possibleBaseTypes = arrayMem.memLocs.possibleTypes()
                                                              .stream()
                                                              .filter(t -> t instanceof ArrayType)
                                                              .map(t -> ((ArrayType)t).baseType)
                                                              .collect(Collectors.toSet());

                if (arrayMem.isPrimitive || Collections.disjoint(memLocs.possibleTypes(), possibleBaseTypes))
                    return false;
            }

            return memLocsIntersect((SootMemoryLocations) m);
        }


        protected boolean memLocsIntersect(SootMemoryLocations m)
        {
            return intersectQueries.computeIfAbsent(this, (k) -> new ConcurrentHashMap<>())
                                   .computeIfAbsent(m, (k) -> memLocs.hasNonEmptyIntersection(m.memLocs) || (!memLocs.getClass().equals(m.memLocs.getClass()) && m.memLocs.hasNonEmptyIntersection(memLocs)));
        }

        @Nonnull
        @Override
        public Collection<Expr> getExprsIn(@Nonnull Set<Expr> freeVars)
        {
            return memLocs.possibleTypes()
                          .stream()
                          .map(t -> {
                              // We currently we only use this class to represtent the base of an array access.
                              assert t instanceof ArrayType;
                              return VCGenUtils.getHeapArrayForArrayType((ArrayType) t);
                          })
                          .filter(freeVars::contains)
                          .collect(Collectors.toSet());
        }

        @Nonnull
        @Override
        public MemoryLocations overApproximateWithoutField(@Nonnull SootField sootField)
        {
            return new SootMemoryLocations(memLocs);
        }
    }

    /**
     * Memory Location representation for standard global/fields
     * represented as {@link SootField}.
     */
    public static class FieldMemoryLocations extends SootMemoryLocations
    {
        public final SootField memObject;

        public final boolean isPrimitive;

        public FieldMemoryLocations(SootField obj, PointsToSet memLocs, boolean isPrimitive) {
            super(memLocs);
            this.memObject = obj;
            this.isPrimitive = isPrimitive;
        }

        @Override
        public boolean intersect(@Nonnull MemoryLocations m)
        {
            if(m instanceof EmptyMemoryLocations) return false;
            if (!(m instanceof SootMemoryLocations))
            {
                throw new IllegalArgumentException("invalid argument, only objects of type SootMemoryLocations are accepted");
            }

            // v.array and v.array[index] do not conflict
            if (m instanceof ArrayMemoryLocations)
                return false;

            if (isPrimitive)
            {
                if (!(m instanceof FieldMemoryLocations))
                    return false;
                else
                {
                    FieldMemoryLocations other = (FieldMemoryLocations) m;
                    if (!this.memObject.equals(other.memObject))
                        return false;

                    // primitive static field
                    if (memLocs == null)
                        return true;
                }
            }
            else if (m instanceof FieldMemoryLocations)
            {
                FieldMemoryLocations fldMem = (FieldMemoryLocations) m;
                if (fldMem.isPrimitive || !this.memObject.equals(fldMem.memObject))
                    return false;
            }

            return memLocsIntersect((SootMemoryLocations) m);
        }

        @Nonnull
        @Override
        public Collection<Expr> getExprsIn(@Nonnull Set<Expr> freeVars)
        {
            FunctionApp memLocAsExpr = VCGenUtils.getFieldHeapArrayOrGlobal(memObject);
            return freeVars.contains(memLocAsExpr) ? Collections.singleton(memLocAsExpr) : Collections.emptySet();
        }

        @Nonnull
        @Override
        public MemoryLocations overApproximateWithoutField(@Nonnull SootField sootField)
        {
            MemoryLocations withoutRefsToField;
            // if soot field is memObject, removing it leaves empty set
            if(Objects.equals(sootField, memObject))
            {
                withoutRefsToField = new EmptyMemoryLocations();
            }
            else // otherwise, remove it does nothing. Just build a copy of this object
            {
                withoutRefsToField = new FieldMemoryLocations(memObject, memLocs, isPrimitive);
            }

            return withoutRefsToField;
        }
    }

    public static class ArrayMemoryLocations extends SootMemoryLocations
    {
        final private boolean isPrimitive;

        public ArrayMemoryLocations(PointsToSet memLocs, boolean isPrimitive)
        {
            super(memLocs);
            this.isPrimitive = isPrimitive;
        }

        @Override
        public boolean intersect(@Nonnull MemoryLocations m)
        {
            if(m instanceof EmptyMemoryLocations) return false;
            if (!(m instanceof SootMemoryLocations))
                throw new IllegalArgumentException("invalid argument, only objects of type SootMemoryLocations are accepted");

            if (!(m instanceof ArrayMemoryLocations))
            {
                return false;
            }

            return memLocsIntersect((SootMemoryLocations) m);
        }

        @Nonnull
        @Override
        public Collection<Expr> getExprsIn(@Nonnull Set<Expr> freeVars)
        {
            return memLocs.possibleTypes()
                          .stream()
                          .filter(t -> t instanceof ArrayType)
                          .map(t -> VCGenUtils.getHeapArrayForArrayType((ArrayType) t))
                          .filter(freeVars::contains)
                          .collect(Collectors.toSet());
        }

        @Nonnull
        @Override
        public MemoryLocations overApproximateWithoutField(@Nonnull SootField sootField)
        {
            return new ArrayMemoryLocations(memLocs, isPrimitive);
        }
    }



    /**
     *
     * @throws IllegalStateException if {@link soot} does not have a pointer
     *      analysis (see {@link Scene#hasPointsToAnalysis()}).
     * @throws IllegalStateException if {@link soot} is not in whole-program
     *      mode and has no main class set
     */
    public SootMayRWSetAnalysis(SootClass monitorClass)
    {
        if(!Options.v().whole_program() && "".equals(Options.v().main_class()))
        {
            throw new IllegalStateException("soot must be in whole-program mode, or have a main class set.");
        }
        if(!Scene.v().hasPointsToAnalysis())
        {
            throw new IllegalStateException("soot does not have a points-to analysis.");
        }
        log.debug("Getting soot points-to analysis");
        pointsToAnalysis = Scene.v().getPointsToAnalysis();
        log.debug("soot points-to analysis complete.");
        log.debug("Building soot side-effect analysis.");
        Filter ignoredMethFilter = new Filter(edge ->
                                              {
                                                  SootMethod tgt = edge.tgt();
                                                  // Invalid edge.
                                                  if (tgt == null)
                                                      return false;

                                                  SootClass tgtClass = tgt.getDeclaringClass();
                                                  return !typeHierarchy.canStoreClass(tgtClass, throwableClass) &&
                                                         (!ignListParserInst.hasIgnoreMethod(tgt) || !ignListParserInst.hasIgnoreClass(tgtClass)) &&
                                                          !tgt.isStaticInitializer();
                                              });

        sideEffectAnalysis = new SideEffectAnalysis(pointsToAnalysis, Scene.v().getCallGraph(), ignoredMethFilter);
        log.debug("soot side-effect analysis complete.");
        this.monitorClass = monitorClass;
        this.monitorState = getMemoryLocationsForFields(monitorClass);
        computeMtrTransTrgs();
    }

    private void computeMtrTransTrgs()
    {
        this.mtrTransTrgs = new TransitiveTargets(Scene.v().getCallGraph(), new Filter(edge -> edge.tgt() != null && edge.tgt().getDeclaringClass().equals(monitorClass)));
        // listen for new edges
        this.callGraphListener = Scene.v().getCallGraph().newListener();
    }

    private Set<MemoryLocations> getReachableMonitorState(SootMethod meth)
    {
        return meth.getActiveBody()
                   .getUnits()
                   .stream()
                   .flatMap(u -> getReachableMonitorState(meth, u).stream())
                   .collect(Collectors.toSet());
    }

    private Set<MemoryLocations> getReachableMonitorState(SootMethod inMethod, Unit u)
    {
        Set<Type> mtrRefFlds = monitorClass.getFields()
                                           .stream()
                                           .map(SootField::getType)
                                           .filter(t -> t instanceof RefType)
                                           .collect(Collectors.toSet());

        Set<MemoryLocations> rv = new HashSet<>();
        for (ValueBox vb : u.getUseAndDefBoxes())
        {
            Value v = vb.getValue();
            PointsToSet basePointTo = null;
            if (v instanceof Local)
            {
                basePointTo = pointsToAnalysis.reachingObjects((Local) v);
                rv.add(new SootMemoryLocations(basePointTo));
            }
            else if (v instanceof InstanceFieldRef)
            {
                InstanceFieldRef fldRef = (InstanceFieldRef) v;
                Local base = (Local)fldRef.getBase();
                SootField field = fldRef.getField();

                basePointTo = pointsToAnalysis.reachingObjects(base);
                rv.add(new SootMemoryLocations(basePointTo));
                rv.add(new SootMayRWSetAnalysis.FieldMemoryLocations(field, basePointTo, field.getType() instanceof PrimType));
                rv.addAll(MayRWSetAnalysis.getAllReachableStateFromField(field, basePointTo));
            }
            else if (v instanceof ArrayRef)
            {
                ArrayRef arrRef = (ArrayRef) v;
                Local base = (Local) arrRef.getBase();

                basePointTo = pointsToAnalysis.reachingObjects(base);
                rv.add(new SootMemoryLocations(basePointTo));

                ArrayType type = (ArrayType) base.getType();
                rv.add(new SootMayRWSetAnalysis.ArrayMemoryLocations(basePointTo, type.getElementType() instanceof PrimType));
            }

            Type valTy = v.getType();
            // basePointTo can be null for values like @this
            if (valTy instanceof RefType && basePointTo != null)
            {
                RefType refTy = (RefType)valTy;
                if (mtrRefFlds.contains(refTy))
                {
                    PointsToSet pt = basePointTo;
                    basePointTo.possibleTypes()
                               .stream()
                               .filter(t -> t instanceof RefType)
                               .forEach(t -> rv.addAll(MayRWSetAnalysis.followFieldsFromClass(((RefType)t).getSootClass(), pt)));
                }
            }
         }

        // FIXME: FIX CONTEXT SENSITIVITY ISSUE WITH WSDATALISTENER
        // recompute transitive targets if stale
//        if(callGraphListener.hasNext())
//        {
//            computeMtrTransTrgs();
//        }
//        assert !callGraphListener.hasNext();

        // FIXME: FIX CONTEXT SENSITIVE ISSUE WITH WSDATALISTENER
//        Iterator<MethodOrMethodContext> trgs = mtrTransTrgs.iterator(MethodContext.v(inMethod, u));
        Iterator<MethodOrMethodContext> trgs = mtrTransTrgs.iterator(u);

        while (trgs.hasNext())
        {
            MethodOrMethodContext trg = trgs.next();
            SootMethod mtrMeth = trg.method();
            rv.addAll(getReachableMonitorState(mtrMeth));
        }

        rv.removeIf(locs -> !MemoryLocations.mayIntersectAny(Collections.singleton(locs), monitorState) &&
                            !MemoryLocations.mayIntersectAny(Collections.singleton(locs), getThisMemoryLocs(monitorClass)));
        return rv;
    }

    private Collection<MemoryLocations> getMemoryLocations(RWSet rwSet, boolean isRead)
    {
        Collection<MemoryLocations> memoryLocations = new ArrayList<>();
        // If Unit doesn't read from memory, return empty collection
        if(rwSet == null) return memoryLocations; //empty

        // Add all globals & fields that unit reads from to collection and return
        Stream.concat(rwSet.getGlobals().stream(),
                      rwSet.getFields().stream())
              .forEach(globalOrField -> {
                  List<MemoryLocations> objLocs = new ArrayList<>();
                  if(globalOrField instanceof SootField)
                  {
                      SootField fld = (SootField) globalOrField;
                      boolean isPrimitive = PrimType.class.isAssignableFrom(fld.getType().getClass());

                      PointsToSet ptSet;
                      if (fld.isStatic())
                      {
                          // TODO: handle globals
                          ptSet = isPrimitive ? null : pointsToAnalysis.reachingObjects(fld);
                      }
                      else
                      {
                          PointsToSet basePT = rwSet.getBaseForField(fld);

                          if (!isPrimitive)
                          {
                              if (isRead)
                              {
                                  // Just over-approximate in this case. Not sure why, but side-effect analysis does not propagate context information.
                                  if (!(basePT instanceof PointsToSetInternal))
                                      ptSet = basePT;
                                  else
                                      ptSet = pointsToAnalysis.reachingObjects(basePT, fld);
                              }
                              else
                                  ptSet = basePT;
                          }
                          else
                              ptSet = basePT;
                      }

                      if (ptSet != null)
                          objLocs.add(new FieldMemoryLocations(fld, ptSet, isPrimitive));
                  }
                  else if(globalOrField.equals(PointsToAnalysis.ARRAY_ELEMENTS_NODE))
                  {
                      PointsToSet basePT = rwSet.getBaseForField(globalOrField);

                      Set<Type> possibleTypes = basePT.possibleTypes();
                      Set<Type> primTypes = possibleTypes.stream()
                                                         .filter(t -> t instanceof ArrayType && ((ArrayType)t).baseType instanceof PrimType)
                                                         .collect(Collectors.toSet());

                      boolean isPrimitive = !primTypes.isEmpty();

                      // Sanity check
                      assert !isPrimitive || primTypes.size() == possibleTypes.size();

                      objLocs.add(new ArrayMemoryLocations(basePT, isPrimitive));

                      if (isRead)
                      {
                          if (!(basePT instanceof PointsToSetInternal))
                              objLocs.add(new SootMemoryLocations(basePT));
                          else
                              objLocs.add(new SootMemoryLocations(pointsToAnalysis.reachingObjectsOfArrayElement(basePT)));
                      }
                  }
                  else
                  {
                      throw new IllegalStateException("Expected global/field of type SootField, but instead got type "
                                + globalOrField.getClass());
                  }

                  memoryLocations.addAll(objLocs);
                });

        return memoryLocations;
    }

    @Override
    public Collection<MemoryLocations> readSet(SootMethod sm, Unit ut)
    {
        methodsWithComputedTransTargets.computeIfAbsent(sm, (m) ->{
            sideEffectAnalysis.findNTRWSets(sm);
            return true;
        });

        return readCache.computeIfAbsent(sm, (k) -> new ConcurrentHashMap<>())
                        .computeIfAbsent(ut, (k) -> {
                            // Perform Side Effect Analysis
                            RWSet unitReadSet = sideEffectAnalysis.readSet(sm, (Stmt) ut);
                            // extract memory locations from the rw-set
                            Collection<MemoryLocations> rv = getMemoryLocations(unitReadSet, true);

                            if (sm.getDeclaringClass().equals(monitorClass))
                            {
                                Set<MemoryLocations> reachableMonitorState = getReachableMonitorState(sm, ut);
                                return reachableMonitorState.stream()
                                                            .filter(locs -> MemoryLocations.mayIntersectAny(Collections.singleton(locs), rv))
                                                            .collect(Collectors.toSet());
                            }
                            else
                                return rv;
                        });
    }

    @Override
    public Collection<MemoryLocations> writeSet(SootMethod sm, Unit ut)
    {
        methodsWithComputedTransTargets.computeIfAbsent(sm, (m) ->{
            sideEffectAnalysis.findNTRWSets(sm);
            return true;
        });

        return writeCache.computeIfAbsent(sm, (k) -> new ConcurrentHashMap<>())
                         .computeIfAbsent(ut, (k) -> {
                             // Perform Side Effect Analysis
                             RWSet unitWriteSet = sideEffectAnalysis.writeSet(sm, (Stmt) ut);
                             // extract memory locations from the rw-set
                             Collection<MemoryLocations> rv = getMemoryLocations(unitWriteSet, false);

                             if (sm.getDeclaringClass().equals(monitorClass))
                             {
                                 Set<MemoryLocations> reachableMonitorState = getReachableMonitorState(sm, ut);
                                 return reachableMonitorState.stream()
                                                             .filter(locs -> MemoryLocations.mayIntersectAny(Collections.singleton(locs), rv))
                                                             .collect(Collectors.toSet());
                             }
                             else
                                 return rv;
                         });
    }
}