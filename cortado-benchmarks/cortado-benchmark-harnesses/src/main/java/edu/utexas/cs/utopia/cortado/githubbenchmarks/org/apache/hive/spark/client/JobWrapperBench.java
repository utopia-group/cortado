package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hive.spark.client;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class JobWrapperBench
{
    private volatile JobWrapperInterface jobWrapper;
    private Callable<JobWrapperInterface> jobWrapperFactory;
    private int numJobs;
    @SuppressWarnings("unused")
    @Param({"ExplicitOriginal",
            "Implicit",
            "Expresso",
            "Ablated"})
    private String whichImplementation;

    @Param("0")  ///< non-positive value indicates default value.
    /// Can be overridden by command line e.g. "-p numJobsString=2,4,5"
    String numJobsString;

    @Setup(Level.Iteration)
    public void setup(BenchmarkParams params) throws Exception
    {
        numJobs = Integer.parseInt(numJobsString);
        if(numJobs <= 0)
        {
            numJobs = params.getThreads() - 1;
        }
        int numJobsCopy = numJobs;
        switch(this.whichImplementation) {
            case "ExplicitOriginal":
                this.jobWrapperFactory = () -> new JobWrapper(numJobsCopy);
                break;
            case "Implicit":
                this.jobWrapperFactory = () -> new ImplicitJobWrapper(numJobsCopy);
                break;
            case "Expresso":
                this.jobWrapperFactory = () -> new ImplicitJobWrapperExpresso(numJobsCopy);
                break;
            case "Ablated":
                this.jobWrapperFactory = () -> new ImplicitJobWrapperAblated(numJobsCopy);
                break;
            default:
                String msg = String.format(
                        "Unrecognized value '%s' for whichBlockingQueue",
                        whichImplementation
                );
                throw new IllegalStateException(msg);
        }
        jobWrapper = jobWrapperFactory.call();
        leaderDone.set(false);
        leaderIsDeclared.set(false);
        numJobsRemaining.set(numJobs);
    }

    private static final AtomicBoolean leaderIsDeclared = new AtomicBoolean();
    private static final AtomicBoolean leaderDone = new AtomicBoolean(false);
    private static final AtomicInteger numJobsRemaining = new AtomicInteger();

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
            waitForJobs();
            jobWrapper = jobWrapperFactory.call();
            numJobsRemaining.set(numJobs);
            if(++threadData.count == params.getMeasurement().getBatchSize())
            {
                leaderDone.set(true);
            }
        }
        else
        {
            completeJob();
            // ensure benchmark terminates
            if(++threadData.count == params.getMeasurement().getBatchSize())
            {
                while(!leaderDone.get())
                {
                    completeJob();
                }
            }
        }
    }

    private void waitForJobs() throws InterruptedException
    {
        jobWrapper._waitForJobs();
    }

    private void completeJob()
    {
        while(numJobsRemaining.getAndUpdate(x -> x > 0 ? x - 1 : x) <= 0 && !leaderDone.get()); // spin
        jobWrapper.jobDone();
    }
}
