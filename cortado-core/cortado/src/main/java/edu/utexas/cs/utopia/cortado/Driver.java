package edu.utexas.cs.utopia.cortado;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import edu.utexas.cs.utopia.cortado.ccrs.*;
import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.integration.ExplicitMonitorWriter;
import edu.utexas.cs.utopia.cortado.integration.ImplicitMonitorReader;
import edu.utexas.cs.utopia.cortado.integration.SignalInferenceResults;
import edu.utexas.cs.utopia.cortado.lockPlacement.*;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperation;
import edu.utexas.cs.utopia.cortado.staticanalysis.*;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.SootMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.util.graph.GuavaExceptionalUnitGraphCache;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.util.sat.enumeration.WeightedPartialMaxSatEnumeratorFactory;
import edu.utexas.cs.utopia.cortado.util.sat.enumeration.Z3WeightedPartialMaxSatEnumeratorFactory;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.MaxSatSolverFailException;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.WeightedPartialMaxSatSolverFactory;
import edu.utexas.cs.utopia.cortado.util.sat.maxsat.Z3WeightedPartialMaxSatSolverFactory;
import edu.utexas.cs.utopia.cortado.util.soot.*;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;
import edu.utexas.cs.utopia.cortado.vcgen.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.options.Options;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils.CONDITION_CLASSNAME;
import static edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils.LOCK_CLASSNAME;

/**
 * Copied and modified from edu.utexas.cs.utopia.cfpchecker.Driver by kferles
 * <p>
 * Command line interface:
 * <p>
 * Run
 * java -jar lockPlacementBenchmarks.jar -h
 */
public class Driver
{
    private static final Logger log = LoggerFactory.getLogger(Driver.class);

    /**
     * Get index before first soot argument
     *
     * @param args command line args
     * @return First index where "--" appears, or -1
     */
    private static int argsSplitIndex(String[] args)
    {
        for (int i = 0; i < args.length; ++i)
            if (args[i].equals("--"))
                return i;
        return -1;
    }

