package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hive.spark.client;

public interface JobWrapperInterface
{
    void _waitForJobs() throws InterruptedException;

    void jobDone();
}
