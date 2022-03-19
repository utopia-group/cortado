package edu.utexas.cs.utopia.cortado.signalling;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;
import soot.SootMethod;

import java.util.*;
import java.util.stream.Collectors;

public abstract class SignalOperationInferencePhase
{
    protected final SootClass monitor;

    protected final Set<CCR> ccrs;

    protected final Map<CCR, Set<SignalOperation>> signalOperations = new HashMap<>();

    private final CortadoMonitorProfiler profiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
    private final Logger log = LoggerFactory.getLogger(SignalOperationInferencePhase.class.getName());

    public SignalOperationInferencePhase(SootClass monitor, Set<CCR> ccrs)
    {
        this.monitor = monitor;
        this.ccrs = ccrs;
    }

    /**
     * @return A map from CCRs to {@link SignalOperation}s.
     */
    public Map<CCR, Set<SignalOperation>> getSignalOperations()
    {
        return signalOperations;
    }

    protected void addSignalOperation(CCR ccr, SignalOperation sigOp)
    {
        if (!signalOperations.containsKey(ccr)) {
            signalOperations.put(ccr, new HashSet<>());
        }
        // handle conditional signals on predicates with parameters
        SootMethod pred = sigOp.getPredicate();
        if(sigOp.isConditional() && pred.getParameterCount() > 0)
        {
            log.warn("Changing conditional signal of predicate with parameters to unconditional");
            sigOp = new SignalOperation(pred,
                    false,
                    sigOp.isBroadCast(),
                    sigOp.isCascadedBroadcast()
                    );
        }
        // Handle case of multiple signals to same predicate
        Set<SignalOperation> sigOps = this.signalOperations.get(ccr);
        List<SignalOperation> otherSigOpsToPred = sigOps.stream()
                .filter(otherSigOp -> otherSigOp.getPredicate().equals(pred))
                .collect(Collectors.toList());
        if(!otherSigOpsToPred.isEmpty()) {
            assert(otherSigOpsToPred.size() == 1);
            SignalOperation otherSigOp = otherSigOpsToPred.get(0);
            if(!otherSigOp.equals(sigOp)) {
                // Exactly one should be a cascaded broadcast
                assert (otherSigOp.isCascadedBroadcast() ^ sigOp.isCascadedBroadcast());
                // Other sanity checks
                assert (!otherSigOp.isBroadCast());
                assert (!sigOp.isBroadCast());
                // Cascaded broadcast + unconditional signal -> unconditional signal
                // Cascaded broadcast + conditional signal -> cascaded broadcast
                boolean isConditional = otherSigOp.isConditional() & sigOp.isConditional();
                sigOp = new SignalOperation(pred, isConditional, false, isConditional);
                sigOps.remove(otherSigOp);
            }
        }
        sigOps.add(sigOp);
    }

    public abstract void inferSignalOperations();
}
