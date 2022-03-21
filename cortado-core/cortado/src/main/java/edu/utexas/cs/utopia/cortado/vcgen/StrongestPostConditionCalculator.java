package edu.utexas.cs.utopia.cortado.vcgen;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.ImmutableGraph;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.ImplExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.singletons.CachedMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.util.graph.SootGraphConverter;
import edu.utexas.cs.utopia.cortado.util.graph.StronglyConnectedComponents;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.CompleteBlockGraph;
import soot.toolkits.graph.MHGDominatorsFinder;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.expression.ExprUtils.*;
import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.getOrAddLocal;
import static edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils.*;

public class StrongestPostConditionCalculator
{
    private static final CachedNormalizedBodyFactory NORMALIZED_BODY_FACTORY = CachedNormalizedBodyFactory.getInstance();

    private static final ExprFactory eFac = CachedExprFactory.getInstance();

    private final StronglyConnectedComponents<Block> sccs;

    private final Map<Unit, Block> unitBlockMap = new HashMap<>();

    private final Map<Block, ImmutableList<Block>> blockSCCMap = new HashMap<>();

    private final Set<Block> loopHeads = new HashSet<>();

    private final Stack<SootMethod> callStack;

    final NormalizedMethodClone normMethod;

    @SuppressWarnings("UnstableApiUsage")
    public StrongestPostConditionCalculator(SootMethod inMethod, Stack<SootMethod> inCallStack)
    {
        // get the normalized method
        this.normMethod = NORMALIZED_BODY_FACTORY.getOrCompute(inMethod);

        CompleteBlockGraph blockGraph = new CompleteBlockGraph(normMethod.getNormalizedMethod()
                                                                         .getActiveBody());

        final ImmutableGraph<Block> blockGraphGuava = SootGraphConverter.convertToGuavaGraph(blockGraph);
        this.sccs = new StronglyConnectedComponents<>(blockGraphGuava);
        assert this.sccs.sccIsValid(blockGraphGuava);

        blockGraph.forEach(b -> b.forEach(u -> {
            assert !unitBlockMap.containsKey(u) : "Invalid assumption";
            unitBlockMap.put(u, b);
        }));

        sccs.getComponents()
            .forEach(scc -> scc.forEach(b -> blockSCCMap.put(b, scc)));

        MHGDominatorsFinder<Block> dominators = new MHGDominatorsFinder<>(blockGraph);

        blockGraph.forEach(b -> {
            List<Block> bDoms = dominators.getDominators(b);
            b.getSuccs()
             .forEach(succ -> {
                if (bDoms.contains(succ))
                    loopHeads.add(succ);
             });
        });

        this.callStack = new Stack<>();
        this.callStack.addAll(inCallStack);
        this.callStack.add(inMethod);
    }

    public StrongestPostConditionCalculator(SootMethod inMethod)
    {
        this(inMethod, new Stack<>());
    }

    public boolean isValid(ProgramPointHoareTriple triple)
    {
        return isValid(triple, false);
    }

    public boolean isValidUnderApprox(ProgramPointHoareTriple triple)
    {
        return isValidUnderApprox(triple, false);
    }

    public boolean isValid(ProgramPointHoareTriple triple, boolean disableExceptions) {
        return checkIsValid(triple, disableExceptions, false);
    }

    public boolean isValidUnderApprox(ProgramPointHoareTriple triple, boolean disableExceptions) {
        return checkIsValid(triple, disableExceptions, true);
    }

    private boolean checkIsValid(ProgramPointHoareTriple triple, boolean disableExceptions, boolean underApprox)
    {
        Expr pre = triple.preCond;
        Expr post = triple.postCond;

        ExprSkolemPair sp = sp(triple.start, triple.end, calculateSkolemMap(pre), disableExceptions, false);

        return checkPreImpliesPostIsValid(underApprox, pre, post, sp);
    }

    public boolean isValid(MethodHoareTriple triple)
    {
        return isValid(triple, false);
    }


    public boolean isValidUnderApprox(MethodHoareTriple triple)
    {
        return isValidUnderApprox(triple, false);
    }

