package edu.utexas.cs.utopia.cortado.githubbenchmarks.com.ericsson.research.transport.ws;

import java.util.concurrent.TimeoutException;
import com.ericsson.research.transport.ws.WSListener;
import com.ericsson.research.transport.ws.WSInterface;

public interface WSDataListenerInterface extends WSListener {
    public void notifyOpen(WSInterface socket);
    public void waitForOpen(long timeout) throws TimeoutException;

    public void notifyMessage(byte[] data);
    public byte[] waitForBytes(long timeout) throws TimeoutException;

    public void notifyMessage(String string);
    public String waitForString(long timeout) throws TimeoutException;

    public void notifyClose();
    public void notifyError(Throwable t);
    public void waitForClose(long timeout) throws TimeoutException;

    public void notifyPong(byte[] payload);
    public byte[] waitForPong(long timeout) throws TimeoutException;
}
