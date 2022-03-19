package edu.utexas.cs.utopia.cortado;

import com.microsoft.z3.Version;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.WeightedPartialMaxSatSolverFactory;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.Z3WeightedPartialMaxSatSolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Copied and Modified from edu.utexas.cs.utopia.cfpchecker.CmdLine by kferles
 * <p>
 * Holds command line arguments for this project
 */
class CmdLine
{
    private static final Logger log = LoggerFactory.getLogger(CmdLine.class.getName());
    // classes to process
    private final List<String> targetClasses = new ArrayList<>();
    // silence warnings?
    // print .dot files of control flow?
    private boolean fragPartToDot = false;
    private boolean fragPartToSmallDot = false;
    private String dotDir;
    // is user requesting help screen?
    private boolean isHelp = false;
    // signal inference options
    private String signalInfAlgorithm = "expresso";
    private final List<String> signalInferenceOpts = Arrays.asList("annotations", "expresso");
    private String flipPredSigOpt = "unconditional-sigs";
    private final List<String> flipPredSigOptOpts = Arrays.asList("never", "unconditional-sigs", "always");
    private boolean cascadingBcasts = true;
    // which rw set analysis to use
    private String rwSetAnalysis = "soot";
    private final List<String> rwSetAnalyses = Arrays.asList("soot", "dummy");
    // how should we build code fragments
    private String fragmentConstructor = "pt-based";
    private final List<String> fragmentConstructors = Arrays.asList("annotationsFinest", "annotationsCoarse", "ccr", "statement", "pt-based");
    private String fragmentWeightMethod = "par-op";
    private final List<String> fragmentWeightMethods = Arrays.asList("par-op", "uniform");

    private long maxSatTimeoutInMs = TimeUnit.SECONDS.toMillis(20);
    private long expressoSMTDefaultTimeoutInMs = TimeUnit.MINUTES.toMillis(10);
    private long cortadoSMTDefaultTimeoutInMs = TimeUnit.SECONDS.toMillis(15);
    // lock enumeration
    private boolean enumerateLockings = false;
    private long lockingEnumerationTimeoutInMs = TimeUnit.SECONDS.toMillis(10);
    private String enumerationOutputDir = null;
    private String ignoreListsDir = "ignore-lists/";
    private String modelsDir = "models/";

