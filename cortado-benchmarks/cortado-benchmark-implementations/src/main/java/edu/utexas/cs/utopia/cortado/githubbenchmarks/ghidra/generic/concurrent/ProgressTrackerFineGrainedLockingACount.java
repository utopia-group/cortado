// Benchmark source: https://github.com/NationalSecurityAgency/ghidra/blob/4ee3c59c08212610f9fdeef68cde6a307c9740e0/Ghidra/Framework/Generic/src/main/java/generic/concurrent/ProgressTracker.java

/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.utexas.cs.utopia.cortado.githubbenchmarks.ghidra.generic.concurrent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class to synchronize and track the progress of the items being processed by a concurrentQ. It 
 * provides various wait methods for when one item is completed or all items are completed. 
 */
class ProgressTrackerFineGrainedLockingACount implements ProgressTrackerInterface
{
    private AtomicLong inProgressCount = new AtomicLong();

    private AtomicLong nextID = new AtomicLong();

    private final ReentrantLock condLock = new ReentrantLock();
	private final Condition done = condLock.newCondition();        // notifies when all items have been completed or cancelled 

    private long totalCount = 0;
	private long completedOrCancelledCount = 0;

	// private final Condition itemCompleted; // notifies when an item is processed or cancelled

	ProgressTrackerFineGrainedLockingACount()
    {
		// itemCompleted = lock.newCondition();
	}

	// made public for consistent benchmarking
	public void itemsAdded(int n)
    {
		condLock.lock();
		try
        {
			totalCount += n;
		}
		finally
        {
			condLock.unlock();
		}
	}

	// made public for consistent benchmarking
	public void itemStarted()
    {
        inProgressCount.getAndIncrement();
	}

	// made public for consistent benchmarking
	public void inProgressItemCompletedOrCancelled()
    {
		condLock.lock();
		try
        {
			completedOrCancelledCount++;
            inProgressCount.getAndDecrement();

			if (isDone())
            {
				done.signalAll();
			}
			// itemCompleted.signalAll();
		}
		finally
        {
			condLock.unlock();
		}
	}

	// made public for consistent benchmarking
	public void neverStartedItemsRemoved(int n)
    {
		condLock.lock();
		try
        {
			completedOrCancelledCount += n;
			if (isDone())
            {
				done.signalAll();
			}
			// itemCompleted.signalAll();
		}
		finally
        {
			condLock.unlock();
		}
	}

	// made return int for easier benchmarking
	public int getCompletedItemCount()
    {
		condLock.lock();
		try
        {
			return (int) completedOrCancelledCount;
		}
		finally
        {
			condLock.unlock();
		}
	}

	// made return int for ease of use
	public int getTotalItemCount()
    {
		condLock.lock();
		try
        {
			return (int) totalCount;
		}
		finally
        {
			condLock.unlock();
		}
	}

	// made return int for ease of use
	public int getItemsInProgressCount()
    {
        return (int) inProgressCount.get();
	}

	private boolean isDone()
    {
		condLock.lock();
		try
        {
			return completedOrCancelledCount == totalCount;
		}
		finally
        {
			condLock.unlock();
		}
	}

	// made public for consistent benchmarking
	public void waitUntilDone() throws InterruptedException
    {
		condLock.lockInterruptibly();
		try
        {
			while (!isDone())
            {
				done.await();
			}
		}
		finally
        {
			condLock.unlock();
		}
	}

	// void waitForNext() throws InterruptedException {
	// 	lock.lockInterruptibly();
	// 	try
    //     {
	// 		if (!isDone())
    //         {
	// 			itemCompleted.await();
	// 		}
	// 	}
	// 	finally {
	// 		lock.unlock();
	// 	}
	// }

	// public boolean waitUntilDone(long timeout, TimeUnit unit) throws InterruptedException {
	// 	lock.lockInterruptibly();
	// 	try
    //     {
	// 		if (!isDone())
    //         {
	// 			done.await(timeout, unit);
	// 		}
	// 		return isDone();
	// 	}
	// 	finally
    //     {
	// 		lock.unlock();
	// 	}
	// }

	// made return int for consistent benchmarking
	public int getNextID()
    {
        return (int) nextID.incrementAndGet();
	}
}
