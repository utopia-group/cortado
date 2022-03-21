package edu.utexas.cs.utopia.cortado.util.sat.satsolver;

public class SatSolveFailedException extends Exception
{
    public SatSolveFailedException(String reasonUnknown)
    {
        super(reasonUnknown);
    }
}
