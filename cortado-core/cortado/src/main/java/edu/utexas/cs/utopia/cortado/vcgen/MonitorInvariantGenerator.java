package edu.utexas.cs.utopia.cortado.vcgen;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.SelectExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.array.StoreExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.ForAllExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.ImplExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.integer.*;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.AbstractExprTransformer;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.PostOrderExprVisitor;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoMonitorProfiler;
import edu.utexas.cs.utopia.cortado.util.logging.CortadoProfiler;
import edu.utexas.cs.utopia.cortado.vcgen.templategen.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.expression.ExprUtils.*;
import static edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils.*;

public class MonitorInvariantGenerator
{
    private static final ModelImporter models = ModelImporter.getInstance();

    private static final Logger log = LoggerFactory.getLogger(MonitorInvariantGenerator.class);

    private static final CachedStrongestPostConditionCalculatorFactory spCalcFac = CachedStrongestPostConditionCalculatorFactory.getInstance();

    private static final ExprFactory eFac = CachedExprFactory.getInstance();

    private static final ConcurrentHashMap<SootClass, MonitorInvariantGenerator> INSTANCES = new ConcurrentHashMap<>();

    public static MonitorInvariantGenerator getInstance(SootClass monitor)
    {
        INSTANCES.putIfAbsent(monitor, new MonitorInvariantGenerator(monitor));
        return INSTANCES.get(monitor);
    }

    public static void clear() {
        INSTANCES.clear();
    }

    private final SootClass monitor;

    private final Set<SootMethod> constructors = new HashSet<>();

    private final Set<SootMethod> atomicMethods = new HashSet<>();

    private final Set<Expr> monitorInvariant = new HashSet<>();

    private MonitorInvariantGenerator(SootClass monitor)
    {
        this.monitor = monitor;

        this.monitor.getMethods()
                    .stream()
                    .filter(SootMethod::hasActiveBody)
                    .forEach(m -> {
                       if (m.isConstructor())
                           constructors.add(m);
                       else if (!m.isStatic() && m.isPublic() && CCR.isCCRMethod(m))
                       {
                           atomicMethods.add(m);
                       }
                    });

        monitorInvariant.add(eFac.mkTRUE());
    }

    public void inferInvariant()
    {
        // Try to load invariant, if one exists.
        File invariantFile = Paths.get("target", monitor.getName() + "-inv.txt").toFile();
        if (invariantFile.exists())
        {
            try(FileInputStream fileInStream = new FileInputStream(invariantFile))
            {
                monitorInvariant.add(ExprUtils.parseFrom(fileInStream));
                return;
            }
            catch (IOException | RuntimeException ex)
            {
                log.warn("Unable to load invariant from file: " + ex.getMessage());
            }
        }

        CortadoMonitorProfiler monitorProfiler = CortadoProfiler.getGlobalProfiler().getCurrentMonitorProfiler();
        monitorProfiler.pushEvent("invariantInference");

        FunctionApp thisExpr = VCGenUtils.getThisConstantForClass(monitor);
        Set<Expr> intTermExpr = new HashSet<>();
        Set<Expr> boolTermExpr = new HashSet<>();
        Set<Expr> nestedRefs = new HashSet<>();

        for (SootField fld : monitor.getFields())
        {
            if (fld.isStatic())
                continue;

            Type fldTy = fld.getType();

            // Currently only inferring invariants over immediate primitive fields.
            Expr fldArr = getFieldHeapArrayOrGlobal(fld);
            SelectExpr thisFld = eFac.mkSELECT(fldArr, thisExpr);
            if (fldTy instanceof PrimType)
            {
                if (fldTy.equals(BooleanType.v()))
                    boolTermExpr.add(thisFld);
                else
                    intTermExpr.add(thisFld);
            }
            else if (fldTy instanceof ArrayType)
            {
                intTermExpr.add(eFac.mkSELECT(getLengthHeapArray(), thisFld));
            }
            else if (fldTy instanceof RefType)
            {
                nestedRefs.add(thisFld);

                SootClass fldClass = ((RefType)fldTy).getSootClass();
                models.getAllPossibleModeledFields(fldClass)
                      .forEach(nestFld -> {
                          Type nestFldType = nestFld.getType();
                          if (nestFldType instanceof PrimType)
                          {
                              FunctionApp nestFldHeapArr = getFieldHeapArrayOrGlobal(nestFld);
                              SelectExpr nestTerm = eFac.mkSELECT(nestFldHeapArr, thisFld);

                              if (nestFldType.equals(BooleanType.v()))
                                  boolTermExpr.add(nestTerm);
                              else
                                  intTermExpr.add(nestTerm);
                          }
                      });
            }
        }

        CachedStrongestPostConditionCalculatorFactory spCalcFactory = CachedStrongestPostConditionCalculatorFactory.getInstance();
        HoudiniOpAndConstCollector opAndConstCollector = new HoudiniOpAndConstCollector();

        for (SootMethod ctr : constructors)
        {
            StrongestPostConditionCalculator ctrSpCalc = spCalcFactory.getOrCompute(ctr);
            ctrSpCalc.getMethodEncoding(null).accept(opAndConstCollector);
        }

        for (SootMethod aMeth : atomicMethods)
        {
            StrongestPostConditionCalculator mSpCalc = spCalcFactory.getOrCompute(aMeth);
            mSpCalc.getMethodEncoding(null).accept(opAndConstCollector);
        }

        HoudiniCandidateGenerator candidateGenerator = new HoudiniCandidateGenerator(intTermExpr, boolTermExpr, nestedRefs, opAndConstCollector.intConstExpr,
                                                                                     opAndConstCollector.naryCandGens, opAndConstCollector.binaryCandGens);

        Collection<Expr> candidates = candidateGenerator.getCandidates();
        candidates = filterInvalidInvariantCandidates(candidates);

        monitorInvariant.addAll(candidates);

        if (!candidates.isEmpty())
        {
            try
            {
                monitorProfiler.logMonitorInvariant(getInvariant());
            } catch (IOException e)
            {
                log.error("Unable to log monitor invariant: " + e.getMessage());
            }
        }

        log.debug("Invariant inference complete");
        monitorProfiler.popEvent();
    }

