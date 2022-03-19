package edu.utexas.cs.utopia.cortado.util.soot;

import soot.Body;
import soot.Local;
import soot.Type;
import soot.jimple.Jimple;
import soot.util.Chain;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for dealing with {@link soot.Local}s
 */
public class SootLocalUtils
{
    /**
     * Retrieve a local of the desired name and type from the body,
     * or return null if one does not exist
     *
     * @param localName the name of the local
     * @param t the type of the local
     * @param activeBody the body to look in
     * @return the local, or null if it does not exist
     */
    @Nullable
    public static Local retrieveLocal(@Nonnull String localName, @Nonnull Type t, @Nonnull Body activeBody) {
        Chain<Local> locals = activeBody.getLocals();
        for (Local l : locals)
        {
            if (l.toString().equals(localName) && l.getType().equals(t))
                return l;
        }
        return null;
    }

    /**
     * Add a new local to the body activeBody
     *
     * @param localName the name of the local
     * @param t the type of the local
     * @param activeBody the body
     * @return the newly created local
     * @throws IllegalArgumentException if the a local of name localName and type t
     *                                  already exists in activeBody
     */
    @Nonnull
    public static Local addNewLocal(@Nonnull String localName, @Nonnull Type t, @Nonnull Body activeBody)
    {
        if(retrieveLocal(localName, t, activeBody) != null)
        {
            throw new IllegalArgumentException("Local " + localName + " of type " + t + " already exists in activeBody.");
        }
        return addLocal(localName, t, activeBody);
    }

    /**
     * Get a local of the desired name and type in the body,
     * or create one if it does not exist
     *
     * @param localName the name of the local
     * @param t the type of the local
     * @param activeBody the body to look in
     * @return the local
     */
    @Nonnull
    public static Local getOrAddLocal(@Nonnull String localName, @Nonnull Type t, @Nonnull Body activeBody)
    {
        Local loc = retrieveLocal(localName, t, activeBody);
        if(loc != null) {
            return loc;
        }
        return addLocal(localName, t, activeBody);
    }

    /**
     * Create a local of name localName and type t, and add it to activeBody
     *
     * @param localName the name of the local
     * @param t the type of the local
     * @param activeBody the active body
     * @return the newly created local
     */
    @Nonnull
    private static Local addLocal(@Nonnull String localName, @Nonnull Type t, @Nonnull Body activeBody)
    {
        Local newLocal = Jimple.v().newLocal(localName, t);
        activeBody.getLocals().add(newLocal);

        return newLocal;
    }
}
