package edu.utexas.cs.utopia.cortado.githubbenchmarks.us.codecraft.webmagic.thread;

import edu.utexas.cs.utopia.cortado.githubbenchmarks.alluxio.worker.job.task.DummyRunnable;
import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImplicitCountableThreadPool extends AbstractCountableThreadPool
{
    private int threadNum;

    private int threadAlive;

    public ImplicitCountableThreadPool(int threadNum) {
        this(threadNum, Executors.newFixedThreadPool(threadNum));
    }


    public ImplicitCountableThreadPool(int threadNum, ExecutorService executorService) {
        super(executorService);
        this.threadAlive = 0;
        this.threadNum = threadNum;
    }

    @Override
    @Atomic
    public int getThreadAlive()
    {
        return threadAlive;
    }

    @Override
    @Atomic
    public int getThreadNum()
    {
        return threadNum;
    }

    private boolean threadAliveLessThanThreadNum()
    {
        return threadAlive < threadNum;
    }

    @Atomic(waituntil="threadAliveLessThanThreadNum")
    public void _enterExecute()
    {
        threadAliveLessThanThreadNum();
        threadAlive++;
    }

    @Atomic
    public void _exitRun()
    {
        threadAlive--;
    }

    /**
     * Add main method so that all CCRs are reachable inside soot pointer analysis
     */
    public static void main(String[] args)
    {
        AbstractCountableThreadPool countableThreadPool = new ImplicitCountableThreadPool(100);
        countableThreadPool.execute(new DummyRunnable());
        countableThreadPool.getThreadAlive();
        countableThreadPool.getThreadNum();
        countableThreadPool.isShutdown();
        countableThreadPool.shutdown();
    }
}
