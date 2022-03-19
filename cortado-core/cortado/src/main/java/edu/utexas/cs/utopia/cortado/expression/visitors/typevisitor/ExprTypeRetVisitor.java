package edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor;

import edu.utexas.cs.utopia.cortado.expression.type.*;

public interface ExprTypeRetVisitor<R>
{
    R visit(ArrType t);

    R visit(BooleanType t);

    R visit(FunctionType t);

    R visit(IntegerType t);

    R visit(StringType t);

    R visit(UnitType t);
}
