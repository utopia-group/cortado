package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hadoop.metrics2.impl;

public class ImplicitSinkQueueExpresso<E> implements SinkQueueInterface<E>
{
    public ImplicitSinkQueueExpresso(int queueCapacity)
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }

    @Override
    public E dequeue() throws InterruptedException
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }

    @Override
    public boolean enqueue(E element) throws InterruptedException
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }
}
