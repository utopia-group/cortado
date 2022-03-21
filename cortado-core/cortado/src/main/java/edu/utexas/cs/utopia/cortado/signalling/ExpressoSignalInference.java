package edu.utexas.cs.utopia.cortado.signalling;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.staticanalysis.CommutativityAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.RWSetRaceConditionAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.SMTBasedCommutativityAnalysis;
import edu.utexas.cs.utopia.cortado.vcgen.CachedStrongestPostConditionCalculatorFactory;
import edu.utexas.cs.utopia.cortado.vcgen.MethodHoareTriple;
import edu.utexas.cs.utopia.cortado.vcgen.StrongestPostConditionCalculator;
import soot.SootClass;
import soot.SootMethod;

import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.signalling.SignalOperation.FLIP_PRED_OPT_CONDITION.ALWAYS;
import static edu.utexas.cs.utopia.cortado.signalling.SignalOperation.FLIP_PRED_OPT_CONDITION.UNCONDITIONAL;

public class ExpressoSignalInference extends SignalOperationInferencePhase
{
    private static final CachedStrongestPostConditionCalculatorFactory spCalcFactory = CachedStrongestPostConditionCalculatorFactory.getInstance();

    private static final ExprFactory exprFactory = CachedExprFactory.getInstance();

    private final CommutativityAnalysis commutativityAnalysis;

    private final boolean cascadingBcasts;

    public ExpressoSignalInference(SootClass monitor, Set<CCR> ccrs, boolean cascadingBcasts)
    {
        super(monitor, ccrs);
        commutativityAnalysis = new SMTBasedCommutativityAnalysis(monitor, new RWSetRaceConditionAnalysis());
        this.cascadingBcasts = cascadingBcasts;
    }

    @Override
    public void inferSignalOperations()
    {
        Collection<SootMethod> preds = new HashSet<>();
        Map<CCR, SootMethod> ccrsWithPreds = new HashMap<>();

        for (CCR ccr : ccrs)
        {
            if (ccr.hasGuard())
            {
                preds.add(ccr.getGuard());
                ccrsWithPreds.put(ccr, ccr.getGuard());
            }
        }

        Map<SootMethod, Set<CCR>> predToCCRs = new HashMap<>();
        for (CCR ccr : ccrsWithPreds.keySet())
        {
            SootMethod pred = ccrsWithPreds.get(ccr);
            if (!predToCCRs.containsKey(pred)) {
                predToCCRs.put(pred, new HashSet<>());
            }

            predToCCRs.get(pred).add(ccr);
        }

        for (SootMethod pred : preds)
        {
            StrongestPostConditionCalculator predSpCalc = getOrConstructSpCalculator(pred);
            Expr negPredEncoding = predSpCalc.getMethodEncoding(exprFactory.mkFALSE());

            Set<MethodHoareTriple> bcastTriples = constructBroadcastTriples(predToCCRs.get(pred), pred);

            Set<MethodHoareTriple> invalidTriples = bcastTriples.parallelStream()
                                                                .filter(triple -> !predSpCalc.isValid(triple))
                                                                .collect(Collectors.toSet());
            boolean bcast = !invalidTriples.isEmpty();

            for (CCR ccr : ccrs)
            {
                MethodHoareTriple mustNotSig = constructMustNotSignalTriple(ccr, pred, negPredEncoding);

                if (predSpCalc.isValid(mustNotSig))
                    continue;

                MethodHoareTriple mustSig = constructMustSignalTriple(ccr, pred, negPredEncoding);
                boolean condSig = !predSpCalc.isValid(mustSig);

                boolean flipPred = SignalOperation.getDefaultFlipPredOptCondition().equals(ALWAYS) ||
                        (!condSig && SignalOperation.getDefaultFlipPredOptCondition().equals(UNCONDITIONAL));

                if (flipPred)
                    bcast = true;

                if (bcast && !flipPred)
                {
                    // check if the composition of the signaling ccr and all the notified ccrs falsify the predicate
                    Set<MethodHoareTriple> ccrBcastTriples = constructCCRBcastTriples(ccr, predToCCRs.get(pred), pred, negPredEncoding);

                    if (ccrBcastTriples.parallelStream().allMatch(predSpCalc::isValid))
                    {
                        // then check if the signalling ccr commutes with all the other ccrs in the monitor.
                        bcast = !ccrs.parallelStream().allMatch(c1 -> commutativityAnalysis.commutes(c1, ccr));
                    }
                }

                if (bcast && cascadingBcasts)
                {
                    addSignalOperation(ccr, new SignalOperation(pred, condSig, false, false));
                    predToCCRs.get(pred).forEach(sigCCR ->
                        addSignalOperation(sigCCR, new SignalOperation(pred, true, false, true))
                    );
                }
                else
                {
                    addSignalOperation(ccr, new SignalOperation(pred, condSig, bcast, false));
                }
            }
        }
    }

