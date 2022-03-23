package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hadoop.metrics2.impl;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class SinkQueueBench
{
    private SinkQueueInterface<Integer> sinkQueue;
    @SuppressWarnings("unused")
    @Param({"Original",
            "Implicit",
            "Expresso",
            "Ablated"})
    private String whichImplementation;

    @Param("0")  ///< non-positive value indicates default value.
                 /// Can be overridden by command line e.g. "-p queueCapacityString=2,4,5"
    String queueCapacityString;

    @Setup(Level.Trial)
    public void setupSinkQueue(BenchmarkParams params) {
        int queueCapacity = Integer.parseInt(queueCapacityString);
        if(queueCapacity <= 0)
        {
            queueCapacity = params.getThreads();
        }
        // initialize the DataListener
        switch(this.whichImplementation) {
            case "Original":
                this.sinkQueue = new SinkQueueWrapper<>(queueCapacity);
                break;
            case "Implicit":
                // Can't pass type parameter because cortado transformation strips generic status of class
                //noinspection unchecked
                this.sinkQueue = new ImplicitSinkQueue(queueCapacity);
                break;
            case "Expresso":
                // Can't pass type parameter because cortado transformation strips generic status of class
                //noinspection unchecked
                this.sinkQueue = new ImplicitSinkQueueExpresso(queueCapacity);
                break;
            case "Ablated":
                // Can't pass type parameter because cortado transformation strips generic status of class
                //noinspection unchecked
                this.sinkQueue = new ImplicitSinkQueueAblated(queueCapacity);
                break;
            default:
                String msg = String.format(
                        "Unrecognized value '%s' for whichImplementation",
                        whichImplementation
                );
                throw new IllegalStateException(msg);
        }
    }

    @Benchmark
    @Group("putAndTake")
    public void put(Blackhole bh) throws InterruptedException
    {
        boolean finishedEnqueue = false;
        while(!finishedEnqueue){
            finishedEnqueue = this.sinkQueue.enqueue(17);
            bh.consume(finishedEnqueue);
        }
    }

    @Benchmark
    @Group("putAndTake")
    public void take(Blackhole bh) throws InterruptedException
    {
        bh.consume(this.sinkQueue.dequeue());
    }
}