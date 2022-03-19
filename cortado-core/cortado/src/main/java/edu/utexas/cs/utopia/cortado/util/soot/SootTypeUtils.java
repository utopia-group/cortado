package edu.utexas.cs.utopia.cortado.util.soot;

import soot.*;

/**
 * Utilities related to {@link soot.Type}s
 */
public class SootTypeUtils
{
    /**
     * @param type the type to check
     * @return true iff type is an {@link PrimType}
     */
    public static boolean isPrimitive(Type type)
    {
        return type instanceof PrimType;
    }

    /**
     * @param type the type to check
     * @return true iff type is an {@link ArrayType}
     */
    public static boolean isArray(Type type)
    {
        return type instanceof ArrayType;
    }

    /**
     * @param type the type
     * @return true iff type is an {@link IntegerType} or {@link LongType} (covers bool, char, short, int, long)
     */
    public static boolean isIntegerType(Type type)
    {
        return type instanceof IntegerType || type instanceof LongType;
    }
}
