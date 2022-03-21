package edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis;

import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * MayRWSetAnalysis where abstract memory locations are either nothing
 * or everything.
 */
public class DummyMayRWSetAnalysis implements MayRWSetAnalysis {

    private static class AllOrNothingMemoryLocations implements MemoryLocations
    {
        boolean isTop;
        public AllOrNothingMemoryLocations(boolean isTop) {
            this.isTop = isTop;
        }

        @Override
        public boolean intersect(@Nonnull MemoryLocations m)
        {
            return this.isTop;
        }

        @Nonnull
        @Override
        public Collection<Expr> getExprsIn(@Nonnull Set<Expr> freeVars)
        {
            return isTop ? freeVars : Collections.emptySet();
        }

        @Nonnull
        @Override
        public MemoryLocations overApproximateWithoutField(@Nonnull SootField sootField)
        {
            // Assume there are more objects than just sootField in our universe,
            // so removing it from top, we still wind up having to return top
            return new AllOrNothingMemoryLocations(isTop);
        }
    }

    private final AllOrNothingMemoryLocations top = new AllOrNothingMemoryLocations(true);
    private final AllOrNothingMemoryLocations bot = new AllOrNothingMemoryLocations(false);

    @Override
    public Collection<MemoryLocations> readSet(SootMethod sm, Unit ut) {
        AllOrNothingMemoryLocations readLocs = bot;
        if(ut.getUseBoxes().size() > 0 || isCall(ut)) {
            readLocs = top;
        }
        return Collections.singleton(readLocs);
    }

    @Override
    public Collection<MemoryLocations> writeSet(SootMethod sm, Unit ut) {
        AllOrNothingMemoryLocations writeLocs = bot;
        if(ut.getDefBoxes().size() > 0 || isCall(ut)) {
            writeLocs = top;
        }
        return Collections.singleton(writeLocs);
    }

    private boolean isCall(Unit ut)
    {
        if (ut instanceof InvokeStmt)
            return true;

        if (ut instanceof AssignStmt)
            return ((AssignStmt)ut).containsInvokeExpr();

        return false;
    }
}
