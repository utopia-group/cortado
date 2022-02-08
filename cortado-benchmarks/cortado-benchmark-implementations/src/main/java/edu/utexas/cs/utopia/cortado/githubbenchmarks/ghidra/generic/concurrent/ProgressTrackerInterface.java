package edu.utexas.cs.utopia.cortado.githubbenchmarks.ghidra.generic.concurrent;

public interface ProgressTrackerInterface
{
    void itemsAdded(int n);
    void itemStarted();
    void inProgressItemCompletedOrCancelled();
    void neverStartedItemsRemoved(int n);
    int getCompletedItemCount();
    int getTotalItemCount();
    int getItemsInProgressCount();
    void waitUntilDone() throws InterruptedException;
    int getNextID();
}
