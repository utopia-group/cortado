package edu.utexas.cs.utopia.cortado.integration;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.CCRMarker;
import edu.utexas.cs.utopia.cortado.signalling.ExpressoSignalInference;
import edu.utexas.cs.utopia.cortado.signalling.SignalAnnotationExtractor;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperationInferencePhase;
import edu.utexas.cs.utopia.cortado.signalling.SignalPredicateEvalInjector;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.vcgen.MonitorInvariantGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ImplicitMonitorReader
{
    private final static Logger log = LoggerFactory.getLogger(ImplicitMonitorReader.class.getName());

    /**
     * Extract CCRs, transforming them to an intermediate state,
     * and perform signal inference.
     *
     * The extracted CCRs do not have fragments yet, upon return.
     *
     * Also, sets {@link CachedMayRWSetAnalysis#setMayRWSetAnalysis(SootClass)} to the
     * current monitor class
     *
     * @param monitorClass the monitor class
     * @param useAnnotations if true, use signal annotations. Otherwise, use the expresso algorithm.
     * @return the results of signal inference
     */
    public SignalInferenceResults setupCCRsAndInferSignals(SootClass monitorClass, boolean useAnnotations, boolean cascadingBcasts)
    {
        // grab our class name (for logging) and the monitor profiler
        String className = monitorClass.getName();
        final CortadoMonitorProfiler monitorProfiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
        // also alias the jtp pack
        final Pack jtpPack = PackManager.v().getPack("jtp");

        // Extract CCRs and map into intermediate representation
        CCRMarker ccrMarker = new CCRMarker();
        Transform ccrMarkerT = new Transform("jtp.CCRMarker." + className, ccrMarker);
        jtpPack.add(ccrMarkerT);
        log.debug("Translating CCRs into intermediate atomic sections");
        monitorClass.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .map(SootMethod::getActiveBody)
                .forEach(ccrMarkerT::apply);

        // build our rw-set analysis
        CachedMayRWSetAnalysis.getInstance().setMayRWSetAnalysis(monitorClass);

        // Infer invariant
        log.debug("Beginning invariant inference");
        MonitorInvariantGenerator invGen = MonitorInvariantGenerator.getInstance(monitorClass);
        invGen.inferInvariant();

        // begin signal inference
        log.debug("Beginning inferring signal for " + monitorClass.getName());
        monitorProfiler.pushEvent("signalInference");
        // get the CCRs as a set
        Set<CCR> ccrsAsSet = new HashSet<>(ccrMarker.getCCRs());
        // infer signals
        SignalOperationInferencePhase sigOpInf;
        if(useAnnotations)
        {
            sigOpInf = new SignalAnnotationExtractor(monitorClass, ccrsAsSet);
        }
        else
        {
            sigOpInf = new ExpressoSignalInference(monitorClass, ccrsAsSet, cascadingBcasts);
        }
        sigOpInf.inferSignalOperations();
        // record signal operations in profiler
        sigOpInf.getSignalOperations()
                .values()
                .stream()
                .flatMap(Collection::stream)
                .forEach(monitorProfiler::recordSigOp);

        log.debug("Signal inference complete.");
        monitorProfiler.popEvent();

        // Inject predicate evaluation for conditional signalling.
        log.debug("Inject predicate evaluation for conditional signalling for " + monitorClass.getName());

        SignalPredicateEvalInjector predEvalInjector = new SignalPredicateEvalInjector(sigOpInf.getSignalOperations(), ccrMarker.getOriginalCCRStartInstr());
        Transform predEvalInjectorT = new Transform("jtp.predEvalInjector." + className, predEvalInjector);

        jtpPack.add(predEvalInjectorT);
        monitorClass.getMethods()
                    .stream()
                    .filter(CCR::isCCRMethod)
                    .map(SootMethod::getActiveBody)
                    .forEach(predEvalInjectorT::apply);

        // return the results of signal inference, with empty set added in
        return new SignalInferenceResults(ccrMarker.getCCRs(), sigOpInf.getSignalOperations(), predEvalInjector.getPredPreChecks());
    }
}
