package edu.utexas.cs.utopia.cortado.expression.factories.astfactory;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;

public interface IntExprFactory
{
    DivExpr mkDIV(Expr dividend, Expr divisor);

    MinusExpr mkMINUS(Expr... args);

    ModExpr mkMOD(Expr dividend, Expr divisor);

    MultExpr mkMULT(Expr... args);

    PlusExpr mkPLUS(Expr... args);

    RemExpr mkREM(Expr dividend, Expr divisor);
}
