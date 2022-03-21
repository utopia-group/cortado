package edu.utexas.cs.utopia.cortado.util.logging;

import com.google.common.collect.Streams;
import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperation;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicOperation;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Profiler for whichever monitor is currently being processed.
 * Starts timer upon creation
 *
 * Should be obtained using {@link CortadoProfiler#getCurrentMonitorProfiler()}.
 */
public class CortadoMonitorProfiler implements Serializable {
    private static final String EVENT_SUFFIX = "Time";

    // just for convenience
    private final CortadoProfiler globalProfiler = CortadoProfiler.getGlobalProfiler();
    // the monitor class name
    private final String monitorClassName;
    // put keys in this map if you want them to be kept
    private final Map<String, String> profileInfo = new HashMap<>();
    // stack of events
    private final Deque<Event> eventStack = new ArrayDeque<>();
    private final List<String> currentNamePrefix = new ArrayList<>();
    private final Set<String> eventNames = new HashSet<>();
    // is this object closed?
    private boolean closed = false;

    private class Event {
        private final Instant startTime;
        private Instant endTime = null;
        private final String name;

        Event(@Nonnull String name)
        {
            this.name = name;
            this.startTime = Instant.now();
        }

        void end()
        {
            this.endTime = Instant.now();
        }

        @Nonnull
        String getDuration()
        {
            assert startTime != null;
            if(endTime == null)
            {
                throw new IllegalStateException("Event has not ended");
            }
            return Objects.requireNonNull(globalProfiler.timeBetween(startTime, endTime));
        }

        @Nonnull
        public String getName()
        {
            return name;
        }
    }

    /**
     * Starts with "totalTime" as an event name
     *
     * @param monitorClassName the class name
     */
    CortadoMonitorProfiler(@Nonnull String monitorClassName) {
        this.monitorClassName = monitorClassName;
        this.eventStack.push(new Event("total"));
    }

    /**
     * @return The (key, value) profiling info
     * @throws IllegalStateException if not closed
     */
    @Nonnull
    Map<String, String> getProfileInfo() {
        if(!closed) throw new IllegalStateException("Illegal attempt to get profile info before closing.");
        return profileInfo;
    }

    /**
     * @return the name of the current monitor
     */
    @Nonnull
    String getMonitor() {
        return monitorClassName;
    }

    /**
     * close the profiler
     */
    void close() {
        closed = true;
        // make sure all events have ended
        assert eventStack.size() >= 1;
        if(eventStack.size() != 1)
        {
            final StringBuilder userEvents = new StringBuilder();
            while(eventStack.size() > 1)
            {
                if(eventStack.size() > 2)
                {
                    userEvents.insert(0, ",");
                }
                userEvents.insert(0, eventStack.pop().getName());
            }
            throw new IllegalStateException("Closed before all events popped from stack: " + userEvents);
        }
        eventStack.pop();
    }

    @Nonnull
    private String prependPrefix(@Nonnull String suffix)
    {
        return Streams.concat(currentNamePrefix.stream(), Stream.of(suffix))
                .reduce("", (s1, s2) -> s1.isEmpty() || s2.isEmpty() ? s1 + s2 : s1 + "." + s2);
    }

    /// basic timing //////////////////////////////////////////////////////////

    /**
     * Start recording the time of an event
     * @param eventName the name. All names on the stack are prepended. This final name must be unique
     */
    public void pushEvent(@Nonnull String eventName)
    {
        String fullEventName = prependPrefix(eventName);
        if(this.eventNames.contains(fullEventName))
        {
            throw new IllegalArgumentException("An event of name " + fullEventName + " already exists");
        }
        if(this.profileInfo.containsKey(fullEventName + EVENT_SUFFIX))
        {
            throw new IllegalArgumentException("Profiling info for key " + fullEventName + " already exists");
        }
        currentNamePrefix.add(eventName);
        this.profileInfo.put(fullEventName + EVENT_SUFFIX, ""); //< placeholder value to avoid records with duplicate names
        this.eventNames.add(fullEventName);
        this.eventStack.push(new Event(fullEventName));
    }

    public void popEvent()
    {
        // the first event is the start event! Don't let users popEvent it
        if(this.eventStack.size() <= 1)
        {
            throw new IllegalStateException("No event to popEvent");
        }
        final Event top = this.eventStack.pop();
        top.end();
        currentNamePrefix.remove(currentNamePrefix.size() - 1);
        profileInfo.replace(top.getName() + EVENT_SUFFIX, top.getDuration());
    }

    ///////////////////////////////////////////////////////////////////////////

    /// counting occurrences //////////////////////////////////////////////////
    /**
     * Record a timeout in a weighted max-sat problem
     */
    public void recordWeightedMaxSatTimeout() {
        if(closed) throw new IllegalStateException("Profiler is closed");
        incrementValue("numWeightedMaxSatTimeouts", 0);
    }

    /**
     * @param initialLockUpperBound the first upper bound on number of locks
     */
    public void recordInitialLockUpperBound(int initialLockUpperBound)
    {
        if(closed) throw new IllegalStateException("Profiler is closed");
        recordProfileInfo(prependPrefix("initialLockUpperBound"), initialLockUpperBound);
    }

    /**
     * @param numLocksUsed the number of lock variables used
     */
    public void recordNumLocksUsed(int numLocksUsed)
    {
        if(closed) throw new IllegalStateException("Profiler is closed");
        recordProfileInfo(prependPrefix("numLocks"), numLocksUsed);
    }

    /**
     * @param numAtomicsUsed the number of atomic variables used
     */
    public void recordNumAtomicsUsed(int numAtomicsUsed)
    {
        if(closed) throw new IllegalStateException("Profiler is closed");
        recordProfileInfo(prependPrefix("numAtomics"), numAtomicsUsed);    }

    /**
     * count number of signals/broadcasts
     *
     * @param sigOp the signal operation to record
     */
    public void recordSigOp(@Nonnull SignalOperation sigOp)
    {
        if(closed) throw new IllegalStateException("Profiler is closed");
        boolean isConditional = sigOp.isConditional();
        boolean isBroadcast = sigOp.isBroadCast();
        if(isConditional)
        {
            if(isBroadcast)
            {
                incrementValue("numConditionalBroadcasts", 0);
            }
            else
            {
                incrementValue("numConditionalSignals", 0);
            }
        }
        else
        {
            if(isBroadcast)
            {
                incrementValue("numUnconditionalBroadcasts", 0);
            }
            else
            {
                incrementValue("numUnconditionalSignals", 0);
            }
        }
    }

    /**
     * Increment the total number of lock invocations
     * @param numLockOps the number of calls to .lock()
     */
    public void recordNumLockInvocations(int numLockOps) {
        incrementValue("numLockInvocations", 0, numLockOps);
    }

    /**
     * Increment the total number of lock invocations
     * @param numUnlockOps the number of calls to .lock()
     */
    public void recordNumUnlockInvocations(int numUnlockOps) {
        incrementValue("numUnlockInvocations", 0, numUnlockOps);
    }

    /**
     * Record an invocation of an atomic operation
     * @param potentialAtomicOperation the atomic operation being invoked
     */
    public void recordAtomicInvocation(PotentialAtomicOperation potentialAtomicOperation) {
        potentialAtomicOperation.recordOperationInProfiler(this);
    }

    ///////////////////////////////////////////////////////////////////////

    //// basic CCR statistics /////////////////////////////////////////////

    /**
     * Record the number of CCRs
     * @param numCCRs the number of CCRs
     */
    public void recordNumCCRs(int numCCRs) {
        if(closed) throw new IllegalStateException("Profiler is closed");
        recordProfileInfo("numCCRs", numCCRs);
    }

    /**
     * Record the number of waiting CCRs
     * @param numAwaits the number of waiting CCRs
     */
    public void recordNumAwaits(int numAwaits){
        if(closed) throw new IllegalStateException("Profiler is closed");
        recordProfileInfo("numAwaits", numAwaits);
    }

    ///////////////////////////////////////////////////////////////////////

    /**
     * Increment the value associated to key by increment (if no such value exists, use defaultValue)
     * @param key the key (the event prefix will be pre-pended to this key automatically)
     * @param defaultValue the default value to increment from if key is absent
     * @param increment the amount to increment by
     */
    public void incrementValue(@Nonnull String key, int defaultValue, int increment) {
        if(closed) throw new IllegalStateException("Profiler is closed");
        profileInfo.compute(prependPrefix(key), (k, v) -> {
            if(v == null)
            {
                return Integer.toString(defaultValue + increment);
            }
            return Integer.toString(Integer.parseInt(v) + increment);
        });
    }

    /**
     *
     * @param key the key to increment (the event prefix will be pre-pended to this key automatically)
     * @param defaultValue the default value to increment from if key has no associated value
     * @see #incrementValue(String, int, int)
     */
    public void incrementValue(@Nonnull String key, int defaultValue)
    {
       incrementValue(key, defaultValue, 1);
    }

    /**
     * Store profiling info to this object
     *
     * @param key the key (column name)
     * @param value the value (column entry)
     */
    public void recordProfileInfo(@Nonnull String key, @Nonnull String value)
    {
        if(closed) throw new IllegalStateException("Profiler is closed");
        if(profileInfo.containsKey(key)) throw new IllegalArgumentException("key has already been recorded: " + key);
        profileInfo.put(key, value);
    }

    /**
     * Wrapper around {@link #recordProfileInfo(String, String)} which
     * converts ints to strings
     */
    public void recordProfileInfo(@Nonnull String key, int value)
    {
        recordProfileInfo(key, Integer.toString(value));
    }

    /// monitor invariant //////////////////////////////////////////////////

    public void logMonitorInvariant(@Nonnull Expr invariant) throws IOException
    {
        // TODO: change to field once we have an out-dir option
        File outDir = new File("target");
        if(!outDir.exists() && !outDir.mkdirs())
        {
            throw new IOException("Failed to create directory target");
        }
        else if(!outDir.isDirectory())
        {
            throw new IOException("target is not a directory.");
        }

       try (BufferedWriter invWriter = new BufferedWriter(new FileWriter(Paths.get("target", monitorClassName + "-inv.txt").toFile())))
       {
            invWriter.write(ExprUtils.toParsableString(invariant));
       }
    }

    ///////////////////////////////////////////////////////////////////////////
}
