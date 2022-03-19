package edu.utexas.cs.utopia.cortado.util.sat.enumeration;

import com.microsoft.z3.Context;

public class Z3WeightedPartialMaxSatEnumeratorFactory implements WeightedPartialMaxSatEnumeratorFactory
{
    @Override
    public WeightedPartialMaxSatEnumerator getEnumerator()
    {
        return new Z3WeightedPartialMaxSatEnumerator(new Context());
    }
}
