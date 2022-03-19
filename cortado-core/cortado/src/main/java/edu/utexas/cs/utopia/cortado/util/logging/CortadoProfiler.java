package edu.utexas.cs.utopia.cortado.util.logging;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Record global profiling statistics of interest.
 * Follows java singleton pattern
 */
public class CortadoProfiler implements Serializable {
    /// global settings ///////////////////////////////////////////////////////
    final TimeUnit defaultTimeUnit = TimeUnit.MILLISECONDS;

    /// Store data written to csv /////////////////////////////////////////////
    // generic profiling, not specific to a monitor (will go in separate csv)
    private final Map<String, String> runInfo = new HashMap<String, String>(){{
        put("timeUnit", defaultTimeUnit.toString());
        put("startTime", java.time.ZonedDateTime.now().toString());
    }};
    private final Map<String, Map<String, String>> monitorProfileInfo = new HashMap<>();

    /// We'll have one global profiler ////////////////////////////////////////
    private static CortadoProfiler globalProfiler = null;

    public static void buildGlobalProfiler(List<String> targetClasses)
    {
        if(globalProfiler != null)
        {
            throw new IllegalStateException("global profiler already exists.");
        }
        globalProfiler = new CortadoProfiler(targetClasses);
    }

    public static CortadoProfiler getGlobalProfiler()
    {
        if(globalProfiler == null)
        {
            throw new IllegalStateException("No global profiler built. Try calling buildGlobalProfiler().");
        }
        return globalProfiler;
    }

    ///////////////////////////////////////////////////////////////////////////

    /// Outputting information ////////////////////////////////////////////////

    private static class CSVWriter
    {
        private final Writer fileWriter;
        boolean firstEntryOnLine = true;
        int row = 0;
        int numColsOnRowZero = 0, numCols = 0;
        boolean closed = false;

        /**
         * Write a CSV using fileWriter
         * @param fileName the name of the file to write to
         * @throws IOException if cannot create writer to file
         */
        CSVWriter(String fileName) throws IOException {
            File file = new File(fileName);
            this.fileWriter = new BufferedWriter(new FileWriter(file));
        }

        /**
         * Write the string representation of entry to the current column
         * A null string will be written as "NA"
         *
         * @param entry the entry to write
         * @throws IOException if write fails
         */
        public void writeEntry(@Nullable String entry) throws IOException {
            if(closed) {
                throw new IllegalStateException("CSV writer is closed.");
            }
            // write "NA" instead of null
            if(entry == null)
            {
                entry = "NA";
            }
            if(!firstEntryOnLine)
            {
                entry = "," + entry;
            }
            firstEntryOnLine = false;
            fileWriter.write(entry);
            numCols++;
            if(row == 0) {
                numColsOnRowZero++;
            }
        }

        /**
         * Write each entry in entries to a row, then end the row
         *
         * @param entries the entries on the row
         * @throws IOException if the write fails
         */
        public void writeRow(@Nonnull List<String> entries) throws IOException {
            if(closed) {
                throw new IllegalStateException("CSV writer is closed.");
            }
            for (String entry : entries) {
                writeEntry(entry);
            }
            endRow();
        }

        /**
         * Start a new line
         * @throws IOException if write fails
         */
        public void endRow() throws IOException {
            if(closed) {
                throw new IllegalStateException("CSV writer is closed.");
            }
            fileWriter.write("\n");
            firstEntryOnLine = true;
            if(numCols != numColsOnRowZero)
            {
                throw new IllegalStateException("row " + row + " has " + numCols +
                        " columns, but header has " + numColsOnRowZero);
            }
            row++;
            numCols = 0;
        }

        /**
         * Close the csv writer
         */
        public void close() throws IOException
        {
            closed = true;
            fileWriter.close();
        }
    }

