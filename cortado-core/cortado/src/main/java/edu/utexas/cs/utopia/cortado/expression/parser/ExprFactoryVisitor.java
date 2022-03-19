package edu.utexas.cs.utopia.cortado.expression.parser;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.typefactory.ExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.type.FunctionType;
import org.antlr.v4.runtime.Token;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class ExprFactoryVisitor extends ExprBaseVisitor<Expr>
{
    private final ExprFactory eFac = CachedExprFactory.getInstance();

    private final Map<String, FunctionDecl> idToDecl = new HashMap<>();

    private String unQuoteIdentifier(String id)
    {
        return id.substring(1, id.length() - 1);
    }

    @Override
    public Expr visitConstant_decl(ExprParser.Constant_declContext ctx)
    {
        String id = ctx.id.getText();
        String unquotedId = unQuoteIdentifier(id);

        ExprTypeFactoryVisitor tyVis = new ExprTypeFactoryVisitor();
        idToDecl.put(unquotedId, eFac.mkDECL(unquotedId, (FunctionType) ctx.ty.accept(tyVis)));
        return null;
    }

    @Override
    public Expr visitBuildinUnOp(ExprParser.BuildinUnOpContext ctx)
    {
        switch (ctx.op.getText())
        {
            case "neg":
                return eFac.mkNEG(ctx.oper.accept(this));
            default:
                assert false;
                throw new IllegalStateException("This should be unreachable");

        }
    }

    @Override
    public Expr visitUninterpUnOp(ExprParser.UninterpUnOpContext ctx)
    {
        String unquoteId = unQuoteIdentifier(ctx.op.getText());

        if (!idToDecl.containsKey(unquoteId))
            throw new RuntimeException("undefined identifier: " + unquoteId);

        return eFac.mkAPP(idToDecl.get(unquoteId), ctx.oper.accept(this));
    }

    @Override
    public Expr visitBuildinBinOp(ExprParser.BuildinBinOpContext ctx)
    {
        Expr left = ctx.left.accept(this);
        Expr right = ctx.right.accept(this);

        switch (ctx.op.getText())
        {
            case "/":
                return eFac.mkDIV(left, right);
            case "rem":
                return eFac.mkREM(left, right);
            case "mod":
                return eFac.mkMOD(left, right);
            case "=":
                return eFac.mkEQ(left, right);
            case ">=":
                return eFac.mkGTEQ(left, right);
            case ">":
                return eFac.mkGT(left, right);
            case "-->":
                return eFac.mkIMPL(left, right);
            case "<=":
                return eFac.mkLTEQ(left, right);
            case "<":
                return eFac.mkLT(left, right);
            case "select":
                return eFac.mkSELECT(left, right);
            default:
                assert false;
                throw new IllegalStateException("This should be unreachable");
        }
    }

    @Override
    public Expr visitUninterpBinOp(ExprParser.UninterpBinOpContext ctx)
    {
        Expr left = ctx.left.accept(this);
        Expr right = ctx.right.accept(this);

        String unquoteId = unQuoteIdentifier(ctx.op.getText());
        if (!idToDecl.containsKey(unquoteId))
            throw new RuntimeException("undefined identifier: " + unquoteId);

        return eFac.mkAPP(idToDecl.get(unquoteId), left, right);
    }

    @Override
    public Expr visitBuildinNOp(ExprParser.BuildinNOpContext ctx)
    {
        Expr[] args = ctx.ops.stream()
                             .map(op -> op.accept(this))
                             .toArray(Expr[]::new);

        switch (ctx.op.getText())
        {
            case "and":
                return eFac.mkAND(args);
            case "or":
                return eFac.mkOR(args);
            case "+":
                return eFac.mkPLUS(args);
            case "-":
                return eFac.mkMINUS(args);
            case "*":
                return eFac.mkMULT(args);
            case "store":
                if (args.length != 3)
                    throw new RuntimeException("invalid store expression");
                return eFac.mkSTORE(args[0], args[1], args[2]);
            default:
                assert false;
                throw new IllegalStateException("This should be unreachable");
        }
    }

    @Override
    public Expr visitUninterpNOp(ExprParser.UninterpNOpContext ctx)
    {
        Expr[] args = ctx.ops.stream()
                             .map(op -> op.accept(this))
                             .toArray(Expr[]::new);

        String unquoteId = unQuoteIdentifier(ctx.op.getText());
        if (!idToDecl.containsKey(unquoteId))
            throw new RuntimeException("undefined identifier: " + unquoteId);

        return eFac.mkAPP(idToDecl.get(unquoteId), args);
    }

    @Override
    public Expr visitNullaryApp(ExprParser.NullaryAppContext ctx)
    {
        String unquoteId = unQuoteIdentifier(ctx.op.getText());
        if (!idToDecl.containsKey(unquoteId))
            throw new RuntimeException("undefined identifier: " + unquoteId);

        return eFac.mkAPP(idToDecl.get(unquoteId));
    }

    @Override
    public Expr visitIntConst(ExprParser.IntConstContext ctx)
    {
        return eFac.mkINT(new BigInteger(ctx.c.getText()));
    }

    @Override
    public Expr visitBoolConst(ExprParser.BoolConstContext ctx)
    {
        switch (ctx.c.getText())
        {
            case "TRUE":
                return eFac.mkTRUE();
            case "FALSE":
                return eFac.mkFALSE();
            default:
                assert false;
                throw new IllegalStateException("This should be unreachable");
        }
    }

    @Override
    public Expr visitQuant_expr(ExprParser.Quant_exprContext ctx)
    {
        Expr body = ctx.body.accept(this);
        BoundedVar[] boundVars = ctx.quant.stream()
                                          .map(Token::getText)
                                          .map(this::unQuoteIdentifier)
                                          .map(id ->
                                           {
                                               if (!idToDecl.containsKey(id))
                                                   throw new RuntimeException("undefined identifier");

                                               return eFac.mkBNDVAR(idToDecl.get(id));
                                           })
                                          .toArray(BoundedVar[]::new);

        switch (ctx.qKind.getText())
        {
            case "forall":
                return eFac.mkFORALL(body, boundVars);
            case "exist":
                return eFac.mkEXISTS(body, boundVars);
            default:
                assert false;
                throw new IllegalStateException("This should be unreachable");
        }
    }

    @Override
    protected Expr aggregateResult(Expr aggregate, Expr nextResult)
    {
        return nextResult == null ? aggregate : nextResult;
    }
}

