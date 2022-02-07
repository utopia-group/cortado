package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hive.spark.client;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inner class extracted from
 * https://github.com/apache/hive/blob/4446414f4478091db1eb20bc782a5c1825356153/spark-client/src/main/java/org/apache/hive/spark/client/RemoteDriver.java#L365-L485
 *
 * All non-monitor logic has been deleted
 */
class JobWrapper implements JobWrapperInterface
{
    private final AtomicInteger jobEndReceived;
    private final int numJobs;

    JobWrapper(int numJobs)
    {
        jobEndReceived = new AtomicInteger();
        this.numJobs = numJobs;
    }

    @Override
    public void _waitForJobs() throws InterruptedException
    {
        synchronized (jobEndReceived) {
            while (jobEndReceived.get() < numJobs) {
                jobEndReceived.wait();
            }
        }
    }

    @Override
    public void jobDone() {
        synchronized (jobEndReceived) {
            jobEndReceived.incrementAndGet();
            jobEndReceived.notifyAll();
        }
    }

}