    public String usage()
    {

        return "java -jar lockPlacementBenchmarks.jar targetsFile [command line options] -- [soot-options]\n" +
                "targetsFile                  text file of fully qualified target class names, one on each line\n" +
                "--signal-inference inf-alg    Algorithm for inferring signal operations\n" +
                "                              (default) expresso -- use expresso's algorithm\n" +
                "                                        annotations -- search annotations in source code\n" +
                "--flip-pred-sig-opt opt       Only signal if the predicate was false at the beginning of the CCR\n" +
                "                                        never -- turns off this optimization\n" +
                "                              (default) unconditional-sigs -- only for unconditional signals\n" +
                "                                        always -- for every signal operation\n" +
                "--no-cascading-bcasts         Turns off cascading broadcasts\n" +
                "--rwsetanalysis analysis      Use the specified analysis for read/write set analyses\n" +
                "                              Available analyses are are:\n" +
                "                              (default) soot  -- Use soot's side effect analysis\n" +
                "                                        dummy -- Only two abstract memory locations: statements\n" +
                "                                                 either access everything or nothing\n" +
                "--fragmentconstructor method  Use the specified method to construct fragments:\n" +
                "                                        annotationsFinest -- Use annotations in source code,\n" +
                "                                                             using fine fragments for non-annotated code.\n" +
                "                                        annotationsCoarse -- Use annotations in source code,\n" +
                "                                                             using coarse fragments for non-annotated code.\n" +
                "                                        ccr -- Each CCR is a fragment\n" +
                "                                        statement -- Each statement is its own fragment\n" +
                "                              (default) pt-based -- Use pointer analysis to group statements heuristically\n" +
                "--fragmentweights method      Use the specified method to put weights on fragments\n" +
                "                              (default) par-op -- Heuristic based on parallelization opportunities\n" +
                "                                        uniform -- Weight fragments uniformly\n" +
                "--countlockings timeout units               Count the number of lockings.\n" +
                "                                            timeout should be a positive integer\n" +
                "                                            indicating how much time each solve is allowed run.\n" +
                "                                            units specifies the time unit (see TIME UNITS below)\n" +
                "--writeenumeratedlockings dir               Write out the enumerated lockings to directory dir.\n" +
                "                                            --countlockings must also be passed as an argument.\n" +
                "--expressosmttimeout time units   Set the specified timeout as a default for SMT timeouts.\n" +
                "                                  During the Expresso algorithm\n" +
                "                                  Default is 30 minutes.\n" +
                "                                  time must be a positive integer. units must be\n" +
                "                                  a time unit (see TIME UNITS below)\n" +
                "--cortadosmttimeout time units    Set the specified timeout as a default for SMT timeouts.\n" +
                "                                  During the cortado algorithm\n" +
                "                                  Default is 15 seconds.\n" +
                "                                  time must be a positive integer. units must be\n" +
                "                                  a time unit (see TIME UNITS below)\n" +
                "--maxsattimeout time units       Set the specified timeout for weighted max sat.\n" +
                "                                 Default is 20 seconds.\n" +
                "                                 time must be a positive integer. units must be\n" +
                "                                 a time unit (see TIME UNITS below)\n" +
                "--ignoredir <directory>          specifies the directory that all methods/classes to ignore\n" +
                "--modelsdir <directory>          specifies the directory containing all explicit models\n" +
                "VISUALIZATION/DEBUGGING\n" +
                "     --fragpart2dot <directory>        make graphviz *.dot files of the fragment partition\n" +
                "									      in the given directory. Includes every instruction in\n" +
                "										  each fragment.\n" +
                "     --fragpart2smalldot <directory>   make graphviz *.dot files of the fragment partition\n" +
                "									  in the given directory. Includes only the first and last\n" +
                "									  instruction of each fragment.\n" +
                "-h, --help:                  print this message and exit\n" +
                "TIME UNITS:\n" +
                "time units must passed as arguments must be one of\\n\" +\n" +
                "        ms -- milliseconds\n" +
                "        s -- seconds\n" +
                "        m -- minutes\n";
    }

