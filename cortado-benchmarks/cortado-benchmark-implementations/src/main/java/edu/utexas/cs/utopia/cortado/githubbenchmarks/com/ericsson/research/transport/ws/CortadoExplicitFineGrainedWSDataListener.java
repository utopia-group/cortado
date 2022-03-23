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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.ericsson.research.transport.ws.WSInterface;

public class CortadoExplicitFineGrainedWSDataListener implements WSDataListenerInterface {

    protected WSInterface socket;
    private boolean open = false;
    private final List<String> strings = new ArrayList<String>();
    private final List<byte[]> datas =  new ArrayList<byte[]>();
    private final List<byte[]> pongs =  new ArrayList<byte[]>();

    private Lock openLock = new ReentrantLock(),
        datasLock = new ReentrantLock(),
        stringsLock = new ReentrantLock(),
        pongsLock = new ReentrantLock();
    private Condition openIsTrue = openLock.newCondition(),
        openIsFalse = openLock.newCondition(),
        datasNotEmpty = datasLock.newCondition(),
        stringsNotEmpty = stringsLock.newCondition(),
        pongsNotEmpty = pongsLock.newCondition();

    public void notifyOpen(WSInterface socket) {
        this.openLock.lock();
        try {
            this.socket = socket;
            open = true;
            openIsTrue.signalAll();
        } finally {
            openLock.unlock();
        }
    }

    public void waitForOpen(long timeout) throws TimeoutException {
        long expiry = System.currentTimeMillis() + timeout;
        this.openLock.lock();
        try {
            while (!open) {
                long waitTime = expiry - System.currentTimeMillis();
                if (waitTime <= 0 && !open)
                    throw new TimeoutException();
                try {
                    this.openIsTrue.await(waitTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                }
            }
        } finally {
            this.openLock.unlock();
        }
    }

    public void notifyMessage(byte[] data) {
        this.datasLock.lock();
        try {
            datas.add(data);
            this.datasNotEmpty.signal();
        } finally {
            this.datasLock.unlock();
        }
    }

    public byte[] waitForBytes(long timeout) throws TimeoutException {
        byte[] bytes = null;
        this.datasLock.lock();
        try {
            long expiry = System.currentTimeMillis() + timeout;
            while (datas.isEmpty()) {
                long waitTime = expiry - System.currentTimeMillis();
                if (waitTime <= 0)
                    throw new TimeoutException();
                try {
                    datasNotEmpty.await(waitTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                }
                bytes = datas.remove(0);
                if(!datas.isEmpty()) {
                    this.datasNotEmpty.signal();
                }
            }
        }finally {
            this.datasLock.unlock();
        }
        return bytes;
    }

    public void notifyMessage(String string) {
        this.stringsLock.lock();
        try {
            strings.add(string);
            stringsNotEmpty.signal();
        } finally {
            this.stringsLock.unlock();
        }
    }

    public String waitForString(long timeout) throws TimeoutException {
        long expiry = System.currentTimeMillis() + timeout;
        String str = null;
        this.stringsLock.lock();
        try {
            while (strings.isEmpty()) {
                long waitTime = expiry - System.currentTimeMillis();
                if (waitTime <= 0)
                    throw new TimeoutException();
                try {
                    this.stringsNotEmpty.await(waitTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                }
            }
            str = strings.remove(0);
            if(!strings.isEmpty()) {
                this.stringsNotEmpty.signal();
            }
        } finally {
            this.stringsLock.unlock();
        }
        return str;
    }

    public void notifyClose() {
        this.openLock.lock();
        try {
            open = false;
            this.openIsFalse.signalAll();
        } finally {
            this.openLock.unlock();
        }
    }

    public void notifyError(Throwable t) {
        this.openLock.lock();
        try {
            t.printStackTrace();
            open = false;
            this.openIsFalse.signalAll();
        } finally {
            this.openLock.unlock();
        }
    }

    public void waitForClose(long timeout) throws TimeoutException {
        long expiry = System.currentTimeMillis() + timeout;
        this.openLock.lock();
        try {
            while (open) {
                long waitTime = expiry - System.currentTimeMillis();
                if (waitTime <= 0)
                    throw new TimeoutException();
                try {
                    this.openIsFalse.await(waitTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                }
            }
        } finally {
            this.openLock.unlock();
        }
    }

    public void notifyPong(byte[] payload) {
        this.pongsLock.lock();
        try {
            pongs.add(payload);
            this.pongsNotEmpty.signal();
        } finally {
            this.pongsLock.unlock();
        }
    }

    public byte[] waitForPong(long timeout) throws TimeoutException {
        long expiry = System.currentTimeMillis() + timeout;
        byte[] pong = null;
        this.pongsLock.lock();
        try {
            while (pongs.isEmpty()) {
                long waitTime = expiry - System.currentTimeMillis();
                if (waitTime <= 0)
                    throw new TimeoutException();
                try {
                    this.pongsNotEmpty.await(waitTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                }
            }
            pong = pongs.remove(0);
            if(!pongs.isEmpty()) {
                this.pongsNotEmpty.signal();
            }
        } finally {
            this.pongsLock.unlock();
        }
        return pong;
    }

}