    public Expr getInvariant()
    {
        return narryOrSelfDistinct(monitorInvariant, eFac::mkAND);
    }

    private Collection<Expr> filterInvalidInvariantCandidates(Collection<Expr> candidates)
    {
        log.debug("Starting Houdini with  " + candidates.size() + " invariant candidates");
        for (SootMethod ctr : constructors)
        {
            StrongestPostConditionCalculator ctrSpCalc = spCalcFac.getOrCompute(ctr);

            Map<Expr, MethodHoareTriple> candidatesTriples = new HashMap<>();
            for (Expr c : candidates)
            {
                candidatesTriples.put(c, new MethodHoareTriple(eFac.mkTRUE(), c, ctr));
            }

            candidates = candidates.parallelStream().
                            filter(c -> ctrSpCalc.isValid(candidatesTriples.get(c), true))
                            .collect(Collectors.toSet());
            log.debug(candidates.size() + " invariant candidates are instantiated by constructor");

            if (candidates.isEmpty()) return candidates;
        }

        int currSize;
        do
        {
            currSize = candidates.size();
            Expr inv = narryOrSelfDistinct(candidates, eFac::mkAND);

            candidates = candidates.parallelStream()
                            .filter(c ->
                                    atomicMethods.parallelStream()
                                            .map(m -> new MethodHoareTriple(inv, c, m))
                                            .allMatch(t -> {
                                                StrongestPostConditionCalculator mSpCalc = spCalcFac.getOrCompute(t.method);
                                                return mSpCalc.isValidUnderApprox(t);
                                            })
                            ).collect(Collectors.toSet());
            log.debug("Pruned down to " + candidates.size() + " invariant candidates after one Houdini step");
        }
        while (!candidates.isEmpty() && currSize != candidates.size());

        return candidates;
    }
}

class HoudiniOpAndConstCollector extends PostOrderExprVisitor
{
    final Set<IntegerCandidateGenerator> naryCandGens = new HashSet<>();

    final Set<IntegerCandidateGenerator> binaryCandGens = new HashSet<>();

    final Set<Expr> intConstExpr = new HashSet<>();

    @Override
    public void visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            binaryCandGens.add(DivCandidateGenerator.getInstance());
        }
    }

    @Override
    public void visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            naryCandGens.add(MinusCandidateGenerator.getInstance());
        }
    }

    @Override
    public void visit(ModExpr e)
    {
        if (!alreadyVisited(e))
        {
            binaryCandGens.add(ModCandidateGenerator.getInstance());
        }
    }

    @Override
    public void visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            naryCandGens.add(MultCandidateGenerator.getInstance());
        }
    }

    @Override
    public void visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            naryCandGens.add(PlusCandidateGenerator.getInstance());
        }
    }

    @Override
    public void visit(RemExpr e)
    {
        if (!alreadyVisited(e))
        {
            binaryCandGens.add(RemCandidateGenerator.getInstance());
        }
    }
}
