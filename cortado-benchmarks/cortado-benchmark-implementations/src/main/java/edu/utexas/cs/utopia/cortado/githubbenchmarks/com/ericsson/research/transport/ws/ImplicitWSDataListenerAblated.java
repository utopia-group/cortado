package edu.utexas.cs.utopia.cortado.githubbenchmarks.com.ericsson.research.transport.ws;

import com.ericsson.research.transport.ws.WSInterface;

import java.util.concurrent.TimeoutException;

/**
 * Dummy implementation to be replaced by cortado
 */
public class ImplicitWSDataListenerAblated implements WSDataListenerInterface
{
    @Override
    public void notifyOpen(WSInterface socket)
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void waitForOpen(long timeout) throws TimeoutException
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void notifyMessage(byte[] data)
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public byte[] waitForBytes(long timeout) throws TimeoutException
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void notifyMessage(String string)
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public String waitForString(long timeout) throws TimeoutException
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void notifyClose()
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void notifyError(Throwable t)
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void waitForClose(long timeout) throws TimeoutException
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public void notifyPong(byte[] payload)
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }

    @Override
    public byte[] waitForPong(long timeout) throws TimeoutException
    {
        throw new RuntimeException("Cortado should replace this implementation");
    }
}