    /**
     * Run soot packages using soot command line args,
     * then do our transforms
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws IOException, MaxSatSolverFailException, UnexpectedOutputException
    {
        // Separate cortado command line arguments from soot command line arguments
        int splitIndex = argsSplitIndex(args);
        String[] sootOptions, checkerOptions;

        CmdLine cmdLine = new CmdLine();

        if (splitIndex == -1)
        {
            checkerOptions = args;
            sootOptions = new String[]{};
        }
        else
        {
            checkerOptions = Arrays.copyOfRange(args, 0, splitIndex);
            sootOptions = Arrays.copyOfRange(args, splitIndex + 1, args.length);
        }

        // validate requested output format (and store requested output
        // format in case the user wants bytecode, since CmdLine.setupSoot
        // will force output to jimple)
        Options.v().parse(sootOptions);
        int cmdLineOutFormat = Options.v().output_format();
        if (cmdLineOutFormat != Options.output_format_jimple && cmdLineOutFormat != Options.output_format_class)
        {
            System.err.println("Unsupported output format. Supported formats are jimple (-f jimple) and bytecode (default format)");
            System.exit(1);
        }

        // Parse non-soot args
        log.debug("Parsing non-soot command line arguments");
        String err = cmdLine.parseArgs(checkerOptions);

        if (err != null || cmdLine.isHelp())
        {
            if (err != null)
                System.err.println(err);

            System.err.println(cmdLine.usage());
            System.exit(1);
        }

        // Create an object to log global statistics
        CortadoProfiler.buildGlobalProfiler(cmdLine.getTargetClasses());
        final CortadoProfiler cortadoProfiler = CortadoProfiler.getGlobalProfiler();
        cmdLine.recordRunInfoToGlobalProfiler();
        cortadoProfiler.startCortado();

        // we'll use this to record the number of lockings, if it is
        // counted. className -> list, list[i] = # of lockings using i locks
        Map<String, List<Integer>> monitorToNumLockings = cmdLine.isEnumerateLockingsEnabled() ? new HashMap<>() : null;

        // set the default flip-predicate optimization
        switch(cmdLine.getFlipPredSigOpt())
        {
            case "never":
                SignalOperation.setDefaultFlipPredOptCondition(SignalOperation.FLIP_PRED_OPT_CONDITION.NEVER);
                break;
            case "unconditional-sigs":
                SignalOperation.setDefaultFlipPredOptCondition(SignalOperation.FLIP_PRED_OPT_CONDITION.UNCONDITIONAL);
                break;
            case "always":
                SignalOperation.setDefaultFlipPredOptCondition(SignalOperation.FLIP_PRED_OPT_CONDITION.ALWAYS);
                break;
            default:
                throw new RuntimeException("Unrecognized flip-pred sig opt: " + cmdLine.getFlipPredSigOpt());
        }

        // run cortado on each class
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        final ZoneId centralTimeZone = ZoneId.of("America/Chicago");
        for (String targetClassName : cmdLine.getTargetClasses())
        {
            System.out.println("Beginning to run cortado on "
                    + targetClassName + " at "
                    + dateTimeFormatter.format(ZonedDateTime.now(centralTimeZone)));
            runCortado(cmdLine, targetClassName, sootOptions, cmdLineOutFormat, monitorToNumLockings);
            System.out.println("cortado completed on class "
                    + targetClassName + " at "
                    + dateTimeFormatter.format(ZonedDateTime.now(centralTimeZone)));
       }

        cortadoProfiler.endCortado();
        // write profiling info
        cortadoProfiler.writeToCSV("target", "cortado-profiling");

        // If counted number of solutions, record those
        if(cmdLine.isEnumerateLockingsEnabled())
        {
            // get a writer to the csv
            String fileName = Paths.get("target", "num-lockings.csv").toString();
            final FileWriter fileWriter = new FileWriter(fileName);
            final BufferedWriter csvWriter = new BufferedWriter(fileWriter);
            /// write out the data
            assert monitorToNumLockings != null;
            csvWriter.write("monitor,numLocks,numSolutions\n");
            // for each monitor
            for (Map.Entry<String, List<Integer>> entry : monitorToNumLockings.entrySet())
            {
                final String monitorName = entry.getKey();
                final List<Integer> lockingCounts = entry.getValue();
                // for each # of locks for which we computed num solutions:
                for (int index = 0; index < lockingCounts.size(); ++index)
                {
                    int numLocks = index + 1;
                    // write the data
                    final int numSols = lockingCounts.get(index);
                    final String csvLine = String.format("%s,%s,%s\n", monitorName, numLocks, numSols);
                    csvWriter.write(csvLine);
                }
            }
            // close the writer
            csvWriter.close();
        }
    }

    /**
     * Runs cortado, including a reset and setup of soot.
     */
    private static void runCortado(@Nonnull CmdLine cmdLine,
                                   @Nonnull String targetClassName,
                                   @Nonnull String[] sootOptions,
                                   int cmdLineOutFormat,
                                   @Nullable Map<String, List<Integer>> monitorToNumLockings)
            throws IOException, MaxSatSolverFailException
    {
        final CortadoProfiler cortadoGlobalProfiler = CortadoProfiler.getGlobalProfiler();
        cortadoGlobalProfiler.startMonitorProfiler(targetClassName);
        final CortadoMonitorProfiler monitorProfiler = cortadoGlobalProfiler.getCurrentMonitorProfiler();

        // start timing soot setup
        monitorProfiler.pushEvent("sootSetup");

        // Load relevant classes, set class in monitor file as applications
        log.debug("Setting up soot and running command-line specified soot packs for " + targetClassName);
        final boolean wholeProgram = "soot".equals(cmdLine.getRWSetAnalysisType());
        G.reset();
        // process soot command line options
        Options sootCmdLine = Options.v();
        log.debug("Parsing soot command line arguments for " + targetClassName);
        sootCmdLine.parse(sootOptions);
        SootSetup.setupSoot(wholeProgram, Collections.singletonList(targetClassName));
        // soot setup is now over
        monitorProfiler.popEvent();
        log.debug("soot setup complete for " + targetClassName);

        // set default SMT timeout
        ExprUtils.setDefaultTimeout(cmdLine.getExpressoSmtDefaultTimeoutInMs());
        // set desired analysis type
        switch(cmdLine.getRWSetAnalysisType())
        {
            case "dummy":
                CachedMayRWSetAnalysis.getInstance().useDummyRWSetAnalysis();
                break;
            case "soot":
                CachedMayRWSetAnalysis.getInstance().useSootRWSetAnalysis();
                break;
            default:
                throw new IllegalStateException("Unrecognized may rw-set analysis type " + cmdLine.getRWSetAnalysisType());
        }

        // read in ignore lists and models
        log.debug("Reading in ignore lists and manual models for " + targetClassName);
        IgnoreListsParser.getInstance().readIgnoreLists(cmdLine.getIgnoreListsDir());
        ModelImporter.getInstance().readModels(cmdLine.getModelsDir());

        // our reader/set up routines for implicit monitors
        final ImplicitMonitorReader implicitMonitorReader = new ImplicitMonitorReader();
        // our implementer for explicit monitors
        final ExplicitMonitorWriter explicitMonitorWriter = new ExplicitMonitorWriter();

        log.debug("Applying custom transforms for " + targetClassName);
        PackManager packManager = PackManager.v();
        Pack jtpPack = packManager.getPack("jtp");

        // load the target class
        SootClass targetClass = Scene.v().getSootClass(targetClassName);

        // set ignore lists & models context
        IgnoreListsParser.getInstance().setCurrentTargetClass(targetClass);

        // set up the CCRs and perform signal inference
        log.debug("cascading benchmarks set to " + cmdLine.isCascadingBcasts() + " for " + targetClassName);
        final boolean useAnnotations = "annotations".equals(cmdLine.getSignalInfAlgorithm());
        final SignalInferenceResults signalInferenceResults = implicitMonitorReader.setupCCRsAndInferSignals(targetClass, useAnnotations, cmdLine.isCascadingBcasts());

        // Switch to cortado timeout
        ExprUtils.setDefaultTimeout(cmdLine.getCortadoSmtDefaultTimeoutInMs());
        // Get list of CCRs
        List<CCR> ccrs = signalInferenceResults.getCCRs();
        monitorProfiler.recordNumCCRs(ccrs.size());
        monitorProfiler.recordNumAwaits((int) ccrs.stream().filter(CCR::hasGuard).count());
        Map<Body, CCR> body2ccr = ccrs.stream()
                .collect(Collectors.toMap(
                        ccr -> ccr.getAtomicSection().getActiveBody(),
                        Function.identity()
                ));

        // Obtain fragment partition
        FragmentPartitioner fragPartitioner;
        switch(cmdLine.getFragmentConstructor())
        {
            case "annotationsCoarse":
            case "annotationsFinest":
                // Get user-provided parallel fragments
                UserParallelizationOracleTranslator
                        userOracleTranslator = new UserParallelizationOracleTranslator(body2ccr);
                Transform userOracleT = new Transform("jtp.UserOracle." + targetClassName, userOracleTranslator);
                jtpPack.add(userOracleT);
                log.debug("Translating user-provided parallelization fragments for " + targetClassName);
                targetClass.getMethods()
                        .stream()
                        .filter(SootMethod::hasActiveBody)
                        .map(SootMethod::getActiveBody)
                        .forEach(userOracleT::apply);
                // build a paroracle
                ParallelizationOracle parOracle = userOracleTranslator.getUserOracle();
                if("annotationsCoarse".equals(cmdLine.getFragmentConstructor()))
                {
                    log.debug("Extending user fragments to coarse fragment partition for " + targetClassName);
                    fragPartitioner = new CoarseFragmentPartitioner(parOracle);
                }
                else
                {
                    log.debug("Extending user fragments to finest fragment partition for " + targetClassName);
                    fragPartitioner = new FinestFragmentPartitioner(parOracle);
                }
                break;
            case "ccr":
                log.debug("Making one fragment for each CCR for " + targetClassName);
                fragPartitioner = new CoarseFragmentPartitioner();
                // sanity check
                fragPartitioner.getCCR2fragPartition().values().forEach(fragments -> {
                    assert fragments.size() == 1;
                });
                break;
            case "statement":
                log.debug("Making one fragment for each statement for " + targetClassName);
                fragPartitioner = new FinestFragmentPartitioner();
                break;
            case "pt-based":
                log.debug("Making fragments using underlying points-to analysis for " + targetClassName);
                fragPartitioner = new PointerAnalysisFragmentPartitioner(targetClass);
                break;
            default:
                throw new IllegalStateException("Unrecognized fragmentconstructor " +
                        cmdLine.getFragmentConstructor());
        }
        ccrs.forEach(fragPartitioner::partitionIntoFragments);

        // Build our rw-set based race condition analysis from our rw-set analysis
        RaceConditionAnalysis rwSetBasedRaceConditionAnalysis = new RWSetRaceConditionAnalysis();

        // Build fragmented monitor
        FragmentedMonitor fragmentedMonitor = new FragmentedMonitor(targetClass, ccrs);
        // send some debug info
        log.debug(fragmentedMonitor.getCCRs().size() + " CCRs in " + targetClassName);
        log.debug(fragmentedMonitor.numFragments() + " fragments in " + targetClassName);

        // retrieve any annotated monitor invariants
        log.debug("Retrieving monitor invariant annotations for " + targetClassName);

        final SMTBasedCommutativityAnalysis commutativityAnalysis = new SMTBasedCommutativityAnalysis(fragmentedMonitor.getMonitor(), rwSetBasedRaceConditionAnalysis);

        // make fragment dot files if requested
        if (cmdLine.isFragPartToDot() || cmdLine.isFragPartToSmallDot())
        {
            explicitMonitorWriter.writeDotFiles(
                    fragmentedMonitor,
                    cmdLine.getDotDir(),
                    rwSetBasedRaceConditionAnalysis,
                    commutativityAnalysis,
                    cmdLine.isFragPartToSmallDot()
            );
        }

        FragmentWeightStrategy fragmentWeightStrategy;
        switch(cmdLine.getFragmentWeightMethod()) {
            case "par-op":
                fragmentWeightStrategy = FragmentWeightStrategy.PARALLELIZATION_OPPORTUNITIES;
                break;
            case "uniform":
                fragmentWeightStrategy = FragmentWeightStrategy.UNIFORM;
                break;
            default:
                throw new IllegalStateException("Unknown fragment weight assignment method " + cmdLine.getFragmentWeightMethod());
        }

        // build checker
        LockAssignmentChecker lockAssignmentChecker = new LockAssignmentChecker(
                fragmentedMonitor,
                rwSetBasedRaceConditionAnalysis,
                commutativityAnalysis,
                signalInferenceResults.getPreChecks()
        );

        // Count number of lockings, if requested
        List<ImmutableLockAssignment> enumeratedLockings = null;
        if(cmdLine.isEnumerateLockingsEnabled())
        {
            log.debug("Beginning enumeration for " + targetClassName);
            monitorProfiler.pushEvent("enumeration");
            assert monitorToNumLockings != null;
            WeightedPartialMaxSatEnumeratorFactory wpmseFactory = new Z3WeightedPartialMaxSatEnumeratorFactory();
            WeightedPartialMaxSatSolverFactory wpmsFactory = new Z3WeightedPartialMaxSatSolverFactory();
            final LockAssignmentMaxSatEnumerator enumerator = new LockAssignmentMaxSatEnumerator(wpmseFactory,
                    wpmsFactory,
                    lockAssignmentChecker,
                    signalInferenceResults.getPreChecks(),
                    fragmentWeightStrategy);
            final long enumTimeoutInMS = cmdLine.getLockingEnumerationTimeoutInMs();
            final long solveTimeoutInMS = cmdLine.getMaxSatTimeoutInMs();
            enumeratedLockings = enumerator.enumerateLockings(enumTimeoutInMS, solveTimeoutInMS);
            final List<Integer> numberOfLockingsGroupedByNumLocksUsed = enumeratedLockings.stream()
                    .collect(Collectors.groupingBy(
                            LockAssignment::numLocks,
                            Collectors.counting()
                    )).entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .map(Long::intValue)
                    .collect(Collectors.toList());
            monitorToNumLockings.put(targetClassName, numberOfLockingsGroupedByNumLocksUsed);
            log.debug("enumeration complete for " + targetClassName);
            monitorProfiler.popEvent();
        }

        ImmutableLockAssignment lockAssignment = solveMaxSatForLockAssignment(targetClassName,
                lockAssignmentChecker,
                signalInferenceResults.getPreChecks(),
                cmdLine.getNewWeightedMaxSatSolverFactory(),
                fragmentWeightStrategy, cmdLine.getMaxSatTimeoutInMs());

        // record some summary statistics from the commutativity checker
        commutativityAnalysis.recordProfilingInfo();

        // implement an expresso version on a replica
        final ImmutableLockAssignment expressoLockAssignment = buildCoarseLockAssignment(fragmentedMonitor);
        // implement an ablation on a replica
        final ImmutableLockAssignment ablatedLockAssignment = buildAblatedLockAssignment(targetClassName,
                fragmentedMonitor,
                signalInferenceResults,
                cmdLine.getNewWeightedMaxSatSolverFactory(),
                fragmentWeightStrategy,
                cmdLine.getMaxSatTimeoutInMs());

        // Implement the expresso and ablated lock assignments
        implementAsReplica(fragmentedMonitor,
                targetClassName + "Expresso",
                signalInferenceResults,
                expressoLockAssignment,
                explicitMonitorWriter);
        implementAsReplica(fragmentedMonitor,
                targetClassName + "Ablated",
                signalInferenceResults,
                ablatedLockAssignment,
                explicitMonitorWriter);

        // implement any enumerated assignments
        if(enumeratedLockings != null && cmdLine.getEnumerationOutputDir() != null)
        {
            for(int i = 0; i < enumeratedLockings.size(); ++i)
            {
                implementAsReplica(fragmentedMonitor,
                        targetClassName + "Enumerated" + i,
                        signalInferenceResults,
                        enumeratedLockings.get(i),
                        explicitMonitorWriter);
            }
        }

        // implement the explicit monitor (insert locks and signals)
        explicitMonitorWriter.implementExplicitMonitor(fragmentedMonitor,
                signalInferenceResults.getSignalOperations(),
                lockAssignment,
                lockAssignment.getFieldsToConvertToAtomics(),
                signalInferenceResults.getPreChecks());

        // close profiler for this monitor
        cortadoGlobalProfiler.closeMonitorProfiler();
        clearGlobalCaches();

        // Write out implemented classes
        explicitMonitorWriter.writeExplicitMonitors(cmdLineOutFormat);
    }

