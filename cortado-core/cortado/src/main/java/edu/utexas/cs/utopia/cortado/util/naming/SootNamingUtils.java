package edu.utexas.cs.utopia.cortado.util.naming;

import edu.utexas.cs.utopia.cortado.ccrs.CCR;
import edu.utexas.cs.utopia.cortado.signalling.SignalOperation;
import edu.utexas.cs.utopia.cortado.vcgen.NormalizedMethodClone;
import soot.Local;
import soot.SootField;
import soot.SootMethod;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Naming utils for {@link soot} objects
 */
public class SootNamingUtils
{
    private static final ConcurrentHashMap<String, String> predEvalLocalNames = new ConcurrentHashMap<>();

    /**
     * Return the name of the lock field associated to the given lock id
     *
     * @param lockID the lock number
     * @return the name of the field
     */
    static public String getLockFieldName(int lockID)
    {
        return "$cortado$lock" + lockID;
    }

    /**
     * Get the name of the local used to initialize a lock field
     * inside the monitor constructors
     *
     * @param lockID the lock number
     * @return the name of the lock initializer local
     */
    static public String getInitLocalNameForLock(int lockID)
    {
        return getLockFieldName(lockID) + "$initializer";
    }

    /**
     * Get the name of the local used to alias a lock field
     *
     * @param lockID the lock number
     * @return the name of the lock initializer local
     */
    static public String getAliasingLocalNameForLock(int lockID)
    {
        return getLockFieldName(lockID) + "$localAlias";
    }

    /**
     * Get a unique name for the local which will store
     * the evaluation of ccr's predicate
     * @return the name
     * @throws IllegalArgumentException if ccr has no guard
     */
    static public String getLocalForCCRGuardEvaluation(CCR ccr)
    {
        if(!ccr.hasGuard()) {
            throw new IllegalArgumentException("ccr has no guard.");
        }
        final SootMethod atomicSection = ccr.getAtomicSection();
        final SootMethod guard = ccr.getGuard();
        String basename = "$CCR$" + atomicSection.getName() + "$guard$" + guard.getName();
        String name = basename;
        final Set<String> existingLocalNames = atomicSection.getActiveBody()
                .getLocals()
                .stream()
                .map(Local::getName)
                .filter(localName -> localName.contains(basename))
                .collect(Collectors.toSet());
        int suffix = existingLocalNames.size() - 1;
        while(existingLocalNames.contains(name))
        {
            name = basename + suffix;
            suffix++;
        }
        return name;
    }

    /**
     * Return the name of the condition variable field associated
     * to the given lock and predicate
     *
     * @param lockID the lock number
     * @param pred the predicate associated to the condition variable
     * @return the name of the field
     */
    static public String getConditionVarFieldName(int lockID, SootMethod pred)
    {
        return getLockFieldName(lockID) + "$condVar$" + pred.getName();
    }

    /**
     * Return the name of the condition variable local used
     * to initialize the condition variable field
     *
     * @param lockID the lock number
     * @param pred the predicate associated to the condition variable
     * @return the name of the local initializer for the field
     */
    static public String getInitLocalNameForConditionVar(int lockID, SootMethod pred)
    {
        return getConditionVarFieldName(lockID, pred) + "$initializer";
    }

    /**
     * Get the name of the local used to alias a condition field
     *
     * @param lockID the lock number
     * @param pred the predicate associated to the condition variable
     * @return the name of the local aliasing the field
     */
    static public String getAliasingLocalNameForConditionVar(int lockID, SootMethod pred)
    {
        return getConditionVarFieldName(lockID, pred) + "$localAlias";
    }

    /**
     * @param field the field
     * @return a name for a local alias of the field
     */
    static public String getAliasingLocalNameForField(@Nonnull SootField field)
    {
        return "$" + field.getName() + "$localAlias";
    }

    /**
     * Get the name of the local used to initialize a field
     *
     * @param field the field
     * @return the name of the local used to initialize the field
     */
    static public String getInitLocalNameForField(@Nonnull SootField field)
    {
        return "$" + field.getName() + "$initializerLocal";
    }

    /**
     * @return a name for the local used to normalize return statements
     *          in {@link NormalizedMethodClone}
     */
    static public String getNormalizationReturnLocalName()
    {
        return "cortado$new_ret_local";
    }

    static public String getLocalForPredEval(SignalOperation sigOp)
    {
        String predName = sigOp.getPredicate().getName();
        return predEvalLocalNames.computeIfAbsent(predName, (k) -> "cortado$" + predName + "$pred_eval");
    }

    static public boolean isPredEvalLocal(Local l)
    {
        return predEvalLocalNames.contains(l.getName());
    }

    public static String getCallersExceptionContainerLocalName()
    {
        return "$cortado$caller_exc";
    }

    public static String getLocalExceptionContainerLocalName()
    {
        return "$cortado$exc";
    }

    public static String getThrowableFieldLocalName()
    {
        return "$cortado$throw_fld";
    }
}
