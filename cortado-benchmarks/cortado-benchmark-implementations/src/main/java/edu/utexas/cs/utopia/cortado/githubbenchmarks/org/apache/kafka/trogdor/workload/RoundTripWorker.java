package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.kafka.trogdor.workload;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Extracted synchronization routines from
 * https://github.com/apache/kafka/blob/dccc82ea9dca1c29a86dd655802d26f646615317/tools/src/main/java/org/apache/kafka/trogdor/workload/RoundTripWorker.java
 */
public class RoundTripWorker implements RoundTripWorkerInterface
{
    private final int maxMessages;
    private int unackedSends;
    private AtomicBoolean running = new AtomicBoolean();
    private ReentrantLock lock;
    private Condition unackedSendsAreZero;

    public RoundTripWorker(int maxMessages)
    {
        if(maxMessages <= 0)
        {
            throw new IllegalStateException("maxMessages must be at least 1");
        }
        this.maxMessages = maxMessages;
        this.unackedSends = 0;
        this.lock = new ReentrantLock();
        this.unackedSendsAreZero = lock.newCondition();
    }

    /**
     * https://github.com/apache/kafka/blob/dccc82ea9dca1c29a86dd655802d26f646615317/tools/src/main/java/org/apache/kafka/trogdor/workload/RoundTripWorker.java#L257-L264
     */
    @Override
    public void _ackSend()
    {
        try {
            lock.lock();
            unackedSends -= 1;
            if (unackedSends <= 0)
                unackedSendsAreZero.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * https://github.com/apache/kafka/blob/dccc82ea9dca1c29a86dd655802d26f646615317/tools/src/main/java/org/apache/kafka/trogdor/workload/RoundTripWorker.java#L362-L370
     */
    @Override
    public void _waitForNoAcks() throws InterruptedException
    {
        try {
            lock.lock();
            while (unackedSends > 0)
                unackedSendsAreZero.await();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void start()
    {
        if(!running.compareAndSet(false, true))
        {
            throw new IllegalStateException("RoundTripWorker is already running.");
        }
        unackedSends = maxMessages;
    }

    @Override
    public void stop()
    {
        if (!running.compareAndSet(true, false)) {
            throw new IllegalStateException("RoundTripWorker is not running.");
        }
    }
}
