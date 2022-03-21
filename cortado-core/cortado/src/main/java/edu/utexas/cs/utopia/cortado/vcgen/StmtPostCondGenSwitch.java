package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.ConstArrayExpr;
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
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.PostOrderExprVisitor;
import edu.utexas.cs.utopia.cortado.mockclasses.Assertions;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JCastExpr;
import soot.jimple.internal.JNewArrayExpr;
import soot.jimple.internal.JNewMultiArrayExpr;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.expression.ExprUtils.narryOrSelfDistinct;
import static edu.utexas.cs.utopia.cortado.expression.ExprUtils.replace;
import static edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils.*;

// StmtExprSwitch
class StmtPostCondGenSwitch extends AbstractStmtSwitch
{
    private static final Logger log = LoggerFactory.getLogger(StmtPostCondGenSwitch.class);

    private static final ConcurrentHashMap.KeySetView<String, Boolean> ignoredCallWarns = ConcurrentHashMap.newKeySet();

    private static final SootMethod asssumeMeth = Scene.v().getSootClass(Assertions.class.getName()).getMethodByName("assume");

    private final static ExprFactory eFac = CachedExprFactory.getInstance();

    private final static ExprTypeFactory eTyFac = CachedExprTypeFactory.getInstance();

    private final static IgnoreListsParser ignListsInst = IgnoreListsParser.getInstance();

    private final static ModelImporter modelImporterInst = ModelImporter.getInstance();

    private final SootMethod inMethod;

    private final Map<Expr, Integer> skolemMap;

    private final Stack<SootMethod> callStack;

    // Only for havocing write sets.
    private final Set<Expr> freeVarsInPre;

    private Expr result;

    public StmtPostCondGenSwitch(SootMethod inMethod, Map<Expr, Integer> skolemMap, Stack<SootMethod> callStack, Set<Expr> freeVarsInPre)
    {
        assert !NormalizedMethodClone.isNormalizedMethodSignature(inMethod.getSignature());

        this.inMethod = inMethod;
        this.skolemMap = skolemMap;
        this.callStack = callStack;
        this.freeVarsInPre = freeVarsInPre;
    }

