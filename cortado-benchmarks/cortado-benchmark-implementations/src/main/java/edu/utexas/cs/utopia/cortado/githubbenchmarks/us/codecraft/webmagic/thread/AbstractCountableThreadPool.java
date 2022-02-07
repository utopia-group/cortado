package edu.utexas.cs.utopia.cortado.githubbenchmarks.us.codecraft.webmagic.thread;

import java.util.concurrent.ExecutorService;

public abstract class AbstractCountableThreadPool
{
    protected ExecutorService executorService;

    AbstractCountableThreadPool(ExecutorService executorService)
    {
        this.executorService = executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    abstract int getThreadAlive();

    abstract int getThreadNum();

    public void execute(Runnable runnable)
    {
        _enterExecute();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } finally {
                    _exitRun();
                }
            }
        });
    }

    protected abstract void _exitRun();

    protected abstract void _enterExecute();

    public boolean isShutdown()
    {
        return executorService.isShutdown();
    }

    public void shutdown()
    {
        executorService.shutdown();
    }
}
