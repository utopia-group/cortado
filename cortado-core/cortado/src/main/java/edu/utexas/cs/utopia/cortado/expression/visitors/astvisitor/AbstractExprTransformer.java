package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.*;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.*;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

import static edu.utexas.cs.utopia.cortado.expression.ExprUtils.narryOrSelfDistinct;

public abstract class AbstractExprTransformer extends CachedExprRetVisitor<Expr>
{
    protected final ExprFactory exprFactory = CachedExprFactory.getInstance();

    protected final Map<Expr, Expr> cachedResults = new HashMap<>();

    @Override
    public Expr visit(BoolConstArrayExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoolConstExpr newConst = (BoolConstExpr) e.getConstantExpr().accept(this);

            Expr res = exprFactory.mkBoolConstArray(e.getType(), newConst);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(IntConstArrayExpr e)
    {
        if (!alreadyVisited(e))
        {
            IntConstExpr newConst = (IntConstExpr) e.getConstantExpr().accept(this);

            Expr res = exprFactory.mkIntConstArray(e.getType(), newConst);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(SelectExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr newArrayExpr = e.getArrayExpr().accept(this);
            Expr newIdxExpr = e.getIndexExpr().accept(this);

            Expr res = exprFactory.mkSELECT(newArrayExpr, newIdxExpr);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(StoreExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr newArrayExpr = e.getArrayExpr().accept(this);
            Expr newIdxExpr = e.getIndexExpr().accept(this);
            Expr newNewVarExpr = e.getNewVal().accept(this);

            Expr res = exprFactory.mkSTORE(newArrayExpr, newIdxExpr, newNewVarExpr);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(AndExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformNaryOperatorDistinct(e, exprFactory::mkAND);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(EqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getLeft(), e.getRight(), exprFactory::mkEQ);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ExistsExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoundedVar[] newBoundVars = new BoundedVar[e.quantifierNum()];
            int i = 0;
            for (BoundedVar b : e)
                newBoundVars[i++] = (BoundedVar) b.accept(this);

            Expr newBody = e.getBody().accept(this);

            Expr res = exprFactory.mkEXISTS(newBody, newBoundVars);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ForAllExpr e)
    {
        if (!alreadyVisited(e))
        {
            BoundedVar[] newBoundVars = new BoundedVar[e.quantifierNum()];
            int i = 0;
            for (BoundedVar b : e)
                newBoundVars[i++] = (BoundedVar) b.accept(this);

            Expr newBody = e.getBody().accept(this);

            Expr res = exprFactory.mkFORALL(newBody, newBoundVars);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(GreaterEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getLeft(), e.getRight(), exprFactory::mkGTEQ);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(GreaterExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getLeft(), e.getRight(), exprFactory::mkGT);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ImplExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getAntecedent(), e.getConsequent(), exprFactory::mkIMPL);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(LessEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getLeft(), e.getRight(), exprFactory::mkLTEQ);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(LessExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getLeft(), e.getRight(), exprFactory::mkLT);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(NegExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr newArg = e.getArg().accept(this);

            Expr res = exprFactory.mkNEG(newArg);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(OrExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformNaryOperatorDistinct(e, exprFactory::mkOR);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(BoundedVar e)
    {
        if (!alreadyVisited(e))
        {
            Expr newDecl = e.getDecl().accept(this);

            Expr res = exprFactory.mkBNDVAR((FunctionDecl) newDecl);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(FunctionApp e)
    {
        if (!alreadyVisited(e))
        {
            Expr newDecl = e.getDecl().accept(this);

            int i = 0;
            Expr[] newArgs = new Expr[e.getArgNum()];
            for (Expr arg : e)
                newArgs[i++] = arg.accept(this);

            Expr res = exprFactory.mkAPP((FunctionDecl) newDecl, newArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(FunctionDecl e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getDividend(), e.getDivisor(), exprFactory::mkDIV);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformNaryOperator(e, exprFactory::mkMINUS);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ModExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getDividend(), e.getDivisor(), exprFactory::mkMOD);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformNaryOperator(e, exprFactory::mkMULT);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformNaryOperator(e, exprFactory::mkPLUS);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(RemExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = transformBinaryOperator(e.getDividend(), e.getDivisor(), exprFactory::mkREM);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(FalseExpr e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(IntConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr)e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(StringConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(TrueExpr e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    // Sub-classes can override this to have a default case. All the above visit methods call this one
    // before storing the transformed expression in the cache.
    @Override
    public Expr visit(Expr e)
    {
        return e;
    }

    // Make these unreachable and prevent sub-classes from overriding them.
    @Override
    public final Expr visit(ArrayExpr e)
    {
        throw new IllegalArgumentException("this code mus be unreachable");
    }

    @Override
    public final Expr visit(BoolExpr e)
    {
        throw new IllegalArgumentException("this code mus be unreachable");
    }

    @Override
    public final Expr visit(FunctionExpr e)
    {
        throw new IllegalArgumentException("this code mus be unreachable");
    }

    @Override
    public final Expr visit(IntExpr e)
    {
        throw new IllegalArgumentException("this code mus be unreachable");
    }

    @Override
    public final Expr visit(TermExpr e)
    {
        throw new IllegalArgumentException("this code mus be unreachable");
    }

    @Override
    public final Expr visit(BoolConstExpr e)
    {
        throw new IllegalArgumentException("this code mus be unreachable");
    }

    private Expr transformNaryOperator(Iterable<Expr> args, ExprUtils.NaryExprCreator mkMethod)
    {
        List<Expr> newArgs = new ArrayList<>();
        for (Expr a : args)
            newArgs.add(a.accept(this));

        return mkMethod.apply(newArgs.toArray(new Expr[0]));
    }

    private Expr transformNaryOperatorDistinct(Iterable<Expr> args, ExprUtils.NaryExprCreator mkMethod)
    {
        List<Expr> newArgs = new ArrayList<>();
        for (Expr a : args)
            newArgs.add(a.accept(this));

        return narryOrSelfDistinct(newArgs, mkMethod);
    }

    private Expr transformBinaryOperator(Expr left, Expr right, BinaryOperator<Expr> mkMethod)
    {
        Expr newLeft = left.accept(this);
        Expr newRight = right.accept(this);

        return mkMethod.apply(newLeft, newRight);
    }
}
