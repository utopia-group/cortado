package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.kafka.trogdor.workload;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class RoundTripWorkerBench
{
    private RoundTripWorkerInterface roundTripWorker;
    private int maxMessages;
    @SuppressWarnings("unused")
    @Param({
            "ExplicitOriginal",
            "Implicit",
            "Expresso",
            "Ablated"
    })
    private String whichImplementation;


    @Setup(Level.Iteration)
    public void setup(BenchmarkParams params) throws Exception
    {
        maxMessages = 5;
        switch(this.whichImplementation) {
            case "ExplicitOriginal":
                this.roundTripWorker = new RoundTripWorker(maxMessages);
                break;
            case "Implicit":
                this.roundTripWorker = new ImplicitRoundTripWorker(maxMessages);
                break;
            case "Expresso":
                this.roundTripWorker = new ImplicitRoundTripWorkerExpresso(maxMessages);
                break;
            case "Ablated":
                this.roundTripWorker = new ImplicitRoundTripWorkerAblated(maxMessages);
                break;
            default:
                String msg = String.format(
                        "Unrecognized value '%s' for whichBlockingQueue",
                        whichImplementation
                );
                throw new IllegalStateException(msg);
        }
        leaderIsDeclared.set(false);
        leaderIsDone.set(false);
    }

    private static final AtomicBoolean leaderIsDeclared = new AtomicBoolean();
    private static final AtomicInteger numAcksRemaining = new AtomicInteger();
    private static final AtomicBoolean leaderIsDone = new AtomicBoolean();

    @State(Scope.Thread)
    public static class ThreadData
    {
        boolean leader;
        int count;

        @Setup(Level.Iteration)
        public void setup()
        {
            leader = leaderIsDeclared.compareAndSet(false, true);
            count = 0;
        }
    }

    @Benchmark
    public void benchmark(ThreadData threadData, BenchmarkParams params) throws Exception
    {
        if(threadData.leader)
        {
            startAndWaitForAcks();
            if(++threadData.count == params.getMeasurement().getBatchSize())
            {
                leaderIsDone.set(true);
            }
        }
        else
        {
            sendAck();
            if(++threadData.count == params.getMeasurement().getBatchSize())
            {
                while(!leaderIsDone.get())
                {
                    sendAck();
                }
            }
        }
    }

    private void startAndWaitForAcks() throws InterruptedException
    {
        roundTripWorker.start();
        numAcksRemaining.set(maxMessages);
        roundTripWorker._waitForNoAcks();
        roundTripWorker.stop();
    }

    private void sendAck()
    {
        roundTripWorker._ackSend();
    }
}
