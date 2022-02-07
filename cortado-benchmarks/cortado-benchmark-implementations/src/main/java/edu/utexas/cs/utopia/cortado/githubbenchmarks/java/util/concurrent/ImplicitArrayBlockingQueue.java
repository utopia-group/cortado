package edu.utexas.cs.utopia.cortado.githubbenchmarks.java.util.concurrent;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

import java.util.ArrayList;
import java.util.Collection;

import static edu.utexas.cs.utopia.cortado.mockclasses.Assertions.assume;

public class ImplicitArrayBlockingQueue<E> implements BlockingQueue<E>
{
    /** The queued items */
    final Object[] items;

    /** items index for next take, poll, peek or remove */
    int takeIndex = 0;

    /** items index for next put, offer, or add */
    int putIndex = 0;

    /** Number of elements in the queue */
    int count = 0;

    int capacity;

    /**
     * Circularly increment i.
     */
    final int inc(int i)
    {
        return (i + 1) % capacity;
    }

    @SuppressWarnings("unchecked")
    static <E> E cast(Object item)
    {
        return (E) item;
    }

    /**
     * Returns item at index i.
     */
    final E itemAt(int i)
    {
        return this.<E>cast(items[i]);
    }

    /**
     * Throws NullPointerException if argument is null.
     *
     * @param v the element
     */
    private static void checkNotNull(Object v)
    {
        if (v == null)
            throw new NullPointerException();
    }

