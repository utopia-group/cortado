package edu.utexas.cs.utopia.cortado.githubbenchmarks.java.util.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;


@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class ArrayBlockingQueueBench
{
    private BlockingQueue<Integer> blockingQueue;
    @SuppressWarnings("unused")
    @Param({"ExplicitCoarseOriginal",
            "Implicit",
            "Expresso",
            "Ablated"})
    private String whichImplementation;

    @Param("0")  ///< non-positive value indicates default value.
                 /// Can be overridden by command line e.g. "-p queueCapacityString=2,4,5"
    String queueCapacityString;

    @State(Scope.Thread)
    public static class ThreadData {
        int data;
        @Setup(Level.Iteration)
        public void setup()
        {
            data = (new Random()).nextInt();
        }
    }

    @Setup(Level.Iteration)
    public void setupBlockingQueue(BenchmarkParams params) {
        int queueCapacity = Integer.parseInt(queueCapacityString);
        if(queueCapacity <= 0)
        {
            queueCapacity = params.getThreads();
        }
        // initialize the DataListener
        switch(this.whichImplementation) {
            case "ExplicitCoarseOriginal":
                this.blockingQueue = new ArrayBlockingQueueWrapper<>(queueCapacity);
                break;
            case "Implicit":
                // Can't pass type parameter because cortado transformation strips generic status of class
                //noinspection unchecked
                this.blockingQueue = new ImplicitArrayBlockingQueue(queueCapacity);
                break;
            case "Expresso":
                // Can't pass type parameter because cortado transformation strips generic status of class
                //noinspection unchecked
                this.blockingQueue = new ImplicitArrayBlockingQueueExpresso(queueCapacity);
                break;
            case "Ablated":
                // Can't pass type parameter because cortado transformation strips generic status of class
                //noinspection unchecked
                this.blockingQueue = new ImplicitArrayBlockingQueueAblated(queueCapacity);
                break;
            default:
                String msg = String.format(
                        "Unrecognized value '%s' for whichBlockingQueue",
                        whichImplementation
                );
                throw new IllegalStateException(msg);
        }
    }

    @Benchmark
    @Group("PutAndTake")
    public void put(ThreadData threadData) throws InterruptedException
    {
        this.blockingQueue.put(threadData.data);
    }

    @Benchmark
    @Group("PutAndTake")
    public void take(Blackhole bh) throws InterruptedException
    {
        bh.consume(this.blockingQueue.take());
    }
}

