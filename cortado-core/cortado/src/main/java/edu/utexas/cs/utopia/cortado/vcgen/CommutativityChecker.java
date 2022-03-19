package edu.utexas.cs.utopia.cortado.vcgen;

import com.google.common.graph.EndpointPair;
import edu.utexas.cs.utopia.cortado.ccrs.Fragment;
import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.util.soot.ExceptionalUnitGraphCache;
import soot.*;
import soot.toolkits.graph.ExceptionalUnitGraph;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to check whether fragments of the same class
 * commute with each other
 */
public class CommutativityChecker
{
    private static final ModelImporter models = ModelImporter.getInstance();

    private final SootClass sootClass;
    private final ExprFactory exprFactory;
    private final CachedStrongestPostConditionCalculatorFactory spCalcFactory = CachedStrongestPostConditionCalculatorFactory.getInstance();

    private final Set<Expr> monitorState;

    /**
     * @param sootClass the class this commutativity checker
     *                  can perform commutativity checks on
     */
    public CommutativityChecker(SootClass sootClass)
    {
        this.sootClass = sootClass;
        // used cached expression/type factories
        this.exprFactory = CachedExprFactory.getInstance();
        this.monitorState = new HashSet<>();
        calculateMonitorState();
    }

    /**
     * Try to prove frag1 and frag2 commute
     *
     * @param frag1 the first fragment
     * @param frag2 the second fragment
     * @return true iff we can prove frag1 and frag2 commute
     */
    public boolean check(@Nonnull Fragment frag1, @Nonnull Fragment frag2)
    {
        if (frag1.getMonitorWriteSet().isEmpty() || frag2.getMonitorWriteSet().isEmpty())
            return true;

        SootMethod frag1Meth = frag1.getEnclosingBody().getMethod();
        SootMethod frag2Meth = frag2.getEnclosingBody().getMethod();
        return frag1.getExitUnits()
                    .stream()
                    .noneMatch(exit1 -> frag2.getExitUnits()
                                             .stream()
                                             .anyMatch(exit2 -> !check(frag1Meth, frag1.getEntryUnit(), exit1,
                                                                       frag2Meth, frag2.getEntryUnit(), exit2)));
    }

    /**
     * Try to prove method1 and method2 commute
     *
     * @param method1 the first method
     * @param method2 the second method
     * @return true iff we can prove method1 and method2 commute
     */
    public boolean check (@Nonnull SootMethod method1, @Nonnull SootMethod method2)
    {
        return check(method1, null, null, method2, null, null);
    }

    /**
     * check if execution of (composite)
     * statement 1 commutes with execution of (composite) statement 2
     *
     * @param method1 method containing the (composite) statement 1
     * @param start1 Either the first unit in statement 1, or null. If null,
     *               end1 must also be null. In that case, the entire
     *               active body of method1 is statement 1.
     * @param end1 The last unit in statement 1, or null. If null, start1
     *             must also be null.
     * @param method2 As method1, but for statement 2
     * @param start2 As start1, but for statement 2
     * @param end2 As end1, but for statement 2
     *
     * @throws IllegalArgumentException if method1 or method2's declaring class is not {@link #sootClass}
     * @throws IllegalArgumentException if method1 or method2 has no active body
     * @throws IllegalArgumentException if start1 is null, but not end1, or vice versa (or for start2/end2)
     * @throws IllegalArgumentException if start1/end1 (resp. start2/end2), and not contained
     *                                  in the active body of method1 (resp. method2)
     * @return true if we can prove statement 1 commutes with statement 2.
     *         false otherwise.
     */
    public boolean check(@Nonnull SootMethod method1, @Nullable Unit start1, @Nullable Unit end1,
                         @Nonnull SootMethod method2, @Nullable Unit start2, @Nullable Unit end2)
    {
        // validate input
        // make sure methods come from soot class
        if(!Objects.equals(method1.getDeclaringClass(), sootClass))
        {
            throw new IllegalArgumentException("method1 " + method1.getName() +
                    " does not come from class " + sootClass.getName());
        }
        if(!Objects.equals(method2.getDeclaringClass(), sootClass))
        {
            throw new IllegalArgumentException("method2 " + method2.getName() +
                    " does not come from class " + sootClass.getName());
        }
        // make sure methods have active bodies
        if(!method1.hasActiveBody())
        {
            throw new IllegalArgumentException("method1 " + method1.getName() + " has no active body.");
        }
        if(!method2.hasActiveBody())
        {
            throw new IllegalArgumentException("method2 " + method2.getName() + " has no active body.");
        }
        // make sure start is null if and only if end is null
        assert (start1 == null && end1 == null) || (start1 != null && end1 != null);
        assert (start2 == null && end2 == null) || (start2 != null && end2 != null);
        // make sure we got called correctly
        assert start1 == null || method1.getActiveBody().getUnits().contains(start1);
        assert end1 == null || method1.getActiveBody().getUnits().contains(end1);
        assert start2 == null || method2.getActiveBody().getUnits().contains(start2);
        assert end2 == null || method2.getActiveBody().getUnits().contains(end2);
        // TODO: handle static methods
        if(method1.isStatic() || method2.isStatic())
        {
            throw new RuntimeException("Static methods are not yet supported.");
        }

        Expr post1 = strongestPost(method1, start1, end1, exprFactory.mkTRUE());

        Expr post12 = strongestPost(method2, start2, end2, post1);

        // reset to our initial state
        Expr resetPostState1 = resetGlobalPostState(method1, post12);
        Expr post12Reset = exprFactory.mkAND(post12, resetPostState1);

        Expr post12ResetPost2 = strongestPost(method2, start2, end2, post12Reset);

        Expr post12ResetPost21 = strongestPost(method1, start1, end1, post12ResetPost2);

        Expr postStatesAreEqual = monitorState.stream()
                                              .map(objConst -> {
                                                  Expr objPost12 = StrongestPostConditionCalculator.encodeConditionToPostState(objConst, post12);
                                                  Expr objPost12ResetPost21 = StrongestPostConditionCalculator.encodeConditionToPostState(objConst, post12ResetPost21);
                                                  return (Expr) exprFactory.mkEQ(objPost12, objPost12ResetPost21);
                                              })
                                              .reduce(exprFactory::mkAND)
                                              .orElse(exprFactory.mkTRUE());

        // TODO handle return values (currently only care about global state)

        return ExprUtils.isUnsatUnderApprox(exprFactory.mkNEG(exprFactory.mkIMPL(post12ResetPost21, postStatesAreEqual)));
    }

