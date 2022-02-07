package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SignalOptConcurrencyThrottleSupport implements ConcurrencyThrottleSupportInterface
{
    private final int threadLimit;
    private int threadCount = 0;
    private final Lock lock = new ReentrantLock();
    private final Condition canAccess = lock.newCondition();

    public SignalOptConcurrencyThrottleSupport(int threadLimit)
    {
        this.threadLimit = threadLimit;
    }

    private boolean canAccess()
    {
        return threadCount < threadLimit;
    }

    @Override
    public void beforeAccess() throws InterruptedException
    {
        try
        {
            lock.lock();
            while(!canAccess())
            {
                canAccess.await();
            }
            threadCount++;
        } finally
        {
            lock.unlock();
        }
    }

    @Override
    public void afterAccess() throws InterruptedException
    {
        try
        {
            lock.lock();
            threadCount--;
            if(threadCount >= threadLimit - 1)
            {
                canAccess.signal();
            }
        } finally
        {
            lock.unlock();
        }
    }
}
