package edu.utexas.cs.utopia.cortado.githubbenchmarks.alluxio.worker.job.task;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

import java.util.concurrent.*;

public class ImplicitPausableThreadPoolExecutor extends AbstractPausableThreadPoolExecutor
{
    private int mNumPaused;
    boolean mIsPaused;

    public ImplicitPausableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                                 TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        mIsPaused = false;
        mNumPaused = 0;
    }

    @Override
    @Atomic
    public int getNumActiveTasks()
    {
        return super.getActiveCount() - mNumPaused;
    }

    @Override
    @Atomic
    public void pause()
    {
        mIsPaused = true;
    }

    @Override
    @Atomic
    public void resume()
    {
        mIsPaused = false;
    }

    @Atomic
    public boolean _incrementNumPausedIfPaused()
    {
        if(mIsPaused)
        {
            mNumPaused++;
            return true;
        }
        return false;
    }

    private boolean notPaused()
    {
        return !mIsPaused;
    }

    @Atomic(waituntil="notPaused")
    public void _waituntilNotPaused()
    {
        notPaused();
        mNumPaused--;
    }

    @Override
    public void _beforeExecute(Thread t, Runnable r)
    {
        if(_incrementNumPausedIfPaused())
        {
            _waituntilNotPaused();
        }
    }

    /**
     * Add main method so that all CCRs are reachable inside soot pointer analysis
     */
    public static void main(String[] args)
    {
        int corePoolSize = 1;
        int maximumPoolSize = 2;
        long keepAliveTime = 3;
        TimeUnit unit = TimeUnit.MILLISECONDS;
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(100);
        final ImplicitPausableThreadPoolExecutor implicitPausableThreadPoolExecutor = new ImplicitPausableThreadPoolExecutor(
                corePoolSize, maximumPoolSize, keepAliveTime,
                unit, workQueue, threadFactory);
        implicitPausableThreadPoolExecutor.pause();
        implicitPausableThreadPoolExecutor.resume();
        implicitPausableThreadPoolExecutor.getNumActiveTasks();
        final DummyRunnable runnable = new DummyRunnable();
        implicitPausableThreadPoolExecutor._beforeExecute(new Thread(runnable), runnable);
        implicitPausableThreadPoolExecutor._waituntilNotPaused();
        implicitPausableThreadPoolExecutor._incrementNumPausedIfPaused();
    }
}
