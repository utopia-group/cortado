package edu.utexas.cs.utopia.cortado.expression.type;

import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeVisitor;

public interface ExprType
{
    int getArity();

    void accept(ExprTypeVisitor v);

    <R> R accept(ExprTypeRetVisitor<R> v);
}
