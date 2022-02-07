package edu.utexas.cs.utopia.cortado.githubbenchmarks.alluxio.worker.job.task;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AbstractPausableThreadPoolExecutor extends ThreadPoolExecutor
{
    public AbstractPausableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                              TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory)
    {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    abstract int getNumActiveTasks();

    abstract void pause();

    abstract void resume();

    abstract void _beforeExecute(Thread t, Runnable r);
}
