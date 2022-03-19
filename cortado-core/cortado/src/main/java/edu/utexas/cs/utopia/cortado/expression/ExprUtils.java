package edu.utexas.cs.utopia.cortado.expression;

import com.microsoft.z3.*;
import edu.utexas.cs.utopia.cortado.expression.ast.Expr;
import edu.utexas.cs.utopia.cortado.expression.ast.bool.BoolExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.function.BoundedVar;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionApp;
import edu.utexas.cs.utopia.cortado.expression.ast.function.FunctionDecl;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.BoolConstExpr;
import edu.utexas.cs.utopia.cortado.expression.ast.terminal.IntConstExpr;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.CachedExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.astfactory.ExprFactory;
import edu.utexas.cs.utopia.cortado.expression.factories.typefactory.ExprTypeFactory;
import edu.utexas.cs.utopia.cortado.expression.parser.ExprFactoryVisitor;
import edu.utexas.cs.utopia.cortado.expression.parser.ExprLexer;
import edu.utexas.cs.utopia.cortado.expression.parser.ExprParser;
import edu.utexas.cs.utopia.cortado.expression.parser.ThrowingErrorListener;
import edu.utexas.cs.utopia.cortado.expression.type.ArrType;
import edu.utexas.cs.utopia.cortado.expression.type.ExprType;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.AbstractExprTransformer;
import edu.utexas.cs.utopia.cortado.expression.visitors.astvisitor.PostOrderExprVisitor;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStreams;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class ExprUtils
{
    private static final ExprFactory exprFactory = CachedExprFactory.getInstance();
    private static final ExprTypeFactory exprTypeFactory = CachedExprTypeFactory.getInstance();
    private static int defaultTimeoutInMs = -1;

    public static String toParsableString(Expr e)
    {
        AbstractExprTransformer declQuoter = new AbstractExprTransformer()
        {
            @Override
            public Expr visit(FunctionDecl e)
            {
                if (!alreadyVisited(e))
                {
                    cachedResults.put(e, exprFactory.mkDECL("|" + e.getName() + "|", e.getType()));
                }

                return cachedResults.get(e);
            }
        };

        Expr quotedExpr = e.accept(declQuoter);

        StringBuilder rv = new StringBuilder();

        PostOrderExprVisitor declCollector = new PostOrderExprVisitor()
        {
            @Override
            public void visit(FunctionDecl e)
            {
                if (!alreadyVisited(e))
                {
                    rv.append("(")
                      .append(e.getName())
                      .append(" : ")
                      .append(e.getType())
                      .append(")")
                      .append("\n");
                }
            }
        };

        quotedExpr.accept(declCollector);

        rv.append(quotedExpr);

        return rv.toString();
    }

    public static Expr parseFrom(InputStream inStream) throws IOException
    {
        ExprLexer lexer = new ExprLexer(CharStreams.fromStream(inStream));
        BufferedTokenStream tokenStream = new BufferedTokenStream(lexer);
        ExprParser parser = new ExprParser(tokenStream);

        parser.addErrorListener(ThrowingErrorListener.INSTANCE);
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

        ExprParser.ExprContext parseTree = parser.expr();
        return parseTree.accept(new ExprFactoryVisitor());
    }

    public static long size(Expr e)
    {
        ResultContainer<Long> size = new ResultContainer<>(0L);
        PostOrderExprVisitor sizeViz = new PostOrderExprVisitor()
        {
            @Override
            public void visit(Expr e)
            {
                size.res++;
            }
        };

        e.accept(sizeViz);
        return size.res;
    }

    public static Set<FunctionApp> collectAllNullaryApps(Expr e)
    {
        Set<FunctionApp> apps = new HashSet<>();

        PostOrderExprVisitor appCollect = new PostOrderExprVisitor()
        {
            @Override
            public void visit(FunctionApp e)
            {
                assert !(e instanceof BoundedVar) : "unexpected expression";
                if (e.getArgNum() == 0)
                    apps.add(e);
            }
        };

        e.accept(appCollect);

        return apps;
    }

    public static boolean contains(Expr subExpr, Expr e)
    {
        ResultContainer<Boolean> resultContainer = new ResultContainer<>(false);

        PostOrderExprVisitor containsViz = new PostOrderExprVisitor()
        {
            @Override
            public void visit(Expr e)
            {
                if (e == subExpr)
                    resultContainer.res = true;
            }
        };

        e.accept(containsViz);

        return resultContainer.res;
    }

    public static boolean containsAll(Collection<Expr> subExprs, Expr e)
    {
        return subExprs.stream().allMatch(subExpr -> contains(subExpr, e));
    }

    /// Replacement methods ///////////////////////////////////////////////////

    public static Expr replace(Expr e, Map<Expr, Expr> replMap)
    {
        AbstractExprTransformer replVis = new AbstractExprTransformer()
        {
            @Override
            public Expr visit(Expr e)
            {
                return replMap.getOrDefault(e, e);
            }
        };

        return e.accept(replVis);
    }

    public static Expr replace(Expr e, Expr newExpr, Expr toRepl)
    {
        return replace(e, Collections.singletonMap(toRepl, newExpr));
    }

    /// FunctionDecl convenience functions ////////////////////////////////////

    /// bool handling /////////////////////////////////////////////////////////

    public static Expr boolToInt(BoolConstExpr boolConst)
    {
        return boolConst.getKind() == Expr.ExprKind.TRUE ? exprFactory.mkINT(BigInteger.ONE) :
                                                           exprFactory.mkINT(BigInteger.ZERO);
    }

    public static boolean isBoolConst(Expr e)
    {
        return e.getKind() == Expr.ExprKind.TRUE || e.getKind() == Expr.ExprKind.FALSE;
    }

    /// narray  ////////////////////////////////////////////////////////

    public static Expr narryOrSelfDistinct(Collection<Expr> args, ExprUtils.NaryExprCreator mkMethod)
    {
        Expr[] argsAsArray = new HashSet<>(args).toArray(new Expr[0]);
        return argsAsArray.length == 1 ? argsAsArray[0] : mkMethod.apply(argsAsArray);
    }

    /// sat checking ///////////////////////////////////////////////////

    public static AST toZ3Expr(Expr e, Context ctx)
    {
        Z3MarshalVisitor z3Marshaler = new Z3MarshalVisitor(ctx);
        return e.accept(z3Marshaler);
    }

    public static Expr simplifyExprWithSolver(BoolExpr e)
    {
        Context ctx = new Context();
        com.microsoft.z3.BoolExpr z3Expr = (com.microsoft.z3.BoolExpr) toZ3Expr(e, ctx);
        com.microsoft.z3.Goal g = ctx.mkGoal(true, false, false);
        g.add(z3Expr);

        com.microsoft.z3.Goal[] subgoals = ctx.mkTactic("ctx-solver-simplify").apply(g).getSubgoals();
        assert subgoals.length == 1 : "unexpected subgoal length";
        com.microsoft.z3.BoolExpr[] simplFormulas = subgoals[0].getFormulas();
        return simplFormulas.length == 0 ? exprFactory.mkTRUE() : fromZ3Expr(ctx.mkAnd(simplFormulas));
    }

    public static Expr qe(BoolExpr e)
    {
        Context ctx = new Context();
        com.microsoft.z3.BoolExpr z3Expr = (com.microsoft.z3.BoolExpr) toZ3Expr(e, ctx);
        com.microsoft.z3.Goal g = ctx.mkGoal(true, false, false);
        g.add(z3Expr);

        com.microsoft.z3.Goal[] subgoals = ctx.mkTactic("qe2").apply(g).getSubgoals();
        assert subgoals.length == 1 : "unexpected subgoal length";
        return fromZ3Expr(ctx.mkAnd(subgoals[0].getFormulas()));
    }

    public static com.microsoft.z3.BoolExpr[] tseitin(com.microsoft.z3.Expr e, Context ctx)
    {
        com.microsoft.z3.Goal g = ctx.mkGoal(true, false, false);
        g.add((com.microsoft.z3.BoolExpr) e);

        com.microsoft.z3.Goal[] subgoals = ctx.mkTactic("tseitin-cnf").apply(g).getSubgoals();
        return subgoals[0].getFormulas();
    }

    public static Expr fromZ3Expr(com.microsoft.z3.Expr e)
    {
        switch(e.getASTKind())
        {
            case Z3_APP_AST:
                return fromZ3AppExpr(e);
            case Z3_NUMERAL_AST:
                return fromZ3NumeralExpr(e);
            case Z3_QUANTIFIER_AST:
                return fromZ3QuantExpr((Quantifier) e);
            case Z3_VAR_AST:
                return fromZ3Var(e.getIndex(), fromZ3Sort(e.getSort()));
            default:
                throw new IllegalStateException("This should be unreachable");
        }
    }

    /**
     * Set the default timeout for SMT solves
     * (queries to {@link #isSat(BoolExpr)} or {@link #isUnsat(BoolExpr)})
     *
     * @param timeoutInMs the timeout in milliseconds
     */
    public static void setDefaultTimeout(long timeoutInMs)
    {
        if(timeoutInMs <= 0)
        {
            throw new IllegalArgumentException("Expected positive timeoutInMs, received " + timeoutInMs);
        }
        if(timeoutInMs != (int) timeoutInMs)
        {
            throw new IllegalArgumentException("timeoutInMs of " + timeoutInMs + " is not convertible to an int.");
        }
        ExprUtils.defaultTimeoutInMs = (int) timeoutInMs;
    }

    public static boolean isUnsatUnderApprox(BoolExpr e)
    {
        try(Context ctx = new Context())
        {
            Solver solver = ctx.mkSolver();
            return !isSat(solver, (com.microsoft.z3.BoolExpr) toZ3Expr(e, ctx), timeoutSolverProcessBuilder.get(), true);
        }
        catch (SMTException ex)
        {
            return false;
        }
    }

    public static boolean isSatUnderApprox(BoolExpr e)
    {
        try(Context ctx = new Context())
        {
            Solver solver = ctx.mkSolver();
            return isSat(solver, (com.microsoft.z3.BoolExpr) toZ3Expr(e, ctx), timeoutSolverProcessBuilder.get(), true);
        }
        catch (SMTException ex)
        {
            return false;
        }
    }

    public static boolean isUnsat(BoolExpr e)
    {
        return !isSat(e);
    }

    public static boolean isSat(BoolExpr e)
    {
        try(Context ctx = new Context())
        {
            Solver solver = ctx.mkSolver();

            com.microsoft.z3.BoolExpr z3Expr = (com.microsoft.z3.BoolExpr) toZ3Expr(e, ctx);
            return isSat(solver, z3Expr, defaultSolverProcessBuilder.get(), false);
        }
        catch (SMTException ex)
        {
            // Convert SMTException to a runtime exception.
            throw new RuntimeException(ex);
        }
    }

    private static final String SOLVER_EXEC = System.getProperty("solver.exec");

    private static final ThreadLocal<File> solverFile = ThreadLocal.withInitial(() -> {
        try
        {
            File tmpFile = File.createTempFile("solver", null);
            tmpFile.deleteOnExit();

            return tmpFile;
        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    });

    private static final ThreadLocal<ProcessBuilder> defaultSolverProcessBuilder = ThreadLocal.withInitial(() -> new ProcessBuilder(SOLVER_EXEC, solverFile.get().toString()));

    private static final ThreadLocal<ProcessBuilder> timeoutSolverProcessBuilder = ThreadLocal.withInitial(() -> new ProcessBuilder(SOLVER_EXEC, "-T:" + TimeUnit.MILLISECONDS.toSeconds(defaultTimeoutInMs), solverFile.get().toString()));

    private static final java.util.regex.Pattern UNKNOWN_REASON_PATTERN = java.util.regex.Pattern.compile("\\(:reason-unknown \"(.*)\"\\)");

    private static boolean isSat(Solver solver, com.microsoft.z3.BoolExpr e, ProcessBuilder solverProcBuilder, boolean useTimeout) throws SMTException
    {
        solver.add(e);

        try (BufferedWriter queryWriter = Files.newBufferedWriter(solverFile.get().toPath()))
        {
            // Empty Contents
            queryWriter.write("");
            // Print solver and commands
            queryWriter.write(solver + "\n(check-sat)\n(get-info :reason-unknown)");
            // Flush writer
            queryWriter.flush();
        }
        catch (IOException ioException)
        {
            throw new RuntimeException(ioException);
        }

        // force sync the file
        // https://stackoverflow.com/questions/4072878/i-o-concept-flush-vs-sync
        try (FileInputStream in = new FileInputStream(solverFile.get().toPath().toString()))
        {
            in.getFD().sync();
        } catch(IOException ioException)
        {
            throw new RuntimeException(ioException);
        }

        try
        {
            Process solverProc = solverProcBuilder.start();

            // Z3's timeout option does not seem to agree with class Process.
            if (!useTimeout)
            {
                try
                {
                    solverProc.waitFor();
                } catch (InterruptedException ignored)
                {
                    if(solverProc.isAlive())
                    {
                        solverProc.destroy();
                    }
                }
            }
            else
            {
                solverProc.waitFor(defaultTimeoutInMs, TimeUnit.MILLISECONDS);
                if (solverProc.isAlive())
                {
                    solverProc.destroy();
                    throw new SMTException("timeout");
                }
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(solverProc.getInputStream()));
            List<String> procOut = new ArrayList<>();

            String line;
            while ( (line = reader.readLine()) != null)
            {
                procOut.add(line);
            }

            assert procOut.size() >= 1 : "invalid solver output";

            // Read (check-sat) result
            switch (procOut.get(0))
            {
                case "sat":
                    return true;
                case "unsat":
                    return false;
                case "unknown":
                    Matcher m = UNKNOWN_REASON_PATTERN.matcher(procOut.get(1));
                    boolean matches = m.matches();

                    assert matches : "Unexpected reason-unknown";

                    // Grab reason and throw exception.
                    throw new SMTException(m.group(1));
                case "timeout":
                    throw new SMTException("timeout");
                default:
                    assert false;
                    throw new IllegalStateException("unhandled solver output");
            }
        }
        catch (IOException | InterruptedException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private static Expr fromZ3QuantExpr(Quantifier quantExpr)
    {
        int nBound = quantExpr.getNumBound();
        BoundedVar[] bVars = new BoundedVar[nBound];

        Sort[] bVarSorts = quantExpr.getBoundVariableSorts();
        Symbol[] vBarNames = quantExpr.getBoundVariableNames();
        Map<Expr, Expr> replMap = new HashMap<>();
        for (int i = 0; i < nBound; ++i)
        {
            ExprType exprType = fromZ3Sort(bVarSorts[i]);
            bVars[i] = exprFactory.mkBNDVAR(exprFactory.mkDECL(vBarNames[i].toString(), exprTypeFactory.mkFunctionType(new ExprType[0], exprType)));
            replMap.put(fromZ3Var(i, exprType), bVars[i]);
        }

        Expr body = replace(fromZ3Expr(quantExpr.getBody()), replMap);

        assert containsAll(Arrays.asList(bVars), body) : "Body does not contain all quantifiers";

        if (quantExpr.isExistential())
        {
            return exprFactory.mkEXISTS(body, bVars);
        }
        else
        {
            assert quantExpr.isUniversal();
            return exprFactory.mkFORALL(body, bVars);
        }
    }

    private static Expr fromZ3NumeralExpr(com.microsoft.z3.Expr numExpr)
    {
        //noinspection SwitchStatementWithTooFewBranches
        switch (numExpr.getFuncDecl().getRange().getSortKind())
        {
            case Z3_INT_SORT:
                return exprFactory.mkINT(((IntNum) numExpr).getBigInteger());
            default:
                throw new UnsupportedOperationException("unsupported numeral expression " + numExpr);
        }
    }

    private static Expr fromZ3AppExpr(com.microsoft.z3.Expr appExpr)
    {
        int nArgs = appExpr.getNumArgs();
        Expr[] args = new Expr[nArgs];
        com.microsoft.z3.Expr[] z3Args = appExpr.getArgs();

        for (int i = 0; i < nArgs; ++i)
            args[i] = fromZ3Expr(z3Args[i]);

        com.microsoft.z3.FuncDecl funcDecl = appExpr.getFuncDecl();
        switch (funcDecl.getDeclKind())
        {
            case Z3_OP_TRUE:
                return exprFactory.mkTRUE();
            case Z3_OP_FALSE:
                return exprFactory.mkFALSE();
            case Z3_OP_EQ:
                return exprFactory.mkEQ(args[0], args[1]);
            case Z3_OP_AND:
                return narryOrSelfDistinct(Arrays.asList(args), exprFactory::mkAND);
            case Z3_OP_OR:
                return narryOrSelfDistinct(Arrays.asList(args), exprFactory::mkOR);
            case Z3_OP_IFF:
                return exprFactory.mkAND(exprFactory.mkIMPL(args[0], args[1]),
                                         exprFactory.mkIMPL(args[1], args[0]));
            case Z3_OP_NOT:
                return exprFactory.mkNEG(args[0]);
            case Z3_OP_IMPLIES:
                return exprFactory.mkIMPL(args[0], args[1]);
            case Z3_OP_LE:
                return exprFactory.mkLTEQ(args[0], args[1]);
            case Z3_OP_GE:
                return exprFactory.mkGTEQ(args[0], args[1]);
            case Z3_OP_LT:
                return exprFactory.mkLT(args[0], args[1]);
            case Z3_OP_GT:
                return exprFactory.mkGT(args[0], args[1]);
            case Z3_OP_ADD:
                return exprFactory.mkPLUS(args);
            case Z3_OP_SUB:
                return exprFactory.mkMINUS(args);
            case Z3_OP_UMINUS:
                return exprFactory.mkMULT(exprFactory.mkINT(BigInteger.valueOf(-1)), args[0]);
            case Z3_OP_MUL:
                return exprFactory.mkMULT(args);
            case Z3_OP_DIV:
            case Z3_OP_IDIV:
                return exprFactory.mkDIV(args[0], args[1]);
            case Z3_OP_REM:
                return exprFactory.mkREM(args[0], args[1]);
            case Z3_OP_MOD:
                return exprFactory.mkMOD(args[0], args[1]);
            case Z3_OP_STORE:
                return exprFactory.mkSTORE(args[0], args[1], args[2]);
            case Z3_OP_SELECT:
                return exprFactory.mkSELECT(args[0], args[1]);
            case Z3_OP_CONST_ARRAY:
            {
                ArraySort arrSort = (ArraySort) appExpr.getFuncDecl().getRange();
                switch (arrSort.getRange().getSortKind())
                {
                    case Z3_INT_SORT:
                        return exprFactory.mkIntConstArray((ArrType) fromZ3Sort(arrSort), (IntConstExpr) args[0]);
                    case Z3_BOOL_SORT:
                        return exprFactory.mkBoolConstArray((ArrType) fromZ3Sort(arrSort), (BoolConstExpr) args[0]);
                    default:
                        throw new UnsupportedOperationException("unsupported const array");
                }
            }
            case Z3_OP_UNINTERPRETED:
            {
                String funcName = funcDecl.getName().toString();
                Sort[] z3Domain = funcDecl.getDomain();
                ExprType[] domain = new ExprType[z3Domain.length];
                for (int i = 0; i < z3Domain.length; ++i)
                    domain[i] = fromZ3Sort(z3Domain[i]);

                ExprType coDomain = fromZ3Sort(funcDecl.getRange());
                return exprFactory.mkAPP(exprFactory.mkDECL(funcName, exprTypeFactory.mkFunctionType(domain, coDomain)), args);
            }
            default:
                throw new UnsupportedOperationException("unsupported Expr Decl kind " + appExpr);
        }
    }

    private static ExprType fromZ3Sort(Sort sort)
    {
        switch (sort.getSortKind())
        {
            case Z3_BOOL_SORT:
                return exprTypeFactory.mkBooleanType();
            case Z3_INT_SORT:
                return exprTypeFactory.mkIntegerType();
            case Z3_ARRAY_SORT:
            {
                ArraySort arrSort = (ArraySort) sort;
                ExprType domain = fromZ3Sort(arrSort.getDomain());
                ExprType coDomain = fromZ3Sort(arrSort.getRange());
                // TODO: check if z3 "normalizes" array sorts
                return exprTypeFactory.mkArrayType(new ExprType[]{ domain }, coDomain);
            }
            default:
                throw new UnsupportedOperationException("unsupported z3 sort" + sort);
        }
    }

    private static Expr fromZ3Var(int index, ExprType exprType)
    {
        return exprFactory.mkAPP(exprFactory.mkDECL(":var"+index, exprTypeFactory.mkFunctionType(new ExprType[0], exprType)));
    }

    @FunctionalInterface
    public interface NaryExprCreator {
        Expr apply(Expr... es);
    }

    private static class ResultContainer<T>
    {
        T res;

        public ResultContainer(T init)
        {
            this.res = init;
        }
    }
}


