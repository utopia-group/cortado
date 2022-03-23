package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hive.spark.client;

public class ImplicitJobWrapperExpresso implements JobWrapperInterface
{
    @SuppressWarnings("unused")
    ImplicitJobWrapperExpresso(int numJobs)
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }

    @Override
    public void _waitForJobs() throws InterruptedException
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }

    @Override
    public void jobDone()
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }
}
