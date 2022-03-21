package edu.utexas.cs.utopia.cortado.expression.factories;

import edu.utexas.cs.utopia.cortado.expression.factories.typefactory.ExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.*;

import java.util.concurrent.ConcurrentHashMap;

public class CachedExprTypeFactory implements ExprTypeFactory
{
    private final static CachedExprTypeFactory INSTANCE = new CachedExprTypeFactory();

    private final ConcurrentHashMap<ExprType, ExprType> cache = new ConcurrentHashMap<>();

    private CachedExprTypeFactory()
    {

    }

    public static void clear() {
        CachedExprTypeFactory.getInstance().cache.clear();
    }

    public static CachedExprTypeFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public UnitType mkUnitType()
    {
        return UnitType.getInstance();
    }

    @Override
    public IntegerType mkIntegerType()
    {
        return IntegerType.getInstance();
    }

    @Override
    public BooleanType mkBooleanType()
    {
        return BooleanType.getInstance();
    }

    @Override
    public StringType mkStringType()
    {
        return StringType.getInstance();
    }

    @Override
    public FunctionType mkFunctionType(ExprType[] domain, ExprType codomain)
    {
        FunctionType funcTy = new FunctionType(domain, codomain);
        cache.putIfAbsent(funcTy, funcTy);
        return (FunctionType) cache.get(funcTy);
    }

    @Override
    public ArrType mkArrayType(ExprType[] domain, ExprType codomain)
    {
        ArrType arrayTy = new ArrType(domain, codomain);
        cache.putIfAbsent(arrayTy, arrayTy);
        return (ArrType) cache.get(arrayTy);
    }
}
