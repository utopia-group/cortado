package edu.utexas.cs.utopia.cortado.util.soot;

import edu.utexas.cs.utopia.cortado.mockclasses.Assertions;
import edu.utexas.cs.utopia.cortado.mockclasses.ExceptionContainer;
import edu.utexas.cs.utopia.cortado.util.MonitorInterfaceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Object used to handle {@link soot} setup
 */
public class SootSetup
{
    private final static Logger log = LoggerFactory.getLogger(SootSetup.class.getName());

    /**
     * Set up {@link soot}
     *
     * Assumes any soot command line arguments have already been
     * set up. Must use java version 8.
     *
     * Details:
     * - Sets output format to {@link Options#output_format_jimple}
     * - allows phantom refs
     * - loads lock/condition/monitor related classes in as basic classes
     * - loads all applicationClassNames as application classes
     * - Runs default packs using {@link PackManager#runPacks()}.
     * If wholeProgram is true:
     * - Sets {@link Options#set_whole_program(boolean)} to true
     *   and sets {@link Options#setPhaseOption(String, String)} "cg.spark" to "on"
     * - Requires each application class to have a main function, and sets
     *   those as entry points
     *
     * @param wholeProgram if true, set up whole-program mode
     * @param applicationClassNames the application classes.
     */
    public static void setupSoot(boolean wholeProgram, String... applicationClassNames)
    {
        Options.v().set_output_format(Options.output_format_jimple);
        // Need this for ImplicitMergeThread, since transitive dependencies
        // try to load from internal libraries that I cannot find on Maven
        if(!Options.v().allow_phantom_refs())
        {
            log.warn("Enabling phantom refs");
            Options.v().set_allow_phantom_refs(true);
        }
        if(Options.v().java_version() != 0 && Options.v().java_version() != Options.java_version_8)
        {
            throw new IllegalStateException("Only java version 8 is supported");
        }
        Options.v().set_java_version(Options.java_version_8);
        // set up whole program mode, if necessary
        if(wholeProgram)
        {
            log.debug("enabling whole program mode in soot.");
            Options.v().set_whole_program(true);
            Options.v().setPhaseOption("cg.spark", "on");
        }
        // add necessary lock/IR/monitor related classes as basic classes
        log.debug("loading classes to soot");
        Arrays.asList(MonitorInterfaceUtils.LOCK_CLASSNAME,
                MonitorInterfaceUtils.CONDITION_CLASSNAME,
                MonitorInterfaceUtils.YIELD_CLASSNAME,
                MonitorInterfaceUtils.SIGNALLINGANNOTATION_CLASSNAME,
                MonitorInterfaceUtils.FRAGMENT_MOCK_CLASS_CLASSNAME,
                // Also load in VCGen required class
                ExceptionContainer.class.getName(),
                Assertions.class.getName())
                .forEach(className -> Scene.v().addBasicClass(className, SootClass.SIGNATURES));
        // load target classes as application classes, setting
        // their main methods as entry points if whole program mode is enabled
        // https://github.com/soot-oss/soot/wiki/Using-Soot-with-custom-entry-points
        log.debug("Resolving target class bodies.");
        final List<SootClass> applicationClasses = Stream.of(applicationClassNames)
                .map(className -> Scene.v().forceResolve(className, SootClass.BODIES))
                .collect(Collectors.toList());
        applicationClasses.forEach(SootClass::setApplicationClass);
        // make sure no application classes are phantom
        applicationClasses.stream()
                .filter(SootClass::isPhantom)
                .forEach(phantomAppClass -> {
                    throw new IllegalStateException("application class " + phantomAppClass + " is phantom." +
                            " Check your soot classpath.");
                });
        log.debug("Loading necessary classes.");
        Scene.v().loadNecessaryClasses();
        log.debug("Necessary classes loaded.");
        // make sure each target class has a main method if in whole-program mode
        if(wholeProgram) {
            for (SootClass appClass : applicationClasses) {
                if (appClass.getMethods().stream().noneMatch(SootMethod::isMain)) {
                    throw new IllegalArgumentException("class " + appClass + " has no main method. Reachable methods may be computed incorrectly.");
                }
            }
            final List<SootMethod> mains = applicationClasses.stream()
                    .map(SootClass::getMethods)
                    .flatMap(Collection::stream)
                    .filter(SootMethod::isMain)
                    .collect(Collectors.toList());
            Scene.v().setEntryPoints(mains);
        }
        log.debug("Running soot packs requested from command line.");
        PackManager.v().runPacks();
        log.debug("Soot packs complete.");
    }

    /**
     * Exactly as {@link #setupSoot(boolean, String...)}, but with a list
     * instead of varargs
     *
     * @param wholeProgram as {@link #setupSoot(boolean, String...)}
     * @param applicationClassNames as {@link #setupSoot(boolean, String...)}
     */
    public static void setupSoot(boolean wholeProgram, List<String> applicationClassNames)
    {
        setupSoot(wholeProgram, applicationClassNames.toArray(new String[0]));
    }
}
