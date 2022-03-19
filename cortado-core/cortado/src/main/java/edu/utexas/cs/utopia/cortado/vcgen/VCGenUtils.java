package edu.utexas.cs.utopia.cortado.vcgen;

import com.google.common.collect.ImmutableList;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.IntConstExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.typefactory.ExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.AbstractExprTransformer;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.PostOrderExprVisitor;
import edu.utexas.cs.utopia.cortado.util.naming.VCGenNamingUtils;
import soot.*;
import soot.jimple.*;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class VCGenUtils
{
    private static final ExprFactory exprFactory = CachedExprFactory.getInstance();

    private static final ExprTypeFactory exprTypeFactory = CachedExprTypeFactory.getInstance();

    static final String SKOLEM_SUFFIX_REGEX = "^.*_sk[0-9]*$";

    static final String HEAP_ARRAY_PREFIX = "HEAP@";

    private static final FunctionApp ALLOC_HEAP_ARRAY;

    private static final FunctionApp DTYPE_HEAP_ARRAY;

    private static final FunctionApp LENGTH_HEAP_ARRAY;

    private static final ConcurrentHashMap<Type, Integer> typeIDMap = new ConcurrentHashMap<>();

    private static final AtomicInteger nextTypeId = new AtomicInteger(0);

    static
    {
        // Init Heap Alloc Array
        String funcName = HEAP_ARRAY_PREFIX + "Alloc";
        ArrType allocArTy = exprTypeFactory.mkArrayType(new ExprType[]{exprTypeFactory.mkIntegerType()},
                                                        exprTypeFactory.mkBooleanType());
        ALLOC_HEAP_ARRAY = makeConstant(funcName, allocArTy);

        String dTypeFuncName = HEAP_ARRAY_PREFIX + "DType";
        ArrType dTypeArTy = exprTypeFactory.mkArrayType(new ExprType[]{exprTypeFactory.mkIntegerType()}, exprTypeFactory.mkIntegerType());
        DTYPE_HEAP_ARRAY = makeConstant(dTypeFuncName, dTypeArTy);

        String lengthFuncName = HEAP_ARRAY_PREFIX + "Length";
        ArrType lengthArTy = exprTypeFactory.mkArrayType(new ExprType[]{exprTypeFactory.mkIntegerType()}, exprTypeFactory.mkIntegerType());
        LENGTH_HEAP_ARRAY = makeConstant(lengthFuncName, lengthArTy);
    }

    // Skolemization-related methods.

    static FunctionDecl getDeclToSkolemize(Expr v)
    {
        if (v.getKind() != Expr.ExprKind.FAPP)
            return null;

        FunctionApp fapp = (FunctionApp) v;
        FunctionDecl decl = fapp.getDecl();

        return fapp.getArgNum() == 0 || decl.getName().startsWith(HEAP_ARRAY_PREFIX) ? decl : null;
    }

    static Expr freshSkolem(Expr v, Map<Expr, Integer> skolemMap)
    {
        FunctionDecl varFuncDec = getDeclToSkolemize(v);

        if (varFuncDec == null)
            throw new IllegalArgumentException(v + " must be null-ary function application or a heap object");

        // Create skolemized variable.
        Integer newSkolVal = skolemMap.getOrDefault(v, -1) + 1;
        FunctionApp skolemVar = exprFactory.mkAPP(exprFactory.mkDECL(varFuncDec.getName() + "_sk" + newSkolVal, varFuncDec.getType()));

        // Update skolem map
        skolemMap.put(v, newSkolVal);

        return skolemVar;
    }

    static FunctionApp getSkolemOf(Expr v, Map<Expr, Integer> skolemMap)
    {
        FunctionDecl varFuncDec = getDeclToSkolemize(v);

        if (varFuncDec == null)
            throw new IllegalArgumentException(v + " must be null-ary function application");

        if (!skolemMap.containsKey(v))
            return (FunctionApp) v;

        int skolemVal = skolemMap.get(v);
        String varName = varFuncDec.getName();

        if (varName.matches(SKOLEM_SUFFIX_REGEX))
            throw new IllegalStateException(v + " is already skolemized");

        return exprFactory.mkAPP(exprFactory.mkDECL(varName + "_sk" + skolemVal, varFuncDec.getType()));
    }

    static Expr skolemize(Expr expr, Map<Expr, Integer> skolemMap)
    {
        AbstractExprTransformer skolemizer = new AbstractExprTransformer()
        {
            @Override
            public Expr visit(Expr e)
            {
                if (skolemMap.containsKey(e))
                    return getSkolemOf(e, skolemMap);
                return e;
            }
        };

        return expr.accept(skolemizer);
    }

    static Map<Expr, Integer> calculateSkolemMap(Expr e)
    {
        Map<Expr, Integer> skolemMap = new HashMap<>();

        PostOrderExprVisitor skolemCollector = new PostOrderExprVisitor()
        {
            @Override
            public void visit(FunctionApp e)
            {
                if (e.getArgNum() == 0)
                {
                    FunctionDecl funcDecl = e.getDecl();
                    String varName = funcDecl.getName();

                    if (varName.matches(SKOLEM_SUFFIX_REGEX))
                    {
                        int skolemStartIdx = varName.lastIndexOf("_");
                        String orgVarName = varName.substring(0, skolemStartIdx);
                        int skolemVal = Integer.parseInt(varName.substring(skolemStartIdx + 3));

                        Expr nonSkolemVar = exprFactory.mkAPP(exprFactory.mkDECL(orgVarName, funcDecl.getType()));
                        skolemMap.put(nonSkolemVar, Math.max(skolemMap.getOrDefault(nonSkolemVar, -1), skolemVal));
                    }
                }
            }
        };

        e.accept(skolemCollector);
        return skolemMap;
    }

    static Expr dropSkolems(Expr e)
    {
        AbstractExprTransformer skolemDropper = new AbstractExprTransformer()
        {
            @Override
            public Expr visit(FunctionApp e)
            {
                if (!alreadyVisited(e))
                {
                    Expr newExpr = e;
                    if (e.getArgNum() == 0)
                    {
                        FunctionDecl funcDecl = e.getDecl();
                        String varName = funcDecl.getName();

                        if (varName.matches(SKOLEM_SUFFIX_REGEX))
                        {
                            String orgVarName = varName.substring(0, varName.lastIndexOf("_"));
                            newExpr = exprFactory.mkAPP(exprFactory.mkDECL(orgVarName, funcDecl.getType()));
                        }
                    }

                    cachedResults.put(e, newExpr);
                }

                return cachedResults.get(e);
            }
        };

        return e.accept(skolemDropper);
    }

    static Map<Expr, Integer> copySkolemMap(Map<Expr, Integer> skolemMap)
    {
        return new HashMap<>(skolemMap);
    }

    static Map<Expr, Set<Integer>> findPhiNodes(Set<Map<Expr, Integer>> skolemMaps)
    {
        Map<Expr, Set<Integer>> exprCopies = new HashMap<>();
        Map<Expr, Integer> exprAppearances = new HashMap<>();
        for(Map<Expr, Integer> skolemMap : skolemMaps)
        {
            skolemMap.forEach((e, i) -> {
                exprAppearances.put(e, exprAppearances.getOrDefault(e, 0) + 1);

                if (!exprCopies.containsKey(e))
                    exprCopies.put(e, new HashSet<>());

                exprCopies.get(e).add(i);
            });
        }

        return exprCopies.entrySet()
                         .stream()
                         .filter(entry -> exprAppearances.get(entry.getKey()) < skolemMaps.size() ||
                                          entry.getValue().size() > 1)
                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static Map<Expr, Integer> combineSkolemMaps(Set<Map<Expr, Integer>> skolemMaps, Map<Expr, Set<Integer>> phiNodes)
    {
        Map<Expr, Integer> newSkolemMap = new HashMap<>();
        for (Map<Expr, Integer> skolemMap : skolemMaps)
        {
            skolemMap.forEach((e, i) -> {
                if (!phiNodes.containsKey(e))
                {
                    newSkolemMap.put(e, i);
                }
            });
        }

        phiNodes.forEach((e, set) -> newSkolemMap.put(e, Collections.max(set)));

        return newSkolemMap;
    }

    // Type-related methods.

    @Nonnull
    static ExprType getExprTypeForVarDecl(@Nonnull Type ty)
    {
        VarDeclTypeGeneratorSwitch exprTySw = new VarDeclTypeGeneratorSwitch();
        ty.apply(exprTySw);
        return exprTySw.getResult();
    }

    @Nonnull
    static Expr castPrimTypeToPrimType(@Nonnull CastExpr v, @Nonnull SootMethod inMethod, @Nonnull Map<Expr, Integer> skolemMap)
    {
        ExprGeneratorSwitch opGenSw = new ExprGeneratorSwitch(inMethod, skolemMap);

        assert(v.getCastType() instanceof PrimType);
        assert(v.getOp().getType() instanceof PrimType);
        PrimType castType = (PrimType) v.getCastType(),
                vType = (PrimType) v.getOp().getType();
        v.getOp().apply(opGenSw);
        Expr castResult = opGenSw.getResult();

        if(!castType.equals(vType))
        {
            ImmutableList<PrimType> intTypes = ImmutableList.<PrimType>builder()
                    .add(ByteType.v(), CharType.v(), ShortType.v(), IntType.v(), LongType.v())
                    .build();
            ImmutableList<IntConstExpr> intTypeMaxValues = ImmutableList.<IntConstExpr>builder()
                    .add(new IntConstExpr(BigInteger.valueOf(1L << 7)),
                         new IntConstExpr(BigInteger.valueOf(1L << 7)),
                         new IntConstExpr(BigInteger.valueOf(1L << 15)),
                         new IntConstExpr(BigInteger.valueOf(1L << 31)),
                         new IntConstExpr(BigInteger.valueOf(1L << 63)))
                    .build();
            final CachedExprFactory eFac = CachedExprFactory.getInstance();
            // ensure that both are supported types
            if(!intTypes.contains(castType) && !castType.equals(BooleanType.v()))
            {
                throw new IllegalArgumentException("Unsupported primitive cast-type " + castType);
            }
            if(!intTypes.contains(vType) && !vType.equals(BooleanType.v()))
            {
                throw new IllegalArgumentException("Unsupported primitive-to-primitive cast operand type " + vType);
            }
            // cast to boolean
            if(castType.equals(BooleanType.v()))
            {
                castResult = eFac.mkNEG(eFac.mkEQ(castResult, eFac.mkINT(BigInteger.ZERO)));
            }
            // cast from boolean
            else if(vType.equals(BooleanType.v()))
            {
                castResult = opGenSw.getResultAsInteger();
            }
            // integer types
            else
            {
                assert(intTypes.contains(castType) && intTypes.contains(vType));
                assert(!castType.equals(vType));
                int castTypeIndex = intTypes.indexOf(castType);
                int vTypeIndex = intTypes.indexOf(vType);
                // widening is no-op, so only need to handle narrowing
                if(vTypeIndex > castTypeIndex)
                {
                    IntConstExpr castTypeMaxValue = intTypeMaxValues.get(castTypeIndex);
                    castResult = eFac.mkMOD(castResult, castTypeMaxValue);
                }
            }
        }

        return castResult;
    }

    // convenience utils for building object constants

    /**
     * Convenience function to build/retrieve an {@link Expr}
     * which represents a {@link soot} object of type
     * {@link Type} (see {@link #getExprTypeForVarDecl(Type)} for type conversions
     * between {@link Type} and {@link ExprType}).
     *
     * If an object of the desired name and type already existed
     * in the {@link CachedExprFactory}, it is returned.
     * Otherwise, such an object is created.
     *
     * object constants are represented as applications
     * of arity-0 functions.
     *
     * @param name the name of the returned {@link FunctionDecl}
     * @param sootType the type of the {@link soot} object which
     *                 the returned {@link FunctionDecl} represents
     * @return Application of an arity-0 function with the given name
     *      which returns an {@link Expr} of type {@link ExprType}
     *      compatible with sootType
     */
    static FunctionApp getOrMakeObjectConstant(String name, Type sootType)
    {
        final ExprType exprType = VCGenUtils.getExprTypeForVarDecl(sootType);
        return makeConstant(name, exprType);
    }

    static FunctionApp makeConstant(String name, ExprType exprType)
    {
        final FunctionType objectConstType = exprTypeFactory.mkFunctionType(new ExprType[0], exprType);
        final FunctionDecl functionDecl = exprFactory.mkDECL(name, objectConstType);
        return exprFactory.mkAPP(functionDecl);
    }

    /**
     * Build an object constant represent local in inMethod
     *
     * @param local the local
     * @param inMethod the method containing the local
     * @return an object constant representing the local
     */
    static FunctionApp getOrMakeObjectConstant(Local local, SootMethod inMethod)
    {
        return getOrMakeObjectConstant(
                VCGenNamingUtils.getLocalExprName(local, inMethod),
                local.getType());
    }

    /**
     * Build an object constant represent thisRef. Since @this is read-oly,
     * there is no need to append a method name.
     *
     * @param thisRef the reference to this
     * @return an object constant representing the thisRef
     */
    static FunctionApp getOrMakeObjectConstant(ThisRef thisRef)
    {
        return getOrMakeObjectConstant(thisRef.toString(), thisRef.getType());
    }

    /**
     * Build an object constant represent parameterRef in inMethod
     *
     * @param parameterRef the parameter reference
     * @param inMethod the method containing the parameterRef
     * @return an object constant representing the parameterRef
     */
    static FunctionApp getOrMakeObjectConstant(ParameterRef parameterRef, SootMethod inMethod)
    {
        return getOrMakeObjectConstant(
                VCGenNamingUtils.getParameterRefExprName(parameterRef, inMethod),
                parameterRef.getType());
    }

    /**
     * Build an object constant represent thisRef of sootClass.
     *
     * @param sootClass the Soot Class
     * @return an object constant representing the thisRef
     */
    static FunctionApp getThisConstantForClass(SootClass sootClass)
    {
        return getOrMakeObjectConstant(new ThisRef(sootClass.getType()));
    }

    static BoundedVar getBoundedVar(String name, ExprType ty)
    {
        return exprFactory.mkBNDVAR(exprFactory.mkDECL(name, exprTypeFactory.mkFunctionType(new ExprType[0], ty)));
    }

    // Symbolic heap arrays.

    static int getTypeId(Type t)
    {
        typeIDMap.putIfAbsent(t, nextTypeId.getAndIncrement());
        return typeIDMap.get(t);
    }

    static FunctionApp getAllocHeapArray()
    {
        return ALLOC_HEAP_ARRAY;
    }

    static FunctionApp getDTypeHeapArray()
    {
        return DTYPE_HEAP_ARRAY;
    }

    static FunctionApp getLengthHeapArray()
    {
        return LENGTH_HEAP_ARRAY;
    }

    public static FunctionApp getFieldHeapArrayOrGlobal(SootField field)
    {
        String fldSignature = field.getSignature();
        Type fieldTy = field.getType();

        VarDeclTypeGeneratorSwitch tyGen = new VarDeclTypeGeneratorSwitch();
        fieldTy.apply(tyGen);

        ExprType fldExprTy = tyGen.getResult();
        if (field.isStatic())
        {
            return makeConstant(fldSignature, fldExprTy);
        }
        else
        {
            ArrType heapArrTy = exprTypeFactory.mkArrayType(new ExprType[]{exprTypeFactory.mkIntegerType()}, fldExprTy);
            return makeConstant(HEAP_ARRAY_PREFIX + fldSignature, heapArrTy);
        }
    }

    public static FunctionApp getHeapArrayForArrayType(ArrayType arrayType)
    {
        String declName = HEAP_ARRAY_PREFIX + "Array_" + arrayType.baseType.toString() + "_NDim_" + arrayType.numDimensions;
        edu.utexas.cs.utopia.cortado.expression.type.IntegerType integerTy = exprTypeFactory.mkIntegerType();
        if (arrayType.numDimensions == 1)
        {
            VarDeclTypeGeneratorSwitch baseTyGen = new VarDeclTypeGeneratorSwitch();
            arrayType.baseType.apply(baseTyGen);

            ArrType heapArrTy = exprTypeFactory.mkArrayType(new ExprType[]{integerTy},
                                                            exprTypeFactory.mkArrayType(new ExprType[]{integerTy}, baseTyGen.getResult()));
            return makeConstant(declName, heapArrTy);
        }
        else
        {
            ArrType heapArrTy = exprTypeFactory.mkArrayType(new ExprType[]{integerTy},
                                                            exprTypeFactory.mkArrayType(new ExprType[]{integerTy}, integerTy));
            return makeConstant(declName, heapArrTy);
        }
    }

    public static boolean isSymbolicHeapArrayForArrayType(FunctionApp fapp)
    {
        return fapp.getDecl().getName().startsWith(HEAP_ARRAY_PREFIX + "Array_");
    }

    static Unit findReturnStatement(UnitPatchingChain units)
    {
        for (Unit u : units)
        {
            if (u instanceof ReturnStmt || u instanceof ReturnVoidStmt)
                return u;
        }

        return null;
    }

    /**
     * Returns a list of all the subclasses of a particular local
     * in reverse-topological order. If {@link Local} l is not
     * a class object, returns null. Utilizes {@link FastHierarchy}
     * to find all subclasses.
     *
     * @param l     {@link Local} to get all subclasses of
     * @return      list of {@link SootClass} subclasses in topological order
     */
    public static List<SootClass> getTopologicalSortedSubclassesOf(Local l) {
        if(!(l.getType() instanceof RefType)) {
            // Local l is not a class object, so return empty list
            return null;
        }

        // Get associated {@link SootClass}
        SootClass localClass = ((RefType)l.getType()).getSootClass();

        List<SootClass> allSubclasses;
        if (Scene.v().hasPointsToAnalysis())
        {
            PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();
            allSubclasses = pointsToAnalysis.reachingObjects(l)
                                            .possibleTypes()
                                            .stream()
                                            .map(t -> ((RefType)t).getSootClass())
                                            .collect(Collectors.toList());
        }
        else
        {
            // Get all subclasses of ```localClass```
            allSubclasses = getAllSubclassesOf(localClass);
            allSubclasses.add(localClass);
        }

        return classesInReverseTopologicalOrder(allSubclasses);
    }

    public static List<SootClass> classesInReverseTopologicalOrder(Collection<SootClass> classes)
    {
        Map<SootClass, Collection<SootClass>> adjList = getAdjListForClasses(classes);

        // Topological sort the list of subclasses
        Map<SootClass, Integer> topoMap = reverseTopologicalSortMap(adjList);
        return classes.stream()
                      .sorted((c1, c2) -> topoMap.get(c2) - topoMap.get(c1))
                      .collect(Collectors.toList());
    }

    private static Map<SootClass, Collection<SootClass>> getAdjListForClasses(Collection<SootClass> allSubclasses)
    {
        Map<SootClass, Collection<SootClass>> adjList = new HashMap<>();

        // Construct adjacency list for topological sort
        FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
        for(SootClass sc : allSubclasses) {
            Collection<SootClass> list = fh.getSubclassesOf(sc);
            adjList.put(sc, list);
        }
        return adjList;
    }

    /**
     * Method that utilizes {@link FastHierarchy} to return all
     * subclasses of input {@link SootClass}. Used as subprocedure
     * in {@link #getTopologicalSortedSubclassesOf(Local)}
     * 
     * @param root      {@link SootClass} to find all subclasses of
     * @return          unordered list of {@link SootClass} subclasses
     */
    static List<SootClass> getAllSubclassesOf (SootClass root) {
        // Get list of possible target methods using class hierarchy
        FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();

        ArrayList<SootClass> subclassesOf = new ArrayList<>();
        // Since fh.getSubclassOf(class) only get immediate children,
        // need to recursively move down the tree
        ArrayList<SootClass> needsToVisit = new ArrayList<>(fh.getSubclassesOf(root));

        while(!needsToVisit.isEmpty()) {
            SootClass curr = needsToVisit.get(0);
            subclassesOf.add(curr);
            needsToVisit.remove(curr);
            needsToVisit.addAll(fh.getSubclassesOf(curr).stream()
                    .filter(u -> needsToVisit.contains(u) || subclassesOf.contains(u))
                    .collect(Collectors.toList()));

        }

        return subclassesOf;
    }

    /**
     * Function that takes in an adjacency list of the Class Hierarchy
     * (represented as a map: class --> list of subclasses) and returns
     * the Class Hierarchy in reverse topological order. Calls {@link #recursiveTopoSortMap}
     *
     * For example, if we define class A, defined class B and C to extend A,
     * and class D extends B, then a possible reverse topological sort would
     * give: [D, B, C, A].
     *
     *
     * @param adjList   the Class Hierarchy, represented as a map: class --> subclasses
     * @return          a map that gives the reverse topological sort by ordering the classes
     */
    static Map<SootClass, Integer> reverseTopologicalSortMap(Map<SootClass, Collection<SootClass>> adjList) {
        Map<SootClass, Boolean> visited = new HashMap<>();
        Map<SootClass, Integer> topoSortedList = new HashMap<>();

        for(SootClass sc : adjList.keySet()) {
            visited.put(sc, false);
        }

        for(SootClass currClass : adjList.keySet()) {
            if(!visited.get(currClass)) {
                recursiveTopoSortMap(adjList, currClass, visited, topoSortedList);
            }
        }

        return topoSortedList;
    }

    /**
     * Helper recursive function to compute (reverse) topological sort 
     * in {@link #reverseTopologicalSortMap}
     *
     * @param adjList           map representing Call Hierarchy: class --> subclasses
     * @param currClass         the current SootClass being topologically sorted
     * @param visited           boolean array storing visited information in DFS
     * @param topoSortedList    the results from topological sort
     */
    static private void recursiveTopoSortMap(Map<SootClass, Collection<SootClass>> adjList,
                                   SootClass currClass,
                                   Map<SootClass, Boolean> visited,
                                   Map<SootClass, Integer> topoSortedList) {
        visited.replace(currClass, true);

        for(SootClass neighbor : adjList.getOrDefault(currClass, Collections.emptySet())) {
            if(!visited.getOrDefault(neighbor, false)) {
                recursiveTopoSortMap(adjList, neighbor, visited, topoSortedList);
            }
        }

        // gives an integer ordering associated with SootClasses
        topoSortedList.put(currClass, topoSortedList.size());
    }
}

class VarDeclTypeGeneratorSwitch extends TypeSwitch
{
    protected static final ExprTypeFactory exprTypeFactory = CachedExprTypeFactory.getInstance();

    protected ExprType result;

    @Override
    public void caseArrayType(ArrayType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseBooleanType(BooleanType t)
    {
        result = exprTypeFactory.mkBooleanType();
    }

    @Override
    public void caseByteType(ByteType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseCharType(CharType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseDoubleType(DoubleType t)
    {
        // We will just havoc non-integer variables for now.
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseFloatType(FloatType t)
    {
        // We will just havoc non-integer variables for now.
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseIntType(IntType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseLongType(LongType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseRefType(RefType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseShortType(ShortType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void caseVoidType(VoidType t)
    {
        result = exprTypeFactory.mkUnitType();
    }

    @Override
    public void caseNullType(NullType t)
    {
        result = exprTypeFactory.mkIntegerType();
    }

    @Override
    public void defaultCase(Type t)
    {
        throw new UnsupportedOperationException(t.getClass().getName() + " is not supported");
    }

    @Override
    public ExprType getResult()
    {
        return result;
    }
}
