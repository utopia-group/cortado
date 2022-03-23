package edu.utexas.cs.utopia.cortado.githubbenchmarks.io.realm.internal.async;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class RealmThreadPoolExecutorBench
{
    private RealmThreadPoolExecutorInterface poolExecutor;
    @SuppressWarnings("unused")
    @Param({"ExplicitCoarseOriginal",
            "Implicit",
            "Expresso",
            "Ablated"})
    private String whichImplementation;

    @State(Scope.Thread)
    public static class ThreadData
    {
        int invocationCount;
        int numJobsSincePause;
        int numJobsSinceResume;

        @Setup(Level.Iteration)
        public void setup()
        {
            invocationCount = 0;
            numJobsSincePause = 0;
            numJobsSinceResume = 0;
        }
    }

    final AtomicInteger numResumingThreadsActive = new AtomicInteger();
    final AtomicBoolean someResumingThreadIsActive = new AtomicBoolean();

    @Setup(Level.Iteration)
    public void setupPoolExecutor(BenchmarkParams benchmarkParams) {
        // initialize the DataListener
        switch(this.whichImplementation) {
            case "ExplicitCoarseOriginal":
                this.poolExecutor = new RealmThreadPoolExecutor();
                break;
            case "Implicit":
                // after cortado, class is no longer a generic
                this.poolExecutor = new ImplicitRealmThreadPoolExecutor();
                break;
            case "Expresso":
                this.poolExecutor = new ImplicitRealmThreadPoolExecutorExpresso();
                break;
            case "Ablated":
                this.poolExecutor = new ImplicitRealmThreadPoolExecutorAblated();
                break;
            default:
                String msg = String.format(
                        "Unrecognized value '%s' for whichImplementation",
                        whichImplementation
                );
                throw new IllegalStateException(msg);
        }
        assert benchmarkParams.getThreads() % 2 == 0;
        assert benchmarkParams.getThreads() > 0;
        numResumingThreadsActive.set(benchmarkParams.getThreads() / 2);
        someResumingThreadIsActive.set(true);
    }

    @Benchmark
    @Group("Jobs")
    public void startJob(ThreadData threadData, BenchmarkParams benchmarkParams)
    {
        this.poolExecutor.beforeExecute(null, null);
        if(++threadData.numJobsSincePause == 10)
        {
            this.poolExecutor.pause();
            threadData.numJobsSincePause = 0;
        }
        // resume if we're leaving to avoid deadlock
        if(++threadData.invocationCount == benchmarkParams.getMeasurement().getBatchSize()
            || !someResumingThreadIsActive.get())
        {
            this.poolExecutor.resume();
        }
    }

    @Benchmark
    @Group("Jobs")
    public void resumeJobs(ThreadData threadData, BenchmarkParams benchmarkParams)
    {
        if(++threadData.numJobsSinceResume == 10)
        {
            this.poolExecutor.resume();
            threadData.numJobsSinceResume = 0;
        }

        if(++threadData.invocationCount == benchmarkParams.getMeasurement().getBatchSize())
        {
            if(numResumingThreadsActive.decrementAndGet() <= 0)
            {
                someResumingThreadIsActive.set(false);
                this.poolExecutor.resume();
            }
        }
    }
}

