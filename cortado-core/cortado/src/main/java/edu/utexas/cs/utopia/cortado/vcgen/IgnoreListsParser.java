package edu.utexas.cs.utopia.cortado.vcgen;

import soot.SootClass;
import soot.SootMethod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * parser and container for classes/methods we want
 * to ignore & use manual annotations for
 */
public class IgnoreListsParser {
    private final static IgnoreListsParser INSTANCE = new IgnoreListsParser();

    private final static String IGNORE_SUFFIX = "-ign.txt";
    // used to keep context of class cortado is analyzing
    private static String currentTargetClass = "";

    private final Map<String, List<String>> classesToIgnoreMap = new HashMap<>(),
                                            methodsToIgnoreMap = new HashMap<>();

    private IgnoreListsParser()
    {

    }

    public static IgnoreListsParser getInstance() { return INSTANCE; }

    // sets cortado class context
    public void setCurrentTargetClass(SootClass targetClass) {
        currentTargetClass = targetClass.getName();
    }

    public boolean hasIgnoreClassesFromTarget(String targetClassName) {
        return this.classesToIgnoreMap.containsKey(targetClassName);
    }

    public boolean hasIgnoreMethodsFromTarget(String targetClassName) {
        return this.methodsToIgnoreMap.containsKey(targetClassName);
    }

    // uses context to check if class should be ignored in vc gen
    public boolean hasIgnoreClass(SootClass ignoredClass) {
        return hasIgnoreClassesFromTarget(currentTargetClass)
                && this.classesToIgnoreMap.get(currentTargetClass).contains(ignoredClass.getName());
    }

    // same as above but for method instead of class
    public boolean hasIgnoreMethod(SootMethod ignoredMethod) {
        return hasIgnoreMethodsFromTarget(currentTargetClass)
                && this.methodsToIgnoreMap.get(currentTargetClass).contains(ignoredMethod.getSignature());
    }

    public void readIgnoreLists(String directoryLocation) {
        // get list of all files in {@param directoryLocation}
        try (Stream<Path> paths = Files.walk(Paths.get(directoryLocation))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase().endsWith(IGNORE_SUFFIX))
                 .forEach(this::readSingleFile);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    // read in classes/methods to ignore from current file
    private void readSingleFile(Path path) {
        try {
            List<String> allLines = Files.readAllLines(path);

            String fileName = String.valueOf(path.getFileName());
            String targetClassName = fileName.substring(0, fileName.length() - IGNORE_SUFFIX.length());

            List<String> classNames = allLines.stream()
                    .filter(s -> s.toLowerCase().startsWith("class: "))
                    .map(s -> s.substring(7))
                    .collect(Collectors.toList());
            this.classesToIgnoreMap.put(targetClassName, classNames);

            List<String> methodNames = allLines.stream()
                    .filter(s -> s.toLowerCase().startsWith("method: "))
                    .map(s -> s.substring(8))
                    .collect(Collectors.toList());

            this.methodsToIgnoreMap.put(targetClassName, methodNames);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
