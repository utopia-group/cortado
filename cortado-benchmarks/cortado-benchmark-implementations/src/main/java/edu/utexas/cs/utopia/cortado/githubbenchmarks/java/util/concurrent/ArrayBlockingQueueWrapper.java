package edu.utexas.cs.utopia.cortado.githubbenchmarks.java.util.concurrent;

import java.util.concurrent.ArrayBlockingQueue;

public class ArrayBlockingQueueWrapper<E> implements BlockingQueue<E>
{
    private final ArrayBlockingQueue<E> blockingQueue;

    public ArrayBlockingQueueWrapper(int queueCapacity) {
        this.blockingQueue = new ArrayBlockingQueue<>(queueCapacity);
    }

    @Override
    public void put(E element) throws InterruptedException
    {
        this.blockingQueue.put(element);
    }

    @Override
    public E take() throws InterruptedException
    {
        return this.blockingQueue.take();
    }
}
