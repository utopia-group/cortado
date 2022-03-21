package edu.utexas.cs.utopia.cortado.expression.factories.astfactory;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;

public interface FuncExprFactory
{
    BoundedVar mkBNDVAR(FunctionDecl decl);

    FunctionApp mkAPP(FunctionDecl decl, Expr... args);

    FunctionDecl mkDECL(String name, FunctionType funcType);
}