    @Override
    public void caseAssignStmt(AssignStmt stmt)
    {
        Value leftOp = stmt.getLeftOp();
        Value rightOp = stmt.getRightOp();

        if (leftOp instanceof FieldRef)
        {
            // Store expression.
            ExprGeneratorSwitch rightExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            rightOp.apply(rightExprGen);
            Expr rightExpr = leftOp.getType()
                                   .equals(IntType.v()) ? rightExprGen.getResultAsInteger() : rightExprGen.getResult();

            FieldRef fldRef = (FieldRef) leftOp;
            SootField fld = fldRef.getField();
            FunctionApp fldArrOrGlobal = getFieldHeapArrayOrGlobal(fld);

            if (fld.isStatic())
            {
                result = eFac.mkEQ(freshSkolem(fldArrOrGlobal, skolemMap), rightExpr);
            }
            else
            {
                InstanceFieldRef instFld = (InstanceFieldRef) fldRef;
                Value baseVal = instFld.getBase();

                ExprGeneratorSwitch baseExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
                baseVal.apply(baseExprGen);
                Expr base = baseExprGen.getResult();

                Expr storeExpr = eFac.mkSTORE(getSkolemOf(fldArrOrGlobal, skolemMap), base, rightExpr);
                result = eFac.mkEQ(freshSkolem(fldArrOrGlobal, skolemMap), storeExpr);
            }
        }
        else if (leftOp instanceof ArrayRef)
        {
            // Array store.
            ExprGeneratorSwitch rightExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            rightOp.apply(rightExprGen);
            Expr rightExpr = leftOp.getType()
                                   .equals(IntType.v()) ? rightExprGen.getResultAsInteger() : rightExprGen.getResult();

            ArrayRef arrRef = (ArrayRef) leftOp;
            FunctionApp heapArray = getHeapArrayForArrayType((ArrayType) arrRef.getBase().getType());
            Expr heapArrSkolemized = skolemize(heapArray, skolemMap);

            ExprGeneratorSwitch baseExprSwitch = new ExprGeneratorSwitch(inMethod, skolemMap);
            ExprGeneratorSwitch idxExprSwitch = new ExprGeneratorSwitch(inMethod, skolemMap);
            arrRef.getBase().apply(baseExprSwitch);
            arrRef.getIndex().apply(idxExprSwitch);

            Expr baseExpr = baseExprSwitch.getResult();
            Expr idxExpr = idxExprSwitch.getResultAsInteger();

            // Update array at index: Heap_Arr[base][idx] = right
            Expr newArrVersion = eFac.mkSTORE(eFac.mkSELECT(heapArrSkolemized, baseExpr), idxExpr, rightExpr);

            // Create a new heap array for this type
            result = eFac.mkEQ(freshSkolem(heapArray, skolemMap), eFac.mkSTORE(heapArrSkolemized, baseExpr, newArrVersion));
        }
        else if (rightOp instanceof AnyNewExpr)
        {
            // Allocation expression.

            AnyNewExpr newExpr = (AnyNewExpr) rightOp;
            Type allocatedTy = newExpr.getType();

            // Create fresh expr for left.
            ExprGeneratorSwitch leftExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            leftOp.apply(leftExprGen);
            Expr leftExpr = freshSkolem(leftExprGen.getResultWithoutSkolem(), skolemMap);

            List<Expr> encodings = new ArrayList<>(getAllocEncoding(allocatedTy, leftExpr));

            if (newExpr instanceof JNewArrayExpr)
            {
                JNewArrayExpr newArrExpr = (JNewArrayExpr) newExpr;
                ArrayType arrTy = newArrExpr.getBaseType().getArrayType();

                ExprGeneratorSwitch sizeGen = new ExprGeneratorSwitch(inMethod, skolemMap);
                newArrExpr.getSize().apply(sizeGen);
                Expr lengthExpr = sizeGen.getResultAsInteger();

                encodings.addAll(getArrayInitEncoding(leftExpr, lengthExpr, arrTy));
            }
            else if (newExpr instanceof JNewMultiArrayExpr)
            {
                // int [][] ar = new int[2][2]
                // ===
                // int [][] ar = new int[2][]
                // for (i \in {0,1}) ar[i] = new int[2]

                // int [][][] ar = new int[2][2][2]
                // ===
                // int [][][] ar = new int[2][][]
                // for (i \in {0,1})
                //    ar[i] = new int[2][]
                //    for(j \in {0,1})
                //       ar[i][j] = new int[2]

                JNewMultiArrayExpr newMultArrExpr = (JNewMultiArrayExpr) newExpr;

                encodings.addAll(getEncodingForNewMultiArray(leftExpr, newMultArrExpr));
            }

            result = eFac.mkAND(encodings.toArray(new Expr[0]));
        }
        else if (rightOp instanceof InvokeExpr)
        {
            // Method invocation.
            InvokeExpr invExpr = (InvokeExpr) rightOp;
            result = encodeInvokeExpr(invExpr, leftOp);
        }
        else if (rightOp instanceof JCastExpr)
        {
            // get expr for LHS
            ExprGeneratorSwitch leftExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            leftOp.apply(leftExprGen);
            Expr left = freshSkolem(leftExprGen.getResultWithoutSkolem(), skolemMap);

            // case of casting to primitive type
            JCastExpr castSootExpr = (JCastExpr) rightOp;
            if(castSootExpr.getCastType() instanceof PrimType)
            {
                if(castSootExpr.getOp().getType() instanceof PrimType)
                {
                    final Expr castResult = castPrimTypeToPrimType(castSootExpr, inMethod, skolemMap);
                    result = eFac.mkEQ(left, castResult);
                    return;
                }
                throw new IllegalArgumentException("cast from non-primitive type to primitive type is not supported");
            }
            else
            {
                // case of casting to reference/array
                ExprGeneratorSwitch genSw = new ExprGeneratorSwitch(inMethod, skolemMap);
                rightOp.apply(genSw);
                Expr castExpr = genSw.getResult();

                ExprGeneratorSwitch rightExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
                ((JCastExpr) rightOp).getOp().apply(rightExprGen);
                Expr right = freshSkolem(rightExprGen.getResultWithoutSkolem(), skolemMap);

                result = eFac.mkEQ(left, right);
                result = eFac.mkAND(result, castExpr);
            }
        }
        else if(rightOp instanceof CmpExpr)
        {
            // get expr for LHS
            ExprGeneratorSwitch leftExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            leftOp.apply(leftExprGen);
            Expr left = freshSkolem(leftExprGen.getResultWithoutSkolem(), skolemMap);

            // get the expr representing the objects cmp is being applied to
            ExprGeneratorSwitch op1Gen = new ExprGeneratorSwitch(inMethod, skolemMap);
            ((CmpExpr) rightOp).getOp1().apply(op1Gen);
            Expr op1Expr = op1Gen.getResult();

            ExprGeneratorSwitch op2Gen = new ExprGeneratorSwitch(inMethod, skolemMap);
            ((CmpExpr) rightOp).getOp2().apply(op2Gen);
            Expr op2Expr = op2Gen.getResultAsInteger();

            // get object constant representing result of CMP
            ExprGeneratorSwitch genSw = new ExprGeneratorSwitch(inMethod, skolemMap);
            rightOp.apply(genSw);
            Expr cmpResult = genSw.getResultAsInteger();

            // build constraints to make sure that CMP result holds correct result
            Expr cmpResultConstraints = eFac.mkOR(
                eFac.mkAND(eFac.mkEQ(cmpResult, eFac.mkINT(BigInteger.ONE)),
                                 eFac.mkGT(op1Expr, op2Expr)),
                      eFac.mkAND(eFac.mkEQ(cmpResult, eFac.mkINT(BigInteger.ZERO)),
                                 eFac.mkEQ(op1Expr, op2Expr)),
                      eFac.mkAND(eFac.mkEQ(cmpResult, eFac.mkINT(BigInteger.valueOf(-1))),
                                 eFac.mkLT(op1Expr, op2Expr))
            );

            result = eFac.mkAND(eFac.mkEQ(left, cmpResult), cmpResultConstraints);
        }
        else
        {
            // Regular assignment.
            encodeAssignment(leftOp, rightOp);
        }
    }

