package edu.utexas.cs.utopia.cortado.githubbenchmarks.us.codecraft.webmagic.thread;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class CountableThreadPoolBench
{
    @Param({
            "ExplicitOriginal",
            "Implicit",
            "Expresso",
            "Ablated",
    })
    String whichImplementation;
    AbstractCountableThreadPool countableThreadPool;

    @Param("0")  ///< non-positive value indicates default value.
                 /// Can be overridden by command line e.g. "-p threadNumString=2,4,5"
    String threadNumString;

    @Setup(Level.Iteration)
    public void setup(BenchmarkParams params) {
        int threadNum = Integer.parseInt(threadNumString);
        if(threadNum <= 0)
        {
            threadNum = Math.max(params.getThreads() / 2, 1);
        }
        else
        {
            threadNum = Math.min(params.getThreads() / 2, threadNum);
        }
        if(threadNum <= 0)
        {
            throw new IllegalStateException("threadNum is non-positive");
        }
        switch(this.whichImplementation) {
            case "ExplicitOriginal":
                this.countableThreadPool = new CountableThreadPool(threadNum);
                break;
            case "Implicit":
                this.countableThreadPool = new ImplicitCountableThreadPool(threadNum);
                break;
            case "Expresso":
                this.countableThreadPool = new ImplicitCountableThreadPoolExpresso(threadNum);
                break;
            case "Ablated":
                this.countableThreadPool = new ImplicitCountableThreadPoolAblated(threadNum);
                break;
            default:
                throw new IllegalStateException("Unrecognized value for whichDisconnectableInputStream");
        }
    }

    @TearDown(Level.Iteration)
    public void teardown()
    {
        countableThreadPool.shutdown();
        if(!countableThreadPool.isShutdown())
        {
            throw new RuntimeException("Shutdown failed");
        }
    }

    @State(Scope.Thread)
    static public class ThreadData
    {
        int data;
        final Random random = new Random();
        Runnable r = () -> {
            data = random.nextInt();
        };
    }

    @Benchmark
    @Group("All")
    public void enterAndExit(ThreadData threadData, Blackhole bh)
    {
        countableThreadPool.execute(threadData.r);
        bh.consume(threadData.data);
    }

    @Benchmark
    @Group("All")
    public void checkNumThreads(Blackhole bh)
    {
        bh.consume(countableThreadPool.getThreadAlive());
    }
}
