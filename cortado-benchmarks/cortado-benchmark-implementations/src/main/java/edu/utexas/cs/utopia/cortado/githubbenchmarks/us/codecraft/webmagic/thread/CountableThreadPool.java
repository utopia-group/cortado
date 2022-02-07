package edu.utexas.cs.utopia.cortado.githubbenchmarks.us.codecraft.webmagic.thread;

//package us.codecraft.webmagic.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread pool for workers.<br><br>
 * Use {@link java.util.concurrent.ExecutorService} as inner implement. <br><br>
 * New feature: <br><br>
 * 1. Block when thread pool is full to avoid poll many urls without process. <br><br>
 * 2. Count of thread alive for monitor.
 *
 * @author code4crafer@gmail.com
 * @since 0.5.0
 */
public class CountableThreadPool extends AbstractCountableThreadPool
{

    private int threadNum;

    private AtomicInteger threadAlive = new AtomicInteger();

    private ReentrantLock reentrantLock = new ReentrantLock();

    private Condition condition = reentrantLock.newCondition();

    public CountableThreadPool(int threadNum) {
        this(threadNum, Executors.newFixedThreadPool(threadNum));
    }

    public CountableThreadPool(int threadNum, ExecutorService executorService) {
        super(executorService);
        this.threadNum = threadNum;
    }

    @Override
    public int getThreadAlive() {
        return threadAlive.get();
    }

    @Override
    public int getThreadNum() {
        return threadNum;
    }

    @Override
    public void _enterExecute()
    {
        if(threadAlive.getAndUpdate(x -> x < threadNum ? x + 1 : x) >= threadNum)
        {
            reentrantLock.lock();
            try
            {
                while (threadAlive.getAndUpdate(x -> x < threadNum ? x + 1 : x) >= threadNum)
                {
                    try
                    {
                        condition.await();
                    } catch (InterruptedException e)
                    {
                    }
                }
            } finally
            {
                reentrantLock.unlock();
            }
        }
    }

    @Override
    public void _exitRun()
    {
        try {
            reentrantLock.lock();
            threadAlive.decrementAndGet();
            condition.signal();
        } finally {
            reentrantLock.unlock();
        }
    }
}
