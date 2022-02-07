package edu.utexas.cs.utopia.cortado.githubbenchmarks.org.springframework.util;

public interface ConcurrencyThrottleSupportInterface
{
    void beforeAccess() throws InterruptedException;
    void afterAccess() throws InterruptedException;
}