class ExprTypeFactoryVisitor extends ExprBaseVisitor<ExprType>
{
    private final ExprTypeFactory eTyFac = CachedExprTypeFactory.getInstance();

    @Override
    public ExprType visitIntTy(ExprParser.IntTyContext ctx)
    {
        return eTyFac.mkIntegerType();
    }

    @Override
    public ExprType visitBoolTy(ExprParser.BoolTyContext ctx)
    {
        return eTyFac.mkBooleanType();
    }

    @Override
    public ExprType visitStringTy(ExprParser.StringTyContext ctx)
    {
        return eTyFac.mkStringType();
    }

    @Override
    public ExprType visitNullFuncTy(ExprParser.NullFuncTyContext ctx)
    {
        return eTyFac.mkFunctionType(new ExprType[0], ctx.codomain.accept(this));
    }

    @Override
    public ExprType visitNaryFuncTy(ExprParser.NaryFuncTyContext ctx)
    {
        int domSz = ctx.domain.size();
        ExprType[] domain = new ExprType[domSz];

        for (int i = 0; i < domSz; i++)
            domain[i] = ctx.domain.get(i).accept(this);

        return eTyFac.mkFunctionType(domain, ctx.codomain.accept(this));
    }

    @Override
    public ExprType visitArray_type(ExprParser.Array_typeContext ctx)
    {
        int domSz = ctx.domain.size();
        ExprType[] domain = new ExprType[domSz];

        for (int i = 0; i < domSz; i++)
            domain[i] = ctx.domain.get(i).accept(this);

        return eTyFac.mkArrayType(domain, ctx.codomain.accept(this));
    }
}
