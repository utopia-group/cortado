package edu.utexas.cs.utopia.cortado.githubbenchmarks.ghidra.generic.concurrent;

/**
 * Dummy class to be replaced by cortado
 */
public class ImplicitProgressTrackerAblated implements ProgressTrackerInterface
{
    @Override
    public void itemsAdded(int n)
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public void itemStarted()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public void inProgressItemCompletedOrCancelled()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public void neverStartedItemsRemoved(int n)
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public int getCompletedItemCount()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public int getTotalItemCount()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public int getItemsInProgressCount()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public void waitUntilDone() throws InterruptedException
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public int getNextID()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }
}
