package edu.utexas.cs.utopia.cortado.integration;

import com.google.common.collect.ImmutableMap;
import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor;
import edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitorAtomicTransformer;
import edu.utexas.cs.utopia.cortado.lockPlacement.ImmutableLockAssignment;
import edu.utexas.cs.utopia.cortado.lockPlacement.LockInserter;
import edu.utexas.cs.utopia.cortado.signalling.ConditionVarInjector;
import edu.utexas.cs.utopia.cortado.signalling.ConditionVarLockAssigner;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperation;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperationInjector;
import edu.utexas.cs.utopia.cortado.staticanalysis.CommutativityAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.RaceConditionAnalysis;
import edu.utexas.cs.utopia.cortado.util.soot.SootTransformUtils;
import edu.utexas.cs.utopia.cortado.util.soot.atomics.PotentialAtomicField;
import edu.utexas.cs.utopia.cortado.util.visualization.DotFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.baf.BafASMBackend;
import soot.options.Options;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static edu.utexas.cs.utopia.cortado.util.soot.SootTransformUtils.registerTransformer;

/**
 * Combines tools from across the cortado project to implement and
 * write out explicit monitor implementations of some {@link edu.utexas.cs.utopia.cortado.ccrs.FragmentedMonitor}
 * read by {@link ImplicitMonitorReader}
 */
public class ExplicitMonitorWriter
{
    private static final Logger log = LoggerFactory.getLogger(ExplicitMonitorWriter.class.getName());
    private final Set<SootClass> explicitMonitorsToWrite = new HashSet<>();

    /**
     * Insert locks and signals into the monitor according to sigOpInf
     * and the lockAssignment.
     *
     * Note that this modifies the soot class underlying fragmentedMonitor
     * @param fragmentedMonitor the monitor
     * @param signalOperations the signal operations which will be inserted
     * @param lockAssignment the locking assignment
     * @param fieldsToMakeAtomic the fields which should be made atomic
     * @param preChecks map from predicates to units performing a pre-check for the predFlip optimization
     */
    public void implementExplicitMonitor(@Nonnull FragmentedMonitor fragmentedMonitor,
                                         @Nonnull Map<CCR, Set<SignalOperation>> signalOperations,
                                         @Nonnull ImmutableLockAssignment lockAssignment,
                                         @Nonnull Collection<PotentialAtomicField> fieldsToMakeAtomic,
                                         @Nonnull Map<SootMethod, Set<Unit>> preChecks)
    {
        implementExplicitMonitor(
                fragmentedMonitor,
                signalOperations,
                lockAssignment,
                fieldsToMakeAtomic,
                fragmentedMonitor.getMonitor().getName(),
                preChecks);
    }

    /**
     * Just like { #implementExplicitMonitor(FragmentedMonitor, Map, ImmutableLockAssignment, Collection)},
     * except provides option to not add the modified class to
     * {@link #explicitMonitorsToWrite}
     * @param classNameForPacks the name to use when naming transforms inside of soot {@link Pack}s
     *
     **/
    private void implementExplicitMonitor(@Nonnull FragmentedMonitor fragmentedMonitor,
                                          @Nonnull Map<CCR, Set<SignalOperation>> signalOperations,
                                          @Nonnull ImmutableLockAssignment lockAssignment,
                                          @Nonnull Collection<PotentialAtomicField> fieldsToMakeAtomic,
                                          @Nonnull String classNameForPacks,
                                          Map<SootMethod, Set<Unit>> preChecks)
    {
        // grab the soot class and name
        SootClass monitorClass = fragmentedMonitor.getMonitor();
        String className = monitorClass.getName();
        // grab the JTP-pack
        final Pack jtpPack = PackManager.v().getPack("jtp");


        // delay lock releases where possible to avoid release(); acquire() pattern
        // of signalling
        final ConditionVarLockAssigner conditionVarLockAssigner = new ConditionVarLockAssigner(fragmentedMonitor.getCCRs(), lockAssignment);
        ImmutableMap<SootMethod, Integer> predToLockID = conditionVarLockAssigner.getGuardToLock();
        lockAssignment = LockReleaseDelayer.delayReleaseAfterConditionCheck(lockAssignment, signalOperations, predToLockID);

        // insert our locks
        log.debug("Inserting locks into " + className);
        LockInserter lockInserter = new LockInserter(fragmentedMonitor.getCCRs(), lockAssignment);
        Transform lockInserterT = registerTransformer(jtpPack, classNameForPacks, lockInserter);
        SootTransformUtils.applyTransform(monitorClass, lockInserterT);

        log.debug("Lock insertion complete. Beginning signal insertion for " + className);
        // apply condition var injector
        ConditionVarInjector condVarInjector = new ConditionVarInjector(monitorClass, fragmentedMonitor.getCCRs(), lockAssignment);
        Transform condVarInjectorT = registerTransformer(jtpPack, classNameForPacks, condVarInjector);
        SootTransformUtils.applyTransform(monitorClass, condVarInjectorT);

        // apply signal operation injector
        SignalOperationInjector sigOpInjector = new SignalOperationInjector(
                signalOperations,
                lockAssignment,
                condVarInjector.getPredToLockID(),
                condVarInjector.getYieldToAwait()
        );
        Transform sigOpInjectorT = registerTransformer(jtpPack, classNameForPacks, sigOpInjector);
        SootTransformUtils.applyTransform(monitorClass, sigOpInjectorT);

        log.debug("Signal injection complete.");

        FragmentedMonitorAtomicTransformer atomicTransformer = new FragmentedMonitorAtomicTransformer(fragmentedMonitor, fieldsToMakeAtomic, preChecks);
        atomicTransformer.atomicTransformation();

        // store the explicit monitors in case we want to write them later
        explicitMonitorsToWrite.add(monitorClass);
    }

