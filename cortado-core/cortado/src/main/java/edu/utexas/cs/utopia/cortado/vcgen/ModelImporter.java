package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ModelImporter
{
    private static final Logger log = LoggerFactory.getLogger(ModelImporter.class);

    private static final ConcurrentHashMap.KeySetView<String, Boolean> ignoredFieldWarns = ConcurrentHashMap.newKeySet();

    private static FastHierarchy tyHierarchy = null;

    private final static ModelImporter INSTANCE = new ModelImporter();

    private final static String MODELS_SUFFIX = "-model.txt";

    private final static String FIELDS_SUFFIX = "-fields.txt";

    private final Map<SootMethod, Expr> methodToModelMap = new HashMap<>();

    private final Map<SootClass, Set<SootField>> classFields = new HashMap<>();

    private ModelImporter()
    {

    }

    public static ModelImporter getInstance() {
        tyHierarchy = Scene.v().getFastHierarchy();
        return INSTANCE;
    }

    public static void clear() {
        ignoredFieldWarns.clear();
        tyHierarchy = null;
        getInstance().methodToModelMap.clear();
        getInstance().classFields.clear();
    }

    public boolean hasMethodModel(SootMethod modeledMethod)
    {
        return this.methodToModelMap.containsKey(modeledMethod);
    }

    public boolean hasModeledFields(SootClass sootClass)
    {
        return this.classFields.containsKey(sootClass);
    }

    public Expr getModel(SootMethod modeledMethod)
    {
        if(!hasMethodModel(modeledMethod))
        {
            throw new IllegalStateException("Cannot find key " + modeledMethod.getName() + " in model map");
        }
        return this.methodToModelMap.get(modeledMethod);
    }

    public Set<SootField> getModeledFields(SootClass sootClass)
    {
        if (!hasModeledFields(sootClass))
            throw new IllegalArgumentException("No modeled fields for " + sootClass.getName());

        return this.classFields.get(sootClass);
    }

    // If topClass is an interface, it returns fields for all possible implementations.
    public Set<SootField> getAllPossibleModeledFields(SootClass topClass)
    {
        Set<SootField> rv = new HashSet<>();
        Set<SootClass> classesToConsider = new HashSet<>();

        if (!topClass.isInterface())
            classesToConsider.add(topClass);
        else
        {
            assert tyHierarchy != null;
            classesToConsider.addAll(tyHierarchy.getAllImplementersOfInterface(topClass));
        }

        classesToConsider.forEach(fldCl ->
                                  {
                                      String className = fldCl.getName();
                                      if (!hasModeledFields(fldCl))
                                      {
                                          if (ignoredFieldWarns.add(className))
                                              log.warn("Ignoring nested fields of " + className);
                                      }
                                      else
                                      {
                                          rv.addAll(getModeledFields(fldCl));
                                      }
                                  });

        return rv;
    }

    public void readModels(String directoryLocation)
    {
        try (Stream<Path> paths = Files.walk(Paths.get(directoryLocation))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> isModelFile(p) || isFieldFile(p))
                 .forEach(this::readSingleFile);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isModelFile(Path p)
    {
        return p.getFileName().toString().toLowerCase().endsWith(MODELS_SUFFIX);
    }

    private boolean isFieldFile(Path p)
    {
        return p.getFileName().toString().toLowerCase().endsWith(FIELDS_SUFFIX);
    }

    private boolean haveEmittedPhantomWarning = false;

    private void readSingleFile(Path path)
    {
        try {
            if (isModelFile(path))
            {
                String fileName = String.valueOf(path.getFileName());
                String targetMethodSig = "<"
                                       + fileName.substring(0, fileName.length() - MODELS_SUFFIX.length())
                                                 .replaceAll(";", ":")
                                                 .replaceAll("@", "<")
                                                 .replaceAll("!", ">")
                                       + ">";
                String className = fileName.substring(0, fileName.indexOf(";"));
                if(Scene.v().getSootClass(className).isPhantom())
                {
                    if(!haveEmittedPhantomWarning)
                    {
                        haveEmittedPhantomWarning = true;
                        log.warn("Ignoring attempt to import model of phantom soot class");
                    }
                    return;
                }

                SootMethod method = Scene.v().getMethod(targetMethodSig);
                Expr expr = ExprUtils.parseFrom(Files.newInputStream(path));

                methodToModelMap.put(method, expr);
            }
            else if (isFieldFile(path))
            {
                String fileName = String.valueOf(path.getFileName());
                String className = fileName.substring(0, fileName.length() - FIELDS_SUFFIX.length())
                                           .replaceAll(";", ":")
                                           .replaceAll("@", "<")
                                           .replaceAll("!", ">");

                SootClass sootClass = Scene.v().getSootClass(className);
                if(sootClass.isPhantom())
                {
                    if(!haveEmittedPhantomWarning)
                    {
                        haveEmittedPhantomWarning = true;
                        log.warn("Ignoring attempt to import model of phantom soot class");
                    }
                    return;
                }

                classFields.put(sootClass, new HashSet<>());

                Files.lines(path)
                     .forEach(fldSig -> classFields.get(sootClass)
                                                   .add(sootClass.getField(fldSig)));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
