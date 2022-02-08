/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.apache.hadoop.metrics2.impl;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

/**
 * A half-blocking (nonblocking for producers, blocking for consumers) queue
 * for metrics sinks.
 *
 * New elements are dropped when the queue is full to preserve "interesting"
 * elements at the onset of queue filling events
 */
class ImplicitSinkQueue<T> implements SinkQueueInterface<T>{

    interface Consumer<T> {
        void consume(T object) throws InterruptedException;
    }

    // A fixed size circular buffer to minimize garbage
    private final T[] data;
    private int head; // head position
    private int tail; // tail position
    private int size; // number of elements
    private int capacity; // data.length

    @SuppressWarnings("unchecked")
    public ImplicitSinkQueue(int capacity) {
        // TODO: fix issue with Math.max
        if (capacity < 1)
            capacity = 1;
        this.data = (T[]) new Object[capacity];
        head = tail = size = 0;
        this.capacity = capacity;
    }

    @Atomic
    public boolean enqueue(T e) {
        if (capacity == size) {
            return false;
        }
        tail = (tail + 1) % capacity;
        data[tail] = e;

        ++size;
        return true;
    }

    /**
     * Dequeue one element from head of the queue, will block if queue is empty
     * @return  the first element
     * @throws InterruptedException
     */
    @Atomic(waituntil="isNotEmpty")
    public T dequeue() throws InterruptedException {
        isNotEmpty();

        if (0 == size) {
            throw new IllegalStateException("Size must > 0 here.");
        }
        head = (head + 1) % capacity;
        T ret = data[head];
        data[head] = null;  // hint to gc

        --size;
        return ret;
    }

    private boolean isNotEmpty() {
        return 0 != size;
    }

    @Atomic
    public void clear() {
        for (int i = capacity; i-- > 0; ) {
            data[i] = null;
        }
        size = 0;
    }

    @Atomic
    public int size() {
        return size;
    }

    int capacity() {
        return capacity;
    }


    // dummy consumer for use in main method
    public static class DummyConsumer<T> implements Consumer<T> {
        public void consume(T object) throws InterruptedException {
            object.getClass();
        }
    }

    /**
     * Add main method so that CCR methods are reachable from soot pointer analysis
     */
    public static void main(String[] args) throws InterruptedException {
        final ImplicitSinkQueue sinkQueue = new ImplicitSinkQueue<String>(10);
        sinkQueue.enqueue("String1");
        sinkQueue.enqueue("String2");
        sinkQueue.enqueue("String3");
        sinkQueue.enqueue("String4");
        sinkQueue.dequeue();
        sinkQueue.size();
        sinkQueue.clear();
        sinkQueue.capacity();
    }
}