    @Override
    public void caseThrowStmt(ThrowStmt stmt)
    {
        throw new IllegalStateException("Exception flow should be removed before calculating verification conditions");
    }

    @Override
    public void caseIdentityStmt(IdentityStmt stmt)
    {
        encodeAssignment(stmt.getLeftOp(), stmt.getRightOp());
    }

    @Override
    public void caseInvokeStmt(InvokeStmt stmt)
    {
        result = encodeInvokeExpr(stmt.getInvokeExpr(), null);
    }

    @Override
    public void caseNopStmt(NopStmt stmt)
    {
        result = eFac.mkTRUE();
    }

    @Override
    public void defaultCase(Object obj)
    {
        throw new UnsupportedOperationException(obj.getClass() + " is not supported yet (encountered in " + this.inMethod.toString() + ")");
    }

    @Override
    public Expr getResult()
    {
        return result;
    }

    private boolean isRecursiveCall(SootMethod trg)
    {
        return callStack.contains(trg);
    }

    private List<Expr> getAllocEncoding(Type allocatedTy, Expr trg)
    {
        // Get Alloc array.
        FunctionApp allocHeapArr = getAllocHeapArray();
        FunctionApp allocHeapArrSkolem = getSkolemOf(allocHeapArr, skolemMap);

        // Get DType array.
        FunctionApp dTypeHeapArr = getDTypeHeapArray();

        IntConstExpr dTypeVal = eFac.mkINT(BigInteger.valueOf(getTypeId(allocatedTy)));
        return Arrays.asList(// Not previously allocated.
                             eFac.mkNEG(eFac.mkSELECT(allocHeapArrSkolem, trg)),
                             // Now is allocated.
                             eFac.mkEQ(freshSkolem(allocHeapArr, skolemMap),
                                       eFac.mkSTORE(allocHeapArrSkolem, trg, eFac.mkTRUE())),
                             // Assume dynamic type information.
                             eFac.mkEQ(eFac.mkSELECT(dTypeHeapArr, trg), dTypeVal),
                             // Assume allocated object is not null.
                             eFac.mkNEG(eFac.mkEQ(trg, eFac.mkINT(BigInteger.ZERO))));
    }

