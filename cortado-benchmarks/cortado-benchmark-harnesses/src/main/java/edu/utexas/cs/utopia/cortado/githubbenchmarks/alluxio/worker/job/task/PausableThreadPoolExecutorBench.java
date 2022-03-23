package edu.utexas.cs.utopia.cortado.githubbenchmarks.alluxio.worker.job.task;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class PausableThreadPoolExecutorBench
{
    @Param({
            "Implicit",
            "Expresso",
            "ExplicitOriginal",
            "Ablated",
    })
    String whichImplementation;
    AbstractPausableThreadPoolExecutor pausableThreadPoolExecutor;
    int numThreads;

    /**
     * During setup of trial, set thread limit to 2 * number of threads
     */
    @Setup(Level.Iteration)
    public void setupConcurrencyThrottleSupport(BenchmarkParams params) {
        int corePoolSize = 1;
        int maximumPoolSize = 2;
        long keepAliveTime = 3;
        TimeUnit unit = TimeUnit.MILLISECONDS;
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(100);
        switch(this.whichImplementation) {
            case "ExplicitOriginal":
                this.pausableThreadPoolExecutor = new PausableThreadPoolExecutor(
                        corePoolSize, maximumPoolSize, keepAliveTime,
                        unit, workQueue, threadFactory);
                break;
            case "Implicit":
                this.pausableThreadPoolExecutor = new ImplicitPausableThreadPoolExecutor(
                        corePoolSize, maximumPoolSize, keepAliveTime,
                        unit, workQueue, threadFactory);
                break;
            case "Expresso":
                this.pausableThreadPoolExecutor = new ImplicitPausableThreadPoolExecutorExpresso(
                        corePoolSize, maximumPoolSize, keepAliveTime,
                        unit, workQueue, threadFactory);
                break;
            case "Ablated":
                this.pausableThreadPoolExecutor = new ImplicitPausableThreadPoolExecutorAblated(
                        corePoolSize, maximumPoolSize, keepAliveTime,
                        unit, workQueue, threadFactory);
                break;
            default:
                throw new IllegalStateException("Unrecognized value for whichDisconnectableInputStream");
        }
        assert pauseRequesterCount.get() == 0;
        numThreads = params.getThreads();
    }

    private final static AtomicInteger pauseRequesterCount = new AtomicInteger(0);
    private final static AtomicBoolean leaderIsAssigned = new AtomicBoolean(false);

    @State(Scope.Thread)
    public static class ThreadData
    {
        Runnable r = () -> {};
        Thread t = new Thread(r);
        int data;
        int count;
        boolean leader;
        @Setup(Level.Iteration)
        public void setup()
        {
            data = new Random().nextInt();
            count = 0;
            leader = leaderIsAssigned.compareAndSet(false, true);
        }
        @TearDown(Level.Iteration)
        public void teardown()
        {
            leaderIsAssigned.set(false);
        }
    }

    @Benchmark
    public void benchmark(ThreadData threadData, Blackhole bh, BenchmarkParams params)
    {
        if(threadData.leader)
        {
            pause(threadData, bh, params);
        }
        else
        {
            beforeExecute(threadData, bh, params);
        }
    }

    public void pause(ThreadData threadData, Blackhole bh, BenchmarkParams params)
    {
        if(++threadData.count % 10 != 0)
        {
            pausableThreadPoolExecutor.pause();
        }
        if(threadData.count % 10 == 0 || pausableThreadPoolExecutor.getNumActiveTasks() <= -numThreads / 2)
        {
            pausableThreadPoolExecutor.resume();
        }
    }


    public void beforeExecute(ThreadData threadData, Blackhole bh, BenchmarkParams params)
    {
        pausableThreadPoolExecutor._beforeExecute(threadData.t, threadData.r);
    }
}
