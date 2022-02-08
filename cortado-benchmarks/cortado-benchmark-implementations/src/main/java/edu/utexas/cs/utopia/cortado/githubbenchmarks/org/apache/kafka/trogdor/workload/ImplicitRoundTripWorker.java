package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.kafka.trogdor.workload;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

/**
 * Extracted synchronization routines from
 * https://github.com/apache/kafka/blob/dccc82ea9dca1c29a86dd655802d26f646615317/tools/src/main/java/org/apache/kafka/trogdor/workload/RoundTripWorker.java
 */
public class ImplicitRoundTripWorker implements RoundTripWorkerInterface
{
    private final int maxMessages;
    private int unackedSends;
    private boolean running;

    public ImplicitRoundTripWorker(int maxMessages)
    {
        if(maxMessages <= 0)
        {
            throw new IllegalStateException("maxMessages must be at least 1");
        }
        this.maxMessages = maxMessages;
        this.unackedSends = 0;
        running = false;
    }

    /**
     * https://github.com/apache/kafka/blob/dccc82ea9dca1c29a86dd655802d26f646615317/tools/src/main/java/org/apache/kafka/trogdor/workload/RoundTripWorker.java#L257-L264
     */
    @Override
    @Atomic
    public void _ackSend()
    {
        unackedSends -= 1;
    }

    private boolean unackedSendsAreZero()
    {
        return unackedSends <= 0;
    }

    /**
     * https://github.com/apache/kafka/blob/dccc82ea9dca1c29a86dd655802d26f646615317/tools/src/main/java/org/apache/kafka/trogdor/workload/RoundTripWorker.java#L362-L370
     */
    @Override
    @Atomic(waituntil="unackedSendsAreZero")
    public void _waitForNoAcks() throws InterruptedException
    {
        unackedSendsAreZero();
    }

    @Override
    @Atomic
    public void start()
    {
        if(running)
        {
            throw new IllegalStateException("RoundTripWorker is already running.");
        }
        running = true;
        unackedSends = maxMessages;
    }

    @Override
    @Atomic
    public void stop()
    {
        if (!running) {
            throw new IllegalStateException("RoundTripWorker is not running.");
        }
        running = false;
    }

    /**
     * Add main method so that CCR methods are reachable from soot pointer analysis
     */
    public static void main(String[] args) throws InterruptedException
    {
        ImplicitRoundTripWorker roundTripWorker = new ImplicitRoundTripWorker(Integer.parseInt(args[0]));
        roundTripWorker.start();
        roundTripWorker._ackSend();
        roundTripWorker._waitForNoAcks();
        roundTripWorker.stop();
    }
}
