package edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor;

import edu.utexas.cs.utopia.cortado.expression.type.*;

public interface ExprTypeVisitor
{
    void visit(ArrType t);

    void visit(BooleanType t);

    void visit(FunctionType t);

    void visit(IntegerType t);

    void visit(StringType t);

    void visit(UnitType t);
}
