package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExplicitAtomicConcurrencyThrottleSupport implements ConcurrencyThrottleSupportInterface
{
    private final AtomicInteger threadCount = new AtomicInteger(0);
    int threadLimit;
    private final Lock lock = new ReentrantLock();
    private final Condition canAccess = lock.newCondition();

    public ExplicitAtomicConcurrencyThrottleSupport(int threadLimit)
    {
        if (threadLimit <= 0)
            throw new IllegalArgumentException("threadLimit must be positive");

        this.threadLimit = threadLimit;
    }

    private boolean canAccess()
    {
        return threadCount.get() < threadLimit;
    }

    public void beforeAccess() throws InterruptedException
    {
        try
        {
            lock.lock();
            while (!canAccess())
            {
                canAccess.await();
            }
            threadCount.getAndIncrement();
        } finally
        {
            lock.unlock();
        }
    }

    public void afterAccess()
    {
        int prevValue = threadCount.getAndDecrement();
        if(prevValue >= threadLimit)
        {
            try
            {
                lock.lock();
                canAccess.signal();
            } finally
            {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        ExplicitAtomicConcurrencyThrottleSupport obj = new ExplicitAtomicConcurrencyThrottleSupport(10);
        obj.beforeAccess();
        obj.afterAccess();
    }
}