    @Atomic
    public boolean insertIfNotFull(E e)
    {
        if (count == items.length)
            return false;
        else {
            items[putIndex] = e;
            putIndex = inc(putIndex);
            ++count;
            return true;
        }
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity and the specified access policy.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public ImplicitArrayBlockingQueue(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.items = new Object[capacity];
        this.capacity = capacity;
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and throwing an
     * {@code IllegalStateException} if this queue is full.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws IllegalStateException if this queue is full
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        checkNotNull(e);
        if (insertIfNotFull(e))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and {@code false} if this queue
     * is full.  This method is generally preferable to method {@link #add},
     * which can fail to insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        checkNotNull(e);
        return insertIfNotFull(e);
    }

    private boolean isNotFull()
    {
        return count < capacity;
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * for space to become available if the queue is full.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        // This version is equivalent to the original one, we just wrap the waituntil on its own method so
        // it conforms to the assumptions we make for the input language.
        checkNotNull(e);
        waitAndPut(e);
    }

    @Atomic(waituntil = "isNotFull")
    public void waitAndPut(E e)
    {
        isNotFull();
        {
            items[putIndex] = e;
            putIndex = inc(putIndex);
            ++count;
        }
    }

    @Atomic
    public E poll()
    {
        if (count == 0)
            return null;
        else
        {
            final Object[] items = this.items;
            E x = this.<E>cast(items[takeIndex]);
            items[takeIndex] = null;
            takeIndex = inc(takeIndex);
            --count;
            return x;
        }
    }

    private boolean isNotEmpty()
    {
        return count > 0;
    }

    @Atomic(waituntil = "isNotEmpty")
    public E take() throws InterruptedException
    {
        isNotEmpty();
        {
            final Object[] items = this.items;
            E x = this.<E>cast(items[takeIndex]);
            items[takeIndex] = null;
            takeIndex = inc(takeIndex);
            --count;
            return x;
        }
    }

    @Atomic
    public E peek() {
        return (count == 0) ? null : itemAt(takeIndex);
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    @Atomic
    public int size()
    {
        return count;
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    @Atomic
    public int remainingCapacity()
    {
        return capacity - count;
    }

    private void removeInv1(int i, int k)
    {
        assume(i >= 0 && i < capacity && count - k >= 0);
    }

    private void removeInv2(int i)
    {
        assume(i >= 0 && i < capacity);
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * <p>Removal of interior elements in circular array based queues
     * is an intrinsically slow and disruptive operation, so should
     * be undertaken only in exceptional circumstances, ideally
     * only when the queue is known not to be accessible by other
     * threads.
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    @Atomic
    public boolean remove(Object o)
    {
        if (o == null) return false;
        final Object[] items = this.items;
        for (int i = takeIndex, k = count; k > 0; i = inc(i), k--) {
            if (o.equals(items[i])) {
                removeInv1(i, k);
                // if removing front item, just advance
                if (i == takeIndex)
                {
                    items[takeIndex] = null;
                    takeIndex = inc(takeIndex);
                }
                else
                {
                    // slide over all others up through putIndex.
                    for (;;)
                    {
                        int nexti = inc(i);
                        if (nexti != putIndex)
                        {
                            items[i] = items[nexti];
                            i = nexti;
                        }
                        else
                        {
                            removeInv2(i);
                            items[i] = null;
                            putIndex = i;
                            break;
                        }
                    }
                }
                --count;
               return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    @Atomic
    public boolean contains(Object o)
    {
        if (o == null) return false;
        final Object[] items = this.items;

        boolean rv = false;
        for (int i = takeIndex, k = count; k > 0; i = inc(i), k--)
            if (o.equals(items[i]))
            {
                rv = true;
                break;
            }

        return rv;
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    @Atomic
    public Object[] toArray() {
        final Object[] items = this.items;

        final int count = this.count;
        Object[] a = new Object[items.length];
        for (int i = takeIndex, k = 0; k < count; i = inc(i), k++)
            a[k] = items[i];
        return a;
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    @Atomic
    public void clear()
    {
        final Object[] items = this.items;
        for (int i = takeIndex, k = count; k > 0; i = inc(i), k--)
            items[i] = null;
        putIndex = 0;
        takeIndex = 0;
        count = 0;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    @Atomic
    public int drainTo(Collection<? super E> c)
    {
        checkNotNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        final Object[] items = this.items;

        int i = takeIndex;
        int n = 0;
        int max = count;
        while (n < max)
        {
            items[i] = null;
            i = inc(i);
            ++n;
        }
        if (n > 0)
        {
            putIndex = 0;
            takeIndex = 0;
            count = 0;
        }
        return n;
    }

    private void drainToInv(int i, int n)
    {
        assume((takeIndex + n) % capacity == i && n <= count && i >= 0 && i < capacity);
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    @Atomic
    public int drainTo(Collection<? super E> c, int maxElements)
    {
        checkNotNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final Object[] items = this.items;

        int i = takeIndex;
        int n = 0;
        int max = (maxElements < count) ? maxElements : count;
        while (n < max)
        {
            items[i] = null;
            i = inc(i);
            ++n;
        }
        drainToInv(i, n);
        if (n > 0)
        {
            takeIndex = i;
            count -= n;
        }

        return n;
    }

    /**
     * Include main method so that all CCR methods are reachable inside soot
     * pointer analysis
     */
    public static void main(String[] args) throws InterruptedException {
        int i = 0;
        int whichConstructor = Integer.parseInt(args[i++]);
        int capacity = Integer.parseInt(args[i++]);
        ImplicitArrayBlockingQueue<Integer> queue = new ImplicitArrayBlockingQueue<>(capacity);
        queue.add(0);
        queue.add(0);
        queue.offer(0);
        queue.offer(0);
        queue.put(0);
        queue.put(0);
        queue.put(0);
        final Integer poll = queue.poll();
        queue.put(0);
        queue.contains(0);
        queue.put(0);
        final Integer take = queue.take();
        queue.offer(poll);
        final Integer peek = queue.peek();
        queue.offer(peek);
        queue.put(queue.size());
        queue.remove(0);
        if(queue.remainingCapacity() != 0)
        {
            queue.clear();
        }

        queue.toArray();
        queue.drainTo(new ArrayList<>());
        queue.drainTo(new ArrayList<>(), 1);
    }
}
