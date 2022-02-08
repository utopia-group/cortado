package edu.utexas.cs.utopia.cortado.githubbenchmarks.com.ericsson.research.transport.ws;

/*
 * ##_BEGIN_LICENSE_##
 * Transport Abstraction Package (trap)
 * ----------
 * Copyright (C) 2014 Ericsson AB
 * ----------
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the Ericsson AB nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ##_END_LICENSE_##
 */

import edu.utexas.cs.utopia.cortado.mockclasses.Atomic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import com.ericsson.research.transport.ws.WSInterface;
import com.ericsson.research.transport.ws.WSListener;

public class ImplicitWSDataListener implements WSDataListenerInterface
{

    protected WSInterface socket;
    private boolean open = false;
    private final List<String> strings = new ArrayList<>();
    private final List<byte[]> datas = new ArrayList<>();
    private final List<byte[]> pongs = new ArrayList<>();

    @Atomic
    public void notifyOpen(WSInterface socket) {
        this.socket = socket;
        open  = true;
    }

    private boolean isOpen()
    {
        return open;
    }

    @SuppressWarnings("RedundantThrows")
    @Atomic(waituntil="isOpen")
    public void waitForOpen(long timeout) throws TimeoutException {
        isOpen();
    }

    @Atomic
    public void notifyMessage(byte[] data) {
        datas.add(data);
    }

    private boolean datasIsNotEmpty()
    {
        return !datas.isEmpty();
    }

    @Atomic(waituntil="datasIsNotEmpty")
    public byte[] waitForBytes(long timeout) throws TimeoutException {
        datasIsNotEmpty();
        return datas.remove(0);
    }

    @Atomic
    public void notifyMessage(String string) {
        strings.add(string);
    }

    private boolean stringsIsNotEmpty()
    {
        return !strings.isEmpty();
    }

    @SuppressWarnings("RedundantThrows")
    @Atomic(waituntil="stringsIsNotEmpty")
    public String waitForString(long timeout) throws TimeoutException {
        stringsIsNotEmpty();
        return strings.remove(0);
    }

    @Atomic
    public void notifyClose() {
        open = false;
    }

    @Atomic
    public void notifyError(Throwable t) {
        open = false;
    }

    private boolean isClosed()
    {
        return !open;
    }

    @Atomic(waituntil="isClosed")
    public void waitForClose(long timeout) throws TimeoutException {
        isClosed();
    }

    @Atomic
    public void notifyPong(byte[] payload) {
        pongs.add(payload);
    }

    private boolean pongsIsNotEmpty()
    {
        return !pongs.isEmpty();
    }

    @SuppressWarnings("RedundantThrows")
    @Atomic(waituntil="pongsIsNotEmpty")
    public byte[] waitForPong(long timeout) throws TimeoutException {
        pongsIsNotEmpty();
        return pongs.remove(0);
    }

    /**
     * Add main method so that CCR methods are reachable from soot pointer analysis
     */
    public static void main(String[] args) throws TimeoutException {
        final ImplicitWSDataListener implicitWSDataListener = new ImplicitWSDataListener();
        implicitWSDataListener.notifyOpen(new WSInterface() {
            @Override
            public String getOrigin() {
                return null;
            }

            @Override
            public String getHost() {
                return null;
            }

            @Override
            public String getPath() {
                return null;
            }

            @Override
            public URI getUri() {
                return null;
            }

            @Override
            public HashMap<String, String> getHeaders() {
                return null;
            }

            @SuppressWarnings("RedundantThrows")
            @Override
            public void open() throws IOException {

            }

            @Override
            public void setReadListener(WSListener wsListener) {

            }

            @SuppressWarnings("RedundantThrows")
            @Override
            public void send(byte[] bytes) throws IOException {

            }

            @SuppressWarnings("RedundantThrows")
            @Override
            public void send(String s) throws IOException {

            }

            @SuppressWarnings("RedundantThrows")
            @Override
            public void ping(byte[] bytes) throws IOException {

            }

            @Override
            public void close() {

            }

            @Override
            public void close(int i, String s) {

            }

            @Override
            public InetSocketAddress getLocalSocketAddress() {
                return null;
            }

            @Override
            public InetSocketAddress getRemoteSocketAddress() {
                return null;
            }
        });
        implicitWSDataListener.waitForOpen(1);
        implicitWSDataListener.notifyMessage(new byte[1]);
        implicitWSDataListener.waitForBytes(1);
        implicitWSDataListener.notifyMessage("message");
        implicitWSDataListener.waitForString(1);
        implicitWSDataListener.notifyClose();
        implicitWSDataListener.notifyError(new Exception(""));
        implicitWSDataListener.waitForClose(1);
        implicitWSDataListener.notifyPong(new byte[1]);
        implicitWSDataListener.waitForPong(1);
    }
}
