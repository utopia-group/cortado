package edu.utexas.cs.utopia.cortado.integration;

import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.lockPlacement.ImmutableLockAssignment;
import edu.utexas.cs.utopia.cortado.lockPlacement.MutableLockAssignment;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperation;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import soot.Local;
import soot.SootMethod;
import soot.ValueBox;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Used to delay lock releases so to avoid re-acquisition for signalling
 */
class LockReleaseDelayer
{

    @SuppressWarnings("UnstableApiUsage")
    @Nonnull
    public static ImmutableLockAssignment delayReleaseAfterConditionCheck(@Nonnull ImmutableLockAssignment lockAssignment,
                                                                          @Nonnull Map<CCR, Set<SignalOperation>> ccrToSigOps,
                                                                          @Nonnull Map<SootMethod, Integer> predToLockID)
    {
        // map each fragment to the locks it needs to acquire in order to signal,
        // but does not already have
        final Map<Fragment, List<Integer>> fragToLocksNeededToSignal = lockAssignment.getFragments()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        frag -> {
                            final List<Local> predEvalLocals = frag.getAllUnits()
                                    .stream()
                                    .flatMap(ut -> ut.getUseAndDefBoxes()
                                            .stream()
                                            .map(ValueBox::getValue)
                                            .filter(value -> value instanceof Local)
                                            .map(value -> (Local) value)
                                            .filter(SootNamingUtils::isPredEvalLocal)
                                    ).collect(Collectors.toList());

                            final List<Integer> locksNeededToSignal;
                            final Set<SignalOperation> signalOperations = ccrToSigOps.get(frag.getEnclosingCCR());
                            if (signalOperations != null)
                            {
                                locksNeededToSignal = signalOperations.stream()
                                        .filter(sigOp -> {
                                            final String localNameForPredEval = SootNamingUtils.getLocalForPredEval(sigOp);
                                            return predEvalLocals.stream()
                                                    .map(Local::getName)
                                                    .anyMatch(localNameForPredEval::equals);
                                        }).map(SignalOperation::getPredicate)
                                        .map(predToLockID::get)
                                        .distinct()
                                        .filter(lock -> !lockAssignment.holdsLock(frag, lock))
                                        .collect(Collectors.toList());
                            }
                            else
                            {
                                locksNeededToSignal = new ArrayList<>();
                            }
                            return locksNeededToSignal;
                        }
                )).entrySet()
                .stream()
                .filter(entrySet -> !entrySet.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // new lock assignment starts as copy of this one
        final MutableLockAssignment lockAssignmentWithDelays = new MutableLockAssignment(lockAssignment);

        fragToLocksNeededToSignal.forEach((frag, locksNeededToSignal) -> {
            final Graph<Fragment> fragCFG = frag.getEnclosingCCR().getFragmentsCFG();
            locksNeededToSignal.forEach(lock -> {
                // Try to identify a cut along the fragment control-flow graph such that
                // (1) the cut disconnects the entry fragment from frag
                // (2) each node in the cut holds the lock needed to signal
                // (3) each node in the cut has a successor which can reach frag and which does not hold the lock
                //
                // by performing a reverse DFS from the fragment through the subset of the graph
                // which does not hold the lock. If there is a path to the entry fragment, then no such cut exists.
                final Set<Fragment> fragsNotHoldingLock = fragCFG.nodes()
                        .stream()
                        .filter(node -> !lockAssignmentWithDelays.holdsLock(node, lock))
                        .collect(Collectors.toSet());
                // It's possible that some other iteration of the loop caused frag to grab lock
                // in lockAssignmentWithDelays
                if(!fragsNotHoldingLock.contains(frag)) {
                    assert lockAssignmentWithDelays.holdsLock(frag, lock);
                    return;
                }
                final ImmutableGraph<Fragment> fragsNotHoldingLockCFGTranspose = ImmutableGraph.copyOf(
                        Graphs.transpose(
                                Graphs.inducedSubgraph(fragCFG, fragsNotHoldingLock)
                        )
                );
                final Set<Fragment> fragsWhichNeedToGrabLock = Graphs.reachableNodes(fragsNotHoldingLockCFGTranspose, frag);
                assert fragsWhichNeedToGrabLock.contains(frag);
                // if no entry fragments would need to grab the lock
                if(fragsWhichNeedToGrabLock.stream().noneMatch(f -> fragCFG.predecessors(f).isEmpty()))
                {
                    // see if we can delay lock release along all paths from the
                    // cut to frag without causing a deadlock
                    final boolean mayIntroduceDeadlock = fragsWhichNeedToGrabLock.stream()
                            .map(fragCFG::incidentEdges)
                            .flatMap(Collection::stream)
                            .distinct()
                            .anyMatch(edge -> {
                                Set<Integer> srcLocks = new HashSet<>();
                                if (fragsWhichNeedToGrabLock.contains(edge.nodeU()))
                                {
                                    srcLocks.add(lock);
                                }
                                srcLocks.addAll(lockAssignmentWithDelays.getLocksInOrder(edge.nodeU()));

                                Set<Integer> dstLocks = new HashSet<>();
                                if (fragsWhichNeedToGrabLock.contains(edge.nodeV()))
                                {
                                    dstLocks.add(lock);
                                }
                                dstLocks.addAll(lockAssignmentWithDelays.getLocksInOrder(edge.nodeU()));

                                final Comparator<Integer> lockOrder = lockAssignment.getLockOrder();
                                final Sets.SetView<Integer> locksBeingAcquired = Sets.difference(dstLocks, srcLocks);
                                final Sets.SetView<Integer> locksHeldDuringAcquisition = Sets.intersection(srcLocks, dstLocks);
                                final Optional<Integer> leastLockBeingAcquired = locksBeingAcquired.stream()
                                        .reduce(BinaryOperator.minBy(lockOrder));
                                final Optional<Integer> greatestLockHeld = locksHeldDuringAcquisition.stream()
                                        .reduce(BinaryOperator.maxBy(lockOrder));
                                if (leastLockBeingAcquired.isPresent() && greatestLockHeld.isPresent())
                                {
                                    assert !greatestLockHeld.get().equals(leastLockBeingAcquired.get());
                                    // out-of-order obtaining of locks if greatestLockHeld > leastLockBeingAcquired
                                    return lockOrder.compare(greatestLockHeld.get(), leastLockBeingAcquired.get()) > 0;
                                }
                                // all lock obtainment are in-order
                                return false;
                            });
                    if(!mayIntroduceDeadlock)
                    {
                        fragsWhichNeedToGrabLock.forEach(f -> lockAssignmentWithDelays.addLock(f, lock));
                    }
                }
            });
        });

        return ImmutableLockAssignment.copyOf(lockAssignmentWithDelays);
    }
}
