package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hadoop.metrics2.impl;

public class ImplicitSinkQueueAblated<E> implements SinkQueueInterface<E>
{
    public ImplicitSinkQueueAblated(int queueCapacity)
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
