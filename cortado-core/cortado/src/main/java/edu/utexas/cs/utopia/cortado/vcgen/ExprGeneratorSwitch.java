package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.BoolConstExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.IntConstExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.typefactory.ExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;
import edu.utexas.cs.utopia.cortado.expression.type.IntegerType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.AbstractExprTransformer;
import edu.utexas.cs.utopia.cortado.util.soot.SootTypeUtils;
import soot.*;
import soot.jimple.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils.*;

// FIXME: Add an argument for the expected type and remove the getResultAs<Type> methods
/**
 * Converts soot expressions to our symbolic IR.
 */
class ExprGeneratorSwitch extends AbstractJimpleValueSwitch
{
    private final static ExprFactory eFac = CachedExprFactory.getInstance();

    private static final ExprTypeFactory eTyFac = CachedExprTypeFactory.getInstance();

    private final SootMethod inMethod;

    private final Map<Expr, Integer> skolemMap;

    private Expr result;

    ExprGeneratorSwitch(SootMethod inMethod, Map<Expr, Integer> skolemMap)
    {
        assert !NormalizedMethodClone.isNormalizedMethodSignature(inMethod.getSignature());

        this.inMethod = inMethod;
        this.skolemMap = skolemMap;
    }

    public ExprGeneratorSwitch(SootMethod inMethod)
    {
        this(inMethod, new HashMap<>());
    }

    @Override
    public Expr getResult()
    {
        AbstractExprTransformer skolemize = new AbstractExprTransformer()
        {
            @Override
            public Expr visit(FunctionApp e)
            {
                FunctionDecl funcDecl = getDeclToSkolemize(e);
                return funcDecl == null ? e : getSkolemOf(e, skolemMap);
            }
        };

        return result.accept(skolemize);
    }

    public Expr getResultWithoutSkolem()
    {
        return result;
    }

    public Expr getResultAsInteger()
    {
        return getResultAsInteger(true);
    }

    public Expr getResultAsInteger(boolean includeSkolem)
    {
        if (result.getType() == eTyFac.mkIntegerType())
            return includeSkolem ? getResult() : result;

        Expr.ExprKind eKind = result.getKind();
        if (eKind != Expr.ExprKind.TRUE && eKind != Expr.ExprKind.FALSE)
            throw new IllegalStateException("Only boolean constants can get converted to integers");

        return ExprUtils.boolToInt((BoolConstExpr) result);
    }

    static class ExprOperands
    {
        Expr left;
        Expr right;

        public ExprOperands(Expr left, Expr right)
        {
            this.left = left;
            this.right = right;
        }
    }

    static ExprOperands adjustOperandsForEquality(Expr left, Expr right)
    {
        IntegerType intTy = eTyFac.mkIntegerType();
        if (left.getType().equals(intTy) && ExprUtils.isBoolConst(right))
        {
            right = ExprUtils.boolToInt((BoolConstExpr) right);
        }
        else if (right.getType().equals(intTy) && ExprUtils.isBoolConst(left))
        {
            left = ExprUtils.boolToInt((BoolConstExpr) left);
        }

        return new ExprOperands(left, right);
    }

    /**
     * Handles all binary operators and the fact that soot represents booleans with integer values.
     *
     * @param v             Binary expression.
     * @param expectsIntOps Whether it expects integer operands. If false, that means operands must booleans
     * @return The symbolic expression in our IR.
     */
    private BinOpExprOperands handleBinOp(BinopExpr v, boolean expectsIntOps)
    {
        ExprGeneratorSwitch expSw = new ExprGeneratorSwitch(inMethod, skolemMap);
        v.getOp1().apply(expSw);
        Expr left = expSw.getResult();

        expSw = new ExprGeneratorSwitch(inMethod, skolemMap);
        v.getOp2().apply(expSw);
        Expr right = expSw.getResult();

        ExprOperands adjustedOps = adjustOperandsForEquality(left, right);
        left = adjustedOps.left;
        right = adjustedOps.right;

        if (expectsIntOps && ExprUtils.isBoolConst(right))
        {
            right = ExprUtils.boolToInt((BoolConstExpr) right);
        }

        if (expectsIntOps && ExprUtils.isBoolConst(left))
        {
            left = ExprUtils.boolToInt((BoolConstExpr) left);
        }

        return new BinOpExprOperands(left, right);
    }

