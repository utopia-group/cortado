package edu.utexas.cs.utopia.cortado.expression.factories;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.BoolConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.IntConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.StoreExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.*;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;

public class CachedExprFactory implements ExprFactory
{
    private final static CachedExprFactory INSTANCE = new CachedExprFactory();

    private final ConcurrentHashMap<Expr, Expr> cache = new ConcurrentHashMap<>();

    private CachedExprFactory()
    {

    }

    public static void clear() {
        CachedExprFactory.getInstance().cache.clear();
    }

    public static CachedExprFactory getInstance()
    {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private <E extends Expr> E getCachedOrSelf(E expr)
    {
        E cached = (E) cache.putIfAbsent(expr, expr);
        return cached != null ? cached : expr;
    }

    @Override
    public BoolConstArrayExpr mkBoolConstArray(ArrType arrType, BoolConstExpr boolConst)
    {
        BoolConstArrayExpr constArrayExpr = new BoolConstArrayExpr(arrType, boolConst);
        return getCachedOrSelf(constArrayExpr);
    }

    @Override
    public IntConstArrayExpr mkIntConstArray(ArrType arrType, IntConstExpr intConst)
    {
        IntConstArrayExpr constArrayExpr = new IntConstArrayExpr(arrType, intConst);
        return getCachedOrSelf(constArrayExpr);
    }

    @Override
    public SelectExpr mkSELECT(Expr arrayExpr, Expr indexExpr)
    {
        SelectExpr selectExpr = new SelectExpr(arrayExpr, indexExpr);
        return getCachedOrSelf(selectExpr);
    }

    @Override
    public StoreExpr mkSTORE(Expr arrayExpr, Expr indexExpr, Expr newVal)
    {
        StoreExpr storeExpr = new StoreExpr(arrayExpr, indexExpr, newVal);
        return getCachedOrSelf(storeExpr);
    }

    @Override
    public AndExpr mkAND(Expr... args)
    {
        AndExpr andExpr = new AndExpr(args);
        return getCachedOrSelf(andExpr);
    }

    @Override
    public EqExpr mkEQ(Expr left, Expr right)
    {
        EqExpr eqExpr = new EqExpr(left, right);
        return getCachedOrSelf(eqExpr);
    }

    @Override
    public ExistsExpr mkEXISTS(Expr body, BoundedVar... quantifiers)
    {
        ExistsExpr existsExpr = new ExistsExpr(body, quantifiers);
        return getCachedOrSelf(existsExpr);
    }

    @Override
    public ForAllExpr mkFORALL(Expr body, BoundedVar... quantifiers)
    {
        ForAllExpr forAllExpr = new ForAllExpr(body, quantifiers);
        return getCachedOrSelf(forAllExpr);
    }

    @Override
    public GreaterEqExpr mkGTEQ(Expr left, Expr right)
    {
        GreaterEqExpr greaterEqExpr = new GreaterEqExpr(left, right);
        return getCachedOrSelf(greaterEqExpr);
    }

    @Override
    public GreaterExpr mkGT(Expr left, Expr right)
    {
        GreaterExpr greaterExpr = new GreaterExpr(left, right);
        return getCachedOrSelf(greaterExpr);
    }

    @Override
    public ImplExpr mkIMPL(Expr antecedent, Expr consequent)
    {
        ImplExpr implExpr = new ImplExpr(antecedent, consequent);
        return getCachedOrSelf(implExpr);
    }

    @Override
    public LessEqExpr mkLTEQ(Expr left, Expr right)
    {
        LessEqExpr lessEqExpr = new LessEqExpr(left, right);
        return getCachedOrSelf(lessEqExpr);
    }

    @Override
    public LessExpr mkLT(Expr left, Expr right)
    {
        LessExpr lessExpr = new LessExpr(left, right);
        return getCachedOrSelf(lessExpr);
    }

    @Override
    public NegExpr mkNEG(Expr arg)
    {
        NegExpr negExpr = new NegExpr(arg);
        return getCachedOrSelf(negExpr);
    }

    @Override
    public OrExpr mkOR(Expr... args)
    {
        OrExpr orExpr = new OrExpr(args);
        return getCachedOrSelf(orExpr);
    }

    @Override
    public BoundedVar mkBNDVAR(FunctionDecl decl)
    {
        BoundedVar boundedVar = new BoundedVar(decl);
        return getCachedOrSelf(boundedVar);
    }

    @Override
    public FunctionApp mkAPP(FunctionDecl decl, Expr... args)
    {
        FunctionApp functionApp = new FunctionApp(decl, args);
        return getCachedOrSelf(functionApp);
    }

    @Override
    public FunctionDecl mkDECL(String name, FunctionType funcType)
    {
        FunctionDecl functionDecl = new FunctionDecl(name, funcType);
        return getCachedOrSelf(functionDecl);
    }

    @Override
    public DivExpr mkDIV(Expr dividend, Expr divisor)
    {
        DivExpr divExpr = new DivExpr(dividend, divisor);
        return getCachedOrSelf(divExpr);
    }

    @Override
    public MinusExpr mkMINUS(Expr... args)
    {
        MinusExpr minusExpr = new MinusExpr(args);
        return getCachedOrSelf(minusExpr);
    }

    @Override
    public ModExpr mkMOD(Expr dividend, Expr divisor)
    {
        ModExpr modExpr = new ModExpr(dividend, divisor);
        return getCachedOrSelf(modExpr);
    }

    @Override
    public MultExpr mkMULT(Expr... args)
    {
        MultExpr multExpr = new MultExpr(args);
        return getCachedOrSelf(multExpr);
    }

    @Override
    public PlusExpr mkPLUS(Expr... args)
    {
        PlusExpr plusExpr = new PlusExpr(args);
        return getCachedOrSelf(plusExpr);
    }

    @Override
    public RemExpr mkREM(Expr dividend, Expr divisor)
    {
        RemExpr remExpr = new RemExpr(dividend, divisor);
        return getCachedOrSelf(remExpr);
    }

    @Override
    public IntConstExpr mkINT(BigInteger val)
    {
        IntConstExpr intConstExpr = new IntConstExpr(val);
        return getCachedOrSelf(intConstExpr);
    }

    @Override
    public StringConstExpr mkSTRING(String val)
    {
        StringConstExpr stringConstExpr = new StringConstExpr(val);
        return getCachedOrSelf(stringConstExpr);
    }

    @Override
    public TrueExpr mkTRUE()
    {
        return TrueExpr.getInstance();
    }

    @Override
    public FalseExpr mkFALSE()
    {
        return FalseExpr.getInstance();
    }
}
