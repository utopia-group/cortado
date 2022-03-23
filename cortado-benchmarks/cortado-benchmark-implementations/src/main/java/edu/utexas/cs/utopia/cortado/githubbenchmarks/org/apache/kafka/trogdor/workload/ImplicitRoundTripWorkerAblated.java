package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.kafka.trogdor.workload;

public class ImplicitRoundTripWorkerAblated implements RoundTripWorkerInterface
{
    public ImplicitRoundTripWorkerAblated(@SuppressWarnings("unused") int maxMessages)
    {
        throw new RuntimeException("Expected cortado to replace this implementation");
    }

    @Override
    public void _ackSend()
    {
        throw new RuntimeException("Expected cortado to replace this implementation");
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public void _waitForNoAcks() throws InterruptedException
    {
        throw new RuntimeException("Expected cortado to replace this implementation");
    }

    @Override
    public void start()
    {
        throw new RuntimeException("Expected cortado to replace this implementation");
    }

    @Override
    public void stop()
    {
        throw new RuntimeException("Expected cortado to replace this implementation");
    }
}