    private Set<MethodHoareTriple> constructCCRBcastTriples(CCR ccr, Set<CCR> ccrsWaitOnPred, SootMethod pred, Expr pre)
    {
        Set<MethodHoareTriple> triples = new HashSet<>();
        Expr postCond = exprFactory.mkEQ(getOrConstructSpCalculator(pred).getRetVal(), exprFactory.mkFALSE());

        Expr ccrPost = getPostCondForCCR(ccr, pre);
        for (CCR recCCR : ccrsWaitOnPred)
        {
            Expr recCCRPost = getPostCondForCCR(recCCR, ccrPost);
            triples.add(new MethodHoareTriple(recCCRPost, postCond, pred));
        }

        return triples;
    }

    private MethodHoareTriple constructMustNotSignalTriple(CCR ccr, SootMethod pred, Expr pre)
    {
        StrongestPostConditionCalculator predSpCalc = getOrConstructSpCalculator(pred);
        Expr ccrPost = getPostCondForCCR(ccr, pre);

        Expr predRetVal = predSpCalc.getRetVal();
        return new MethodHoareTriple(ccrPost, exprFactory.mkEQ(predRetVal, exprFactory.mkFALSE()), pred);
    }

    private MethodHoareTriple constructMustSignalTriple(CCR ccr, SootMethod pred, Expr pre)
    {
        StrongestPostConditionCalculator predSpCalc = getOrConstructSpCalculator(pred);
        Expr ccrPost = getPostCondForCCR(ccr, pre);

        Expr predRetVal = predSpCalc.getRetVal();
        return new MethodHoareTriple(ccrPost, exprFactory.mkEQ(predRetVal, exprFactory.mkTRUE()), pred);
    }

    private Set<MethodHoareTriple> constructBroadcastTriples(Set<CCR> ccrsOnPred, SootMethod pred)
    {
        Set<MethodHoareTriple> triples = new HashSet<>();

        StrongestPostConditionCalculator predSpCalc = getOrConstructSpCalculator(pred);
        Expr predRetVal = predSpCalc.getRetVal();

        for (CCR ccr : ccrsOnPred)
        {
            // Post of ccr contains a call to pred, no need to pass it as a precondition.
            Expr ccrPost = getPostCondForCCR(ccr, exprFactory.mkTRUE());
            triples.add(new MethodHoareTriple(ccrPost, exprFactory.mkEQ(predRetVal, exprFactory.mkFALSE()), pred));
        }

        return triples;
    }

    private Expr getPostCondForCCR(CCR ccr, Expr pre)
    {
        StrongestPostConditionCalculator ccrSpCalc = getOrConstructSpCalculator(ccr.getAtomicSection());
        Expr identityStmtEncoding = ccrSpCalc.getEncodingForIdentityStatements(pre);
        return ccrSpCalc.getMethodEncoding(null, identityStmtEncoding);
    }

    private StrongestPostConditionCalculator getOrConstructSpCalculator(SootMethod m)
    {
        return spCalcFactory.getOrCompute(m);
    }
}