    public boolean isValid(MethodHoareTriple triple, boolean disableExceptions) {
        return checkIsValid(triple, disableExceptions, false);
    }

    public boolean isValidUnderApprox(MethodHoareTriple triple, boolean disableExceptions) {
        return checkIsValid(triple, disableExceptions, true);
    }

    private boolean checkIsValid(MethodHoareTriple triple, boolean disableExceptions, boolean underApprox)
    {
        if (triple.method != normMethod.getOriginalMethod())
            throw new IllegalArgumentException("Invalid method for triple.");

        Expr pre = triple.preCond;
        Expr post = triple.postCond;

        ExprSkolemPair sp = sp(calculateSkolemMap(pre), disableExceptions);
        return checkPreImpliesPostIsValid(underApprox, pre, post, sp);
    }

    private boolean checkPreImpliesPostIsValid(boolean underApprox, Expr pre, Expr post, ExprSkolemPair skolemPair) {
        ImplExpr query = eFac.mkIMPL(eFac.mkAND(pre, skolemPair.cond),
                                     skolemize(post, skolemPair.skolemMap));

        boolean isValid;
        if(underApprox) {
            isValid = isUnsatUnderApprox(eFac.mkNEG(query));
        } else {
            isValid = isUnsat(eFac.mkNEG(query));
        }
        return isValid;
    }

    public Expr getMethodEncoding(Expr lhs)
    {
        return getMethodEncoding(lhs, false);
    }

    public Expr getMethodEncoding(Expr lhs, boolean disableExceptions)
    {
        return getMethodEncoding(lhs, eFac.mkTRUE(), disableExceptions);
    }

    public Expr getMethodEncoding(Expr lhs, Expr preCondition)
    {
        return getMethodEncoding(lhs, preCondition, false);
    }

    public Expr getMethodEncoding(Expr lhs, Expr preCondition, boolean disableExceptions)
    {
        SootMethod orgMethod = normMethod.getOriginalMethod();
        if (lhs != null && orgMethod.getReturnType().equals(VoidType.v()))
            throw new IllegalArgumentException("lhs is only for non-void methods");

        ExprSkolemPair postCond = sp(calculateSkolemMap(preCondition), disableExceptions);

        Expr rv = postCond.cond;
        if (lhs != null)
        {
            Body normBody = normMethod.getNormalizedMethod().getActiveBody();
            ReturnStmt retStmt = (ReturnStmt) findReturnStatement(normBody.getUnits());
            ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(orgMethod, postCond.skolemMap);
            assert retStmt != null;
            retStmt.getOp().apply(exprGen);

            rv = eFac.mkAND(rv, eFac.mkEQ(exprGen.getResult(), lhs));
        }

        return eFac.mkAND(preCondition, rv);
    }

    public Expr propagateInvariantUpTo(Unit u, Expr pre)
    {
        MonitorInvariantGenerator invGen = MonitorInvariantGenerator.getInstance(normMethod.getOriginalMethod().getDeclaringClass());
        Expr invariant = skolemize(invGen.getInvariant(), calculateSkolemMap(pre));

        if (u == null)
            return eFac.mkAND(pre, invariant);

        Body normBody = normMethod.getOriginalMethod().getActiveBody();
        UnitPatchingChain normUnits = normBody.getUnits();
        Unit start = normUnits.getFirst();

        return sp(start, u, eFac.mkAND(pre, invariant), false, true);
    }

    public Expr spUpTo(Unit u, Expr pre)
    {
        if (u == null)
            return pre;

        Body normBody = normMethod.getOriginalMethod().getActiveBody();
        UnitPatchingChain normUnits = normBody.getUnits();
        Unit start = normUnits.getFirst();

        return sp(start, u, pre, false, true);
    }

    public Expr sp(Unit start, Unit end, Expr pre)
    {
        return sp(start, end, pre, false, false);
    }

