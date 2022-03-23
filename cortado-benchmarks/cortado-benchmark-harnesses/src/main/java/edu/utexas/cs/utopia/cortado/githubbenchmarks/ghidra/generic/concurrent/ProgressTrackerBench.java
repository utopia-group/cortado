package edu.utexas.cs.utopia.cortado.githubbenchmarks.ghidra.generic.concurrent;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class ProgressTrackerBench
{
    private static final int ITEMS_PER_IT = 100000;

    @Param({"Expresso",
            "Ablated",
            "Implicit",
            "ExplicitCoarseOriginal",
//            "ExplicitFine",
//            "ExplicitFineAtomicOpt"
    })
    String whichImplementation;
    ProgressTrackerInterface monitor;

    private void initializeMonitor()
    {
        switch(whichImplementation)
        {
            case "ExplicitCoarseOriginal":
                monitor = new ProgressTracker();
                break;
            case "ExplicitFine":
                monitor = new ProgressTrackerFineGrainedLocking();
                break;
            case "ExplicitFineAtomicOpt":
                monitor = new ProgressTrackerFineGrainedLockingACount();
                break;
            case "Implicit":
                monitor = new ImplicitProgressTracker();
                break;
            case "Expresso":
                monitor = new ImplicitProgressTrackerExpresso();
                break;
            case "Ablated":
                monitor = new ImplicitProgressTrackerAblated();
                break;
            default:
                throw new IllegalArgumentException("Unrecognized implementation " + whichImplementation);
        }
    }

    @Setup(Level.Iteration)
    public void setupProgressTracker(BenchmarkParams params)
    {
        initializeMonitor();
        monitor.itemsAdded(((params.getThreads())/2)*ITEMS_PER_IT);
    }

    @TearDown(Level.Iteration)
    public void tearDown()
    {
        initializeMonitor();
    }

    @Benchmark
    @Group("TrackTask")
    public void completeTask(Blackhole b)
    {
        // Get a new ID.
        b.consume(monitor.getNextID());

        // Start the task.
        monitor.itemStarted();

        // Query inProgressCount
        b.consume(monitor.getItemsInProgressCount());

        // Complete or cancel item.
        monitor.inProgressItemCompletedOrCancelled();

        // Query isDone
//        b.consume(monitor.isDone());
    }

    @Benchmark
    @Group("TrackTask")
    public void neverStartTask(Blackhole b)
    {
        // Get a new ID.
        b.consume(monitor.getNextID());

        // "Remove" task.
        monitor.neverStartedItemsRemoved(1);

        // Query completedItemCount.
        b.consume(monitor.getCompletedItemCount());

        // Query isDone
//        b.consume(monitor.isDone());
    }

    // TODO: Find a better way to benhmark waituntil method.
    // @Benchmark
    // @Group("TrackTask")
    // public void waiting(Blackhole b)
    // {
    //     try
    //     {
    //         monitor.waitUntilDone();
    //     }
    //     catch(InterruptedException e)
    //     {
    //         assert false;
    //     }

    //     b.consume(monitor.isDone());
    // }
}
