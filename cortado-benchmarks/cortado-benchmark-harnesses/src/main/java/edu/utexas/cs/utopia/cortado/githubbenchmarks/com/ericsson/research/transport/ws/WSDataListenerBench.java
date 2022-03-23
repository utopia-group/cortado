package edu.utexas.cs.utopia.cortado.githubbenchmarks.com.ericsson.research.transport.ws;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeoutException;

@Warmup(batchSize = 50000)
@Measurement(batchSize = 50000)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
public class WSDataListenerBench {
    private WSDataListenerInterface wsListener;
    @SuppressWarnings("unused")
    @Param({"Expresso",
            "Ablated",
            "Implicit",
            "ExplicitCoarseOriginal",
//            "ExplicitCoarse",
//            "ExplicitFine"
    })
    private String whichImplementation;

    @State(Scope.Thread)
    public static class ThreadData {
        final byte[] pid = new byte[4];
        String message;

        @Setup(Level.Iteration)
        public void setup()
        {
            final Random random = new Random();
            random.nextBytes(pid);
            message = Integer.toString(random.nextInt());
        }
    }

    private final byte[] handshakeAck = new byte[]{0};

    @Setup(Level.Iteration)
    public void setupWSListener() {
        // initialize the DataListener
        switch(this.whichImplementation) {
            case "ExplicitCoarseOriginal":
                this.wsListener = new WSDataListener();
                break;
            case "ExplicitCoarse":
                this.wsListener = new CortadoExplicitCoarseGrainedWSDataListener();
                break;
            case "ExplicitFine":
                this.wsListener = new CortadoExplicitFineGrainedWSDataListener();
                break;
            case "Implicit":
                this.wsListener = new ImplicitWSDataListener();
                break;
            case "Expresso":
                this.wsListener = new ImplicitWSDataListenerExpresso();
                break;
            case "Ablated":
                this.wsListener = new ImplicitWSDataListenerAblated();
                break;
            default:
                String msg = String.format(
                        "Unrecognized value '%s' for whichWSDataListener",
                        whichImplementation
                );
                throw new IllegalStateException(msg);
        }
    }

    @Benchmark
    @Group("Handshake")
    public void initiateHandshake(Blackhole bh, ThreadData threadData) throws TimeoutException
    {
        this.wsListener.notifyMessage(threadData.pid);
        bh.consume(this.wsListener.waitForPong(Long.MAX_VALUE));
        this.wsListener.notifyMessage(threadData.message);
    }

    @Benchmark
    @Group("Handshake")
    public void acceptHandshake(Blackhole bh) throws TimeoutException
    {
        bh.consume(this.wsListener.waitForBytes(Long.MAX_VALUE));
        this.wsListener.notifyPong(handshakeAck);
        bh.consume(this.wsListener.waitForString(Long.MAX_VALUE));
    }
}