    Expr sp(Unit start, Unit end, Expr pre, boolean disableExceptions, boolean excludeEnd)
    {
        return eFac.mkAND(pre, sp(start, end, calculateSkolemMap(pre), disableExceptions, excludeEnd).cond);
    }

    public Expr getEncodingForIdentityStatements(Expr pre)
    {
        SootMethod orgMethod = normMethod.getOriginalMethod();
        UnitPatchingChain originalMethodUnits = orgMethod.getActiveBody().getUnits();
        Unit first = originalMethodUnits.getFirst();
        Unit last = first;
        while (last instanceof IdentityStmt) last = originalMethodUnits.getSuccOf(last);

        if (first != last)
        {
            return eFac.mkAND(pre, sp(first, originalMethodUnits.getPredOf(last), calculateSkolemMap(pre), false, false).cond);
        }
        else return pre;
    }

    static public Expr encodeConditionToPostState(Expr condition, Expr postStateEncoding)
    {
        return skolemize(condition, calculateSkolemMap(postStateEncoding));
    }

    public Expr getFreshCopyForPostState(Expr e, Expr postState)
    {
        Map<Expr, Integer> skolemMap = calculateSkolemMap(postState);
        skolemMap.replaceAll((k, v) -> v+1);
        return skolemize(e, skolemMap);
    }

    public Expr getRetVal()
    {
        SootMethod orgMethod = normMethod.getOriginalMethod();
        if (orgMethod.getReturnType().equals(VoidType.v()))
            throw new IllegalArgumentException("method has no return value");

        Body normBody = normMethod.getNormalizedMethod().getActiveBody();
        ReturnStmt ret = (ReturnStmt) findReturnStatement(normBody.getUnits());
        ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(orgMethod);
        assert ret != null;
        ret.getOp().apply(exprGen);

        return exprGen.getResultWithoutSkolem();
    }

    public Expr getEdgeCondition(Unit src, Unit trg, Expr pre)
    {
        Unit normalizedSrc = normMethod.originalToNormalizedBottom(src);
        Unit normalizedTrg = normMethod.originalToNormalizedTop(trg);
        return eFac.mkAND(pre, getEdgeCondition(unitBlockMap.get(normalizedSrc), unitBlockMap.get(normalizedTrg), calculateSkolemMap(pre)));
    }

    public Expr getNotEdgeCondition(Unit src, Unit trg, Expr pre)
    {
        Unit normalizedSrc = normMethod.originalToNormalizedBottom(src);
        Unit normalizedTrg = normMethod.originalToNormalizedTop(trg);
        return eFac.mkAND(pre, eFac.mkNEG(getEdgeCondition(unitBlockMap.get(normalizedSrc), unitBlockMap.get(normalizedTrg), calculateSkolemMap(pre))));
    }

    // Returns encoding for the whole method.
    ExprSkolemPair sp(Map<Expr, Integer> inSkolemMap, boolean disableExceptions)
    {
        Body normBody = normMethod.getNormalizedMethod().getActiveBody();
        UnitPatchingChain normUnits = normBody.getUnits();
        Unit start = normUnits.getFirst();
        Unit end = findReturnStatement(normUnits);

        assert end != null : "normalized body does not contain return statement";

        return spOnNormalizedBody(start, end, inSkolemMap, disableExceptions, false);
    }

    ExprSkolemPair sp(Unit start, Unit end, Map<Expr, Integer> inSkolemMap, boolean disableExceptions, boolean excludeEnd)
    {
        Unit normalizedStart = normMethod.originalToNormalizedTop(start);
        Unit normalizedEnd = normMethod.originalToNormalizedBottom(end);
        return spOnNormalizedBody(normalizedStart, normalizedEnd, inSkolemMap, disableExceptions, excludeEnd);
    }

