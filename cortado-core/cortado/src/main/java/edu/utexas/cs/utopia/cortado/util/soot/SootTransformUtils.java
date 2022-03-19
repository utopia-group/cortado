package edu.utexas.cs.utopia.cortado.util.soot;

import soot.*;

import javax.annotation.Nonnull;

/**
 * common operations on {@link soot.BodyTransformer}s
 */
public class SootTransformUtils
{
    /**
     * Register transformer with the pack
     * @param pack the pack
     * @param suffix the suffix to add to the phase name of the transform.
     *               Without the suffix, the phase name is unique
     *               to the pack and the transformer class.
     * @param transformer the body transformer
     * @return a transform that can be applied
     */
    @Nonnull
    public static Transform registerTransformer(@Nonnull Pack pack, @Nonnull String suffix, @Nonnull BodyTransformer transformer)
    {
        String phaseName = pack.getPhaseName() + "." + transformer.getClass().getName();
        if(suffix.length() > 0)
        {
            phaseName += "." + suffix;
        }
        Transform bodyTransformerT = new Transform(phaseName, transformer);
        pack.add(bodyTransformerT);
        return bodyTransformerT;
    }

    /**
     * Applies transform on each method of sootClass with an active body
     * @param sootClass the class
     * @param transform the transform
     */
    public static void applyTransform(@Nonnull SootClass sootClass, @Nonnull Transform transform)
    {
        sootClass.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .map(SootMethod::getActiveBody)
                .forEach(transform::apply);
    }
}
