/*
 * Copyright 2015 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utexas.cs.utopia.cortado.githubbenchmarks.io.realm.internal.async;

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;



/**
 * Custom thread pool settings, instances of this executor can be paused, and resumed, this will also set
 * appropriate number of Threads & wrap submitted tasks to set the thread priority according to
 * <a href="https://developer.android.com/training/multiple-threads/define-runnable.html"> Androids recommendation</a>.
 */
public class ImplicitRealmThreadPoolExecutor implements RealmThreadPoolExecutorInterface /*extends ThreadPoolExecutor*/ {

    private boolean isPaused;

    /**
     * Method invoked prior to executing the given Runnable to pause execution of the thread.
     *
     * @param t the thread that will run task r
     * @param r the task that will be executed
     */

    private boolean isNotPaused() {
        return !isPaused;
    }

    @Atomic(waituntil="isNotPaused")
    public void beforeExecute(Thread t, Runnable r) {
        isNotPaused();
    }

    /**
     * Pauses the executor. Pausing means the executor will stop starting new tasks (but complete current ones).
     */
    @Atomic
    public void pause() {
        isPaused = true;
    }

    /**
     * Resumes executing any scheduled tasks.
     */
    @Atomic
    public void resume() {
        isPaused = false;
    }

    /**
     * Add main method so that CCR methods are reachable from soot pointer analysis
     */
    public static void main(String args[]){
        ImplicitRealmThreadPoolExecutor executor = new ImplicitRealmThreadPoolExecutor();
        final Thread thread = new Thread();
        final Runnable nopRunner = new Runnable()
        {
            @Override
            public void run()
            {
            }
        };
        executor.beforeExecute(thread, nopRunner);
        executor.pause();
        executor.resume();
    }
}
