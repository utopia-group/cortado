package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;

public interface FuncExprRetVisitor<R>
{
    R visit(BoundedVar e);

    R visit(FunctionApp e);

    R visit(FunctionDecl e);
}