    /**
     * Clear all the global caches
     */
    private static void clearGlobalCaches()
    {
        CachedStrongestPostConditionCalculatorFactory.getInstance().clear();
        CombinedDUAnalysisCache.getInstance().clear();
        ExceptionalUnitGraphCache.getInstance().clear();
        PostDominatorTreeCache.getInstance().clear();
        SimpleLiveLocalsSootAnalysisCache.getInstance().clear();

        MonitorInvariantGenerator.clear();
        ModelImporter.clear();

        CachedMayRWSetAnalysis.getInstance().clear();
        SootMayRWSetAnalysis.clear();
        RWSetRaceConditionAnalysis.clear();

        CachedExprTypeFactory.clear();
        CachedExprFactory.clear();
        CachedNormalizedBodyFactory.getInstance().clear();

        GuavaExceptionalUnitGraphCache.getInstance().clear();
    }

    /**
     * Use {@link LockAssignmentMaxSatSolver} to compute a lock assignment
     *
     * @param targetClassName the target class name (used for logging)
     * @param lockAssignmentChecker the checker used to generate correctness constraints
     * @param predPreChecks the pre-checks for predicates using the predicate flip optimization
     * @param wpmsSolverFactory factor for weighted partial max-sat solving
     * @param fragmentWeightStrategy fragment weight selection strategy
     * @param maxSatTimeoutInMs max sat timeout in milliseconds
     * @return the lock assignment
     * @throws MaxSatSolverFailException if the lock assignment solve fails
     */
    private static ImmutableLockAssignment solveMaxSatForLockAssignment(@Nonnull String targetClassName,
                                                                        @Nonnull LockAssignmentChecker lockAssignmentChecker,
                                                                        @Nonnull Map<SootMethod, Set<Unit>> predPreChecks,
                                                                        @Nonnull WeightedPartialMaxSatSolverFactory wpmsSolverFactory,
                                                                        @Nonnull FragmentWeightStrategy fragmentWeightStrategy,
                                                                        long maxSatTimeoutInMs)
            throws MaxSatSolverFailException
    {
        // this will hold map {Fragments} -> {sets of locks}
        ImmutableLockAssignment lockAssignment;
        /// Joint search for lock assignment and allocation ///////////////
        // get our weighted max-sat solver backend
        log.debug("Setting up joint search for lock assignment and lock allocation for class " + targetClassName);
        // solve with retries
        LockAssignmentMaxSatSolver lockAssignmentMaxSatSolver =
                new LockAssignmentMaxSatSolver(wpmsSolverFactory, lockAssignmentChecker, predPreChecks, fragmentWeightStrategy);
        try
        {
            lockAssignment = lockAssignmentMaxSatSolver.computeLockingWithRetries(maxSatTimeoutInMs);
        } catch(MaxSatSolverFailException e)
        {
            log.warn("max-sat solve failed with upper bound of 1 lock for class " + targetClassName);
            throw e;
        }
        // handle UNSAT case
        if(lockAssignment == null)
        {
            throw new IllegalStateException("lock constraints for max-sat problem are UNSAT for class " + targetClassName);
        }
        return lockAssignment;
    }