    ExprSkolemPair spOnNormalizedBody(Unit normalizedStart, Unit normalizedEnd, Map<Expr, Integer> inSkolemMap, boolean disableExceptions, boolean excludeEnd)
    {
        if (!unitBlockMap.containsKey(normalizedStart) || !unitBlockMap.containsKey(normalizedEnd))
            throw new IllegalArgumentException("Units start and end must be part of the method");

        Block startBlock = unitBlockMap.get(normalizedStart),
              endBlock = unitBlockMap.get(normalizedEnd);

        ImmutableList<Block> startSCC = blockSCCMap.get(startBlock),
                    endSCC = blockSCCMap.get(endBlock);

        ImmutableList<ImmutableList<Block>> sccComponents = sccs.getComponents();
        int startSCCIndex = sccComponents.indexOf(startSCC),
            endSCCIndex = sccComponents.indexOf(endSCC);

        // SCCs are stored in reverse topological order.
        if (!(endSCCIndex <= startSCCIndex))
            throw new IllegalArgumentException("Unit end must be a successor of start");

        if ((startSCC.size() > 1 && !isEntryBlock(startSCC, startBlock)) ||
            (endSCC.size() > 1 && !isExitBlock(endSCC, endBlock)))
            throw new IllegalArgumentException("Units start and end can only be in the head and exit of a loop respectively");

        Map<Block, Map<Expr, Integer>> blockSkolemMap = new HashMap<>();
        Map<Block, Expr> blockPostCond = new HashMap<>();

        for (int i = startSCCIndex; i >= endSCCIndex; i--)
        {
            List<Block> scc = sccComponents.get(i);

            boolean isLoop = isLoop(scc);
            if (isLoop)
            {
                Set<Block> loopPreds = scc.stream()
                                          .filter(b -> isEntryBlock(scc, b))
                                          .flatMap(b -> b.getPreds().stream())
                                          .filter(b -> !scc.contains(b))
                                          .collect(Collectors.toSet());
                Set<Expr> preConds = loopPreds.stream()
                                              .map(b -> blockPostCond.getOrDefault(b, eFac.mkTRUE()))
                                              .collect(Collectors.toSet());
                Expr preCond = narryOrSelfDistinct(preConds, eFac::mkOR);
                Set<Expr> loopTrgs = getLoopTargets(scc, preCond);

                loopPreds.forEach(pred -> {
                    // pred might not have been visited if start is the loop head.
                    Map<Expr, Integer> predSkolemMap = blockSkolemMap.getOrDefault(pred, new HashMap<>());
                    loopTrgs.stream().filter(predSkolemMap::containsKey).forEach(trg -> freshSkolem(trg, predSkolemMap));
                });
            }

            // In case of a loop, calculate post-conditions only for entry & exit blocks.
            Stack<Block> sccInTopo = sccTopologicalSort(scc);
            while(!sccInTopo.isEmpty())
            {
                Block curr = sccInTopo.pop();

                // Do not consider predecessors of startBlock.
                List<Block> preds = Objects.equals(curr, startBlock) ? new ArrayList<>() : new ArrayList<>(curr.getPreds());

                // Remove predecessors before starting SCC.
                preds.removeIf(pred -> sccComponents.indexOf(blockSCCMap.get(pred)) > startSCCIndex);

                // Do not consider back edges
                if (isLoop && loopHeads.contains(curr))
                    preds.removeIf(scc::contains);

                // Calculate Skolem Map and Block's Precondition.
                Map<Expr, Integer> currSkolemMap;
                Expr pre;

                int nPreds = preds.size();
                switch (nPreds){
                    case 0:
                    {
                        currSkolemMap = inSkolemMap;
                        pre = eFac.mkTRUE();
                        break;
                    }
                    case 1:
                    {
                        Block pred = preds.get(0);

                        assert (blockSkolemMap.containsKey(pred) &&
                                blockPostCond.containsKey(pred)) :
                                "Predecessor has not been visited yet";

                        currSkolemMap = copySkolemMap(blockSkolemMap.get(pred));

                        Expr predPost = blockPostCond.get(pred);

                        pre = eFac.mkAND(predPost, getEdgeCondition(pred, curr, currSkolemMap));
                        break;
                    }
                    default:
                    {
                        Set<Map<Expr, Integer>> predSkolemMaps = new HashSet<>();

                        for (Block pred : preds)
                        {
                            assert blockSkolemMap.containsKey(pred) : "Predecessor has not been visited yet";
                            predSkolemMaps.add(blockSkolemMap.get(pred));
                        }

                        // Discover Phi nodes and combine skolem maps.
                        Map<Expr, Set<Integer>> phiNodes = findPhiNodes(predSkolemMaps);
                        currSkolemMap = combineSkolemMaps(predSkolemMaps, phiNodes);
                        phiNodes.keySet().forEach(e -> freshSkolem(e, currSkolemMap));

                        Set<Expr> predPostAndPhi = new HashSet<>();

                        for (Block pred : preds)
                        {
                            Map<Expr, Integer> predSkolemMap = blockSkolemMap.get(pred);
                            Expr predPost = eFac.mkAND(blockPostCond.get(pred), getEdgeCondition(pred, curr, currSkolemMap));

                            for (Expr phi : phiNodes.keySet())
                            {
                                // Make sure that the fresh copy is equal to the new skolem for this predecessor.
                                predPost = eFac.mkAND(predPost, eFac.mkEQ(getSkolemOf(phi, currSkolemMap),
                                                                          getSkolemOf(phi, predSkolemMap)));
                            }

                            predPostAndPhi.add(predPost);
                        }

                        // Combine all predecessors.
                        pre = narryOrSelfDistinct(predPostAndPhi, eFac::mkOR);
                    }
                }

                blockSkolemMap.put(curr, currSkolemMap);
                blockPostCond.put(curr, sp(curr, curr == startBlock ? normalizedStart : curr.getHead(),
                                           curr == endBlock ? normalizedEnd : curr.getTail(),
                                           pre, excludeEnd, currSkolemMap));
            }
        }

        Expr postCond = blockPostCond.get(endBlock);
        Map<Expr, Integer> skolemMap = blockSkolemMap.get(endBlock);

        if (disableExceptions)
        {
            Type throwableTy = Scene.v().getSootClass("java.lang.Throwable").getType();
            Local throwFldLocal = getOrAddLocal(SootNamingUtils.getThrowableFieldLocalName(), throwableTy, normMethod.getNormalizedMethod().getActiveBody());
            ExprGeneratorSwitch exprSw = new ExprGeneratorSwitch(this.normMethod.getOriginalMethod(), skolemMap);
            throwFldLocal.apply(exprSw);

            // Assume no exception were thrown.
            postCond = eFac.mkAND(postCond, eFac.mkEQ(exprSw.getResult(), eFac.mkINT(BigInteger.ZERO)));
        }

        return new ExprSkolemPair(postCond, skolemMap);
    }

