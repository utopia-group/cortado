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

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

/**
 * A class to synchronize and track the progress of the items being processed by a concurrentQ. It
 * provides various wait methods for when one item is completed or all items are completed.
 */
class ImplicitProgressTracker implements ProgressTrackerInterface {
    private int totalCount = 0;
    private int inProgressCount = 0;
    private int completedOrCancelledCount = 0;
    private int nextID = 0;

    public ImplicitProgressTracker() {

    }

    @Atomic
    public void itemsAdded(int n) {
        totalCount += n;
    }

    @Atomic
    public void itemStarted() {
        inProgressCount++;
    }

    @Atomic
    public void inProgressItemCompletedOrCancelled() {
        completedOrCancelledCount++;
        inProgressCount--;
    }

    @Atomic
    public void neverStartedItemsRemoved(int n) {
        completedOrCancelledCount += n;
    }

    @Atomic
    public int getCompletedItemCount() {
        return completedOrCancelledCount;
    }

    @Atomic
    public int getTotalItemCount() {
        return totalCount;
    }

    @Atomic
    public int getItemsInProgressCount() {
        return inProgressCount;
    }

    private boolean isDone() {
        return completedOrCancelledCount == totalCount;
    }

    @Atomic(waituntil="isDone")
    public void waitUntilDone()// throws InterruptedException
    {
        isDone();
    }

    @Atomic
    public int getNextID() {
        nextID++;
        return nextID;
    }

    /**
     * Add main method so that CCR methods are reachable from soot pointer analysis
     */
    public static void main(String[] args) {
        ImplicitProgressTracker ipt = new ImplicitProgressTracker();
        ipt.itemsAdded(4);
        ipt.itemStarted();
        ipt.inProgressItemCompletedOrCancelled();
        ipt.neverStartedItemsRemoved(4);
        ipt.getCompletedItemCount();
        ipt.getItemsInProgressCount();
        ipt.getTotalItemCount();
        ipt.getNextID();
        ipt.isDone();
        ipt.waitUntilDone();
    }
}
