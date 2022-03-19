package edu.utexas.cs.utopia.cortado.signalling;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.mockclasses.ir.SignallingAnnotation;
import soot.BooleanType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.StringConstant;

import java.util.Arrays;
import java.util.Set;

public class SignalAnnotationExtractor extends SignalOperationInferencePhase
{
    private static final String SIGNALLING_ANNOTATION_CLASS_NAME = SignallingAnnotation.class.getName();

    private final SootMethod signalAnnotationMethod;

    public SignalAnnotationExtractor(SootClass monitor, Set<CCR> ccrs)
    {
        super(monitor, ccrs);

        SootClass sigAnnotationClass = Scene.v().getSootClass(SIGNALLING_ANNOTATION_CLASS_NAME);
        SootClass stringClass = Scene.v().getSootClass("java.lang.String");
        this.signalAnnotationMethod = sigAnnotationClass.getMethod("signalOperation", Arrays.asList(stringClass.getType(), BooleanType.v(), BooleanType.v()));
    }

    @Override
    public void inferSignalOperations()
    {
        ccrs.forEach(ccr -> ccr.getAtomicSection()
            .getActiveBody()
            .getUnits()
            .forEach(u -> {
                if (u instanceof InvokeStmt)
                {
                    InvokeStmt invokeStmt = (InvokeStmt) u;
                    InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                    if (invokeExpr.getMethod().equals(signalAnnotationMethod))
                    {
                        StringConstant predSignature = (StringConstant) invokeExpr.getArg(0);
                        IntConstant isConditional = (IntConstant) invokeExpr.getArg(1);
                        IntConstant isBroadcast = (IntConstant) invokeExpr.getArg(2);

                        final SignalOperation signalOperation = new SignalOperation(Scene.v().getMethod(predSignature.toString().replace("\"", "")), isConditional.value != 0, isBroadcast.value != 0, false);
                        addSignalOperation(ccr, signalOperation);
                    }
                }
           }));
    }
}
