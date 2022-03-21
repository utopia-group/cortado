package edu.utexas.cs.utopia.cortado.expression.ast.array;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;

public abstract class ConstArrayExpr extends ArrayExpr
{
    public ConstArrayExpr(ArrType exprTy)
    {
        super(exprTy);
    }

    public abstract Expr getConstantExpr();
}
