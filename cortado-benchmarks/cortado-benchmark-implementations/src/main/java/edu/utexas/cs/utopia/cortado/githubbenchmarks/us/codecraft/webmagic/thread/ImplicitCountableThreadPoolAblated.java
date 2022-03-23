package edu.utexas.cs.utopia.cortado.githubbenchmarks.us.codecraft.webmagic.thread;

public class ImplicitCountableThreadPoolAblated extends AbstractCountableThreadPool
{
    public ImplicitCountableThreadPoolAblated(@SuppressWarnings("unused") int threadNum)
    {
        super(null);
        throw new RuntimeException("Expected cortado to implement this method.");
    }

    @Override
    int getThreadAlive()
    {
        throw new RuntimeException("Expected cortado to implement this method.");
    }

    @Override
    int getThreadNum()
    {
        throw new RuntimeException("Expected cortado to implement this method.");
    }

    @Override
    protected void _exitRun()
    {
        throw new RuntimeException("Expected cortado to implement this method.");
    }

    @Override
    protected void _enterExecute()
    {
        throw new RuntimeException("Expected cortado to implement this method.");
    }
}
