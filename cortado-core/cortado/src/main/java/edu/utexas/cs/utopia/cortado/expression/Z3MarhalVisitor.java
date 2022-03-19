package edu.utexas.cs.utopia.cortado.expression;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.*;
import edu.utexas.cs.utopia.cortado.expression.ast.array.*;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.*;
import edu.utexas.cs.utopia.cortado.expression.type.*;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.CachedExprRetVisitor;
import edu.utexas.cs.utopia.cortado.expression.visitors.typevisitor.ExprTypeRetVisitor;

import java.util.HashMap;

/**
 * Converts our Expr to com.microsoft.z3.Expr
 */
class Z3MarshalVisitor extends CachedExprRetVisitor<AST>
{
    private final Context ctx;

    private final HashMap<edu.utexas.cs.utopia.cortado.expression.ast.Expr, AST> asts = new HashMap<>();

    public Z3MarshalVisitor(Context ctx)
    {
        super();
        this.ctx = ctx;
    }

    @Override
    public AST visit(BoolConstArrayExpr e)
    {
        if (!alreadyVisited(e))
        {
            translateConstArray(e);
        }

        return asts.get(e);
    }

    @Override
    public AST visit(IntConstArrayExpr e)
    {
        if (!alreadyVisited(e))
        {
            translateConstArray(e);
        }

        return asts.get(e);
    }