    public String parseArgs(String[] args)
    {
        String parseError = null;

        if (args.length == 0)
        {
            parseError = "Missing targetsFile\n";
        }
        else
        {
            try (BufferedReader reader = Files.newBufferedReader(Paths.get(args[0])))
            {
                String line = reader.readLine();
                while (line != null)
                {
                    line = line.replaceAll("\\s", line); // strip whitespace
                    // Does line begin with comment?
                    String lineStr = line;
                    boolean comment = false;
                    try {
                        comment = Objects.equals(lineStr.substring(0, 2), "//");
                    } catch(IndexOutOfBoundsException ignored) { }
                    if(!comment && !line.isEmpty()) {
                        // add to target class
                        targetClasses.add(line);
                    }
                    line = reader.readLine();
                }
            } catch (IOException e)
            {
                e.printStackTrace();
                System.exit(1);
            }
        }

        parseLoop:
        for (int i = 1; i < args.length; )
        {
            switch (args[i])
            {
                case "-h":
                case "--help":
                    isHelp = true;
                    break parseLoop;
                case "--fragmentconstructor":
                    if(++i >= args.length) {
                        parseError = "--fragmentconstructor missing method";
                        break parseLoop;
                    }
                    fragmentConstructor = args[i];
                    if(!fragmentConstructors.contains(fragmentConstructor)) {
                        parseError = "Unrecognized fragmentconstructor search type type: " + fragmentConstructor;
                        break parseLoop;
                    }
                    ++i;
                    break;
                case "--fragmentweights":
                    if(++i >= args.length) {
                        parseError = "--fragmentweights missing method";
                    }
                    fragmentWeightMethod = args[i];
                    if(!fragmentWeightMethods.contains(fragmentWeightMethod)) {
                        parseError = "Unrecognized fragmentweights method: " + fragmentWeightMethod;
                    }
                    ++i;
                    break;
                case "--signal-inference":
                    if(++i >= args.length) {
                        parseError = "--signal-inference missing algorithm";
                        break parseLoop;
                    }
                    signalInfAlgorithm = args[i];
                    if (!signalInferenceOpts.contains(signalInfAlgorithm)) {
                        parseError = "Unrecognized signal-inference algorithm: " + signalInfAlgorithm;
                        break parseLoop;
                    }
                    ++i;
                    break;
                case "--flip-pred-sig-opt":
                    if(++i >= args.length) {
                        parseError = "--signal-inference missing algorithm";
                        break parseLoop;
                    }
                    flipPredSigOpt = args[i];
                    if (!flipPredSigOptOpts.contains(flipPredSigOpt))
                    {
                        parseError = "Unrecognized options for flip-pred-sig-opt: " + flipPredSigOpt;
                        break parseLoop;
                    }
                    ++i;
                    break;
                case "--no-cascading-bcasts":
                    ++i;
                    cascadingBcasts = false;
                    break;
                case "--rwsetanalysis":
                    if(++i >= args.length) {
                        parseError = "--rwsetanalysis missing analysis type";
                        break parseLoop;
                    }
                    rwSetAnalysis = args[i];
                    if(!rwSetAnalyses.contains(rwSetAnalysis)) {
                        parseError = "Unrecognized rwsetanalysis type: " + rwSetAnalysis;
                        break parseLoop;
                    }
                    ++i;
                    break;
                case "--countlockings":
                    this.enumerateLockings = true;
                    // read the time
                    try {
                        this.lockingEnumerationTimeoutInMs = parseTimeArg(args, i);
                    } catch(IllegalArgumentException e)
                    {
                        parseError = e.getMessage();
                        break parseLoop;
                    }
                    i += 3;
                    break;
                case "--writeenumeratedlockings":
                    if(++i >= args.length) {
                        parseError = "--writeenumeratedlockings option missing output directory";
                        break parseLoop;
                    }
                    enumerationOutputDir = args[i];
                    ++i;
                    break;
                case "--expressosmttimeout":
                    try {
                        this.expressoSMTDefaultTimeoutInMs = parseTimeArg(args, i);
                    } catch(IllegalArgumentException e)
                    {
                        parseError = e.getMessage();
                        break parseLoop;
                    }
                    i += 3;
                    break;
                case "--cortadosmttimeout":
                    try {
                        this.cortadoSMTDefaultTimeoutInMs = parseTimeArg(args, i);
                    } catch(IllegalArgumentException e)
                    {
                        parseError = e.getMessage();
                        break parseLoop;
                    }
                    i += 3;
                    break;
                case "--maxsattimeout":
                    try {
                        this.maxSatTimeoutInMs = parseTimeArg(args, i);
                    } catch(IllegalArgumentException e)
                    {
                        parseError = e.getMessage();
                        break parseLoop;
                    }
                    i += 3;
                    break;
                case "--fragpart2dot":
                    fragPartToDot = true;
                    if(++i >= args.length) {
                        parseError = "--fragpart2dot option missing dot directory";
                        break parseLoop;
                    }
                    dotDir = args[i];
                    ++i;
                    break;
                case "--fragpart2smalldot":
                    fragPartToSmallDot = true;
                    if(++i >= args.length) {
                        parseError = "--fragpart2smalldot option missing dot directory";
                        break parseLoop;
                    }
                    dotDir = args[i];
                    ++i;
                    break;
                case "--ignoredir":
                    if(++i >= args.length) {
                        parseError = "--ignoredir missing directory";
                        break parseLoop;
                    }
                    ignoreListsDir = args[i];
                    ++i;
                    break;
                case "--modelsdir":
                    if(++i >= args.length) {
                        parseError = "--modelsdir missing directory";
                        break parseLoop;
                    }
                    modelsDir = args[i];
                    ++i;
                    break;
                default:
                    parseError = "Invalid option: " + args[i];
                    break parseLoop;
            }
        }

        // make sure writeEnumeratedLockings is only on if --countlockings
        // was also passed
        if(enumerationOutputDir != null && !enumerateLockings)
        {
            parseError = "--writeenumeratedlockings passed without --countlockings";
        }

        return parseError;
    }

