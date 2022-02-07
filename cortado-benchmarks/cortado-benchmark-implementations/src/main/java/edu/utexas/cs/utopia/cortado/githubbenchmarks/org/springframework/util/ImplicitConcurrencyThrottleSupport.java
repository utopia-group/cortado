package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

public class ImplicitConcurrencyThrottleSupport implements ConcurrencyThrottleSupportInterface
{
    private int threadCount = 0, threadLimit;

    public ImplicitConcurrencyThrottleSupport(int threadLimit)
    {
        if (threadLimit <= 0)
            throw new IllegalArgumentException("threadLimit must be positive");

        this.threadLimit = threadLimit;
    }

    private boolean canAccess()
    {
        return threadCount < threadLimit;
    }

    @Atomic(waituntil="canAccess")
    public void beforeAccess()
    {
        canAccess();
        threadCount++;
    }

    @Atomic
    public void afterAccess()
    {
        threadCount--;
    }

    /**
     * Add main method so that all CCR methods are reachable in soot pointer
     * analysis
     */
    public static void main(String[] args)
    {
        ImplicitConcurrencyThrottleSupport obj = new ImplicitConcurrencyThrottleSupport(10);
        obj.beforeAccess();
        obj.afterAccess();
    }
}
