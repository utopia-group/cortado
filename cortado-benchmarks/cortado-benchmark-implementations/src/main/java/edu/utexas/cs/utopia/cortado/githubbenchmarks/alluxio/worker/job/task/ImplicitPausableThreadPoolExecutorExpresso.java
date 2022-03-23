package edu.utexas.cs.utopia.cortado.githubbenchmarks.alluxio.worker.job.task;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ImplicitPausableThreadPoolExecutorExpresso extends AbstractPausableThreadPoolExecutor
{
    public ImplicitPausableThreadPoolExecutorExpresso(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory)
    {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        throw new RuntimeException("Expected cortado to implement this method");
    }

    @Override
    int getNumActiveTasks()
    {
        throw new RuntimeException("Expected cortado to implement this method");
    }

    @Override
    void pause()
    {
        throw new RuntimeException("Expected cortado to implement this method");
    }

    @Override
    void resume()
    {
        throw new RuntimeException("Expected cortado to implement this method");
    }

    @Override
    void _beforeExecute(Thread t, Runnable r)
    {
        throw new RuntimeException("Expected cortado to implement this method");
    }
}
