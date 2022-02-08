package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.kafka.trogdor.workload;

public interface RoundTripWorkerInterface
{
    void _ackSend();

    void _waitForNoAcks() throws InterruptedException;

    void start();

    void stop();
}
