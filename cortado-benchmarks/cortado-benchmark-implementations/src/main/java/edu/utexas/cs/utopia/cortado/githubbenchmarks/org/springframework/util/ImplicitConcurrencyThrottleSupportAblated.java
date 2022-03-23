package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

/**
 * Dummy class to be implemented by cortado
 */
public class ImplicitConcurrencyThrottleSupportAblated implements ConcurrencyThrottleSupportInterface
{
    public ImplicitConcurrencyThrottleSupportAblated(int threadLimit)
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public void beforeAccess()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }

    @Override
    public void afterAccess()
    {
        throw new RuntimeException("Implementation should be replaced by cortado");
    }
}