package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hadoop.metrics2.impl;

public interface SinkQueueInterface<E>
{
    E dequeue() throws InterruptedException;
    boolean enqueue(E element) throws InterruptedException;
}
