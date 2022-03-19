package edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;

import java.util.HashSet;
import java.util.Set;

public abstract class CachedExprVisitor implements ExprVisitor
{
    private final Set<Expr> visitedNodes = new HashSet<>();

    protected boolean alreadyVisited(Expr e)
    {
        boolean rv = visitedNodes.contains(e);

        if (!rv)
            visitedNodes.add(e);

        return rv;
    }
}
