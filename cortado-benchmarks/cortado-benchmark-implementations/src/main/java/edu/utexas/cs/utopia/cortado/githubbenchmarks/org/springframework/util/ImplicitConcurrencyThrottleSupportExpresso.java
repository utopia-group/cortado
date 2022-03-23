package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

/**
 * Dummy class to be implemented by cortado
 */
public class ImplicitConcurrencyThrottleSupportExpresso implements ConcurrencyThrottleSupportInterface
{
    public ImplicitConcurrencyThrottleSupportExpresso(int threadLimit)
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