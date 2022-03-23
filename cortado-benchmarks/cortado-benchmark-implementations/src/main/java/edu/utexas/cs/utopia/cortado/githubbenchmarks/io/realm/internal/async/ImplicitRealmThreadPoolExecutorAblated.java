package edu.utexas.cs.utopia.cortado.githubbenchmarks.io.realm.internal.async;

/**
 * Dummy class to be implemented by cortado
 */
public class ImplicitRealmThreadPoolExecutorAblated implements RealmThreadPoolExecutorInterface
{
    @Override
    public void beforeExecute(Thread t, Runnable r)
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }

    @Override
    public void pause()
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }

    @Override
    public void resume()
    {
        throw new RuntimeException("Expected cortado to replace implementation");
    }
}
