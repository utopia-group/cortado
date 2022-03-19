package edu.utexas.cs.utopia.cortado.util.naming;

import soot.Local;
import soot.SootMethod;
import soot.jimple.ParameterRef;

import javax.annotation.Nonnull;

/**
 * Naming utils for objects involved in
 * VC generation solving (i.e. for objects
 * using the {@link edu.utexas.cs.utopia.cortado.vcgen} package).
 */
public class VCGenNamingUtils {

    /**
     * Get a name for the {@link edu.utexas.cs.utopia.cortado.expression.ast.Expr}
     * representing local in inMethod
     *
     * @param local the {@link Local} we're getting a name for
     * @param inMethod the {@link SootMethod} containing local
     * @throws IllegalArgumentException if inMethod has no active body
     * @return a name unique to local and inMethod
     */
    public static @Nonnull String getLocalExprName(@Nonnull Local local, @Nonnull SootMethod inMethod)
    {
        if(!inMethod.hasActiveBody())
        {
            throw new IllegalArgumentException("inMethod has no active body.");
        }
        return local.getName() + getSuffixForFunctionDecl(inMethod);
    }

    /**
     * Get a name for the {@link edu.utexas.cs.utopia.cortado.expression.ast.Expr}
     * representing parameterRef in inMethod
     *
     * @param parameterRef the {@link ParameterRef} we're getting a name for
     * @param inMethod the {@link SootMethod} with parameter referenced by parameterRef
     * @throws IllegalArgumentException if inMethod has no active body
     * @return a name unique to parameterRef and inMethod
     */
    public static @Nonnull String getParameterRefExprName(@Nonnull ParameterRef parameterRef, @Nonnull SootMethod inMethod)
    {
        if(!inMethod.hasActiveBody())
        {
            throw new IllegalArgumentException("inMethod has no active body.");
        }
        return "param" + parameterRef.getIndex() + getSuffixForFunctionDecl(inMethod);
    }


    /**
     * Used internally for naming consistency between types of objects
     *
     * @param inMethod the method to get a suffix for
     * @return a suffix for a function declaration inside inMethod
     */
    private static @Nonnull String getSuffixForFunctionDecl(@Nonnull SootMethod inMethod)
    {
        return "@" + inMethod.getSignature();
    }
}
