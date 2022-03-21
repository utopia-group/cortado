package edu.utexas.cs.utopia.cortado.staticanalysis.singletons;

import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.DummyMayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.MayRWSetAnalysis;
import edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.SootMayRWSetAnalysis;
import soot.SootClass;

import javax.annotation.Nonnull;

/**
 * Singleton used to grab a {@link edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.RWSetAnalysis}.
 * Holds exactly one rw-set analysis at a time.
 *
 * Follows the java singleton pattern
 */
public class CachedMayRWSetAnalysis
{
    private final static CachedMayRWSetAnalysis instance = new CachedMayRWSetAnalysis();
    private RWSetAnalysisType analysisType;
    private MayRWSetAnalysis cachedMayRWSetAnalysis = null;

    /**
     * The types of {@link edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.RWSetAnalysis}
     */
    private enum RWSetAnalysisType
    {
        DUMMY, SOOT, NO_TYPE_SET
    }

    /**
     * Build the object, initially using the {@link edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.DummyMayRWSetAnalysis}
     */
    private CachedMayRWSetAnalysis()
    {
        this.analysisType = RWSetAnalysisType.NO_TYPE_SET;
    }

    public void clear()
    {
        if(hasMayRWSetAnalysis())
        {
            final MayRWSetAnalysis mayRWSetAnalysis = getMayRWSetAnalysis();
            if(mayRWSetAnalysis instanceof SootMayRWSetAnalysis)
            {
                ((SootMayRWSetAnalysis) mayRWSetAnalysis).clearCaches();
            }
        }
        cachedMayRWSetAnalysis = null;
    }

    /**
     * @return the global singleton
     */
    static public CachedMayRWSetAnalysis getInstance()
    {
        return instance;
    }

    /**
     * Use {@link edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.DummyMayRWSetAnalysis}
     * to compute new may rw-set analyses
     */
    public void useDummyRWSetAnalysis()
    {
        analysisType = RWSetAnalysisType.DUMMY;
    }

    /**
     * Use the {@link edu.utexas.cs.utopia.cortado.staticanalysis.rwsetanalysis.SootMayRWSetAnalysis}
     * to compute new may rw-set analyses
     */
    public void useSootRWSetAnalysis()
    {
        analysisType = RWSetAnalysisType.SOOT;
    }

    /**
     * Set the cached rw-set analysis to one computed on sootClass
     *
     * @param sootClass the class to compute a may rw-set analysis for
     */
    public void setMayRWSetAnalysis(@Nonnull SootClass sootClass)
    {
        switch(analysisType)
        {
            case DUMMY:
                cachedMayRWSetAnalysis = new DummyMayRWSetAnalysis();
                break;
            case SOOT:
                cachedMayRWSetAnalysis = new SootMayRWSetAnalysis(sootClass);
                break;
            default: throw new IllegalStateException("Unrecognized may rw-set analysis type " + analysisType);
        }
    }

    /**
     * @return the current may rw-set analysis.
     */
    @Nonnull
    public MayRWSetAnalysis getMayRWSetAnalysis()
    {
        if(!hasMayRWSetAnalysis())
        {
            throw new IllegalStateException("No " + MayRWSetAnalysis.class.getName() + " has been computed.");
        }
        return cachedMayRWSetAnalysis;
    }

    /**
     * @return true iff the current rw-set analysis has been set
     */
    public boolean hasMayRWSetAnalysis()
    {
        return (cachedMayRWSetAnalysis != null);
    }
}
