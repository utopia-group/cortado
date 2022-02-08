package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hadoop.metrics2.impl;

public class SinkQueueWrapper<E> implements SinkQueueInterface<E>
{
    private final SinkQueue<E> sinkQueue;

    public SinkQueueWrapper(int capacity)
    {
        this.sinkQueue = new SinkQueue<E>(capacity);
    }

    @Override
    public E dequeue() throws InterruptedException
    {
        return sinkQueue.dequeue();
    }

    @Override
    public boolean enqueue(E element) throws InterruptedException
    {
        return sinkQueue.enqueue(element);
    }
}