    /**
     * Parses a time argument of the form --argname time unit.
     * We assume args[i] is "--argname"
     *
     * @param args the command line arguments
     * @param i the current index
     * @return the time in milliseconds
     * @throws IllegalArgumentException if finds parsing errors
     */
    private long parseTimeArg(String[] args, int i) throws IllegalArgumentException
    {
        String argName = args[i];
        if(++i >= args.length) {
            throw new IllegalArgumentException(argName + " missing time");
        }
        int time = Integer.parseInt(args[i]);
        if(time <= 0)
        {
            throw new IllegalArgumentException(argName + " time of " + time + " is not positive.");
        }
        if(++i >= args.length) {
             throw new IllegalArgumentException(argName + " missing units");
        }
        String unit = args[i].toLowerCase();
        TimeUnit timeUnit;
        switch(unit) {
            case "ms":
                timeUnit = TimeUnit.MILLISECONDS;
                break;
            case "s":
                timeUnit = TimeUnit.SECONDS;
                break;
            case "m":
                timeUnit = TimeUnit.MINUTES;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized time unit " + unit);
        }
        return timeUnit.toMillis(time);
    }

    @Nonnull
    public List<String> getTargetClasses()
    {
        return targetClasses;
    }

    public boolean isHelp()
    {
        return isHelp;
    }

    @Nonnull
    public String getSignalInfAlgorithm()
    {
        return signalInfAlgorithm;
    }

    public boolean isCascadingBcasts()
    {
        return cascadingBcasts;
    }

    /**
     * @return the frag_part_to_dot
     */
    public boolean isFragPartToDot()
    {
        return fragPartToDot;
    }

    /**
     * @return the frag_part_to_small_dot
     */
    public boolean isFragPartToSmallDot()
    {
        return fragPartToSmallDot;
    }

    /**
     *
     */
    private void makeDirIfDoesNotExist(@Nonnull String dirName) throws IOException
    {
        File dir = new File(dirName);
        if(!dir.exists())
        {
            if(!dir.mkdirs())
            {
                throw new IOException("Failed to make directory: " + dirName);
            }
        }
    }

    /**
     * @return the dot_dir. Try to create it if it does not exist.
     * @throws IOException if tries to create directory and fails
     */
    @Nonnull
    public String getDotDir() throws IOException {
        makeDirIfDoesNotExist(dotDir);
        return dotDir;
    }

    /**
     * @return the analysisType
     */
    @Nonnull
    public String getRWSetAnalysisType() {
        return rwSetAnalysis;
    }

    /**
     * @return the fragment constructor
     */
    @Nonnull
    public String getFragmentConstructor() {
        return fragmentConstructor;
    }

    /**
     * @return the method for assigning weights
     */
    @Nonnull
    public String getFragmentWeightMethod() {
        return fragmentWeightMethod;
    }

    public String getFlipPredSigOpt()
    {
        return flipPredSigOpt;
    }

    /**
     * @return the weighted maxsat solver factory to use
     */
    @Nonnull
    public WeightedPartialMaxSatSolverFactory getNewWeightedMaxSatSolverFactory() {
        log.debug("Setting up " + com.microsoft.z3.Version.getFullVersion() + " as max-sat solver.");
        String testedVersion = "Z3 4.8.15.0";
        if(!testedVersion.equals(Version.getFullVersion()))
        {
            log.warn("cortado has only been tested with z3 version " + testedVersion);
        }
        return new Z3WeightedPartialMaxSatSolverFactory();
    }

