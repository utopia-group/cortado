package edu.utexas.cs.utopia.cortado.signalling;

import soot.SootMethod;

import javax.annotation.Nonnull;
import java.util.Objects;

public class SignalOperation
{
    private final SootMethod predicate;

    private final boolean isConditional;
    private final boolean isCascadedBroadcast;

    private final boolean isBroadCast;

    public SignalOperation(SootMethod predicate, boolean isConditional, boolean isBroadCast, boolean isCascadedBroadcast)
    {
        this.predicate = predicate;
        this.isConditional = isConditional;
        this.isBroadCast = isBroadCast;
        this.isCascadedBroadcast = isCascadedBroadcast;
        if(isCascadedBroadcast && isBroadCast)
        {
            throw new IllegalArgumentException("No lazy broadcast can also be a broadcast");
        }
    }

    public enum FLIP_PRED_OPT_CONDITION
    {
        NEVER, UNCONDITIONAL, ALWAYS
    }

    private static FLIP_PRED_OPT_CONDITION DEFAULT_FLIP_PRED_OPT_CONDITION = FLIP_PRED_OPT_CONDITION.NEVER;

    /**
     * set the default flip-predicate optimization condition
     * @param flipPredOptCondition the condition
     */
    public static void setDefaultFlipPredOptCondition(@Nonnull FLIP_PRED_OPT_CONDITION flipPredOptCondition)
    {
        DEFAULT_FLIP_PRED_OPT_CONDITION = flipPredOptCondition;
    }

    /**
     * @return the default {@link FLIP_PRED_OPT_CONDITION}
     */
    @Nonnull
    public static FLIP_PRED_OPT_CONDITION getDefaultFlipPredOptCondition()
    {
        return DEFAULT_FLIP_PRED_OPT_CONDITION;
    }

    /**
     * See also {@link #getDefaultFlipPredOptCondition()}
     * @return whether to perform the flip-predicate optimization
     */
    public boolean performFlipPredOpt()
    {
        return performFlipPredOpt(getDefaultFlipPredOptCondition());
    }

    /**
     * @param flipPredOpt when to perform the flip pred opt
     * @return whether to perform the flip-predicate optimization
     */
    public boolean performFlipPredOpt(@Nonnull FLIP_PRED_OPT_CONDITION flipPredOpt)
    {
        switch (flipPredOpt)
        {
            case NEVER: return false;
            case UNCONDITIONAL: return !isConditional() && !isCascadedBroadcast();
            case ALWAYS: return !isCascadedBroadcast() && getPredicate().getParameterCount() <= 0;
            default:
                throw new RuntimeException("Unrecognized flipPredOpt value: " + flipPredOpt);
        }
    }

    public SootMethod getPredicate()
    {
        return predicate;
    }

    public boolean isConditional()
    {
        return isConditional;
    }

    public boolean isBroadCast()
    {
        return isBroadCast;
    }

    public boolean isCascadedBroadcast()
    {
        return isCascadedBroadcast;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignalOperation that = (SignalOperation) o;
        return isConditional == that.isConditional
                && isBroadCast == that.isBroadCast
                && predicate.equals(that.predicate)
                && isCascadedBroadcast == that.isCascadedBroadcast;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(predicate, isConditional, isBroadCast);
    }
}
