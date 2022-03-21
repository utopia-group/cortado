package edu.utexas.cs.utopia.cortado.expression.factories.astfactory;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;

public interface BoolExprFactory
{
    AndExpr mkAND(Expr... args);

    EqExpr mkEQ(Expr left, Expr right);

    ExistsExpr mkEXISTS(Expr body, BoundedVar... quantifiers);

    ForAllExpr mkFORALL(Expr body, BoundedVar... quantifiers);

    GreaterEqExpr mkGTEQ(Expr left, Expr right);

    GreaterExpr mkGT(Expr left, Expr right);

    ImplExpr mkIMPL(Expr antecedent, Expr consequent);

    LessEqExpr mkLTEQ(Expr left, Expr right);

    LessExpr mkLT(Expr left, Expr right);

    NegExpr mkNEG(Expr arg);

    OrExpr mkOR(Expr... args);
}