    /**
     * @return true iff we should enumerate lockings
     */
    public boolean isEnumerateLockingsEnabled()
    {
        return enumerateLockings;
    }

    /**
     * @return the directory to write enumerate lockings to. Try to create it if it does not exist.
     * @throws IOException if tries to create directory and fails
     */
    @Nullable
    public String getEnumerationOutputDir() throws IOException
    {
        if(enumerationOutputDir != null)
        {
            makeDirIfDoesNotExist(enumerationOutputDir);
        }
        return enumerationOutputDir;
    }

    /**
     * @return the timeout for the weight-max sat problem in milliseconds
     */
    public long getMaxSatTimeoutInMs()
    {
        return maxSatTimeoutInMs;
    }

    /**
     * @return the Expresso default SMT timeout in milliseconds
     */
    public long getExpressoSmtDefaultTimeoutInMs() {
        return expressoSMTDefaultTimeoutInMs;
    }

    /**
     * @return the Cortado default SMT timeout in milliseconds
     */
    public long getCortadoSmtDefaultTimeoutInMs() {
        return cortadoSMTDefaultTimeoutInMs;
    }

    /**
     * Record some information about this run to the global
     * profiler ({@link CortadoProfiler#getGlobalProfiler()}
     */
    public void recordRunInfoToGlobalProfiler()
    {
        // throw error if cmd-line z3 does not match z3 Version along path
        String SOLVER_EXEC = System.getProperty("solver.exec");
        ProcessBuilder z3VersionProcessBuilder = new ProcessBuilder(SOLVER_EXEC, "-version");
        Process z3VersionProcess = null;
        try {
            z3VersionProcess = z3VersionProcessBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException("Unable to run " + z3VersionProcessBuilder.command());
        }
        BufferedReader cmdLineVersionReader = new BufferedReader(new InputStreamReader(z3VersionProcess.getInputStream()));
        StringBuilder z3CmdLineVersionString = new StringBuilder();
        try {
            String line;
            while((line = cmdLineVersionReader.readLine()) != null) {
                z3CmdLineVersionString.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String z3OnPathVersionString = Version.getMajor() + "." + Version.getMinor() + "." + Version.getBuild();
        if(!z3CmdLineVersionString.toString().contains(z3OnPathVersionString)) {
            throw new RuntimeException("Mismatch in z3 version: solver.exec " + SOLVER_EXEC
                + " uses version \"" + z3CmdLineVersionString + "\" while z3 on path uses \""
                + z3OnPathVersionString + '"');
        }

        final CortadoProfiler globalProfiler = CortadoProfiler.getGlobalProfiler();
        globalProfiler.recordRunInfo("RWSetAnalysis", getRWSetAnalysisType());
        globalProfiler.recordRunInfo("ExpressoSMTDefaultTimeout(ms)", getExpressoSmtDefaultTimeoutInMs());
        globalProfiler.recordRunInfo("CortadoSMTDefaultTimeout(ms)", getCortadoSmtDefaultTimeoutInMs());
        globalProfiler.recordRunInfo("MaxSatDefaultTimeout(ms)", getMaxSatTimeoutInMs());
        String weightedMaxSatSolver = "z3";
        globalProfiler.recordRunInfo("weightedMaxSatSolver", weightedMaxSatSolver);
        globalProfiler.recordRunInfo("z3Version", com.microsoft.z3.Version.getFullVersion());
        globalProfiler.recordRunInfo("fragmentConstructor", getFragmentConstructor());
        globalProfiler.recordRunInfo("fragmentWeightMethod", getFragmentWeightMethod());
    }

    /**
     * @return the timeout in milliseconds for each solve during locking enumeration
     */
    public long getLockingEnumerationTimeoutInMs()
    {
        return lockingEnumerationTimeoutInMs;
    }

    @Nonnull
    public String getIgnoreListsDir() { return ignoreListsDir; }

    @Nonnull
    public String getModelsDir() { return modelsDir; }
}