    // TODO: add converters for rest of bin operators.
    @Override
    public void caseAddExpr(AddExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkPLUS(operands.left, operands.right);
    }

    @Override
    public void caseDivExpr(DivExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkDIV(operands.left, operands.right);
    }

    @Override
    public void caseSubExpr(SubExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkMINUS(operands.left, operands.right);
    }

    @Override
    public void caseEqExpr(EqExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, false);
        result = eFac.mkEQ(operands.left, operands.right);
    }

    @Override
    public void caseGeExpr(GeExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkGTEQ(operands.left, operands.right);
    }

    @Override
    public void caseGtExpr(GtExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkGT(operands.left, operands.right);
    }

    @Override
    public void caseLeExpr(LeExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkLTEQ(operands.left, operands.right);
    }

    @Override
    public void caseLtExpr(LtExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkLT(operands.left, operands.right);
    }

    /**
     * Just sets result to a fresh object constant
     */
    @Override
    public void caseCmpExpr(CmpExpr v)
    {
        // get a fresh constant to store the result
        String freshNameBase = "cortado$ExprGeneratorSwitch$tmpVar";
        int varSuffix = 0;
        FunctionApp freshConst;
        do
        {
            freshConst = makeConstant(freshNameBase + varSuffix, eTyFac.mkIntegerType());
            varSuffix++;
        }
        while(skolemMap.containsKey(freshConst));
        result = freshConst;
    }

    @Override
    public void caseRemExpr(RemExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkREM(operands.left, operands.right);
    }

    @Override
    public void caseMulExpr(MulExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = eFac.mkMULT(operands.left, operands.right);
    }

    @Override
    public void caseNeExpr(NeExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, false);
        result = eFac.mkNEG(eFac.mkEQ(operands.left, operands.right));
    }

    @Override
    public void caseNegExpr(NegExpr v)
    {
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(inMethod, skolemMap);
        v.getOp().apply(exprSwitch);

        if (exprSwitch.result.getType() == eTyFac.mkIntegerType())
            result = eFac.mkMULT(eFac.mkINT(BigInteger.valueOf(-1)), exprSwitch.result);
        else
            result = eFac.mkNEG(exprSwitch.result);
    }

    @Override
    public void caseArrayRef(ArrayRef v)
    {
        ExprGeneratorSwitch baseExprSwitch = new ExprGeneratorSwitch(inMethod, skolemMap);
        ExprGeneratorSwitch idxExprSwitch = new ExprGeneratorSwitch(inMethod, skolemMap);
        v.getBase().apply(baseExprSwitch);
        v.getIndex().apply(idxExprSwitch);

        SelectExpr arrRef = eFac.mkSELECT(getHeapArrayForArrayType((ArrayType) v.getBase().getType()),
                                          baseExprSwitch.result);
        result = eFac.mkSELECT(arrRef, idxExprSwitch.getResultAsInteger(false));
    }

    @Override
    public void caseLocal(Local v)
    {
        result = VCGenUtils.getOrMakeObjectConstant(v, inMethod);
    }

    @Override
    public void caseParameterRef(ParameterRef v)
    {
        result = VCGenUtils.getOrMakeObjectConstant(v, inMethod);
    }

    @Override
    public void caseThisRef(ThisRef v)
    {
        result = VCGenUtils.getOrMakeObjectConstant(v);
    }

    @Override
    public void caseInstanceFieldRef(InstanceFieldRef v)
    {
        Value base = v.getBase();
        ExprGeneratorSwitch baseExprGen = new ExprGeneratorSwitch(inMethod, skolemMap);
        base.apply(baseExprGen);

        result = eFac.mkSELECT(getFieldHeapArrayOrGlobal(v.getField()), baseExprGen.result);
    }

    @Override
    public void caseStaticFieldRef(StaticFieldRef v)
    {
        result = getFieldHeapArrayOrGlobal(v.getField());
    }

    @Override
    public void caseIntConstant(IntConstant v)
    {
        if (v.value == 0)
            result = eFac.mkFALSE();
        else if (v.value == 1)
            result = eFac.mkTRUE();
        else
            result = eFac.mkINT(BigInteger.valueOf(v.value));
    }

    @Override
    public void caseLongConstant(LongConstant v)
    {
        if (v.value == 0)
        {
            result = eFac.mkFALSE();
        }
        else if (v.value == 1)
        {
            result = eFac.mkTRUE();
        }
        else
        {
            result = eFac.mkINT(BigInteger.valueOf(v.value));
        }
    }

    @Override
    public void caseNullConstant(NullConstant v)
    {
        result = eFac.mkINT(BigInteger.valueOf(0));
    }

    private Expr getInstanceOfResult(Type checkType, Value val)
    {
        Expr toReturn;

        ExprGeneratorSwitch opGenSw = new ExprGeneratorSwitch(inMethod, skolemMap);
        val.apply(opGenSw);

        FunctionApp dTypeAr = getDTypeHeapArray();

        // get list of subclasses, and take disjunction to account for instanceof
        SootClass checkTypeClass = ((RefType) checkType).getSootClass();
        List<SootClass> subclasses = VCGenUtils.getAllSubclassesOf(checkTypeClass);
        toReturn = eFac.mkEQ(eFac.mkSELECT(dTypeAr, opGenSw.getResultWithoutSkolem()),
                eFac.mkINT(BigInteger.valueOf(getTypeId(checkType))));

        if(!subclasses.isEmpty()) {
            List<Expr> clauses = subclasses.stream().map(sc ->
                    eFac.mkEQ(eFac.mkSELECT(dTypeAr, opGenSw.getResultWithoutSkolem()),
                            eFac.mkINT(BigInteger.valueOf(getTypeId(sc.getType())))))
                    .collect(Collectors.toList());
            clauses.add(toReturn);
            toReturn = eFac.mkOR(clauses.toArray(new Expr[0]));
        }
        return toReturn;
    }

    @Override
    public void caseInstanceOfExpr(InstanceOfExpr v)
    {
        if(!(v.getCheckType() instanceof RefType || v.getCheckType() instanceof ArrayType)) {
            throw new IllegalStateException("instanceOfExpr not of type RefType");
        }

        if(v.getCheckType() instanceof ArrayType)
        {
            ArrayType checkType = (ArrayType) v.getCheckType();
            if(! (v.getOp().getType() instanceof ArrayType))
            {
                result = eFac.mkFALSE();
            }
            else if(v.getOp() instanceof ArrayRef)
            {
                final ArrayRef arrayRef = (ArrayRef) v.getOp();
                final ArrayType opType = (ArrayType) arrayRef.getType();
                if(opType.getElementType() instanceof PrimType
                        && checkType.getElementType() instanceof PrimType
                        && checkType.getElementType().equals(opType.getElementType()))
                {
                    result = eFac.mkTRUE();
                }
                else if(opType.getElementType() instanceof PrimType || checkType.getElementType() instanceof PrimType)
                {
                    result = eFac.mkFALSE();
                }
                else
                {
                    result = getInstanceOfResult(checkType.getElementType(), arrayRef.getBase());
                }
            }
            else
            {
                throw new IllegalStateException("Only ArrayRefs are currently supported for instanceof checks");
            }
        }
        else
        {
            result = getInstanceOfResult(v.getCheckType(), v.getOp());
        }
    }

    @Override
    public void caseCastExpr(CastExpr v)
    {
        ExprGeneratorSwitch opGenSw = new ExprGeneratorSwitch(inMethod, skolemMap);
        if(!(v.getCastType() instanceof RefType) && !(v.getCastType() instanceof ArrayType)) {
            throw new IllegalStateException("castExpr not of type RefType or ArrayType");
        }

        v.getOp().apply(opGenSw);

        FunctionApp dTypeAr = getDTypeHeapArray();

        if(v.getCastType() instanceof RefType) {
            result = getInstanceOfResult(v.getCastType(), v.getOp());
        } else {
            // array type
            result = eFac.mkEQ(eFac.mkSELECT(dTypeAr, opGenSw.getResultWithoutSkolem()),
                    eFac.mkINT(BigInteger.valueOf(getTypeId(v.getCastType()))));
        }
    }

    @Override
    public void caseLengthExpr(LengthExpr v)
    {
        FunctionApp lengthHeapArr = getLengthHeapArray();

        ExprGeneratorSwitch opGen = new ExprGeneratorSwitch(inMethod, skolemMap);
        v.getOp().apply(opGen);

        result = eFac.mkSELECT(lengthHeapArr, opGen.getResultWithoutSkolem());
    }

    @Override
    public void caseStringConstant(StringConstant v)
    {
        result = eFac.mkINT(BigInteger.valueOf(v.value.hashCode()));
    }

    // bitwise AND
    @Override
    public void caseAndExpr(AndExpr v)
    {
        final Type op1Type = v.getOp1().getType();
        final Type op2Type = v.getOp2().getType();
        if(!(SootTypeUtils.isIntegerType(op1Type) && SootTypeUtils.isIntegerType(op2Type)))
        {
            throw new IllegalArgumentException("Expected op1 and op2 to be integer types");
        }
        boolean op1IsBoolean = op1Type.equals(BooleanType.v()),
                op2IsBoolean = op2Type.equals(BooleanType.v()),
                expectsIntOps = !op1IsBoolean || !op2IsBoolean;
        BinOpExprOperands operands = handleBinOp(v, expectsIntOps);

        if(expectsIntOps)
        {
            final FunctionType bitwiseAndFunctionType = eTyFac.mkFunctionType(new ExprType[]{eTyFac.mkIntegerType(), eTyFac.mkIntegerType()}, eTyFac.mkIntegerType());
            FunctionDecl bitwiseAndUninterpreted = eFac.mkDECL("bitwiseAnd", bitwiseAndFunctionType);
            result = eFac.mkAPP(bitwiseAndUninterpreted, operands.left, operands.right);
        }
        else
        {
            result = eFac.mkAND(operands.left, operands.right);
        }
    }

    // bitwise OR
    @Override
    public void caseOrExpr(OrExpr v)
    {
        final Type op1Type = v.getOp1().getType();
        final Type op2Type = v.getOp2().getType();
        if(!(SootTypeUtils.isIntegerType(op1Type) && SootTypeUtils.isIntegerType(op2Type)))
        {
            throw new IllegalArgumentException("Expected op1 and op2 to be integer types");
        }
        boolean op1IsBoolean = v.getOp1().getType().equals(BooleanType.v()),
                op2IsBoolean = v.getOp2().getType().equals(BooleanType.v()),
                expectsIntOps = !op1IsBoolean || !op2IsBoolean;
        BinOpExprOperands operands = handleBinOp(v, expectsIntOps);

        if(expectsIntOps)
        {
            final FunctionType bitwiseOrFunctionType = eTyFac.mkFunctionType(new ExprType[]{eTyFac.mkIntegerType(), eTyFac.mkIntegerType()}, eTyFac.mkIntegerType());
            FunctionDecl bitwiseOrUninterpreted = eFac.mkDECL("bitwiseOr", bitwiseOrFunctionType);
            result = eFac.mkAPP(bitwiseOrUninterpreted, operands.left, operands.right);
        }
        else
        {
            result = eFac.mkOR(operands.left, operands.right);
        }
    }

    // bitwise xOR
    @Override
    public void caseXorExpr(XorExpr v)
    {
        final Type op1Type = v.getOp1().getType();
        final Type op2Type = v.getOp2().getType();
        if(!(SootTypeUtils.isIntegerType(op1Type) && SootTypeUtils.isIntegerType(op2Type)))
        {
            throw new IllegalArgumentException("Expected op1 and op2 to be integer types");
        }
        boolean op1IsBoolean = v.getOp1().getType().equals(BooleanType.v()),
                op2IsBoolean = v.getOp2().getType().equals(BooleanType.v()),
                expectsIntOps = !op1IsBoolean || !op2IsBoolean;
        BinOpExprOperands operands = handleBinOp(v, expectsIntOps);

        if(expectsIntOps)
        {
            final FunctionType bitwiseXorFunctionType = eTyFac.mkFunctionType(new ExprType[]{eTyFac.mkIntegerType(), eTyFac.mkIntegerType()}, eTyFac.mkIntegerType());
            FunctionDecl bitwiseXorUninterpreted = eFac.mkDECL("bitwiseXor", bitwiseXorFunctionType);
            result = eFac.mkAPP(bitwiseXorUninterpreted, operands.left, operands.right);
        }
        else
        {
            result = eFac.mkOR(eFac.mkAND(operands.left, eFac.mkNEG(operands.right)),
                               eFac.mkAND(eFac.mkNEG(operands.left), operands.right));
        }
    }

    @Override
    public void caseShlExpr(ShlExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);

        if (operands.right instanceof IntConstExpr)
        {
            BigInteger c = ((IntConstExpr) operands.right).getVal();
            result = eFac.mkMULT(operands.left, eFac.mkINT(new BigInteger("2").pow(c.intValue())));
        }
        else
        {
            FunctionDecl shlUninterpreted = eFac.mkDECL("shl", eTyFac.mkFunctionType(new ExprType[]{eTyFac.mkIntegerType(), eTyFac.mkIntegerType()}, eTyFac.mkIntegerType()));
            result = eFac.mkAPP(shlUninterpreted, operands.left, operands.right);
        }
    }

    @Override
    public void caseShrExpr(ShrExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);

        if (operands.right instanceof IntConstExpr)
        {
            BigInteger c = ((IntConstExpr) operands.right).getVal();
            result = eFac.mkDIV(operands.left, eFac.mkINT(new BigInteger("2").pow(c.intValue())));
        }
        else
        {
            FunctionDecl shlUninterpreted = eFac.mkDECL("shr", eTyFac.mkFunctionType(new ExprType[]{eTyFac.mkIntegerType(), eTyFac.mkIntegerType()}, eTyFac.mkIntegerType()));
            result = eFac.mkAPP(shlUninterpreted, operands.left, operands.right);
        }
    }

    @Override
    public void caseUshrExpr(UshrExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        FunctionDecl ushrUninterpreted = eFac.mkDECL("ushr", eTyFac.mkFunctionType(new ExprType[]{eTyFac.mkIntegerType(), eTyFac.mkIntegerType()}, eTyFac.mkIntegerType()));
        result = eFac.mkAPP(ushrUninterpreted, operands.left, operands.right);
    }

    @Override
    public void caseClassConstant(ClassConstant v)
    {
        result = eFac.mkINT(BigInteger.valueOf(v.hashCode()));
    }

    @Override
    public void defaultCase(Object v)
    {
        throw new UnsupportedOperationException(v.getClass().getName() + " is not yet supported");
    }

    private static class BinOpExprOperands
    {
        Expr left, right;

        BinOpExprOperands(Expr left, Expr right)
        {
            this.left = left;
            this.right = right;
        }
    }
}
