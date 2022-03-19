package edu.utexas.cs.utopia.cortado.expression.factories.typefactory;

import edu.utexas.cs.utopia.cortado.expression.type.*;

public interface ExprTypeFactory
{
    UnitType mkUnitType();

    IntegerType mkIntegerType();

    BooleanType mkBooleanType();

    StringType mkStringType();

    FunctionType mkFunctionType(ExprType[] domain, ExprType codomain);

    ArrType mkArrayType(ExprType[] domain, ExprType codomain);
}
