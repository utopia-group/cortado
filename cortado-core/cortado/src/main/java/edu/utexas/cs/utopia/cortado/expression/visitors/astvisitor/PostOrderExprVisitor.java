package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.*;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.*;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.*;

public abstract class PostOrderExprVisitor extends CachedExprVisitor
{
    @Override
    public void visit(BoolConstArrayExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getConstantExpr().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(IntConstArrayExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getConstantExpr().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(SelectExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getArrayExpr().accept(this);
            e.getIndexExpr().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(StoreExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getArrayExpr().accept(this);
            e.getIndexExpr().accept(this);
            e.getNewVal().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(AndExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (Expr conj : e)
                conj.accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(EqExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getLeft().accept(this);
            e.getRight().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(ExistsExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (BoundedVar b : e)
                b.accept(this);

            e.getBody().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(ForAllExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (BoundedVar b : e)
                b.accept(this);

            e.getBody().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(GreaterEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getLeft().accept(this);
            e.getRight().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(GreaterExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getLeft().accept(this);
            e.getRight().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(ImplExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getAntecedent().accept(this);
            e.getConsequent().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(LessEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getLeft().accept(this);
            e.getRight().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(LessExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getLeft().accept(this);
            e.getRight().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(NegExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getArg().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(OrExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (Expr d : e)
                d.accept(this);

            visit((Expr) e);
        }
    }



    @Override
    public void visit(BoundedVar e)
    {
        if (!alreadyVisited(e))
        {
            e.getDecl().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(FunctionApp e)
    {
        if (!alreadyVisited(e))
        {
            e.getDecl().accept(this);

            for (Expr arg : e)
                arg.accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(FunctionDecl e)
    {
        if (!alreadyVisited(e))
        {
            visit((Expr) e);
        }
    }

    @Override
    public void visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getDivisor().accept(this);
            e.getDividend().accept(this);
            visit((Expr) e);
        }
    }

    @Override
    public void visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (Expr t : e)
                t.accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(ModExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getDivisor().accept(this);
            e.getDividend().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (Expr t : e)
                t.accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            for (Expr t : e)
                t.accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(RemExpr e)
    {
        if (!alreadyVisited(e))
        {
            e.getDivisor().accept(this);
            e.getDividend().accept(this);

            visit((Expr) e);
        }
    }

    @Override
    public void visit(BoolConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            visit((Expr) e);
        }
    }

    @Override
    public void visit(FalseExpr e)
    {
        if (!alreadyVisited(e))
        {
            visit((Expr) e);
        }
    }

    @Override
    public void visit(IntConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            visit((Expr) e);
        }
    }

    @Override
    public void visit(StringConstExpr e)
    {
        if (!alreadyVisited(e))
        {
            visit((Expr) e);
        }
    }

    @Override
    public void visit(TrueExpr e)
    {
        if (!alreadyVisited(e))
        {
            visit((Expr) e);
        }
    }

    // Nothing to do for abstract classes

    @Override
    public void visit(ArrayExpr e)
    {

    }

    @Override
    public void visit(BoolExpr e)
    {

    }

    @Override
    public void visit(FunctionExpr e)
    {

    }

    @Override
    public void visit(IntExpr e)
    {

    }

    @Override
    public void visit(TermExpr e)
    {

    }

    @Override
    public void visit(Expr e)
    {

    }
}
