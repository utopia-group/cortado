package edu.utexas.cs.utopia.cortado;

import javax.annotation.Nonnull;

public class UnexpectedOutputException extends Exception
{
    public UnexpectedOutputException(@Nonnull String errorMsg)
    {
        super(errorMsg);
    }
}