    private boolean isLoop(List<Block> scc)
    {
        return !Collections.disjoint(scc, loopHeads);
    }

    private boolean isEntryBlock(List<Block> loopSCC, Block b)
    {
        if (!isLoop(loopSCC))
            throw new IllegalArgumentException("SCC must be a loop");

        return loopSCC.contains(b) && !loopSCC.containsAll(b.getPreds());
    }

    private boolean isExitBlock(List<Block> loopSCC, Block b)
    {
        if (!isLoop(loopSCC))
            throw new IllegalArgumentException("SCC must be a loop");

        return loopSCC.contains(b) && !loopSCC.containsAll(b.getSuccs());
    }

    private void sccTopologicalSort(List<Block> scc, Block b, Set<Block> visited, Stack<Block> topoSort)
    {
        visited.add(b);

        b.getSuccs()
         .stream()
         // Only consider blocks inside the loop
         .filter(scc::contains)
         // Treat SCC as a DAG, ignore all back-edges
         .filter(succ -> !loopHeads.contains(succ))
         .filter(succ -> !visited.contains(succ))
         .forEach(succ -> sccTopologicalSort(scc, succ, visited, topoSort));

        topoSort.push(b);
    }

    private Stack<Block> sccTopologicalSort(List<Block> scc)
    {
        Stack<Block> topoSort = new Stack<>();
        Set<Block> visited = new HashSet<>();

        for (Block b : scc)
        {
            if (!visited.contains(b))
                sccTopologicalSort(scc, b, visited, topoSort);
        }

        return topoSort;
    }

