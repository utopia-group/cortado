package edu.utexas.cs.utopia.cortado.vcgen;

import com.sun.istack.Nullable;
import edu.utexas.cs.utopia.cortado.mockclasses.Assertions;
import edu.utexas.cs.utopia.cortado.mockclasses.ExceptionContainer;
import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils;
import edu.utexas.cs.utopia.cortado.util.soot.SootUnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Targets;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.CompleteBlockGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.util.Chain;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils.*;
import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.getOrAddLocal;
import static edu.utexas.cs.utopia.cortado.vcgen.VCGenUtils.*;

/**
 * This class takes a deep copy of the active {@link Body} of a {@link SootMethod},
 * then normalizes its copy of the body.
 *
 * It also keeps track of a mapping between cloned/normalized {@link Unit}s and original {@link Unit}s,
 * as well as cloned/normalized {@link Local}s and original {@link Local}s
 *
 * - The association between {@link Local}s is guaranteed to be
 *   an injective mapping from original {@link Local}s to new ones,
 *   i.e. every original {@link Local} has a normalized counterpart,
 *   but the normalized {@link Body} may contain some added {@link Local}s
 *   with no normalized counterpart
 * - Each original
 *   {@link Unit} to a normalized top-boundary {@link Unit}
 *   and a normalized bottom-boundary {@link Unit}.
 *   The original {@link Unit} is associated to all units
 *   reachable from the top-boundary which can also reach
 *   the bottom boundary
 *
 * The following normalizations are performed:
 * - <i>Return Statement normalization</i>: make sure we have exactly one return statement
 *      by either
 *      - No returns case: Append a void return statement to a method without any return statement,
 *      - More than one return case: Append a return statement, and replace other
 *              return statements {ret val;} -> {retLocal = val; goto retStmt;}.
 * - <i>basic block normalization</i>:
 *      * Any basic {@link Block}s which does
 *        not have a {@link GotoStmt} as its last element, but has exactly
 *        one successor has a {@link GotoStmt} appended onto it.
 *      * switch statements are converted to a series of if-then-else
 *
 * - <i>string literal removal</i>: replaces string literals with static
 *     fields
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class NormalizedMethodClone
{
    private static final Logger log = LoggerFactory.getLogger(NormalizedMethodClone.class);

    private static final Map<String, SootMethod> NORMALIZED_TO_ORIGINAL_SIGNATURES = new HashMap<>();

    private static final Map<String, SootMethod> ORIGINAL_TO_NORMALIZED_SIGNATURES = new HashMap<>();

    private static final Type EXCEPTION_CONTAINER_TYPE = Scene.v().getType(ExceptionContainer.class.getName());

    private final SootMethod originalMethod;

    // In order to normalize exceptions we modify the method's signature.
    private final SootMethod normalizedMethod;

    private final JimpleBody normalizedBody;

    // unit/local mappings between original/normalized terms
    private final Map<Local, Local> originalLocalToNormalized = new HashMap<>(),
                                    normalizedLocalToOriginal = new HashMap<>();

    private final Map<Unit, Unit> originalUnitToNormalizedTop = new HashMap<>(),
                                  originalUnitToNormalizedBottom = new HashMap<>();

    private final Map<Unit, List<Unit>> normalizedTopToOriginalUnit = new HashMap<>(),
                                        normalizedBottomToOriginalUnit = new HashMap<>();

    /**
     * Create a deep clone of sootMethod's active {@link Body}
     * and normalize the clone.
     *
     * @throws IllegalArgumentException if sootMethod has no active body
     * @param originalMethod the method to create a normalized body for
     */
    public NormalizedMethodClone(@Nonnull SootMethod originalMethod)
    {
        if (!originalMethod.hasActiveBody())
        {
            throw new IllegalArgumentException(originalMethod.getSignature() + " does not have an active body");
        }
        this.originalMethod = originalMethod;

        if (ORIGINAL_TO_NORMALIZED_SIGNATURES.containsKey(originalMethod.getSignature()))
            this.normalizedMethod = ORIGINAL_TO_NORMALIZED_SIGNATURES.get(originalMethod.getSignature());
        else
            this.normalizedMethod = dupOriginalSootMethod(originalMethod);

        // build a clone of the method's body contents
        this.normalizedBody = Jimple.v().newBody(originalMethod);
        this.normalizedBody.setMethod(this.normalizedMethod);
        this.normalizedMethod.setActiveBody(normalizedBody);

        // build initial map between original units and
        // normalized units
        Map<Object, Object> oneToOneOriginalToNormalized = normalizedBody.importBodyContentsFrom(originalMethod.getActiveBody());
        oneToOneOriginalToNormalized.forEach((key, value) -> {
            if (key instanceof Unit && value instanceof Unit)
            {
                updateUnitMaps((Unit) key, (Unit) value, (Unit) value);
            } else if (key instanceof Local && value instanceof Local)
            {
                originalLocalToNormalized.put((Local) key, (Local) value);
                normalizedLocalToOriginal.put((Local) value, (Local) key);
            }
            // Ignoring traps, will be deleted in the normalized body.
            else if (!(key instanceof Trap))
            {
                throw new IllegalStateException("Unrecognized type received from return of " +
                        Body.class.getName() + ".importBodyContentsFrom(Body). Aborting");
            }
        });

        // normalize the body
        // DO NOT modify order of transformations.
        devirtualizeBodies();
        normalizeExceptions();
        normalizeReturnStatements();
        CompleteBlockGraph blockGraph = new CompleteBlockGraph(normalizedBody);
        blockGraph.forEach(this::normalizeBlock);
    }

    /// Getters ///////////////////////////////////////////////////////////////

    /**
      * @return the original {@link SootMethod} this object is normalizing
      */
    public SootMethod getOriginalMethod()
    {
        return originalMethod;
    }

    /**
      * @return the normalized {@link SootMethod}
      */
    public SootMethod getNormalizedMethod()
    {
        return normalizedMethod;
    }

    /**
     * @param originalUnit a {@link Unit} in the original {@link Body}
     * @return top boundary {@link Unit} in the normalized {@link Body} associated to
     *         originalUnit, or null
     *         if originalUnit is not in the original {@link Body}
     * @throws NullPointerException if originalUnit is null
     */
    @Nullable
    public Unit originalToNormalizedTop(@Nonnull Unit originalUnit)
    {
        return originalUnitToNormalizedTop.get(originalUnit);
    }

    /**
     * @param originalUnit a {@link Unit} in the original {@link Body}
     * @return bottom boundary {@link Unit} in the normalized {@link Body} associated to
     *         originalUnit, or null
     *         if originalUnit is not in the original {@link Body}
     * @throws NullPointerException if originalUnit is null
     */
    @Nullable
    public Unit originalToNormalizedBottom(@Nonnull Unit originalUnit)
    {
        return originalUnitToNormalizedBottom.get(originalUnit);
    }

    /**
     * @param normalizedLocal a {@link Local} in the normalized {@link Body}
     * @return the {@link Local} in the original {@link Body} associated to
     *         normalizedLocal, or null
     *         if normalizedLocal is not in the normalized {@link Body}
     *         or has no associated {@link Local} in the original {@link Body}
     * @throws NullPointerException if normalizedLocal is null
     */
    @Nullable
    public Local normalizedToOriginal(@Nonnull Local normalizedLocal)
    {
        return normalizedLocalToOriginal.get(normalizedLocal);
    }

    /**
     * @param originalLocal a {@link Local} in the original {@link Body}
     * @return The {@link Local} in the normalized {@link Body} associated to
     *         originalLocal, or null
     *         if originalLocal is not in the original {@link Body}
     * @throws NullPointerException if originalLocal is null
     */
    @Nullable
    public Local originalToNormalized(@Nonnull Local originalLocal)
    {
        return originalLocalToNormalized.get(originalLocal);
    }

    /// Normalization Methods /////////////////////////////////////////////////

    /**
     * Normalize all instances of `virtualinvoke` Jimple statements of
     * {@link #normalizedBody} to a series of statements of
     * {@link InstanceOfExpr} and {@link VirtualInvokeExpr}.
     * Any Jimple {@link VirtualInvokeExpr} of the form
     *      `virtualinvoke r5.<Class$C: int foo()>()`
     * guaranteed to be type specified (i.e. Class$C)
     *
     * Calls topological sort from {@link VCGenUtils#reverseTopologicalSortMap(Map)}
     *
     * Requires access to the CallGraph or FastHierarchy. The
     * CallGraph is used for more precise points-to analysis, while
     * the FastHierarchy uses class hierarchy information to
     * derive a similar but less-precise result.
     */
    private void devirtualizeBodies()
    {
        // Get Set of InvokeExprs
        Set<Unit> virtualInvokeExprs = originalMethod.getActiveBody()
                                                     .getUnits()
                                                     .stream()
                                                     .filter(u -> {
                                                         Stmt stmt = (Stmt) u;
                                                         if (!stmt.containsInvokeExpr())
                                                             return false;
                                                         InvokeExpr invExpr = stmt.getInvokeExpr();
                                                         return invExpr instanceof InstanceInvokeExpr && !(invExpr instanceof SpecialInvokeExpr);
                                                     })
                                                     .collect(Collectors.toSet());

        // Get Program Analysis Tools
        Jimple j = Jimple.v();
        FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();

        // Variable to index new created locals as ("cortado$virtual_" + localNum)
        int localNum = 0;

        // For each virtualinvoke expression
        for(Unit originalUnit : virtualInvokeExprs) {
            List<SootMethod> targetMethods = new ArrayList<>();

            // if call graph exists, use it to get more precise target methods
            if(Scene.v().hasCallGraph()) {
                CallGraph cg = Scene.v().getCallGraph();
                Iterator<MethodOrMethodContext> targets = new Targets(cg.edgesOutOf(originalUnit));
                while (targets.hasNext()) {
                    SootMethod tgt = (SootMethod)targets.next();
                    targetMethods.add(tgt);
                }
            } else {
                // Get list of possible target classes using class hierarchy
                SootClass classOfVirtualInvoke = ((Stmt)originalUnit)
                        .getInvokeExpr()
                        .getMethod()
                        .getDeclaringClass();
                List<SootClass> subclassesOf = getAllSubclassesOf(classOfVirtualInvoke);
                subclassesOf.add(classOfVirtualInvoke);
                // now get methods associated with each class
                targetMethods = subclassesOf.stream()
                        .map(c -> c.getMethod(((Stmt)originalUnit).getInvokeExpr().getMethod().getSubSignature()))
                        .collect(Collectors.toList());
            }

            // If only one target, leave it be
            int nTargets = targetMethods.size();
            if(nTargets > 1) {
                // Create Adjacency List for Class Hierarchy Tree/Graph
                Map<SootClass, Collection<SootClass>> adjList = new HashMap<>();
                for(SootMethod sm : targetMethods) {
                    SootClass sc = sm.getDeclaringClass();
                    Collection<SootClass> list = fh.getSubclassesOf(sc);
                    adjList.put(sc, list);
                }

                // Now run reverse topological sort on Class Hierarchy
                // i.e. reverse list to order from subclasses --> superclasses
                Map<SootClass, Integer> topoSortedList = reverseTopologicalSortMap(adjList);
                targetMethods.sort((o1, o2) -> {
                    SootClass sc1 = o1.getDeclaringClass();
                    SootClass sc2 = o2.getDeclaringClass();
                    assert(topoSortedList.containsKey(sc1));
                    assert(topoSortedList.containsKey(sc2));

                    return topoSortedList.get(sc2) - topoSortedList.get(sc1);
                });

                UnitPatchingChain normalizedUnits = normalizedBody.getUnits();
                Unit normUnit = originalUnitToNormalizedTop.get(originalUnit);
                // Since we are adding instanceof checks backwards up the chain
                // the very first instanceof check is added to end
                Unit succUnit = normalizedUnits.getSuccOf(normUnit);
                Unit gotoIfUnit = succUnit;
                //Unit predUnit = originalUnit;

                // keep track of top/bottom to maintain bindings
                Unit topUnit = null;
                Unit botUnit = null;

                InstanceInvokeExpr originalInvokeExpr = (InstanceInvokeExpr) ((Stmt) normUnit).getInvokeExpr();
                Local base = (Local) originalInvokeExpr.getBase();

                for(SootMethod sm : targetMethods) {
                    /*
                     * Suppose we have a class A, with subclass B. If we
                     * have a statement of the form:
                     *      A a; //either ```= new A()```, or ```= new B()```
                     *      a.foo();
                     * We want to transform it to:
                     *      if(a instanceof B)
                     *          ((B)a).foo(); // call class B's foo explicitly
                     *      else if(a instance A)
                     *          ((A)a).foo(); // call class B's foo explicitly
                     *      else if(...)
                     *          ...
                     *
                     * Each loop iteration creates exactly one of these
                     * ```if(a instanceof Class) ((Class)a).foo()```
                     * per target class/method
                     */

                    // local variable to store instanceof result
                    Local instOfLocal = j.newLocal("cortado$virtual_" + localNum++, BooleanType.v());
                    normalizedBody.getLocals().add(instOfLocal);

                    InstanceOfExpr instOfStmt = j.newInstanceOfExpr(base, sm.getDeclaringClass().getType());
                    Unit instanceOfUnit = j.newAssignStmt(instOfLocal, instOfStmt);
                    // instanceOfUnit looks something like
                    //      cortado$virtual_0 = r5 instanceof ClassName$B

                    Value zero = IntConstant.v(0);
                    EqExpr equalExpr = j.newEqExpr(instOfLocal, zero);
                    IfStmt ifUnit = j.newIfStmt(equalExpr, gotoIfUnit);
                    // ifUnit looks something like
                    //      if cortado$virtual_3 == 0 goto [gotoUnit]

                    InvokeExpr newInvExpr;
                    if (!sm.getDeclaringClass().isInterface())
                    {
                        newInvExpr = j.newVirtualInvokeExpr(base, sm.makeRef(), originalInvokeExpr.getArgs());
                    }
                    else
                    {
                        newInvExpr = j.newInterfaceInvokeExpr(base, sm.makeRef(), originalInvokeExpr.getArgs());
                    }

                    // Sanity Check
                    assert newInvExpr != null;

                    Unit virtualUnit = normUnit instanceof AssignStmt ? j.newAssignStmt(((AssignStmt) normUnit).getLeftOp(), newInvExpr) :
                                                                            j.newInvokeStmt(newInvExpr);

                    // virtualUnit is the explicit virtual call that looks like
                    //      virtualinvoke r5.<ClassName$B: int foo()>()
                    // where you are guaranteed ```r5``` is of type B

                    // create goto for the if statement
                    GotoStmt gotoStmt = j.newGotoStmt(succUnit);

                    // now add units in order to the UnitPatchingChain
                    normalizedUnits.insertAfter(instanceOfUnit, normUnit);
                    normalizedUnits.insertAfter(ifUnit, instanceOfUnit);
                    normalizedUnits.insertAfter(virtualUnit, ifUnit);
                    normalizedUnits.insertAfter(gotoStmt, virtualUnit);

                    topUnit = instanceOfUnit;
                    botUnit = botUnit == null ? gotoStmt : botUnit;

                    // build chain backwards to go from most specific to least specific class
                    // assumes correct reverse topological sort for @link{targetMethods}
                    gotoIfUnit = instanceOfUnit;
                }

                normalizedUnits.remove(originalUnit);

                // since above loop creates units in backwards order:
                // top unit is first if statement, i.e. last created in above loop
                // bot unit is last goto statement, i.e. first created in above loop
                updateUnitMaps(originalUnit, topUnit, botUnit);
            }
            else if (nTargets == 1)
            {
                UnitPatchingChain normalizedUnits = normalizedBody.getUnits();
                Unit normUnit = originalUnitToNormalizedTop.get(originalUnit);

                InstanceInvokeExpr originalInvokeExpr = (InstanceInvokeExpr) ((Stmt) normUnit).getInvokeExpr();
                if (originalInvokeExpr instanceof InterfaceInvokeExpr)
                {
                    SootMethod newTrg = targetMethods.get(0);

                    // Sanity check, probably does not always hold but it should though.
                    assert !newTrg.getDeclaringClass().isInterface();
                    Local base = (Local) originalInvokeExpr.getBase();

                    InvokeExpr newInvExpr = j.newVirtualInvokeExpr(base, newTrg.makeRef(), originalInvokeExpr.getArgs());

                    Unit virtualUnit = normUnit instanceof AssignStmt ? j.newAssignStmt(((AssignStmt) normUnit).getLeftOp(), newInvExpr) : j.newInvokeStmt(newInvExpr);

                    normalizedUnits.swapWith(normUnit, virtualUnit);
                    updateUnitMaps(originalUnit, virtualUnit, virtualUnit);
                }
            }
        }
    }

     /* The constructor adds an extra argument of type ExceptionContainer to the normalized method signature, which
      * keeps tracks of any thrown exception (either by the method itself or by any of its callees).
      *
      * Transformations performed:
      * - Create an Identity Statement for the new argument and allocate a new ExceptionContainer, exContainer, at the beginning of the method.
      * - In case of a throw statement: initialize exContainer.ex to the exception being thrown.
      * - In case of a call statement: propagate the local exContainer to the callee method.
      * - In case of a statement that can throw an exception (currently we only consider explicitly thrown exceptions):
      *      1. Check whether exContainer.ex is not null after the statement.
      *      2. If it is null, move to the "unexceptional" successor.
      *      3. If it is not null, this means an exception was thrown during executing the statement.
      *         The exception handling performs the following:
      *             3a. If there is no exception handler for the type of exception thrown, it propagates the exception to the caller and returns.
      *             3b. Otherwise, it transfers control to the appropriate exception handler (by performing a succession of instanceof check on exContainer.ex).
     * */
    private void normalizeExceptions()
    {
        Jimple j = Jimple.v();

        UnitPatchingChain normalizedUnits = normalizedBody.getUnits();

        // Auxiliary state
        Chain<Trap> traps = normalizedBody.getTraps();
        //Map<Unit, Trap> headUnitToTrap = traps.stream().collect(Collectors.toMap(Trap::getBeginUnit, t -> t));

        Map<Unit, SootClass> trapHeadToException = traps.stream().collect(Collectors.toMap(Trap::getHandlerUnit,
                                                                                           Trap::getException,
                                                                                           (ex1, ex2) -> { assert ex1.equals(ex2); return ex1; }));
        // The above map reversed
        Map<SootClass, Collection<Unit>> exceptionToTrapHeads = new HashMap<>();
        trapHeadToException.keySet()
                           .forEach(u -> {
                               SootClass except = trapHeadToException.get(u);
                               if (!exceptionToTrapHeads.containsKey(except))
                                   exceptionToTrapHeads.put(except, new HashSet<>());
                               exceptionToTrapHeads.get(except).add(u);
                           });

        // Gather units to transform
        ExceptionalUnitGraph excUnitGraph = new ExceptionalUnitGraph(normalizedBody);

        Set<Stmt> mayThrowUnits = normalizedUnits.stream()
                                                 .map(u -> (Stmt) u)
                                                 .filter(s -> mayTrhow(s, excUnitGraph))
                                                 .collect(Collectors.toSet());

        // Perform actual transformation
        RefType excContainerAsRefTy = (RefType) EXCEPTION_CONTAINER_TYPE;
        SootClass excContainerClass = excContainerAsRefTy.getSootClass();
        Local callerExceptionLocal = getOrAddLocal(getCallersExceptionContainerLocalName(), excContainerAsRefTy, normalizedBody);
        Local ownExceptionLocal = getOrAddLocal(getLocalExceptionContainerLocalName(), excContainerAsRefTy, normalizedBody);

        SootFieldRef exContainerFieldRef = excContainerClass.getFieldByName("ex").makeRef();
        InstanceFieldRef ownThrowInstanceFldRef = j.newInstanceFieldRef(ownExceptionLocal, exContainerFieldRef);


        Stmt retStmForException;

        Type returnType = originalMethod.getReturnType();
        if (returnType.equals(VoidType.v()))
        {
            retStmForException = j.newReturnVoidStmt();
        }
        else
        {
            Local dummyRetLocal = getOrAddLocal("$cortado$dummyRet", returnType, normalizedBody);
            retStmForException = j.newReturnStmt(dummyRetLocal);
        }

        SootClass throwableClass = Scene.v().getSootClass(Throwable.class.getName());
        Local throwFldLocal = getOrAddLocal(getThrowableFieldLocalName(), throwableClass.getType(), normalizedBody);

        mayThrowUnits.forEach(s -> {
            // Recalculate exceptional graph for each statement.
            ExceptionalUnitGraph newExcUnitGraph = new ExceptionalUnitGraph(normalizedBody);

            // Either the statement set the exception field, or checks whether the statement threw
            Unit setExcOrNullCheck;
            Unit normalizedTop, normalizedBottom;
            if (s instanceof ThrowStmt)
            {
                ThrowStmt throwStmt = (ThrowStmt) s;
                // set own exception container
                AssignStmt setExcept = j.newAssignStmt(ownThrowInstanceFldRef, throwStmt.getOp());
                normalizedUnits.swapWith(s, setExcept);
                setExcOrNullCheck = j.newAssignStmt(throwFldLocal, ownThrowInstanceFldRef);
                normalizedUnits.insertAfter(setExcOrNullCheck, setExcept);

                // Assignment that sets exception is the new normalized Top
                normalizedTop = setExcept;
            }
            else
            {
                List<Unit> unexcSuccs = newExcUnitGraph.getUnexceptionalSuccsOf(s);
                assert unexcSuccs.size() == 1 : "mayThrow statement with multiple successors";
                Unit normalSucc = unexcSuccs.get(0);

                // If exception in container is not null, check the type of the exception against the handled exception (if any) of every exceptional successor.
                // If a successor handles the thrown exception, then go to the trap.
                // If non of the successors handles the thrown exception, then propagate it to the caller.

                // Check if exception in container is not null
                AssignStmt getTrhowFld = j.newAssignStmt(throwFldLocal, ownThrowInstanceFldRef);
                normalizedUnits.insertAfter(getTrhowFld, s);
                setExcOrNullCheck = j.newIfStmt(j.newEqExpr(throwFldLocal, NullConstant.v()), normalSucc);
                SootUnitUtils.insertOnEdge(setExcOrNullCheck, getTrhowFld, normalSucc, normalizedBody);

                // Otherwise, set normalizedTop temporarily to current statement
                normalizedTop = s;
            }

            // Default case: propagate exception to caller
            AssignStmt defaultCaseStart = j.newAssignStmt(j.newInstanceFieldRef(callerExceptionLocal, exContainerFieldRef), throwFldLocal);
            normalizedUnits.insertAfter(defaultCaseStart, setExcOrNullCheck);
            Unit defaultCaseRetStmt = (Unit) retStmForException.clone();
            normalizedUnits.insertAfter(defaultCaseRetStmt, defaultCaseStart);

            // Default-case return statement is always the bottom
            normalizedBottom = defaultCaseRetStmt;

            List<Unit> exceptSuccs = newExcUnitGraph.getExceptionalSuccsOf(s);
            Map<SootClass, Unit> handledExToSucc = new HashMap<>();
            exceptSuccs.forEach(u -> {
                assert trapHeadToException.containsKey(u);
                handledExToSucc.put(trapHeadToException.get(u), u);
            });

            List<SootClass> handledExcInReverseTopo = classesInReverseTopologicalOrder(handledExToSucc.keySet());
            Unit trgForCheck = defaultCaseStart;
            for (int i = handledExcInReverseTopo.size() - 1; i >= 0; --i)
            {
                SootClass handledEx = handledExcInReverseTopo.get(i);

                Local instanceOfCheckLocal = getOrAddLocal("$cortado$instof$check", BooleanType.v(), normalizedBody);
                AssignStmt instOfCheck = j.newAssignStmt(instanceOfCheckLocal, j.newInstanceOfExpr(throwFldLocal, handledEx.getType()));

                normalizedUnits.insertBefore(instOfCheck, trgForCheck);
                GotoStmt gotoHandler = j.newGotoStmt(handledExToSucc.get(handledEx));
                normalizedUnits.insertBefore(gotoHandler, trgForCheck);
                // Stupid Soot.
                SootUnitUtils.insertOnEdge(j.newIfStmt(j.newEqExpr(instanceOfCheckLocal, IntConstant.v(0)), trgForCheck), instOfCheck, gotoHandler, normalizedBody);

                trgForCheck = instOfCheck;
            }

            // In case s contains a call, pass local ExceptionContainer to callee.
            if (s.containsInvokeExpr())
            {
                // Pass ExceptionContainer to the normalized callee
                InvokeExpr originalInvExpr = s.getInvokeExpr();
                SootMethod trgMethod = originalInvExpr.getMethod();

                assert trgMethod.hasActiveBody();

                SootMethod normTrgMeth;
                if (ORIGINAL_TO_NORMALIZED_SIGNATURES.containsKey(trgMethod.getSignature()))
                    normTrgMeth = ORIGINAL_TO_NORMALIZED_SIGNATURES.get(trgMethod.getSignature());
                else
                    normTrgMeth = dupOriginalSootMethod(trgMethod);

                List<Value> newArgs = new ArrayList<>(originalInvExpr.getArgs());
                newArgs.add(ownExceptionLocal);

                InvokeExpr newInvExpr = SootUnitUtils.dupInvokeExpr(originalInvExpr, normTrgMeth, newArgs);

                Stmt newUnit;
                if (s instanceof AssignStmt)
                {
                    newUnit = j.newAssignStmt(((AssignStmt) s).getLeftOp(), newInvExpr);
                }
                else
                {
                    assert s instanceof InvokeStmt : "Unexpected stmt with invoke expr";
                    newUnit = j.newInvokeStmt(newInvExpr);
                }

                normalizedUnits.swapWith(s, newUnit);

                // In case of a call modify top to be the new statement
                normalizedTop = newUnit;
            }

            // Don't make the return statement the bottom here, normalization of return statements messes up things.
            updateUnitMaps(getOriginalUnitOrSelf(s), normalizedTop, normalizedUnits.getPredOf(normalizedBottom));
        });

        // Change caught Exception to point to the container's exception.
        trapHeadToException.keySet()
                           .forEach(u -> {
                               IdentityStmt s = (IdentityStmt) u;
                               AssignStmt newAssign = j.newAssignStmt(s.getLeftOp(), throwFldLocal);
                               normalizedUnits.swapWith(s, newAssign);

                               Unit originalUnit = getOriginalUnitOrSelf(u);
                               assert originalUnit != u;
                               updateUnitMaps(originalUnit, newAssign, newAssign);
                           });

        Stmt firstNonIdent = normalizedBody.getFirstNonIdentityStmt();
        // Create identity statement for caller's exception container
        ParameterRef exceptionParamRef = j.newParameterRef(excContainerAsRefTy, normalizedMethod.getParameterCount() - 1);
        normalizedUnits.insertBeforeNoRedirect(j.newIdentityStmt(callerExceptionLocal, exceptionParamRef), firstNonIdent);
        // Create a new Exception container for this method
        normalizedUnits.insertBeforeNoRedirect(j.newAssignStmt(ownExceptionLocal, j.newNewExpr(excContainerAsRefTy)), firstNonIdent);
        // Just initialize field to null, do not call ExceptionContainer constructor (it will be normalized and add recursive call to itself).
        normalizedUnits.insertBeforeNoRedirect(j.newAssignStmt(ownThrowInstanceFldRef, NullConstant.v()), firstNonIdent);

        traps.clear();
    }

    private boolean mayTrhow(Stmt s, ExceptionalUnitGraph excUnitGraph)
    {
        // TODO: Create a wrapper class that extends a ThrowAnalysis, so we can expose some of the private/protected functionality

        // s is a throw stmt
        if (s instanceof ThrowStmt)
            return true;

        // s is method call
        if (s.containsInvokeExpr())
        {
            SootMethod trgMethod = s.getInvokeExpr().getMethod();
            if (trgMethod.hasActiveBody())
                return !trgMethod.equals(asssumeMeth);

            log.warn("Ignoring exceptions of method: " + trgMethod.getSignature() + " (no active body)");
            return false;
        }

        // s is handled by a trap
        return !s.branches() && !excUnitGraph.getExceptionalSuccsOf(s).isEmpty();
    }

    SootMethod asssumeMeth = Scene.v().getSootClass(Assertions.class.getName()).getMethodByName("assume");

    /**
     * normalize the return statements of {@link #normalizedBody}
     * as described in the class javadoc
     *
     * It is expected that, before calling this function,
     * each return statement in {@link #normalizedBody} has an associated
     * statement in the original body.
     *
     * @throws IllegalStateException if any throw statements present in the body
     * @throws IllegalStateException if return statements are not already
     *      normalized (e.g. there are more than 1 return statements),
     *      and the method has a local of name {@link SootNamingUtils#getNormalizationReturnLocalName()}
     *      with the same type as the method's return type
     */
    private void normalizeReturnStatements()
    {
        UnitPatchingChain units = normalizedBody.getUnits();

        Set<Unit> throwStmts = units.stream()
                                    .filter(u -> u instanceof ThrowStmt)
                                    .collect(Collectors.toSet());


        if (!throwStmts.isEmpty())
            throw new IllegalStateException("Exceptions must be removed before this step");

        Set<Unit> returnStmts = units.stream()
                                     .filter(u -> u instanceof ReturnStmt || u instanceof ReturnVoidStmt)
                                     .collect(Collectors.toSet());

        Jimple j = Jimple.v();

        int numReturns = returnStmts.size();
        // multiple returns case:
        // make a single return statement at the end, and replace
        // returns with gotos
        if (numReturns > 1)
        {
            SootMethod m = normalizedBody.getMethod();

            // Build a local to hold the return value (if not a void method)
            Local retLocal = null;
            final Type returnType = m.getReturnType();
            if (!(returnType instanceof VoidType))
            {
                final String retLocalName = getNormalizationReturnLocalName();
                retLocal = SootLocalUtils.addNewLocal(retLocalName, returnType, normalizedBody);
            }

            // Build our return statement at the end of the normalized method
            Unit retStmt;
            if (retLocal != null)
                retStmt = j.newReturnStmt(retLocal);
            else
                retStmt = j.newReturnVoidStmt();

            units.addLast(retStmt);

            // Replace old return statements with gotos, being careful to
            // maintain our map between original and normalized methods
            for (Unit oldNormalizedRet : returnStmts)
            {
                final Unit topBoundary;
                final GotoStmt gotoStmt = j.newGotoStmt(retStmt);
                // non-void case
                if (retLocal != null)
                {
                    assert oldNormalizedRet instanceof ReturnStmt;
                    Value retVal = ((ReturnStmt) oldNormalizedRet).getOp();
                    AssignStmt retAssign = j.newAssignStmt(retLocal, retVal);
                    topBoundary = retAssign;
                    units.swapWith(oldNormalizedRet, retAssign);
                    units.insertAfter(gotoStmt, retAssign);
                }
                else // void case
                {
                    units.swapWith(oldNormalizedRet, gotoStmt);
                    topBoundary = gotoStmt;
                }

                updateUnitMaps(getOriginalUnitOrSelf(oldNormalizedRet), topBoundary, retStmt);
            }
        } // easy case: make sure we have at least one return statement
        else if(numReturns == 0)
        {
            units.addLast(j.newReturnVoidStmt());
        }
    }

    /**
     * @param b the block to normalize
     */
    private void normalizeBlock(Block b)
    {
        addMissingGotos(b);
        convertSwitchToIfThenElse(b);

        // Things we will need:
        // Single exit for methods/loops.
    }

    /**
     * Perform {@link GotoStmt} normalization for {@link Block}s
     * as described in the class javadoc.
     *
     * @param b the block to add gotos to
     */
    private void addMissingGotos(Block b)
    {
        List<Block> succs = b.getSuccs();

        Unit tail = b.getTail();
        if (succs.size() == 1 && !tail.branches())
        {
            Jimple jimpleGen = Jimple.v();
            Unit succ = succs.get(0).getHead();
            final GotoStmt gotoStmt = jimpleGen.newGotoStmt(succ);
            // update original <-> normalized unit associations
            final List<Unit> originalTails = normalizedBottomToOriginalUnit.get(b.getTail());
            if(originalTails != null)
            {
                originalTails.forEach(originalTail -> {
                    originalUnitToNormalizedBottom.put(originalTail, gotoStmt);
                    normalizedBottomToOriginalUnit.put(gotoStmt, Arrays.asList(originalTail));
                });
                normalizedBottomToOriginalUnit.remove(b.getTail());
            }
            b.insertAfter(gotoStmt, tail);
        }
    }

    private void convertSwitchToIfThenElse(Block b)
    {
        final Unit tail = b.getTail();
        if(tail instanceof LookupSwitchStmt)
        {
            LookupSwitchStmt switchStmt = (LookupSwitchStmt) tail;
            final List<Unit> originalSwitches = normalizedBottomToOriginalUnit.get(switchStmt);
            if(originalSwitches.size() != 1)
            {
                throw new IllegalStateException("Expected exactly one original unit corresponding to the switch statement.");
            }
            Unit originalSwitch = originalSwitches.get(0);

            Value key = switchStmt.getKey();
            final int numLookupValues = switchStmt.getLookupValues().size();
            for(int i = 0; i < numLookupValues; ++i)
            {
                final EqExpr doesKeyEqualValue = Jimple.v().newEqExpr(key, switchStmt.getLookupValues().get(i));
                final IfStmt ifStmt = Jimple.v().newIfStmt(doesKeyEqualValue, switchStmt.getTarget(i));
                b.insertBefore(ifStmt, switchStmt);
                if(i == 0)
                {
                    updateUnitMaps(originalSwitch, ifStmt, ifStmt);
                }
            }
            final GotoStmt defaultGoTo = Jimple.v().newGotoStmt(switchStmt.getDefaultTarget());
            b.insertBefore(defaultGoTo, switchStmt);
            b.remove(switchStmt);
            updateUnitMaps(originalSwitch, originalUnitToNormalizedTop.get(originalSwitch), defaultGoTo);
        }
        else if(tail instanceof TableSwitchStmt)
        {
            TableSwitchStmt switchStmt = (TableSwitchStmt) tail;
            final List<Unit> originalSwitches = normalizedBottomToOriginalUnit.get(switchStmt);
            if(originalSwitches.size() != 1)
            {
                throw new IllegalStateException("Expected exactly one original unit corresponding to the switch statement.");
            }
            Unit originalSwitch = originalSwitches.get(0);

            Value key = switchStmt.getKey();
            final int numLookupValues = switchStmt.getTargets().size();
            // should have one target per index + one default target
            assert(numLookupValues == switchStmt.getHighIndex() - switchStmt.getLowIndex() + 1);
            for(int i = 0; i < numLookupValues; ++i)
            {
                final IntConstant value = IntConstant.v(switchStmt.getLowIndex() + i);
                final EqExpr doesKeyEqualValue = Jimple.v().newEqExpr(key, value);
                final IfStmt ifStmt = Jimple.v().newIfStmt(doesKeyEqualValue, switchStmt.getTarget(i));
                b.insertBefore(ifStmt, switchStmt);
                if(i == 0)
                {
                    updateUnitMaps(originalSwitch, ifStmt, ifStmt);
                }
            }
            final GotoStmt defaultGoTo = Jimple.v().newGotoStmt(switchStmt.getDefaultTarget());
            b.insertBefore(defaultGoTo, switchStmt);
            b.remove(switchStmt);
            updateUnitMaps(originalSwitch, originalUnitToNormalizedTop.get(originalSwitch), defaultGoTo);
        }
    }

    private SootMethod dupOriginalSootMethod(SootMethod originalMethod)
    {
        assert !ORIGINAL_TO_NORMALIZED_SIGNATURES.containsKey(originalMethod.getSignature());
        List<Type> parameterTypes = new ArrayList<>(originalMethod.getParameterTypes());
        parameterTypes.add(EXCEPTION_CONTAINER_TYPE);
        SootMethod newMeth = new SootMethod(originalMethod.getName(), parameterTypes, originalMethod.getReturnType(),
                                            originalMethod.getModifiers(), originalMethod.getExceptions());

        SootClass declaringClass = originalMethod.getDeclaringClass();
        newMeth.setDeclaringClass(declaringClass);
        newMeth.setDeclared(true);

        NORMALIZED_TO_ORIGINAL_SIGNATURES.put(newMeth.getSignature(), originalMethod);
        ORIGINAL_TO_NORMALIZED_SIGNATURES.put(originalMethod.getSignature(), newMeth);

        return newMeth;
    }

    private void updateUnitMaps(Unit transformedUnit, Unit normalizedTop, Unit normalizedBottom)
    {
        UnitPatchingChain orgUnits = originalMethod.getActiveBody().getUnits();

        if (orgUnits.contains(transformedUnit))
        {
            // Update original -> normalized
            originalUnitToNormalizedTop.put(transformedUnit, normalizedTop);
            originalUnitToNormalizedBottom.put(transformedUnit, normalizedBottom);

            // Update normalized -> original
            if (!normalizedTopToOriginalUnit.containsKey(normalizedTop))
                normalizedTopToOriginalUnit.put(normalizedTop, new ArrayList<>());

            if (!normalizedBottomToOriginalUnit.containsKey(normalizedBottom))
                normalizedBottomToOriginalUnit.put(normalizedBottom, new ArrayList<>());

            normalizedTopToOriginalUnit.get(normalizedTop).add(transformedUnit);
            normalizedBottomToOriginalUnit.get(normalizedBottom).add(transformedUnit);
        }
        else
        {
            // This means the transformedUnit was introduced by one of our transformations and then modified by another one.
            // For example, exception normalization introduces return statements which later are removed by the return
            // statement normalization.

            // Replace transformedUnit in original -> normalized maps.
            originalUnitToNormalizedTop.replaceAll((u, oldTop) -> oldTop == transformedUnit ? normalizedTop : oldTop);
            originalUnitToNormalizedBottom.replaceAll((u, oldBottom) -> oldBottom == transformedUnit ? normalizedBottom : oldBottom);

            // If the transformedUnit is already a normalized bottom or top, just propagate the list to the new normalizedTop & normalizedBottom
            if (normalizedTopToOriginalUnit.containsKey(transformedUnit))
            {
                normalizedTopToOriginalUnit.put(normalizedTop, normalizedTopToOriginalUnit.get(transformedUnit));
                normalizedTopToOriginalUnit.remove(transformedUnit);
            }

            if (normalizedBottomToOriginalUnit.containsKey(transformedUnit))
            {
                normalizedBottomToOriginalUnit.put(normalizedBottom, normalizedBottomToOriginalUnit.get(transformedUnit));
                normalizedBottomToOriginalUnit.remove(transformedUnit);
            }
        }
    }

    private Unit getOriginalUnitOrSelf(Unit u)
    {
        if (normalizedTopToOriginalUnit.containsKey(u))
        {
            List<Unit> originalUnits = normalizedTopToOriginalUnit.get(u);
            return originalUnits.get(0);
        }
        else
        {
            return u;
        }
    }

    // Static methods for querying NORMALIZED_TO_ORIGINAL_SIGNATURES/ORIGINAL_TO_NORMALIZED_SIGNATURES maps
    public static SootMethod getNormalizedSootMethod(String signature)
    {
        return ORIGINAL_TO_NORMALIZED_SIGNATURES.get(signature);
    }

    public static SootMethod getOriginalSootMethod(String signature)
    {
        return NORMALIZED_TO_ORIGINAL_SIGNATURES.get(signature);
    }

    public static boolean isNormalizedMethodSignature(String signature)
    {
        return NORMALIZED_TO_ORIGINAL_SIGNATURES.containsKey(signature);
    }
}
