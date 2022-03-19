package edu.utexas.cs.utopia.cortado.util;

import edu.utexas.cs.utopia.cortado.mockclasses.ir.Fragment;
import edu.utexas.cs.utopia.cortado.mockclasses.ir.SignallingAnnotation;
import edu.utexas.cs.utopia.cortado.mockclasses.ir.YieldIface;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;

import java.util.Collections;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is used to manage the classes used to implement
 * implicit, intermediate, and explicit monitors
 * (locks, atomics, waituntils, etc.)
 *
 * @author Ben_Sepanski
 */
public class MonitorInterfaceUtils
{
    // Explicit Monitor classes
    public static final String LOCK_CLASSNAME = ReentrantLock.class.getName();
    public static final String CONDITION_CLASSNAME = Condition.class.getName();

    // Intermediate monitor implementation classes
    public static final String YIELD_CLASSNAME = YieldIface.class.getName();

    // SignalAnnotation
    public static final String SIGNALLINGANNOTATION_CLASSNAME = SignallingAnnotation.class.getName();

    public static final String FRAGMENT_MOCK_CLASS_CLASSNAME = Fragment.class.getName();

    ///
    // Getters for the above classes as SootClasses ///////////////////////////
    //

    static public SootClass getLockClass()
    {
        return Scene.v().getSootClass(LOCK_CLASSNAME);
    }

    static public SootClass getConditionClass()
    {
        return Scene.v().getSootClass(CONDITION_CLASSNAME);
    }

    static public SootClass getYieldClass()
    {
        return Scene.v().getSootClass(YIELD_CLASSNAME);
    }

    ///// Getters for important methods from above classes ////////////////////

    static public SootMethod getLockMethod()
    {
        return getLockClass().getMethod("void lock()");
    }

    static public SootMethod getUnlockMethod()
    {
        return getLockClass().getMethod("void unlock()");
    }

    static public SootMethod getConditionAwaitMethod() {return getConditionClass().getMethod("await", Collections.emptyList());}

    static public SootMethod getYieldMethod()
    {
        return getYieldClass().getMethod("void yield()");
    }

    ////// Build new invocations of the above methods /////////////////////////

    static public InvokeStmt newYieldInvk()
    {
        SootMethodRef yieldRef = getYieldMethod().makeRef();
        // Sanity check: yield must be a static method
        assert (yieldRef.isStatic());
        InvokeExpr yieldInvkExpr = Jimple.v().newStaticInvokeExpr(yieldRef);
        return Jimple.v().newInvokeStmt(yieldInvkExpr);
    }
}
