package edu.utexas.cs.utopia.cortado.githubbenchmarks.java.util.concurrent;

public interface BlockingQueue<DataType>
{
    void put(DataType element) throws InterruptedException;
    DataType take() throws InterruptedException;
}
