package edu.utexas.cs.utopia.cortado.signalling;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Jimple;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.UnitGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Used to insert statements which evaluate the guards
 * of conditions that will be conditionally signalled.
 */
public class SignalPredicateEvalInjector extends BodyTransformer
{
    private final Map<CCR, Set<SignalOperation>> signalOperations;

    private final Map<Body, Unit> originalCCRStartInstr;

    private final Map<SootMethod, Set<Unit>> predPreChecks = new HashMap<>();

    public SignalPredicateEvalInjector(Map<CCR, Set<SignalOperation>> signalOperations, Map<Body, Unit> originalCCRStartInstr)
    {
        this.signalOperations = signalOperations;
        this.originalCCRStartInstr = originalCCRStartInstr;
    }

    public Map<SootMethod, Set<Unit>> getPredPreChecks()
    {
        return predPreChecks;
    }

    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map)
    {
        // if body is not a CCR method, nothing to do
        if(!CCR.isCCRMethod(body.getMethod())) return;
        // Otherwise, see if its CCR has a signal operation
        final List<CCR> ccrs = signalOperations.keySet()
                                               .stream()
                                               .filter(ccr -> body.getMethod().equals(ccr.getAtomicSection()))
                                               .collect(Collectors.toList());
        assert ccrs.size() <= 1;
        // if its CCR is not in signalOperations, nothing to do
        if(ccrs.isEmpty()) {
            return;
        }
        // Get some objects we'll need
        final CCR ccr = ccrs.get(0);
        final Set<SignalOperation> sigOps = this.signalOperations.get(ccr);
        final UnitGraph cfg = ExceptionalUnitGraphCache.getInstance().getOrCompute(ccr.getAtomicSection());
        // For each exit unit
        SootUnitUtils.getExplicitExitUnits(body.getMethod()).forEach(exitUnit -> {
            // For each edge to the exit unit
            cfg.getPredsOf(exitUnit).forEach(exitUnitPred -> {
                // For each conditional signal operation
                for (SignalOperation sigOp : sigOps)
                {
                    if (sigOp.isConditional())
                    {
                        AssignStmt evalPredToLocal = getPredEvaluation(body, sigOp, "");
                        SootUnitUtils.insertOnEdge(evalPredToLocal, exitUnitPred, exitUnit, body);
                        exitUnitPred = evalPredToLocal;
                    }
                } // end for each signal op
            }); // end for each edge to the exit unit
        }); // end for each exit unit

        // If requested inject predicate evaluations at the beginning of the CCR
        if (!SignalOperation.getDefaultFlipPredOptCondition().equals(SignalOperation.FLIP_PRED_OPT_CONDITION.NEVER))
        {
            Unit firstNonIdentity = originalCCRStartInstr.get(body);

            assert firstNonIdentity != null;

            for (SignalOperation sigOp : sigOps)
            {
                if (sigOp.performFlipPredOpt())
                {
                    AssignStmt evalPredToLocal = getPredEvaluation(body, sigOp, "_pre");
                    body.getUnits().insertBefore(evalPredToLocal, firstNonIdentity);

                    SootMethod pred = sigOp.getPredicate();
                    if (!predPreChecks.containsKey(pred))
                        predPreChecks.put(pred, new HashSet<>());

                    predPreChecks.get(pred).add(evalPredToLocal);
                }
            }
        }
    }

    private AssignStmt getPredEvaluation(Body body, SignalOperation sigOp, String localSuffix)
    {
        Jimple j = Jimple.v();

        // get the local the predicate will be evaluated into
        Local predEvalLocal = SootLocalUtils.getOrAddLocal(SootNamingUtils.getLocalForPredEval(sigOp) + localSuffix, BooleanType.v(), body);
        // get the predicate method
        SootMethod predicateMethod = sigOp.getPredicate();
        if (predicateMethod.getParameterCount() > 0)
        {
            throw new IllegalStateException("predicates with parameters cannot be " +
                    "conditionally signalled");
        }
        // insert the predicate evaluations
        final SpecialInvokeExpr evalPredExpr = j.newSpecialInvokeExpr(
                body.getThisLocal(),
                predicateMethod.makeRef()
        );
        AssignStmt evalStmt = j.newAssignStmt(predEvalLocal, evalPredExpr);

        if (Scene.v().hasPointsToAnalysis())
        {
            // TODO: FIX CONTEXT SENSITIVITY ISSUE IN WSDATALISTENER
//            final MethodOrMethodContext callingContext = MethodContext.v(body.getMethod(), evalStmt);
            final SootMethod callingContext = body.getMethod();
            Edge guardInvkEdge = new Edge(callingContext, evalStmt, predicateMethod);
            Scene.v().getCallGraph().addEdge(guardInvkEdge);
            assert Scene.v().getCallGraph().edgesOutOf(evalStmt).hasNext();
            assert !Scene.v().getCallGraph().edgesOutOf(evalStmt).next().isInvalid();
            assert Scene.v().getCallGraph().edgesOutOf(callingContext).hasNext();
            assert !Scene.v().getCallGraph().edgesOutOf(callingContext).next().isInvalid();

        }
        return evalStmt;
    }
}