    /**
     * Write dot-files of fragmentedMonitor to outputDir.
     *
     * @param fragmentedMonitor the monitor to make dot files for
     * @param outputDir the output directory
     * @param raceConditionAnalysis the race-condition analysis
     * @param condenseFragments If true, compress fragments. If false,
     *                          write entire fragments to the dot file
     * @throws IOException if cannot write dot files to outputDir
     */
    public void writeDotFiles(@Nonnull FragmentedMonitor fragmentedMonitor,
                              @Nonnull String outputDir,
                              @Nonnull RaceConditionAnalysis raceConditionAnalysis,
                              @Nonnull CommutativityAnalysis commutativityAnalysis,
                              boolean condenseFragments) throws IOException
    {
        SootClass monitorClass = fragmentedMonitor.getMonitor();
        log.debug("Drawing dot files for " + monitorClass.getName());
        // Draw each method
        List<String> dotTexts = new ArrayList<>(),
                dotFileNames = new ArrayList<>();
        for(CCR ccr : fragmentedMonitor.getCCRs()) {
            String dotText = DotFactory.drawPartitionedCCR(
                    ccr,
                    condenseFragments);
            String fileName = monitorClass.getName() + "." + ccr.getAtomicSection().getName() + ".dot";
            dotTexts.add(dotText);
            dotFileNames.add(fileName);
        }
        // Draw the entire class
        dotTexts.add(DotFactory.drawFragmentedMonitor(fragmentedMonitor, raceConditionAnalysis, commutativityAnalysis, condenseFragments));
        dotFileNames.add(monitorClass.getName() + ".dot");
        assert dotTexts.size() == dotFileNames.size();
        for(int index = 0; index < dotTexts.size(); ++index)
        {
            String dotText = dotTexts.get(index);
            String fileName = dotFileNames.get(index);
            Path filePath = Paths.get(outputDir, fileName);
            try {
                FileWriter outWriter = new FileWriter(filePath.toString());
                outWriter.write(dotText);
                outWriter.close();
            } catch (IOException e) {
                System.err.println("Creation of dot file for " + monitorClass.getName() + " failed.");
                e.printStackTrace();
                throw e;
            }
        }
    }

    /**
     * Write all the explicit monitors (see {@link #implementExplicitMonitor(FragmentedMonitor, Map, ImmutableLockAssignment, Collection, String, Map)})
     * to files.
     *
     * @param outputFormat The output format. Must be either {@link Options#output_format_class}
     *                     or {@link Options#output_format_jimple}
     * @throws IOException if cannot write to the directory specified by
     *                      {@link Options#output_dir()}
     */
    public void writeExplicitMonitors(int outputFormat) throws IOException
    {
        // output format must be jimple or class
        if(outputFormat != Options.output_format_class && outputFormat != Options.output_format_jimple)
        {
            throw new IllegalArgumentException("Output format must be either class or jimple");
        }
        // Print classes out to file
        // Based on edu.utexas.cs.utopia.expresso.Driver
        log.debug("Writing transformed classes to files");
        for (SootClass targetClass : explicitMonitorsToWrite)
        {
            // get output location conforming with the class package
            String fileName = SourceLocator.v().getFileNameFor(targetClass, outputFormat);
            writeClass(targetClass, fileName, outputFormat);
        }
    }

    /**
     * Write sootClass to file fileName with outputFormat
     *
     * @param sootClass the class
     * @param fileName the file name
     * @param outputFormat the output format. Must be class or jimple.
     * @throws IOException if cannot write to file
     */
    private void writeClass(SootClass sootClass, String fileName, int outputFormat) throws IOException
    {
        // make sure that directory exists, and try to create it if it does not
        final Path filePath = Paths.get(fileName);
        final File fileDirectory = new File(filePath.getParent().toString());
        if(!fileDirectory.exists()) {
            if(!fileDirectory.mkdirs()) {
                throw new IOException("Failed to make directory for .class files: " + fileDirectory);
            }
        }
        // build our output stream
        OutputStream outputStream = new FileOutputStream(fileName);
        // now write the class
        // https://github.com/soot-oss/soot/wiki/Creating-a-class-from-scratch#write-to-class-file
        if(outputFormat == Options.output_format_class)
        {
            BafASMBackend backend = new BafASMBackend(sootClass, Options.v().java_version());
            backend.generateClassFile(outputStream);
        }
        else
        {
            assert outputFormat == Options.output_format_jimple;
            PrintWriter writerOut = new PrintWriter(new OutputStreamWriter(outputStream));
            Printer.v().printTo(sootClass, writerOut);
            writerOut.flush();
        }
        // close
        outputStream.close();
    }
}