    /**
     * @return a lock assignment with one coarse lock
     */
    private static ImmutableLockAssignment buildCoarseLockAssignment(@Nonnull FragmentedMonitor fragmentedMonitor)
    {
        // build lock assignment
        final Map<Fragment, Set<Integer>> fragReplicaToCoarseLock = fragmentedMonitor
                .getFragments()
                .stream()
                .collect(Collectors.toMap(Function.identity(), f -> Collections.singleton(0)));
        return new ImmutableLockAssignment(fragReplicaToCoarseLock, Integer::compare, new HashSet<>());
    }

    /**
     * Generates a lock assignment using ablation study (i.e. just check for empty RW-sets)
     *
     * @return the generated lock assignment
     */
    private static ImmutableLockAssignment buildAblatedLockAssignment(@Nonnull String targetClassName,
                                                                      @Nonnull FragmentedMonitor fragmentedMonitor,
                                                                      @Nonnull SignalInferenceResults signalInferenceResults,
                                                                      @Nonnull WeightedPartialMaxSatSolverFactory wpmsSolverFactory,
                                                                      @Nonnull FragmentWeightStrategy fragmentWeightStrategy,
                                                                      long maxSatTimeoutInMs) throws MaxSatSolverFailException
    {
        // perform ablation
        log.debug("Beginning ablation for " + targetClassName);
        final CortadoProfiler cortadoGlobalProfiler = CortadoProfiler.getGlobalProfiler();
        final CortadoMonitorProfiler currentMonitorProfiler = cortadoGlobalProfiler.getCurrentMonitorProfiler();
        currentMonitorProfiler.pushEvent("ablation");

        // build lock checker according to ablation on the original fragmented monitor (to avoid soot issues)
        RaceConditionAnalysis raceConditionAnalysis = new RWSetRaceConditionAnalysis();
        CommutativityAnalysis commutativityAnalysis = new RaceConditionBasedCommutativityAnalysis(raceConditionAnalysis);
        LockAssignmentChecker lockAssignmentChecker = new LockAssignmentChecker(fragmentedMonitor,
                raceConditionAnalysis,
                commutativityAnalysis,
                signalInferenceResults.getPreChecks());

        // get the lock assignment
        final ImmutableLockAssignment lockAssignment = solveMaxSatForLockAssignment(targetClassName,
                lockAssignmentChecker,
                signalInferenceResults.getPreChecks(),
                wpmsSolverFactory,
                fragmentWeightStrategy,
                maxSatTimeoutInMs);

        log.debug("Ablation complete for " + targetClassName);
        currentMonitorProfiler.popEvent();

        return lockAssignment;
    }

