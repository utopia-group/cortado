package edu.utexas.cs.utopia.cortado.integration;

import com.google.common.collect.ImmutableMap;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperation;
import soot.SootMethod;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The results of signal inference on various ccrs
 */
public class SignalInferenceResults
{
    private final List<CCR> ccrs;
    private final Map<CCR, Set<SignalOperation>> signalOperations;
    private final Map<SootMethod, Set<Unit>> preChecks;

    SignalInferenceResults(List<CCR> ccrs, Map<CCR, Set<SignalOperation>> signalOperations, Map<SootMethod, Set<Unit>> preChecks)
    {
        this.ccrs = ccrs;
        this.signalOperations = signalOperations;
        this.preChecks = preChecks;
    }

    public Map<SootMethod, Set<Unit>> getPreChecks()
    {
        return preChecks;
    }

    /**
     * @return the signal operations which need to be performed
     */
    public Map<CCR, Set<SignalOperation>> getSignalOperations()
    {
        return signalOperations;
    }

    /**
     * @return the {@link CCR}s that signal inference was performed over.
     */
    public List<CCR> getCCRs()
    {
        return ccrs;
    }

    /**
     * @param fragmentedMonitorToReplica the replica of a fragmented monitor
     * @return these signal inference results, migrated to the replica
     */
    @Nonnull
    public SignalInferenceResults migrateResultsToReplica(@Nonnull FragmentedMonitor.FragmentedMonitorToReplica fragmentedMonitorToReplica)
    {
        assert fragmentedMonitorToReplica.getCcrToReplicaMap().keySet().containsAll(getCCRs());

        // migrate signalInferenceResults to new CCRs
        final ImmutableMap<CCR, CCR> ccrToReplicaMap = fragmentedMonitorToReplica.getCcrToReplicaMap();
        final ImmutableMap<SootMethod, SootMethod> methodToReplicaMap = fragmentedMonitorToReplica.getReplicator().getMethodToReplicaMap();
        final Map<CCR, Set<SignalOperation>> signalOperationsReplica = getSignalOperations()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> ccrToReplicaMap.get(e.getKey()),
                        e -> e.getValue()
                                .stream()
                                .map(sigOp -> {
                                    assert methodToReplicaMap.containsKey(sigOp.getPredicate());
                                    SootMethod predicateReplica = methodToReplicaMap.get(sigOp.getPredicate());
                                    return new SignalOperation(predicateReplica, sigOp.isConditional(), sigOp.isBroadCast(), sigOp.isCascadedBroadcast());
                                }).collect(Collectors.toSet())
                ));

        // get replicated pre-checks
        final Map<SootMethod, Set<Unit>> replicatedPreChecks = getPreChecks()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> methodToReplicaMap.get(e.getKey()),
                        e -> e.getValue()
                                .stream()
                                .map(fragmentedMonitorToReplica.getReplicator().getUnitToReplicaMap()::get)
                                .collect(Collectors.toSet())
                ));
        final FragmentedMonitor fragmentedMonitorReplica = fragmentedMonitorToReplica.getFragmentedMonitorReplica();
        return new SignalInferenceResults(fragmentedMonitorReplica.getCCRs(), signalOperationsReplica, replicatedPreChecks);
    }
}
