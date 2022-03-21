package edu.utexas.cs.utopia.cortado.vcgen.templategen;

import edu.utexas.cs.utopia.cortado.expression.ExprUtils;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.EqExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class HoudiniCandidateGenerator
{
    private static final ExprFactory eFac = CachedExprFactory.getInstance();

    // TODO: make configurable
    private static final int MAX_HEIGHT_FOR_NARY_OPS = 2;

    // Default Constants and operators
    private final Set<Expr> intConstExpr = new HashSet<>(Collections.singletonList(eFac.mkINT(BigInteger.ZERO)));

    private final Set<IntegerCandidateGenerator> naryCandidateGenerators = new HashSet<>(Collections.singletonList(MinusCandidateGenerator.getInstance()));

    private final Set<IntegerCandidateGenerator> binaryCandidateGenerators = new HashSet<>(Collections.singleton(RemCandidateGenerator.getInstance()));

    private final Set<BooleanCandidateGenerator> boolTemplGenerators = new HashSet<>(Arrays.asList(EqualsCandidateGenerator.getInstance(),
                                                                                                   LTEqualsCandidateGenerator.getInstance()));

    private final Set<Expr> intTermExpr;

    private final Set<Expr> boolTermExpr;

    private final Set<Expr> nestedRefs;
    private final Logger log = LoggerFactory.getLogger(HoudiniCandidateGenerator.class.getName());

    public HoudiniCandidateGenerator(Set<Expr> intTermExpr, Set<Expr> boolTermExpr, Set<Expr> nestedRefs, Set<Expr> intConstExpr,
                                     Set<IntegerCandidateGenerator> naryCandidateGenerators, Set<IntegerCandidateGenerator> binaryCandidateGenerators)
    {
        if(intTermExpr.size() + boolTermExpr.size() + nestedRefs.size() < 5)
        {
            boolTemplGenerators.add(LTCandidateGenerator.getInstance());
        }

        this.intTermExpr = intTermExpr;
        this.boolTermExpr = boolTermExpr;
        this.nestedRefs = nestedRefs;

        this.intConstExpr.addAll(intConstExpr);
        this.naryCandidateGenerators.addAll(naryCandidateGenerators);
        this.binaryCandidateGenerators.addAll(binaryCandidateGenerators);
    }

    private List<Expr> genNestedRefCandidates()
    {
        List<Expr> candidates = new ArrayList<>();
        Expr[] nestedRefsArr = nestedRefs.toArray(new Expr[0]);
        for (int i = 0; i < nestedRefsArr.length; i++)
        {
            Expr ei = nestedRefsArr[i];
            for (int j = i + 1; j < nestedRefsArr.length; j++)
            {
                Expr ej = nestedRefsArr[j];
                EqExpr iEqj = eFac.mkEQ(ei, ej);
                candidates.add(iEqj);
                candidates.add(eFac.mkNEG(iEqj));
            }
        }
        return candidates;
    }

    private List<Expr> genBooleanCandidates(Collection<Expr> ops1, Collection<Expr> ops2)
    {
        return boolTemplGenerators.parallelStream()
                   .flatMap(tGen -> {
                       Set<Expr> rv = new HashSet<>();

                       for (Expr op1 : ops1)
                       {
                           for (Expr op2 : ops2)
                           {
                               rv.add(tGen.genCandidate(op1, op2));
                           }
                       }

                       return rv.stream();
                   })
                   .collect(Collectors.toList());
    }

    private Set<Expr> genIntegerSubExpressions()
    {
        return naryCandidateGenerators.parallelStream()
                                      .flatMap(cGen -> {
                                           Set<Expr> candidateSubExpr = new HashSet<>(intTermExpr);

                                           for (int i = 0; i < MAX_HEIGHT_FOR_NARY_OPS; i++)
                                           {
                                               candidateSubExpr.addAll(getAllSubExprs(cGen, candidateSubExpr));
                                           }

                                           for (IntegerCandidateGenerator binCanGen : binaryCandidateGenerators)
                                           {
                                               candidateSubExpr.addAll(getAllSubExprs(binCanGen, candidateSubExpr));
                                           }

                                           return candidateSubExpr.stream();
                                      })
                                      .collect(Collectors.toSet());
    }

    private Set<Expr> getAllSubExprs(IntegerCandidateGenerator cGen, Set<Expr> candidateSubExpr)
    {
        Set<Expr> newCandidateSubExpr = new HashSet<>();

        for (Expr iTerm : intTermExpr)
        {
            for (Expr c : candidateSubExpr)
            {
                if (!ExprUtils.contains(iTerm, c))
                    newCandidateSubExpr.add(cGen.genCandidate(c, iTerm));
            }
        }
        return newCandidateSubExpr;
    }

    public Collection<Expr> getCandidates()
    {
        // Generate integer sub-expressions.
        Set<Expr> intSubExpr = genIntegerSubExpressions();

        // Generate all boolean candidates.
        List<Expr> candidates = new ArrayList<>();
        candidates.addAll(genBooleanCandidates(intConstExpr, intSubExpr));

        // Add boolean term expressions and their negation.
        candidates.addAll(boolTermExpr);
        candidates.addAll(boolTermExpr.stream().map(eFac::mkNEG).collect(Collectors.toSet()));
        candidates.addAll(genNestedRefCandidates());

        // TODO: Throttle this from command line
        int max_candidates = 3000;
        if(candidates.size() > max_candidates)
        {
            log.warn(candidates.size() + " invariant candidates pruned down to " + max_candidates);
            candidates = candidates.subList(0, max_candidates);
        }

        return candidates;
    }
}