    @SuppressWarnings("UnstableApiUsage")
    public boolean preservesEdgeCondition(Fragment frag, EndpointPair<Fragment> edge)
    {
        Fragment src = edge.source();
        Fragment trg = edge.target();

        // Be conservative in case of a loop, we cannot ensure that fragi preserves the exit condition.
        if ((src.containsLoop() && !src.isWaitUntilFrag()) || trg.containsLoop())
            // if we reach this point that means that the fragi and src have a race condition.
            return false;


        ExceptionalUnitGraph srcUnitGraph = ExceptionalUnitGraphCache.getInstance()
                                                                     .getOrCompute(src.getEnclosingCCR().getAtomicSection());

        Unit exit = null;
        Unit trgHead = trg.getEntryUnit();
        for (Unit u : src.getExitUnits())
        {
            if (srcUnitGraph.getSuccsOf(u).contains(trgHead))
            {
                exit = u;
                break;
            }
        }

        assert exit != null;

        if (!exit.branches())
            return true;


        SootMethod edgeCcrMeth = src.getEnclosingCCR().getAtomicSection();
        StrongestPostConditionCalculator edgeSpCalc = spCalcFactory.getOrCompute(edgeCcrMeth);

        Expr edgeMethIdentities = edgeSpCalc.propagateInvariantUpTo(src.getEntryUnit(), exprFactory.mkTRUE());//
        Expr srcFragSp = edgeSpCalc.sp(src.getEntryUnit(), exit, edgeMethIdentities);
        Expr edgeCond = edgeSpCalc.getEdgeCondition(exit, trg.getEntryUnit(), srcFragSp);

        SootMethod fragMeth = frag.getEnclosingCCR().getAtomicSection();
        StrongestPostConditionCalculator fragSpCalc = spCalcFactory.getOrCompute(fragMeth);


        for (Unit fragExit : frag.getExitUnits())
        {
            Expr fragMethIdentities = fragSpCalc.getEncodingForIdentityStatements(edgeCond);
            Expr fragSp = fragSpCalc.sp(frag.getEntryUnit(), fragExit, fragMethIdentities);

            Expr srcFragSpAfterFrag = edgeSpCalc.sp(src.getEntryUnit(), exit, fragSp);
            Expr query = edgeSpCalc.getNotEdgeCondition(exit, trg.getEntryUnit(), srcFragSpAfterFrag);

            if (!ExprUtils.isUnsatUnderApprox((BoolExpr) query))
                return false;
        }

        return true;
    }

    /**
     * Compute the strongest post-condition of precondition
     * from start to end using spCalc
     *
     * @param method the {@link SootMethod} to use
     * @param start the first unit. If null, then end must also be null.
     *              In that case, computes the strongest post-condition
     *              of the entire method associated to spCalc
     * @param end the last unit. If null, then start must also be null.
     * @param precondition the precondition
     * @return the strongest post condition
     */
    @Nonnull
    private Expr strongestPost(@Nonnull SootMethod method, @Nullable Unit start, @Nullable Unit end, @Nonnull Expr precondition)
    {
        final StrongestPostConditionCalculator spCalc = spCalcFactory.getOrCompute(method);
        if(start == null)
        {
            assert end == null;
            return spCalc.getMethodEncoding(null, precondition);
        }
        assert end != null;
        return spCalc.sp(start, end, precondition);
    }

    private Set<Expr> calculateMonitorState()
    {
        sootClass.getFields()
                 .stream()
                 .filter(fld -> !fld.isStatic())
                 .forEach(fld ->
                          {
                              FunctionApp fldHeapArray = VCGenUtils.getFieldHeapArrayOrGlobal(fld);
                              monitorState.add(fldHeapArray);

                              Type fldType = fld.getType();

                              if (fldType instanceof ArrayType)
                              {
                                  monitorState.add(VCGenUtils.getHeapArrayForArrayType((ArrayType) fldType));
                                  monitorState.add(VCGenUtils.getLengthHeapArray());
                              }
                              else if (fldType instanceof RefType)
                              {
                                  SootClass fldClass = ((RefType) fldType).getSootClass();

                                  monitorState.addAll(models.getAllPossibleModeledFields(fldClass)
                                                            .stream()
                                                            .map(VCGenUtils::getFieldHeapArrayOrGlobal)
                                                            .collect(Collectors.toSet()));
                              }

                          });

        return monitorState;
    }

    private Expr resetGlobalPostState(SootMethod method, Expr postState)
    {
        final StrongestPostConditionCalculator spCalc = spCalcFactory.getOrCompute(method);

        return monitorState.stream()
                           .map(globalObj -> (Expr) exprFactory.mkEQ(spCalc.getFreshCopyForPostState(globalObj, postState), globalObj))
                           .reduce(exprFactory::mkAND)
                           .orElse(exprFactory.mkTRUE());
    }
}
