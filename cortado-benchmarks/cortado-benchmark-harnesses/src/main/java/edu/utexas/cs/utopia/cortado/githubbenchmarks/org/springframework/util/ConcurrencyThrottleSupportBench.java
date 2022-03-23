package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class ConcurrencyThrottleSupportBench
{
    @Param({"Implicit",
            "ExplicitOriginalCoarseSignalOpt",
            "Expresso",
            "Ablated",
//            "ExplicitCoarseSignalOptAtomicOpt"
    })
    String whichImplementation;
    ConcurrencyThrottleSupportInterface concurrencyThrottleSupport;

    @Param("0")  ///< non-positive value indicates default value.
                 /// Can be overridden by command line e.g. "-p threadLimitString=2,4,5"
    String threadLimitString;

    /**
     * During setup of trial, set thread limit to 2 * number of threads
     */
    @Setup(Level.Trial)
    public void setupConcurrencyThrottleSupport(BenchmarkParams params) {
        int threadLimit = Integer.parseInt(threadLimitString);
        if(threadLimit <= 0)
        {
            threadLimit = Math.max(params.getThreads() / 2, 1);
        }
        switch(this.whichImplementation) {
            case "Implicit":
                this.concurrencyThrottleSupport = new ImplicitConcurrencyThrottleSupport(threadLimit);
                break;
            case "ExplicitOriginalCoarseSignalOpt":
                this.concurrencyThrottleSupport = new ExplicitSignalOptConcurrencyThrottleSupport(threadLimit);
                break;
            case "Expresso":
                this.concurrencyThrottleSupport = new ImplicitConcurrencyThrottleSupportExpresso(threadLimit);
                break;
            case "Ablated":
                this.concurrencyThrottleSupport = new ImplicitConcurrencyThrottleSupportAblated(threadLimit);
                break;
            case "ExplicitCoarseSignalOptAtomicOpt":
                this.concurrencyThrottleSupport = new ExplicitAtomicConcurrencyThrottleSupport(threadLimit);
                break;
            default:
                throw new IllegalStateException("Unrecognized value for whichDisconnectableInputStream");
        }
    }

    private void doSomeWork(Blackhole bh)
    {
        bh.consume(0);
    }

    /**
     * access the state, do some work, then finish access
     */
    @Benchmark
    public void beforeAccess(Blackhole bh) throws InterruptedException
    {
        concurrencyThrottleSupport.beforeAccess();
        doSomeWork(bh);
        concurrencyThrottleSupport.afterAccess();
    }
}