    private Set<Expr> encodeActualToFormalAssignments(InvokeExpr invExpr, Map<Expr, Integer> skolemMap)
    {
        Set<Expr> assignments = new HashSet<>();

        SootMethodRef trgMethodRef = invExpr.getMethodRef();
        SootMethod originalMethod = NormalizedMethodClone.getOriginalSootMethod(trgMethodRef.getSignature());
        boolean isNormalized = originalMethod != null;

        // That means the method has not been normalized and we have a model.
        if (!isNormalized)
            originalMethod = invExpr.getMethod();

        Jimple j = Jimple.v();

        if (!originalMethod.isStatic())
        {
            ExprGeneratorSwitch baseExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            Value base = ((InstanceInvokeExpr) invExpr).getBase();
            base.apply(baseExprGen);
            Expr baseExpr = baseExprGen.getResult();

            ExprGeneratorSwitch thisExprGen = new ExprGeneratorSwitch(originalMethod, skolemMap);
            ThisRef thisSym = j.newThisRef(originalMethod.getDeclaringClass().getType());
            thisSym.apply(thisExprGen);
            Expr thisExpr = freshSkolem(thisExprGen.getResultWithoutSkolem(), skolemMap);

            assignments.add(eFac.mkEQ(thisExpr, baseExpr));
        }

        List<Value> paramRef = new ArrayList<>();

        List<Type> parameterTypes = !isNormalized ? originalMethod.getParameterTypes() : NormalizedMethodClone.getNormalizedSootMethod(originalMethod.getSignature()).getParameterTypes();
        for (int i = 0; i < parameterTypes.size(); i++)
            paramRef.add(j.newParameterRef(parameterTypes.get(i), i));

        for (int i = 0, e = paramRef.size(); i < e; ++i)
        {
            ExprGeneratorSwitch actualExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            Value actualParam = invExpr.getArg(i);
            actualParam.apply(actualExprGen);
            Expr actualExpr = parameterTypes.get(i).equals(BooleanType.v()) ? actualExprGen.getResult() : actualExprGen.getResultAsInteger();

            ExprGeneratorSwitch formalExprGen = new ExprGeneratorSwitch(originalMethod, skolemMap);
            Value formalParam = paramRef.get(i);
            formalParam.apply(formalExprGen);
            Expr formalExpr = freshSkolem(formalExprGen.getResultWithoutSkolem(), skolemMap);

            assignments.add(eFac.mkEQ(formalExpr, actualExpr));
        }

        return assignments;
    }