    /**
     * Implements lock/signal inference results as a replica class
     *
     * Note that the replication process is painful to soot, and eliminates many global
     * variables (such as points-to graphs and class hierarchies, see {@link SootClassReplicator})
     *
     * @param fragmentedMonitor the monitor to replicate
     * @param replicaName the name of the replica
     * @param signalInferenceResults signals
     * @param lockAssignment the locking
     * @param writer the writer to use
     */
    private static void implementAsReplica(@Nonnull FragmentedMonitor fragmentedMonitor,
                                           @Nonnull String replicaName,
                                           @Nonnull SignalInferenceResults signalInferenceResults,
                                           @Nonnull ImmutableLockAssignment lockAssignment,
                                           @Nonnull ExplicitMonitorWriter writer) {
        CortadoMonitorProfiler monitorProfiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
        monitorProfiler.pushEvent(replicaName + ".instrumentation");
        // build our replica and migrate signalInferenceResults and lockAssignment to new CCRs/Fragments
        final FragmentedMonitor.FragmentedMonitorToReplica fragmentedMonitorToReplica = fragmentedMonitor.buildReplica(replicaName);
        final FragmentedMonitor fragmentedMonitorReplica = fragmentedMonitorToReplica.getFragmentedMonitorReplica();
        final SignalInferenceResults signalInferenceResultsReplica = signalInferenceResults.migrateResultsToReplica(fragmentedMonitorToReplica);
        final ImmutableMap<Fragment, Fragment> fragToReplica = fragmentedMonitorToReplica.getFragToReplica();
        final Map<Fragment, ImmutableSortedSet<Integer>> replicatedFragsToLockSet = lockAssignment.getFragments()
                .stream()
                .collect(Collectors.toMap(
                        fragToReplica::get,
                        lockAssignment::getLocksInOrder
                ));
        final Set<PotentialAtomicField> replicatedFieldsToConvertToAtomics = lockAssignment.getFieldsToConvertToAtomics()
                .stream()
                .map(potAtomic -> {
                    final SootField fieldReplica = fragmentedMonitorToReplica.getReplicator().getFieldToReplicaMap().get(potAtomic.getNonAtomicField());
                    return PotentialAtomicField.allOperationsCanBeAtomic(fieldReplica,
                            fragmentedMonitorReplica.getNonReadOnlyPredicateFields(),
                            signalInferenceResultsReplica.getPreChecks()
                    );
                })
                .collect(Collectors.toSet());
        final ImmutableLockAssignment lockAssignmentReplica = new ImmutableLockAssignment(replicatedFragsToLockSet,
                lockAssignment.getLockOrder(),
                replicatedFieldsToConvertToAtomics);

        // implement the explicit monitor (insert locks and signals)
        writer.implementExplicitMonitor(
                fragmentedMonitorReplica,
                signalInferenceResultsReplica.getSignalOperations(),
                lockAssignmentReplica,
                lockAssignmentReplica.getFieldsToConvertToAtomics(),
                signalInferenceResultsReplica.getPreChecks());
        monitorProfiler.popEvent();
    }
}