    @Override
    public AST visit(SelectExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArrayExpr arrayExpr = (ArrayExpr) e.getArrayExpr().accept(this);
            Expr idxExpr = (Expr) e.getIndexExpr().accept(this);

            asts.put(e, ctx.mkSelect(arrayExpr, idxExpr));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(StoreExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArrayExpr arrayExpr = (ArrayExpr) e.getArrayExpr().accept(this);
            Expr idxExpr = (Expr) e.getIndexExpr().accept(this);
            Expr newVal = (Expr) e.getNewVal().accept(this);

            asts.put(e, ctx.mkStore(arrayExpr, idxExpr, newVal));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(AndExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoolExpr[] args = new BoolExpr[e.argNum()];
            for(int i = 0; i < args.length; ++i)
            {
                args[i] = (BoolExpr)e.argAt(i).accept(this);
            }

            asts.put(e, ctx.mkAnd(args));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(EqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr left = (Expr) e.getLeft().accept(this);
            Expr right = (Expr) e.getRight().accept(this);

            asts.put(e, ctx.mkEq(left, right));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(ExistsExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr[] boundedVar = new Expr[e.quantifierNum()];

            for(int i = 0; i < boundedVar.length; ++i)
                boundedVar[i] = (Expr) e.boundedVarAr(i).accept(this);

            Expr body = (Expr) e.getBody().accept(this);

            asts.put(e, ctx.mkExists(boundedVar, body, 1, null, null, null, null));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(ForAllExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr[] boundedVar = new Expr[e.quantifierNum()];

            for(int i = 0; i < boundedVar.length; ++i)
                boundedVar[i] = (Expr) e.boundedVarAr(i).accept(this);

            Expr body = (Expr) e.getBody().accept(this);

            asts.put(e, ctx.mkForall(boundedVar, body, 1, null, null, null, null));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(GreaterEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr left = (ArithExpr) e.getLeft().accept(this);
            ArithExpr right = (ArithExpr) e.getRight().accept(this);

            asts.put(e, ctx.mkGe(left, right));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(GreaterExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr left = (ArithExpr) e.getLeft().accept(this);
            ArithExpr right = (ArithExpr) e.getRight().accept(this);

            asts.put(e, ctx.mkGt(left, right));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(ImplExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoolExpr antecedent = (BoolExpr) e.getAntecedent().accept(this);
            BoolExpr consequent = (BoolExpr) e.getConsequent().accept(this);

            asts.put(e, ctx.mkImplies(antecedent, consequent));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(LessEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr left = (ArithExpr) e.getLeft().accept(this);
            ArithExpr right = (ArithExpr) e.getRight().accept(this);

            asts.put(e, ctx.mkLe(left, right));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(LessExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr left = (ArithExpr) e.getLeft().accept(this);
            ArithExpr right = (ArithExpr) e.getRight().accept(this);

            asts.put(e, ctx.mkLt(left, right));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(NegExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoolExpr arg = (BoolExpr) e.getArg().accept(this);

            asts.put(e, ctx.mkNot(arg));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(OrExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoolExpr[] args = new BoolExpr[e.argNum()];

            for (int i = 0; i < args.length; ++i)
                args[i] = (BoolExpr) e.argAt(i).accept(this);

            asts.put(e, ctx.mkOr(args));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(BoundedVar e)
    {
        if (!alreadyVisited(e))
        {
            FuncDecl decl = (FuncDecl) e.getDecl().accept(this);

            asts.put(e, ctx.mkConst(decl.getName(), decl.getRange()));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(FunctionApp e)
    {
        if (!alreadyVisited(e))
        {
            FuncDecl decl = (FuncDecl) e.getDecl().accept(this);

            Expr[] args = new Expr[e.getArgNum()];

            for(int i = 0; i < args.length; ++i)
                args[i] = (Expr) e.argAt(i).accept(this);

            asts.put(e, ctx.mkApp(decl, args));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(FunctionDecl e)
    {
        if (!alreadyVisited(e))
        {
            ExprTypeMarshalVisitor tyVis = new ExprTypeMarshalVisitor(ctx);

            FunctionType funcTy = e.getType();

            Sort[] domain = new Sort[funcTy.getArity()];
            ExprType[] cortadoDom = funcTy.getDomain();

            for(int i = 0; i < domain.length; i++)
                domain[i] = cortadoDom[i].accept(tyVis);

            asts.put(e, ctx.mkFuncDecl(e.getName(), domain, funcTy.getCoDomain().accept(tyVis)));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr dividend = (ArithExpr) e.getDividend().accept(this);
            ArithExpr divisor = (ArithExpr) e.getDivisor().accept(this);

            asts.put(e, ctx.mkDiv(dividend, divisor));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr[] args = new ArithExpr[e.argNum()];

            for (int i = 0; i < args.length; ++i)
                args[i] = (ArithExpr) e.argAt(i).accept(this);

            asts.put(e, ctx.mkSub(args));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(ModExpr e)
    {
        if (!alreadyVisited(e))
        {
            IntExpr dividend = (IntExpr) e.getDividend().accept(this);
            IntExpr divisor = (IntExpr) e.getDivisor().accept(this);

            asts.put(e, ctx.mkMod(dividend, divisor));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr[] args = new ArithExpr[e.argNum()];

            for (int i = 0; i < args.length; ++i)
                args[i] = (ArithExpr) e.argAt(i).accept(this);

            asts.put(e, ctx.mkMul(args));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArithExpr[] args = new ArithExpr[e.argNum()];

            for (int i = 0; i < args.length; ++i)
                args[i] = (ArithExpr) e.argAt(i).accept(this);

            asts.put(e, ctx.mkAdd(args));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(RemExpr e)
    {
        if (!alreadyVisited(e))
        {
            IntExpr dividend = (IntExpr) e.getDividend().accept(this);
            IntExpr divisor = (IntExpr) e.getDivisor().accept(this);

            asts.put(e, ctx.mkRem(dividend, divisor));
        }

        return asts.get(e);
    }



    @Override
    public AST visit(FalseExpr e)
    {
        if (!alreadyVisited(e))
        {
            asts.put(e, ctx.mkFalse());
        }

        return asts.get(e);
    }

    @Override
    public AST visit(IntConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            asts.put(e, ctx.mkInt(e.getVal().toString()));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(StringConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            // TODO: Fix this once we have ASTs for string operations.
            asts.put(e, ctx.mkInt(e.toString().hashCode()));
        }

        return asts.get(e);
    }

    @Override
    public AST visit(TrueExpr e)
    {
        if (!alreadyVisited(e))
        {
            asts.put(e, ctx.mkTrue());
        }

        return asts.get(e);
    }

    @Override
    public Expr visit(edu.utexas.cs.utopia.cortado.expression.ast.array.ArrayExpr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public com.microsoft.z3.Expr visit(BoolConstExpr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public com.microsoft.z3.Expr visit(edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public com.microsoft.z3.Expr visit(FunctionExpr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public com.microsoft.z3.Expr visit(edu.utexas.cs.utopia.cortado.expression.ast.integer.IntExpr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public com.microsoft.z3.Expr visit(TermExpr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public com.microsoft.z3.Expr visit(edu.utexas.cs.utopia.cortado.expression.ast.Expr e)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    private void translateConstArray(ConstArrayExpr e)
    {
        ExprTypeMarshalVisitor tyVis = new ExprTypeMarshalVisitor(ctx);
        ArrType type = (ArrType) e.getType();

        if (type.getArity() > 1)
            // Not sure how to convert these arrays.
            throw new IllegalStateException("arrays with arity greater than 1 are not yet supported");

        Expr constExpr = (Expr) e.getConstantExpr().accept(this);
        Sort indexSort = type.getDomain()[0].accept(tyVis);
        asts.put(e, ctx.mkConstArray(indexSort, constExpr));
    }
}

class ExprTypeMarshalVisitor implements ExprTypeRetVisitor<Sort>
{
    Context ctx;

    ExprTypeMarshalVisitor(Context ctx)
    {
        this.ctx = ctx;
    }

    @Override
    public Sort visit(ArrType t)
    {
        Sort[] domain = new Sort[t.getArity()];
        ExprType[] cortadoDom = t.getDomain();

        for(int i = 0; i < cortadoDom.length; ++i)
            domain[i] = cortadoDom[i].accept(this);

        return ctx.mkArraySort(domain, t.getCoDomain().accept(this));
    }

    @Override
    public Sort visit(BooleanType t)
    {
        return ctx.getBoolSort();
    }

    @Override
    public Sort visit(FunctionType t)
    {
        throw new IllegalStateException("This must be unreachable");
    }

    @Override
    public Sort visit(IntegerType t)
    {
        return ctx.getIntSort();
    }

    @Override
    public Sort visit(StringType t)
    {
        return ctx.getStringSort();
    }

    @Override
    public Sort visit(UnitType t)
    {
        throw new IllegalStateException("This must be unreachable");
    }
};