    /**
     *
     * @param directory the directory to write the CSV in
     * @param baseName the basename of the CSV. If it does not end in ".csv",
     *                 then ".csv" is appended.
     * @throws IOException if directory does not exist and cannot be created
     */
    public void writeToCSV(String directory, String baseName) throws IOException {
        // get the csv file
        File outDir = new File(directory);
        if(!outDir.exists() && !outDir.mkdirs())
        {
            throw new IOException("Failed to create directory " + directory);
        }
        else if(!outDir.isDirectory())
        {
            throw new IOException(directory + " is not a directory.");
        }
        String ext = ".csv";
        if(baseName.lastIndexOf(ext) != baseName.length() - ext.length())
        {
            baseName += ext;
        }
        // build csv file names for both the data, and the run-info csv
        String csvFileName = Paths.get(directory, baseName).toString();
        final String runInfoBaseName = baseName.substring(0, baseName.length() - ext.length()) + "-run-info.csv";
        String runInfoCSVFileName = Paths.get(directory, runInfoBaseName).toString();
        // build our csv writer
        final CSVWriter csvWriter = new CSVWriter(csvFileName);
        // write our header
        List<String> header = new ArrayList<String>(){{add("monitor");}};
        monitorProfileInfo.values()
                .stream()
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .distinct()
                .sorted()
                .forEach(header::add);
        csvWriter.writeRow(header);
        // write out the data
        for (Map.Entry<String, Map<String, String>> stringMapEntry : monitorProfileInfo.entrySet()) {
            final HashMap<String, String> monitorData = new HashMap<>(stringMapEntry.getValue());
            monitorData.put("monitor", stringMapEntry.getKey());
            monitorData.put("runInfoCSVFileName", runInfoCSVFileName);
            for (String colName : header) {
                csvWriter.writeEntry(monitorData.get(colName));
            }
            csvWriter.endRow();
        }

        // close csv writer
        csvWriter.close();

        // write run-info data (recording the name of the associated csv)
        runInfo.put("dataCSVFileName", csvFileName);
        List<String> runInfoHeader = runInfo.keySet().stream().sorted().collect(Collectors.toList()),
                runInfoData = runInfoHeader.stream().map(runInfo::get).collect(Collectors.toList());
        final CSVWriter runInfoCSVWriter = new CSVWriter(runInfoCSVFileName);
        runInfoCSVWriter.writeRow(runInfoHeader);
        runInfoCSVWriter.writeRow(runInfoData);
        runInfoCSVWriter.close();
    }

    ///////////////////////////////////////////////////////////////////////////

    private final List<String> targetClasses;

    private CortadoProfiler(List<String> targetClasses)
    {
        this.targetClasses = targetClasses;
    }

    /// Basic timing setup ////////////////////////////////////////////////////////////

    /**
     * return time between start and end in {@link #defaultTimeUnit}
     * If either start, or end is null, returns null
     *
     * @param start start time
     * @param end end time
     * @return time between
     */
    @Nullable
    String timeBetween(@Nullable Instant start, @Nullable Instant end)
    {
        if(start == null || end == null)
        {
            return null;
        }
        final long timeInNanos = Duration.between(start, end).toNanos();
        final long time = defaultTimeUnit.convert(timeInNanos, TimeUnit.NANOSECONDS);
        return Long.toString(time);
    }

    private Instant cortadoStartTime = null;

    /**
     * Call before starting soot setup
     */
    public void startCortado() {
        cortadoStartTime = Instant.now();
    }

    /**
     * Call after soot setup ends
     */
    public void endCortado() {
        Instant cortadoEndTime = Instant.now();
        runInfo.put("cortadoRunTime", timeBetween(cortadoStartTime, cortadoEndTime));
    }

    ///////////////////////////////////////////////////////////////////////////

    /// per-monitor data //////////////////////////////////////////////////////
    private CortadoMonitorProfiler currentMonitorProfiler = null;

    /**
     * Start a monitor profiler for the given class name
     *
     * @param monitorClassName the name to start profiling on
     * @throws IllegalStateException if monitorClassName is not in this profiler's
     *      recognized monitor classes
     *      or if another monitor profiler has been started, but not closed.
     */
    public void startMonitorProfiler(String monitorClassName)
    {
        if(!targetClasses.contains(monitorClassName))
        {
            throw new IllegalStateException("Unrecognized monitorClassName " + monitorClassName);
        }
        if(currentMonitorProfiler != null)
        {
            throw new IllegalStateException("Current monitor profiler must be closed before starting a new one.");
        }
        currentMonitorProfiler = new CortadoMonitorProfiler(monitorClassName);
    }

    /**
     * @return the current monitor profiler
     * @throws IllegalStateException if there is no current monitor profiler
     */
    public CortadoMonitorProfiler getCurrentMonitorProfiler()
    {
        if(currentMonitorProfiler == null)
        {
            throw new IllegalStateException("No current monitor profiler is started.");
        }
        return currentMonitorProfiler;
    }

    /**
     * Close the current monitor profiler
     */
    public void closeMonitorProfiler()
    {
        if(currentMonitorProfiler == null) {
            throw new IllegalStateException("No monitor profiler to close.");
        }
        currentMonitorProfiler.close();
        monitorProfileInfo.put(currentMonitorProfiler.getMonitor(), currentMonitorProfiler.getProfileInfo());
        currentMonitorProfiler = null;
    }

    ///////////////////////////////////////////////////////////////////////////

    /**
     * Store generic run information to this object
     *
     * @param key the key (column name)
     * @param value the value (column entry)
     */
    public void recordRunInfo(String key, String value)
    {
        runInfo.put(key, value);
    }

    /**
     * Wrapper around {@link #recordRunInfo(String, String)}
     */
    public void recordRunInfo(String key, long value)
    {
        recordRunInfo(key, Long.toString(value));
    }
}
