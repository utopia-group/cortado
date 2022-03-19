package edu.utexas.cs.utopia.cortado.expression.factories.astfactory;

import edu.utexas.cs.utopia.cortado.expression.ast.terminal.FalseExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.IntConstExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.StringConstExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.TrueExpr;

import java.math.BigInteger;

public interface TermExprFactory
{
    IntConstExpr mkINT(BigInteger val);

    StringConstExpr mkSTRING(String val);

    TrueExpr mkTRUE();

    FalseExpr mkFALSE();
}
