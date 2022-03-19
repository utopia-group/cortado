package edu.utexas.cs.utopia.cortado.expression.factories.astfactory;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.BoolConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.IntConstArrayExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.StoreExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.BoolConstExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.IntConstExpr;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;

public interface ArrayExprFactory
{
    BoolConstArrayExpr mkBoolConstArray(ArrType arrType, BoolConstExpr boolConst);

    IntConstArrayExpr mkIntConstArray(ArrType arrType, IntConstExpr intConst);

    SelectExpr mkSELECT(Expr arrayExpr, Expr indexExpr);

    StoreExpr mkSTORE(Expr arrayExpr, Expr indexExpr, Expr newVal);
}
