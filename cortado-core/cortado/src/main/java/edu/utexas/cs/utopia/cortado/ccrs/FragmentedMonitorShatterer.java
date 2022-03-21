package edu.utexas.cs.utopia.cortado.ccrs;

import com.google.common.collect.Streams;
import edu.utexas.cs.utopia.cortado.lockPlacement.ImmutableLockAssignment;
import soot.Unit;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Designed to shatter fragments into fragments
 * which each only contain one unit.
 */
public class FragmentedMonitorShatterer
{
    private final FragmentedMonitor shatteredFragmentedMonitor;
    private final Map<Fragment, Fragment> shatteredFragmentToOriginal = new HashMap<>();

    /**
     * @param fragmentedMonitor the monitor to shatter
     */
    @SuppressWarnings("UnstableApiUsage")
    public FragmentedMonitorShatterer(@Nonnull FragmentedMonitor fragmentedMonitor)
    {
        // build CCRs which use the finest fragment partitioner (1 statement per fragment)
        final FragmentPartitioner finestFragmentPartitioner = new FinestFragmentPartitioner();
        final List<CCR> shatteredCCRs = fragmentedMonitor.getCCRs()
                .stream()
                .map(ccr -> new CCR(ccr.getAtomicSection()))
                .peek(finestFragmentPartitioner::partitionIntoFragments)
                .collect(Collectors.toList());
        // store map from new fragments to their old ones
        Streams.forEachPair(fragmentedMonitor.getCCRs().stream(),
                shatteredCCRs.stream(),
                (ccr, shatteredCCR) -> {
                    // Map units to their shattered fragments
                    final Map<Unit, Fragment> unitToShatteredFragment = shatteredCCR.computeUnitToFragmentMap();
                    // Map units to their original fragment
                    // record the map from shattered fragment to original
                    ccr.computeUnitToFragmentMap()
                            .forEach((unit, originalFragment) -> shatteredFragmentToOriginal.put(
                                    unitToShatteredFragment.get(unit),
                                    originalFragment
                            ));
        });
        // build new fragmented monitor
        this.shatteredFragmentedMonitor = new FragmentedMonitor(fragmentedMonitor.getMonitor(), shatteredCCRs);
        // If not all shattered fragments were mapped, something went wrong
        if(!shatteredFragmentToOriginal.keySet().containsAll(shatteredFragmentedMonitor.getFragments()))
        {
            throw new IllegalStateException("Some shattered fragments are not associated to any original fragment.");
        }
    }
}