    private Expr encodeInvokeExpr(InvokeExpr invExpr, Value lhs)
    {
        // We assume that the body has been de-virtualized.
        // So, we just grab the target of the InvokeExpr

        // IMPORTANT: Only call getMethodRef on invoke expressions after normalization.
        // Calling getMethod triggers Soot's resolution method, which fails for our modified method signatures.
        SootMethodRef trgMethodRef = invExpr.getMethodRef();
        SootMethod originalTrgMeth = null;

        String trgSignature = trgMethodRef.getSignature();

        if (trgSignature.equals(asssumeMeth.getSignature()))
        {
            ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
            invExpr.getArg(0).apply(exprGen);

            return exprGen.getResult();
        }

        // Retrieve original method signature, if any.
        // This check only succeeds for methods with active bodies
        if (NormalizedMethodClone.isNormalizedMethodSignature(trgSignature))
            originalTrgMeth = NormalizedMethodClone.getOriginalSootMethod(trgSignature);

        if (originalTrgMeth == null)
        {
            if (ignoredCallWarns.add(trgSignature))
            {
                log.warn("Ignoring call to method: " + trgSignature + " (no active body)");
            }

            return eFac.mkTRUE();
        }

        // Query models
        if(modelImporterInst.hasMethodModel(originalTrgMeth))
        {
            if (ignoredCallWarns.add(trgSignature))
            {
                log.warn("Using manual implementation: " + trgSignature);
            }

            Set<Expr> encodings = encodeActualToFormalAssignments(invExpr, skolemMap);

            Expr model = modelImporterInst.getModel(originalTrgMeth);

            Expr modelSk = skolemize(model, skolemMap);

            Map<Expr, Expr> primeToUnPrimeFresh = new HashMap<>();
            PostOrderExprVisitor primeFounder = new PostOrderExprVisitor() {
                @Override
                public void visit(FunctionApp e)
                {
                    FunctionDecl decl = e.getDecl();
                    String primedDelcName = decl.getName();
                    if (primedDelcName.endsWith("'"))
                    {
                        FunctionDecl unPrimedDecl = eFac.mkDECL(primedDelcName.substring(0, primedDelcName.length() - 1), decl.getType());
                        List<Expr> args = new ArrayList<>();
                        e.forEach(args::add);
                        primeToUnPrimeFresh.put(e, freshSkolem(eFac.mkAPP(unPrimedDecl, args.toArray(new Expr[0])), skolemMap));
                    }
                }
            };
            modelSk.accept(primeFounder);

            encodings.add(replace(modelSk, primeToUnPrimeFresh));

            if (lhs != null)
            {
                ExprGeneratorSwitch lhsGen = new ExprGeneratorSwitch(inMethod, skolemMap);
                lhs.apply(lhsGen);
                Expr lhsExpr = freshSkolem(lhsGen.getResultWithoutSkolem(), skolemMap);

                List<Expr> retVar = primeToUnPrimeFresh.keySet()
                                                       .stream()
                                                       .filter(e -> e.toString().contains("ret@"))
                                                       .collect(Collectors.toList());

                assert retVar.size() == 1 : "invalid model";

                encodings.add(eFac.mkEQ(lhsExpr, primeToUnPrimeFresh.get(retVar.get(0))));
            }

            return narryOrSelfDistinct(encodings, eFac::mkAND);
        }

        // Query ignore list
        if (ignListsInst.hasIgnoreClass(originalTrgMeth.getDeclaringClass())
            || ignListsInst.hasIgnoreMethod(originalTrgMeth))
        {
            if (ignoredCallWarns.add(trgSignature))
            {
                log.warn("Ignoring call to method: " + trgSignature + " (ignored list)");
            }
            return eFac.mkTRUE();
        }

        if (isRecursiveCall(originalTrgMeth))
        {
            MayRWSetAnalysis mayRwAnalysis = CachedMayRWSetAnalysis.getInstance().getMayRWSetAnalysis();
            mayRwAnalysis.writeSet(originalTrgMeth)
                         .forEach(mLocs -> mLocs.getExprsIn(freeVarsInPre)
                                                .forEach(e -> freshSkolem(e, skolemMap))
            );

            // Havoc lhs if present
            if (lhs != null)
            {
                ExprGeneratorSwitch lhsGen = new ExprGeneratorSwitch(inMethod, skolemMap);
                lhs.apply(lhsGen);
                freshSkolem(lhsGen.getResultWithoutSkolem(), skolemMap);
            }

            return eFac.mkTRUE();
        }
        else
        {
            // IMPORTANT: Do not put any code before this line, this constructor will force the normalization of trgMethod.
            StrongestPostConditionCalculator trgMethSpCalc = new StrongestPostConditionCalculator(originalTrgMeth, callStack);

            Set<Expr> encodings = encodeActualToFormalAssignments(invExpr, skolemMap);
            StrongestPostConditionCalculator.ExprSkolemPair sp = trgMethSpCalc.sp(skolemMap, false);

            assert !callStack.contains(originalTrgMeth);
            encodings.add(sp.cond);

            Map<Expr, Integer> newSkolemMap = sp.skolemMap;
            newSkolemMap.keySet().forEach(e -> skolemMap.put(e, newSkolemMap.get(e)));

            if (lhs != null)
            {
                Body normTrgBody = trgMethSpCalc.normMethod.getNormalizedMethod().getActiveBody();
                ReturnStmt retStmt = (ReturnStmt) findReturnStatement(normTrgBody.getUnits());
                ExprGeneratorSwitch retValGen = new ExprGeneratorSwitch(originalTrgMeth, skolemMap);
                assert retStmt != null;
                retStmt.getOp().apply(retValGen);
                Expr retValExpr = lhs.getType() instanceof BooleanType ? retValGen.getResult() : retValGen.getResultAsInteger();

                ExprGeneratorSwitch lhsGen = new ExprGeneratorSwitch(inMethod, skolemMap);
                lhs.apply(lhsGen);
                Expr lhsExpr = freshSkolem(lhsGen.getResultWithoutSkolem(), skolemMap);

                encodings.add(eFac.mkEQ(lhsExpr, retValExpr));
            }

            return narryOrSelfDistinct(encodings, eFac::mkAND);
        }
    }