    private Set<Expr> getLoopTargets(List<Block> scc, Expr preCond)
    {
        Set<Expr> trgs = new HashSet<>();

        scc.forEach(b ->
            b.forEach(u -> {
                SootMethod orgMethod = normMethod.getOriginalMethod();
                Set<Expr> freeVarsInPre = collectAllNullaryApps(preCond).stream().map(VCGenUtils::dropSkolems).collect(Collectors.toSet());

                for (ValueBox vBox : u.getDefBoxes())
                {

                    Value val = vBox.getValue();
                    if (val instanceof ArrayRef)
                    {
                        ArrayType arrTy = (ArrayType) ((ArrayRef)val).getBase().getType();
                        trgs.add(VCGenUtils.getHeapArrayForArrayType(arrTy));
                    }
                    else if (val instanceof FieldRef)
                    {
                        SootField fld = ((FieldRef) val).getField();
                        trgs.add(VCGenUtils.getFieldHeapArrayOrGlobal(fld));
                    }
                    else
                    {
                        ExprGeneratorSwitch vExprGen = new ExprGeneratorSwitch(orgMethod, new HashMap<>());
                        val.apply(vExprGen);

                        trgs.add(vExprGen.getResultWithoutSkolem());
                    }
                }

                // Havoc Write Set of Unit
                final MayRWSetAnalysis mayRWSetAnalysis = CachedMayRWSetAnalysis.getInstance().getMayRWSetAnalysis();
                mayRWSetAnalysis.writeSet(orgMethod, u)
                                .forEach(mLocs -> trgs.addAll(mLocs.getExprsIn(freeVarsInPre)));
            })
        );

        return trgs;
    }

    private Expr sp(Block b, Unit start, Unit end, Expr pre, boolean excludeEnd, Map<Expr, Integer> skolemMap)
    {
        Set<Expr> encodings = new HashSet<>();

        Set<Expr> freeVarsInPre = collectAllNullaryApps(pre).stream().map(VCGenUtils::dropSkolems).collect(Collectors.toSet());

        encodings.add(pre);
        Unit tail = b.getTail(), last = (end == tail || excludeEnd) ? end : b.getSuccOf(end);
        Unit curr = start;
        while (curr != last)
        {
            StmtPostCondGenSwitch postCondGen = new StmtPostCondGenSwitch(normMethod.getOriginalMethod(), skolemMap, callStack, freeVarsInPre);
            curr.apply(postCondGen);

            Expr currEncoding = postCondGen.getResult();
            encodings.add(currEncoding);

            freeVarsInPre.addAll(collectAllNullaryApps(currEncoding).stream().map(VCGenUtils::dropSkolems).collect(Collectors.toSet()));

            curr = b.getSuccOf(curr);
        }

        return narryOrSelfDistinct(encodings, eFac::mkAND);
    }

    private Expr getEdgeCondition(Block src, Block trg, Map<Expr, Integer> skolemMap)
    {
        if (!src.getSuccs().contains(trg))
            throw new IllegalArgumentException("Block trg must be a successor of src");

        if (src.getSuccs().size() == 1)
            return eFac.mkTRUE();

        Unit srcTail = src.getTail();
        if (!(srcTail instanceof IfStmt))
            throw new IllegalArgumentException("Unsupported branch instruction");

        IfStmt ifStmt = (IfStmt) srcTail;
        ExprGeneratorSwitch condGen = new ExprGeneratorSwitch(normMethod.getOriginalMethod(), skolemMap);
        ifStmt.getCondition().apply(condGen);

        return ifStmt.getTarget() == trg.getHead() ? condGen.getResult() : eFac.mkNEG(condGen.getResult());
    }

    static class ExprSkolemPair
    {
        Expr cond;

        Map<Expr, Integer> skolemMap;

        public ExprSkolemPair(Expr cond, Map<Expr, Integer> skolemMap)
        {
            this.cond = cond;
            this.skolemMap = skolemMap;
        }
    }
}

