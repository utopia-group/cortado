package edu.utexas.cs.utopia.cortado.util.soot;

import edu.utexas.cs.utopia.cortado.util.naming.SootNamingUtils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;

import javax.annotation.Nonnull;
import java.util.Objects;

import static edu.utexas.cs.utopia.cortado.util.soot.SootLocalUtils.retrieveLocal;

/**
 * Tools to handle common operations on {@link SootField}s
 */
public class SootFieldUtils
{
    /**
     * Get a field of the desired name and type in the class,
     * or create one if it does not exist
     *
     * @param fieldName the name of the field
     * @param t the type of the field
     * @param inClass the class to look in
     * @return the field
     */
    public static SootField getOrAddField(String fieldName, Type t, SootClass inClass)
    {
        SootField fld;
        if ((fld = inClass.getFieldUnsafe(fieldName, t)) == null)
        {
            fld = new SootField(fieldName, t);
            inClass.addField(fld);
        }

        return fld;
    }

    /**
     * If no local with name localName and the same type as field
     * exists in activeBody, create it and insert a unit
     * at the very beginning of activeBody which assigns
     * field to the local.
     *
     * Then, return the local in activeBody of that name and type.
     *
     * In the case of a non-static constructor, just returns
     * the local of name {@link SootNamingUtils#getInitLocalNameForField(SootField)}
     * if it exists.
     *
     * @param localName the name of the local
     * @param field the field which the local should alias
     * @param method the method whose active body will be inserted into
     * @throws IllegalArgumentException if field is not a field of
     *         the class of activeBody, or if activeBody is static
     * @throws IllegalStateException if is a non-static constructor with no init local
     * @return the local
     */
    public static Local getOrAddLocalAliasOfField(@Nonnull String localName,
                                                  @Nonnull SootField field,
                                                  @Nonnull SootMethod method)
    {
        assert method.hasActiveBody();
        final JimpleBody activeBody = (JimpleBody) method.getActiveBody();
        // non-static constructor case
        if(method.isConstructor() && !method.isStatic())
        {
            // find the initialization local
            final String initLocalName = SootNamingUtils.getInitLocalNameForField(field);
            final Local initLocal = retrieveLocal(initLocalName, field.getType(), activeBody);
            if(initLocal == null)
            {
                throw new IllegalStateException("Cannot make local alias of field in non-static constructor " +
                        " without an initialization local.");
            }
            return initLocal;
        }
        // Non-constructor case:

        // make sure field matches class of body
        if(!Objects.equals(field.getDeclaringClass(), method.getDeclaringClass())) {
            throw new IllegalArgumentException("declaring class of field " +
                    field.getDeclaringClass() + " does not match declaring class " +
                    method.getDeclaringClass() + " of body");
        }
        // Make sure a static body is only trying to alias static fields
        if(method.isStatic()) {
            throw new IllegalArgumentException(method.getName() + " is static.");
        }
        // Now go look for a local of the correct name/type
        Type t = field.getType();
        Local loc = retrieveLocal(localName, t, activeBody);
        if(loc != null) {
            return loc;
        }
        // We didn't find such a local, make a new local
        Local newLocal = Jimple.v().newLocal(localName, t);
        activeBody.getLocals().add(newLocal);
        // Build a unit to have the local alias the field
        InstanceFieldRef fieldRef = Jimple.v().newInstanceFieldRef(activeBody.getThisLocal(), field.makeRef());
        AssignStmt setLocalToAliasField = Jimple.v().newAssignStmt(newLocal, fieldRef);
        // Insert the alias statement into the beginning of the method
        Unit firstNonID = activeBody.getFirstNonIdentityStmt();
        activeBody.getUnits().insertBeforeNoRedirect(setLocalToAliasField, firstNonID);

        return newLocal;
    }
}