    private void encodeAssignment(Value leftOp, Value rightOp)
    {
        ExprGeneratorSwitch rightExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
        rightOp.apply(rightExprGen);
        Expr right = leftOp.getType()
                           .equals(BooleanType.v()) ? rightExprGen.getResult() : rightExprGen.getResultAsInteger();

        ExprGeneratorSwitch leftExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
        leftOp.apply(leftExprGen);
        Expr left = freshSkolem(leftExprGen.getResultWithoutSkolem(), skolemMap);

        result = eFac.mkEQ(left, right);
    }

    private List<Expr> getArrayInitEncoding(Expr heapArrayIdx, Expr lengthExpr, ArrayType arrType)
    {
        Expr zeroConstArray = createZeroConstArray(arrType.getElementType());

        FunctionApp heapArrayForTy = getHeapArrayForArrayType(arrType);
        FunctionApp heapArrayCurr = getSkolemOf(heapArrayForTy, skolemMap);
        Expr freshHeapArray = freshSkolem(heapArrayForTy, skolemMap);

        // Create fresh array and initialize everything to zero.
        Expr freshArray = eFac.mkEQ(freshHeapArray, eFac.mkSTORE(heapArrayCurr, heapArrayIdx, zeroConstArray));

        FunctionApp lengthHeapArr = getLengthHeapArray();
        FunctionApp lengthHeapArrSkolem = getSkolemOf(lengthHeapArr, skolemMap);

        // Assume length of new array.
        Expr length = eFac.mkEQ(freshSkolem(lengthHeapArr, skolemMap), eFac.mkSTORE(lengthHeapArrSkolem, heapArrayIdx, lengthExpr));

        return Arrays.asList(freshArray, length);
    }

