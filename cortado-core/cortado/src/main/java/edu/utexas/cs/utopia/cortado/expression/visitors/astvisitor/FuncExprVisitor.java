package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;

public interface FuncExprVisitor
{
    void visit(BoundedVar e);

    void visit(FunctionApp e);

    void visit(FunctionDecl e);
}
