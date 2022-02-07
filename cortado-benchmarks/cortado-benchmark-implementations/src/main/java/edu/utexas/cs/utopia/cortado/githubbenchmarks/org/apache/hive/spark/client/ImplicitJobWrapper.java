package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hive.spark.client;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

/**
 * Inner class extracted from
 * https://github.com/apache/hive/blob/4446414f4478091db1eb20bc782a5c1825356153/spark-client/src/main/java/org/apache/hive/spark/client/RemoteDriver.java#L365-L485
 *
 * All non-monitor logic has been deleted
 */
class ImplicitJobWrapper implements JobWrapperInterface
{
    private int jobEndReceived;
    private final int numJobs;

    ImplicitJobWrapper(int numJobs)
    {
        jobEndReceived = 0;
        this.numJobs = numJobs;
    }

    private boolean allJobsReceived()
    {
        return jobEndReceived >= numJobs;
    }

    @Override
    @Atomic(waituntil="allJobsReceived")
    public void _waitForJobs() throws InterruptedException
    {
        allJobsReceived();
    }

    @Override
    @Atomic
    public void jobDone() {
        jobEndReceived++;
    }

    /**
     * Add main method so that all CCRs are reachable inside soot pointer analysis
     */
    public static void main(String[] args) throws InterruptedException
    {
        JobWrapperInterface jobWrapper = new ImplicitJobWrapper(Integer.parseInt(args[0]));
        jobWrapper._waitForJobs();
        jobWrapper.jobDone();
    }
}