    private List<Expr> getEncodingForNewMultiArray(Expr leftExpr, JNewMultiArrayExpr newMultArrExpr)
    {
        List<Expr> encodings = new ArrayList<>();

        int nSizes = newMultArrExpr.getSizeCount();

        assert nSizes > 0 : "no size in multi-dim array allocation";

        List<BoundedVar> bVars = new ArrayList<>();
        List<BoundedVar> allBVars = new ArrayList<>();
        List<Expr> ranges = new ArrayList<>();
        List<Expr> rangesPrimed = new ArrayList<>();

        ArrayType currType = newMultArrExpr.getBaseType();

        Expr symHeapArrTyFresh = skolemize(getHeapArrayForArrayType(currType), skolemMap);
        Expr arrToIndex = eFac.mkSELECT(symHeapArrTyFresh, leftExpr),
                arrToIndexPrimed = eFac.mkSELECT(symHeapArrTyFresh, leftExpr);

        BoundedVar oldObj = getBoundedVar("oldObj", eTyFac.mkIntegerType());

        ExprGeneratorSwitch sizeGen = new ExprGeneratorSwitch(inMethod, skolemMap);
        for (int i = 1; i < nSizes; ++i)
        {
            int prevIndex = i - 1;
            newMultArrExpr.getSize(prevIndex).apply(sizeGen);

            BoundedVar bVar = getBoundedVar("i" + prevIndex, eTyFac.mkIntegerType());
            bVars.add(bVar);

            BoundedVar bVarPrime = getBoundedVar("i'" + prevIndex, eTyFac.mkIntegerType());
            allBVars.add(bVarPrime);
            allBVars.add(bVar);

            ranges.add(eFac.mkAND(eFac.mkGTEQ(bVar, eFac.mkINT(BigInteger.ZERO)),
                                  eFac.mkLT(bVar, sizeGen.getResultAsInteger())));
            rangesPrimed.add(eFac.mkAND(eFac.mkGTEQ(bVarPrime, eFac.mkINT(BigInteger.ZERO)),
                                        eFac.mkLT(bVarPrime, sizeGen.getResultAsInteger())));

            currType = (ArrayType) currType.getElementType();

            Expr memLocs = eFac.mkSELECT(arrToIndex, bVar);
            Expr memLocsPrimed = eFac.mkSELECT(arrToIndexPrimed, bVarPrime);

            FunctionApp heapAllocArray = getAllocHeapArray();
            FunctionApp oldAllocArray = getSkolemOf(heapAllocArray, skolemMap);
            Expr freshAllocArray = freshSkolem(heapAllocArray, skolemMap);

            BoundedVar[] bVarArr = bVars.toArray(new BoundedVar[0]);
            Expr rangeExprs = narryOrSelfDistinct(ranges, eFac::mkAND);

            Expr rangePrimedExprs = narryOrSelfDistinct(rangesPrimed, eFac::mkAND);

            Expr tyIdExpr = eFac.mkINT(BigInteger.valueOf(getTypeId(currType)));

            // Not previously allocated
            encodings.add(eFac.mkFORALL(eFac.mkIMPL(rangeExprs, eFac.mkNEG(eFac.mkSELECT(oldAllocArray, memLocs))), bVarArr));
            // Now allocated, non-null, and assume dynamic type
            encodings.add(eFac.mkFORALL(eFac.mkIMPL(rangeExprs,
                                                    // Now allocated
                                                    eFac.mkAND(eFac.mkSELECT(freshAllocArray, memLocs),
                                                               // Non-null
                                                               eFac.mkNEG(eFac.mkEQ(memLocs, eFac.mkINT(BigInteger.ZERO))),
                                                               // Assume dynamic type
                                                               eFac.mkEQ(eFac.mkSELECT(getDTypeHeapArray(), memLocs), tyIdExpr))),
                                        bVarArr));
            // If previously allocated, it remains allocated
            encodings.add(eFac.mkFORALL(eFac.mkIMPL(eFac.mkSELECT(oldAllocArray, oldObj),
                                                    eFac.mkSELECT(freshAllocArray, oldObj)),
                                        oldObj));
            // All allocated locations are distinct.
            encodings.add(eFac.mkFORALL(eFac.mkIMPL(eFac.mkAND(rangeExprs, rangePrimedExprs,
                                                               eFac.mkNEG(eFac.mkEQ(bVar, bVarPrime))),
                                                    eFac.mkNEG(eFac.mkEQ(memLocs, memLocsPrimed))),
                                        allBVars.toArray(new BoundedVar[0])));

            Expr nextSymHeapArr =  skolemize(getHeapArrayForArrayType(currType), skolemMap);
            arrToIndex = eFac.mkSELECT(nextSymHeapArr, memLocs);
            arrToIndexPrimed = eFac.mkSELECT(nextSymHeapArr, memLocsPrimed);
        }

        // Set everything to zero for
        BoundedVar bVar = getBoundedVar("i" + (nSizes - 1), eTyFac.mkIntegerType());
        bVars.add(bVar);

        newMultArrExpr.getSize(nSizes - 1).apply(sizeGen);
        ranges.add(eFac.mkAND(eFac.mkGTEQ(bVar, eFac.mkINT(BigInteger.ZERO)),
                              eFac.mkLT(bVar, sizeGen.getResultAsInteger())));

        Expr memLocs = eFac.mkSELECT(arrToIndex, bVar);

        encodings.add(eFac.mkFORALL(eFac.mkIMPL(narryOrSelfDistinct(ranges, eFac::mkAND),
                                                eFac.mkEQ(memLocs, getZeroConstForType(currType.getElementType()))),
                                    bVars.toArray(new BoundedVar[0])));

        return encodings;
    }

    private Expr getZeroConstForType(Type ty)
    {
        if (ty.equals(BooleanType.v()))
            return eFac.mkFALSE();

        return eFac.mkINT(BigInteger.ZERO);
    }

    private ConstArrayExpr createZeroConstArray(Type arrBaseType)
    {
        if (arrBaseType.equals(BooleanType.v()))
        {
            ArrType arrTy = eTyFac.mkArrayType(new ExprType[]{eTyFac.mkIntegerType()},
                                               eTyFac.mkBooleanType());
            return eFac.mkBoolConstArray(arrTy, eFac.mkFALSE());
        }
        else
        {
            ArrType arrTy = eTyFac.mkArrayType(new ExprType[]{eTyFac.mkIntegerType()},
                                               eTyFac.mkIntegerType());

            return eFac.mkIntConstArray(arrTy, eFac.mkINT(BigInteger.ZERO));
        }
    }
}
