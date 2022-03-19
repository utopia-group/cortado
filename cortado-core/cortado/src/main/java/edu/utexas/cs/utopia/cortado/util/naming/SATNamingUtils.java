package edu.utexas.cs.utopia.cortado.util.naming;

/**
 * Naming utils for objects involved in
 * SAT solving (i.e. for objects
 * using the {@link edu.utexas.cs.utopia.cortado.util.sat} package).
 */
public class SATNamingUtils {
    /**
     * Get a name for a boolean variable which is unique for the
     * given varID
     *
     * @param varID identifier for the variable
     * @return A name which is unique for the varID
     */
    static public String getFreshBooleanVarName(int varID) {
        return "bool" + varID;
    }

    /**
     * @param lowerLock the lower lock
     * @param upperLock the upper lock
     * @param fragmentID the fragment identifier
     * @return the name for a boolean variable representing the
     *          fact that lowerLock and upperLock
     *          have been distinguished before the fragment
     *          fragmentID
     */
    static public String getVarNameForDistinguishedBefore(int lowerLock, int upperLock, int fragmentID)
    {
        if(lowerLock >= upperLock)
        {
            throw new IllegalArgumentException("lowerLock must be less than upperLock");
        }
        return "dist" + lowerLock + "From" + upperLock + "Before" + fragmentID;
    }
}
