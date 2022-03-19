package edu.utexas.cs.utopia.cortado.util.soot;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.JimpleBody;
import soot.jimple.SpecialInvokeExpr;

import javax.annotation.Nonnull;

/**
 * Common operations performed on {@link soot.SootMethod}s
 */
public class SootMethodUtils
{
    /**
     * @param constructor a non-static method with an active body
     *                    which is a constructor
     * @return the unit which either invokes super(),
     *          or another constructor.
     */
    @Nonnull
    public static Unit getObjectInitializationInvocation(@Nonnull SootMethod constructor)
    {
        // make sure is a non-static method with an active body, and is
        // a constructor
        if(!constructor.isConstructor())
        {
            throw new IllegalArgumentException("Method " + constructor.getName() + " is not a constructor.");
        }
        if(!constructor.hasActiveBody())
        {
            throw new IllegalArgumentException("Method " + constructor.getName() + " has no active body.");
        }
        if(constructor.isStatic())
        {
            throw new IllegalArgumentException("Method " + constructor.getName() + " is static.");
        }
        // get the first non-identity unit of its body
        JimpleBody body = (JimpleBody) constructor.getActiveBody();
        Unit ut = body.getFirstNonIdentityStmt();
        // Find the first invoking the super constructor for the this local,
        // return it
        while(ut != null)
        {
            if(ut instanceof InvokeStmt)
            {
                InvokeExpr superInitInvocation = ((InvokeStmt) ut).getInvokeExpr();
                boolean isObjectInitializer = superInitInvocation instanceof SpecialInvokeExpr
                        && superInitInvocation.getMethod().isConstructor()
                        && ((SpecialInvokeExpr) superInitInvocation).getBase().equals(body.getThisLocal());
                if (isObjectInitializer)
                {
                    return ut;
                }
            }
            ut = body.getUnits().getSuccOf(ut);
        }
        // Otherwise, the body should be invalid
        body.validate();
        throw new IllegalArgumentException("Expected init with no call to super() to be invalid");
    }
}
