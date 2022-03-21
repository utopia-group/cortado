// Generated from edu/utexas/cs/utopia/cortado/expression/parser/Expr.g4 by ANTLR 4.9
package edu.utexas.cs.utopia.cortado.expression.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class ExprParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.9", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, UNARY_OP=2, BINARY_OP=3, NARY_OP=4, QUANT_KIND=5, INTEGER_CONST=6, 
		BOOL_CONST=7, CROSS_PROD=8, COLON=9, L_BRACKET=10, R_BRACKET=11, ARROW=12, 
		INT_TY=13, BOOL_TY=14, STRING_TY=15, IDENTIFIER=16, LPAR=17, RPAR=18, 
		DECL=19, WS=20;
	public static final int
		RULE_expr = 0, RULE_type = 1, RULE_primitive_type = 2, RULE_function_type = 3, 
		RULE_array_type = 4, RULE_constant_decl = 5, RULE_sexpr = 6, RULE_sexpr_types = 7, 
		RULE_unary_expr = 8, RULE_binary_expr = 9, RULE_nary_expr = 10, RULE_quant_expr = 11, 
		RULE_sexpr_arg = 12, RULE_atom = 13, RULE_prim_const = 14;
	private static String[] makeRuleNames() {
		return new String[] {
			"expr", "type", "primitive_type", "function_type", "array_type", "constant_decl", 
			"sexpr", "sexpr_types", "unary_expr", "binary_expr", "nary_expr", "quant_expr", 
			"sexpr_arg", "atom", "prim_const"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "','", "'neg'", null, null, null, null, null, "'x'", "':'", "'['", 
			"']'", "'->'", "'Int'", "'Bool'", "'String'", null, "'('", "')'", "'decl'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, "UNARY_OP", "BINARY_OP", "NARY_OP", "QUANT_KIND", "INTEGER_CONST", 
			"BOOL_CONST", "CROSS_PROD", "COLON", "L_BRACKET", "R_BRACKET", "ARROW", 
			"INT_TY", "BOOL_TY", "STRING_TY", "IDENTIFIER", "LPAR", "RPAR", "DECL", 
			"WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Expr.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ExprParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class ExprContext extends ParserRuleContext {
		public SexprContext sexpr() {
			return getRuleContext(SexprContext.class,0);
		}
		public List<Constant_declContext> constant_decl() {
			return getRuleContexts(Constant_declContext.class);
		}
		public Constant_declContext constant_decl(int i) {
			return getRuleContext(Constant_declContext.class,i);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_expr);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(33);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(30);
					constant_decl();
					}
					} 
				}
				setState(35);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			}
			setState(36);
			sexpr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeContext extends ParserRuleContext {
		public Primitive_typeContext primitive_type() {
			return getRuleContext(Primitive_typeContext.class,0);
		}
		public Function_typeContext function_type() {
			return getRuleContext(Function_typeContext.class,0);
		}
		public Array_typeContext array_type() {
			return getRuleContext(Array_typeContext.class,0);
		}
		public TypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_type; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitType(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeContext type() throws RecognitionException {
		TypeContext _localctx = new TypeContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_type);
		try {
			setState(41);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT_TY:
			case BOOL_TY:
			case STRING_TY:
				enterOuterAlt(_localctx, 1);
				{
				setState(38);
				primitive_type();
				}
				break;
			case LPAR:
				enterOuterAlt(_localctx, 2);
				{
				setState(39);
				function_type();
				}
				break;
			case L_BRACKET:
				enterOuterAlt(_localctx, 3);
				{
				setState(40);
				array_type();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Primitive_typeContext extends ParserRuleContext {
		public Primitive_typeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primitive_type; }
	 
		public Primitive_typeContext() { }
		public void copyFrom(Primitive_typeContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class BoolTyContext extends Primitive_typeContext {
		public TerminalNode BOOL_TY() { return getToken(ExprParser.BOOL_TY, 0); }
		public BoolTyContext(Primitive_typeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterBoolTy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitBoolTy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitBoolTy(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class StringTyContext extends Primitive_typeContext {
		public TerminalNode STRING_TY() { return getToken(ExprParser.STRING_TY, 0); }
		public StringTyContext(Primitive_typeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterStringTy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitStringTy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitStringTy(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IntTyContext extends Primitive_typeContext {
		public TerminalNode INT_TY() { return getToken(ExprParser.INT_TY, 0); }
		public IntTyContext(Primitive_typeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterIntTy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitIntTy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitIntTy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Primitive_typeContext primitive_type() throws RecognitionException {
		Primitive_typeContext _localctx = new Primitive_typeContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_primitive_type);
		try {
			setState(46);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT_TY:
				_localctx = new IntTyContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(43);
				match(INT_TY);
				}
				break;
			case BOOL_TY:
				_localctx = new BoolTyContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(44);
				match(BOOL_TY);
				}
				break;
			case STRING_TY:
				_localctx = new StringTyContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(45);
				match(STRING_TY);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Function_typeContext extends ParserRuleContext {
		public Function_typeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function_type; }
	 
		public Function_typeContext() { }
		public void copyFrom(Function_typeContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class NullFuncTyContext extends Function_typeContext {
		public TypeContext codomain;
		public TerminalNode LPAR() { return getToken(ExprParser.LPAR, 0); }
		public TerminalNode RPAR() { return getToken(ExprParser.RPAR, 0); }
		public TerminalNode ARROW() { return getToken(ExprParser.ARROW, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public NullFuncTyContext(Function_typeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterNullFuncTy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitNullFuncTy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitNullFuncTy(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NaryFuncTyContext extends Function_typeContext {
		public Primitive_typeContext primitive_type;
		public List<Primitive_typeContext> domain = new ArrayList<Primitive_typeContext>();
		public TypeContext codomain;
		public TerminalNode LPAR() { return getToken(ExprParser.LPAR, 0); }
		public TerminalNode RPAR() { return getToken(ExprParser.RPAR, 0); }
		public TerminalNode ARROW() { return getToken(ExprParser.ARROW, 0); }
		public List<Primitive_typeContext> primitive_type() {
			return getRuleContexts(Primitive_typeContext.class);
		}
		public Primitive_typeContext primitive_type(int i) {
			return getRuleContext(Primitive_typeContext.class,i);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public List<TerminalNode> CROSS_PROD() { return getTokens(ExprParser.CROSS_PROD); }
		public TerminalNode CROSS_PROD(int i) {
			return getToken(ExprParser.CROSS_PROD, i);
		}
		public NaryFuncTyContext(Function_typeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterNaryFuncTy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitNaryFuncTy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitNaryFuncTy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Function_typeContext function_type() throws RecognitionException {
		Function_typeContext _localctx = new Function_typeContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_function_type);
		int _la;
		try {
			setState(65);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				_localctx = new NullFuncTyContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(48);
				match(LPAR);
				setState(49);
				match(RPAR);
				setState(50);
				match(ARROW);
				setState(51);
				((NullFuncTyContext)_localctx).codomain = type();
				}
				break;
			case 2:
				_localctx = new NaryFuncTyContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(52);
				match(LPAR);
				setState(53);
				((NaryFuncTyContext)_localctx).primitive_type = primitive_type();
				((NaryFuncTyContext)_localctx).domain.add(((NaryFuncTyContext)_localctx).primitive_type);
				setState(58);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==CROSS_PROD) {
					{
					{
					setState(54);
					match(CROSS_PROD);
					setState(55);
					((NaryFuncTyContext)_localctx).primitive_type = primitive_type();
					((NaryFuncTyContext)_localctx).domain.add(((NaryFuncTyContext)_localctx).primitive_type);
					}
					}
					setState(60);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(61);
				match(RPAR);
				setState(62);
				match(ARROW);
				setState(63);
				((NaryFuncTyContext)_localctx).codomain = type();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Array_typeContext extends ParserRuleContext {
		public Primitive_typeContext primitive_type;
		public List<Primitive_typeContext> domain = new ArrayList<Primitive_typeContext>();
		public TypeContext codomain;
		public TerminalNode L_BRACKET() { return getToken(ExprParser.L_BRACKET, 0); }
		public TerminalNode R_BRACKET() { return getToken(ExprParser.R_BRACKET, 0); }
		public TerminalNode ARROW() { return getToken(ExprParser.ARROW, 0); }
		public List<Primitive_typeContext> primitive_type() {
			return getRuleContexts(Primitive_typeContext.class);
		}
		public Primitive_typeContext primitive_type(int i) {
			return getRuleContext(Primitive_typeContext.class,i);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public List<TerminalNode> CROSS_PROD() { return getTokens(ExprParser.CROSS_PROD); }
		public TerminalNode CROSS_PROD(int i) {
			return getToken(ExprParser.CROSS_PROD, i);
		}
		public Array_typeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_type; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterArray_type(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitArray_type(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitArray_type(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Array_typeContext array_type() throws RecognitionException {
		Array_typeContext _localctx = new Array_typeContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_array_type);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(67);
			match(L_BRACKET);
			setState(68);
			((Array_typeContext)_localctx).primitive_type = primitive_type();
			((Array_typeContext)_localctx).domain.add(((Array_typeContext)_localctx).primitive_type);
			setState(73);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==CROSS_PROD) {
				{
				{
				setState(69);
				match(CROSS_PROD);
				setState(70);
				((Array_typeContext)_localctx).primitive_type = primitive_type();
				((Array_typeContext)_localctx).domain.add(((Array_typeContext)_localctx).primitive_type);
				}
				}
				setState(75);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(76);
			match(R_BRACKET);
			setState(77);
			match(ARROW);
			setState(78);
			((Array_typeContext)_localctx).codomain = type();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Constant_declContext extends ParserRuleContext {
		public Token id;
		public TypeContext ty;
		public TerminalNode LPAR() { return getToken(ExprParser.LPAR, 0); }
		public TerminalNode COLON() { return getToken(ExprParser.COLON, 0); }
		public TerminalNode RPAR() { return getToken(ExprParser.RPAR, 0); }
		public TerminalNode IDENTIFIER() { return getToken(ExprParser.IDENTIFIER, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public Constant_declContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constant_decl; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterConstant_decl(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitConstant_decl(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitConstant_decl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Constant_declContext constant_decl() throws RecognitionException {
		Constant_declContext _localctx = new Constant_declContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_constant_decl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(80);
			match(LPAR);
			setState(81);
			((Constant_declContext)_localctx).id = match(IDENTIFIER);
			setState(82);
			match(COLON);
			setState(83);
			((Constant_declContext)_localctx).ty = type();
			setState(84);
			match(RPAR);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SexprContext extends ParserRuleContext {
		public TerminalNode LPAR() { return getToken(ExprParser.LPAR, 0); }
		public Sexpr_typesContext sexpr_types() {
			return getRuleContext(Sexpr_typesContext.class,0);
		}
		public TerminalNode RPAR() { return getToken(ExprParser.RPAR, 0); }
		public SexprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sexpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterSexpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitSexpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitSexpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SexprContext sexpr() throws RecognitionException {
		SexprContext _localctx = new SexprContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_sexpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(86);
			match(LPAR);
			setState(87);
			sexpr_types();
			setState(88);
			match(RPAR);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sexpr_typesContext extends ParserRuleContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public Unary_exprContext unary_expr() {
			return getRuleContext(Unary_exprContext.class,0);
		}
		public Binary_exprContext binary_expr() {
			return getRuleContext(Binary_exprContext.class,0);
		}
		public Nary_exprContext nary_expr() {
			return getRuleContext(Nary_exprContext.class,0);
		}
		public Quant_exprContext quant_expr() {
			return getRuleContext(Quant_exprContext.class,0);
		}
		public Sexpr_typesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sexpr_types; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterSexpr_types(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitSexpr_types(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitSexpr_types(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Sexpr_typesContext sexpr_types() throws RecognitionException {
		Sexpr_typesContext _localctx = new Sexpr_typesContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_sexpr_types);
		try {
			setState(95);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(90);
				atom();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(91);
				unary_expr();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(92);
				binary_expr();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(93);
				nary_expr();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(94);
				quant_expr();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unary_exprContext extends ParserRuleContext {
		public Unary_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary_expr; }
	 
		public Unary_exprContext() { }
		public void copyFrom(Unary_exprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class BuildinUnOpContext extends Unary_exprContext {
		public Token op;
		public Sexpr_argContext oper;
		public TerminalNode UNARY_OP() { return getToken(ExprParser.UNARY_OP, 0); }
		public Sexpr_argContext sexpr_arg() {
			return getRuleContext(Sexpr_argContext.class,0);
		}
		public BuildinUnOpContext(Unary_exprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterBuildinUnOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitBuildinUnOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitBuildinUnOp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class UninterpUnOpContext extends Unary_exprContext {
		public Token op;
		public Sexpr_argContext oper;
		public TerminalNode IDENTIFIER() { return getToken(ExprParser.IDENTIFIER, 0); }
		public Sexpr_argContext sexpr_arg() {
			return getRuleContext(Sexpr_argContext.class,0);
		}
		public UninterpUnOpContext(Unary_exprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterUninterpUnOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitUninterpUnOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitUninterpUnOp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Unary_exprContext unary_expr() throws RecognitionException {
		Unary_exprContext _localctx = new Unary_exprContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_unary_expr);
		try {
			setState(101);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case UNARY_OP:
				_localctx = new BuildinUnOpContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(97);
				((BuildinUnOpContext)_localctx).op = match(UNARY_OP);
				setState(98);
				((BuildinUnOpContext)_localctx).oper = sexpr_arg();
				}
				break;
			case IDENTIFIER:
				_localctx = new UninterpUnOpContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(99);
				((UninterpUnOpContext)_localctx).op = match(IDENTIFIER);
				setState(100);
				((UninterpUnOpContext)_localctx).oper = sexpr_arg();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Binary_exprContext extends ParserRuleContext {
		public Binary_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binary_expr; }
	 
		public Binary_exprContext() { }
		public void copyFrom(Binary_exprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class BuildinBinOpContext extends Binary_exprContext {
		public Token op;
		public Sexpr_argContext left;
		public Sexpr_argContext right;
		public TerminalNode BINARY_OP() { return getToken(ExprParser.BINARY_OP, 0); }
		public List<Sexpr_argContext> sexpr_arg() {
			return getRuleContexts(Sexpr_argContext.class);
		}
		public Sexpr_argContext sexpr_arg(int i) {
			return getRuleContext(Sexpr_argContext.class,i);
		}
		public BuildinBinOpContext(Binary_exprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterBuildinBinOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitBuildinBinOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitBuildinBinOp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class UninterpBinOpContext extends Binary_exprContext {
		public Token op;
		public Sexpr_argContext left;
		public Sexpr_argContext right;
		public TerminalNode IDENTIFIER() { return getToken(ExprParser.IDENTIFIER, 0); }
		public List<Sexpr_argContext> sexpr_arg() {
			return getRuleContexts(Sexpr_argContext.class);
		}
		public Sexpr_argContext sexpr_arg(int i) {
			return getRuleContext(Sexpr_argContext.class,i);
		}
		public UninterpBinOpContext(Binary_exprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterUninterpBinOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitUninterpBinOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitUninterpBinOp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Binary_exprContext binary_expr() throws RecognitionException {
		Binary_exprContext _localctx = new Binary_exprContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_binary_expr);
		try {
			setState(111);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BINARY_OP:
				_localctx = new BuildinBinOpContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(103);
				((BuildinBinOpContext)_localctx).op = match(BINARY_OP);
				setState(104);
				((BuildinBinOpContext)_localctx).left = sexpr_arg();
				setState(105);
				((BuildinBinOpContext)_localctx).right = sexpr_arg();
				}
				break;
			case IDENTIFIER:
				_localctx = new UninterpBinOpContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(107);
				((UninterpBinOpContext)_localctx).op = match(IDENTIFIER);
				setState(108);
				((UninterpBinOpContext)_localctx).left = sexpr_arg();
				setState(109);
				((UninterpBinOpContext)_localctx).right = sexpr_arg();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Nary_exprContext extends ParserRuleContext {
		public Nary_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nary_expr; }
	 
		public Nary_exprContext() { }
		public void copyFrom(Nary_exprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class BuildinNOpContext extends Nary_exprContext {
		public Token op;
		public Sexpr_argContext sexpr_arg;
		public List<Sexpr_argContext> ops = new ArrayList<Sexpr_argContext>();
		public TerminalNode NARY_OP() { return getToken(ExprParser.NARY_OP, 0); }
		public List<Sexpr_argContext> sexpr_arg() {
			return getRuleContexts(Sexpr_argContext.class);
		}
		public Sexpr_argContext sexpr_arg(int i) {
			return getRuleContext(Sexpr_argContext.class,i);
		}
		public BuildinNOpContext(Nary_exprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterBuildinNOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitBuildinNOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitBuildinNOp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class UninterpNOpContext extends Nary_exprContext {
		public Token op;
		public Sexpr_argContext sexpr_arg;
		public List<Sexpr_argContext> ops = new ArrayList<Sexpr_argContext>();
		public TerminalNode IDENTIFIER() { return getToken(ExprParser.IDENTIFIER, 0); }
		public List<Sexpr_argContext> sexpr_arg() {
			return getRuleContexts(Sexpr_argContext.class);
		}
		public Sexpr_argContext sexpr_arg(int i) {
			return getRuleContext(Sexpr_argContext.class,i);
		}
		public UninterpNOpContext(Nary_exprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterUninterpNOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitUninterpNOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitUninterpNOp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Nary_exprContext nary_expr() throws RecognitionException {
		Nary_exprContext _localctx = new Nary_exprContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_nary_expr);
		int _la;
		try {
			setState(131);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NARY_OP:
				_localctx = new BuildinNOpContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(113);
				((BuildinNOpContext)_localctx).op = match(NARY_OP);
				setState(114);
				((BuildinNOpContext)_localctx).sexpr_arg = sexpr_arg();
				((BuildinNOpContext)_localctx).ops.add(((BuildinNOpContext)_localctx).sexpr_arg);
				setState(115);
				((BuildinNOpContext)_localctx).sexpr_arg = sexpr_arg();
				((BuildinNOpContext)_localctx).ops.add(((BuildinNOpContext)_localctx).sexpr_arg);
				setState(119);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << INTEGER_CONST) | (1L << BOOL_CONST) | (1L << IDENTIFIER) | (1L << LPAR))) != 0)) {
					{
					{
					setState(116);
					((BuildinNOpContext)_localctx).sexpr_arg = sexpr_arg();
					((BuildinNOpContext)_localctx).ops.add(((BuildinNOpContext)_localctx).sexpr_arg);
					}
					}
					setState(121);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			case IDENTIFIER:
				_localctx = new UninterpNOpContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(122);
				((UninterpNOpContext)_localctx).op = match(IDENTIFIER);
				setState(123);
				((UninterpNOpContext)_localctx).sexpr_arg = sexpr_arg();
				((UninterpNOpContext)_localctx).ops.add(((UninterpNOpContext)_localctx).sexpr_arg);
				setState(124);
				((UninterpNOpContext)_localctx).sexpr_arg = sexpr_arg();
				((UninterpNOpContext)_localctx).ops.add(((UninterpNOpContext)_localctx).sexpr_arg);
				setState(128);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << INTEGER_CONST) | (1L << BOOL_CONST) | (1L << IDENTIFIER) | (1L << LPAR))) != 0)) {
					{
					{
					setState(125);
					((UninterpNOpContext)_localctx).sexpr_arg = sexpr_arg();
					((UninterpNOpContext)_localctx).ops.add(((UninterpNOpContext)_localctx).sexpr_arg);
					}
					}
					setState(130);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Quant_exprContext extends ParserRuleContext {
		public Token qKind;
		public Token IDENTIFIER;
		public List<Token> quant = new ArrayList<Token>();
		public Sexpr_argContext body;
		public TerminalNode QUANT_KIND() { return getToken(ExprParser.QUANT_KIND, 0); }
		public List<TerminalNode> IDENTIFIER() { return getTokens(ExprParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(ExprParser.IDENTIFIER, i);
		}
		public Sexpr_argContext sexpr_arg() {
			return getRuleContext(Sexpr_argContext.class,0);
		}
		public Quant_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_quant_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterQuant_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitQuant_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitQuant_expr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Quant_exprContext quant_expr() throws RecognitionException {
		Quant_exprContext _localctx = new Quant_exprContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_quant_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(133);
			((Quant_exprContext)_localctx).qKind = match(QUANT_KIND);
			setState(134);
			((Quant_exprContext)_localctx).IDENTIFIER = match(IDENTIFIER);
			((Quant_exprContext)_localctx).quant.add(((Quant_exprContext)_localctx).IDENTIFIER);
			setState(139);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(135);
				match(T__0);
				setState(136);
				((Quant_exprContext)_localctx).IDENTIFIER = match(IDENTIFIER);
				((Quant_exprContext)_localctx).quant.add(((Quant_exprContext)_localctx).IDENTIFIER);
				}
				}
				setState(141);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(142);
			((Quant_exprContext)_localctx).body = sexpr_arg();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sexpr_argContext extends ParserRuleContext {
		public SexprContext sexpr() {
			return getRuleContext(SexprContext.class,0);
		}
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public Sexpr_argContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sexpr_arg; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterSexpr_arg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitSexpr_arg(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitSexpr_arg(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Sexpr_argContext sexpr_arg() throws RecognitionException {
		Sexpr_argContext _localctx = new Sexpr_argContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_sexpr_arg);
		try {
			setState(146);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LPAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(144);
				sexpr();
				}
				break;
			case INTEGER_CONST:
			case BOOL_CONST:
			case IDENTIFIER:
				enterOuterAlt(_localctx, 2);
				{
				setState(145);
				atom();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AtomContext extends ParserRuleContext {
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	 
		public AtomContext() { }
		public void copyFrom(AtomContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class PrimConstContext extends AtomContext {
		public Prim_constContext prim_const() {
			return getRuleContext(Prim_constContext.class,0);
		}
		public PrimConstContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterPrimConst(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitPrimConst(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitPrimConst(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NullaryAppContext extends AtomContext {
		public Token op;
		public TerminalNode IDENTIFIER() { return getToken(ExprParser.IDENTIFIER, 0); }
		public NullaryAppContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterNullaryApp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitNullaryApp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitNullaryApp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_atom);
		try {
			setState(150);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				_localctx = new NullaryAppContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(148);
				((NullaryAppContext)_localctx).op = match(IDENTIFIER);
				}
				break;
			case INTEGER_CONST:
			case BOOL_CONST:
				_localctx = new PrimConstContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(149);
				prim_const();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Prim_constContext extends ParserRuleContext {
		public Prim_constContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_prim_const; }
	 
		public Prim_constContext() { }
		public void copyFrom(Prim_constContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class BoolConstContext extends Prim_constContext {
		public Token c;
		public TerminalNode BOOL_CONST() { return getToken(ExprParser.BOOL_CONST, 0); }
		public BoolConstContext(Prim_constContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterBoolConst(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitBoolConst(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitBoolConst(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IntConstContext extends Prim_constContext {
		public Token c;
		public TerminalNode INTEGER_CONST() { return getToken(ExprParser.INTEGER_CONST, 0); }
		public IntConstContext(Prim_constContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).enterIntConst(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprListener ) ((ExprListener)listener).exitIntConst(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprVisitor ) return ((ExprVisitor<? extends T>)visitor).visitIntConst(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Prim_constContext prim_const() throws RecognitionException {
		Prim_constContext _localctx = new Prim_constContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_prim_const);
		try {
			setState(154);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INTEGER_CONST:
				_localctx = new IntConstContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(152);
				((IntConstContext)_localctx).c = match(INTEGER_CONST);
				}
				break;
			case BOOL_CONST:
				_localctx = new BoolConstContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(153);
				((BoolConstContext)_localctx).c = match(BOOL_CONST);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\26\u009f\4\2\t\2"+
		"\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\3\2\7\2\"\n\2\f\2"+
		"\16\2%\13\2\3\2\3\2\3\3\3\3\3\3\5\3,\n\3\3\4\3\4\3\4\5\4\61\n\4\3\5\3"+
		"\5\3\5\3\5\3\5\3\5\3\5\3\5\7\5;\n\5\f\5\16\5>\13\5\3\5\3\5\3\5\3\5\5\5"+
		"D\n\5\3\6\3\6\3\6\3\6\7\6J\n\6\f\6\16\6M\13\6\3\6\3\6\3\6\3\6\3\7\3\7"+
		"\3\7\3\7\3\7\3\7\3\b\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\5\tb\n\t\3\n\3\n"+
		"\3\n\3\n\5\nh\n\n\3\13\3\13\3\13\3\13\3\13\3\13\3\13\3\13\5\13r\n\13\3"+
		"\f\3\f\3\f\3\f\7\fx\n\f\f\f\16\f{\13\f\3\f\3\f\3\f\3\f\7\f\u0081\n\f\f"+
		"\f\16\f\u0084\13\f\5\f\u0086\n\f\3\r\3\r\3\r\3\r\7\r\u008c\n\r\f\r\16"+
		"\r\u008f\13\r\3\r\3\r\3\16\3\16\5\16\u0095\n\16\3\17\3\17\5\17\u0099\n"+
		"\17\3\20\3\20\5\20\u009d\n\20\3\20\2\2\21\2\4\6\b\n\f\16\20\22\24\26\30"+
		"\32\34\36\2\2\2\u00a4\2#\3\2\2\2\4+\3\2\2\2\6\60\3\2\2\2\bC\3\2\2\2\n"+
		"E\3\2\2\2\fR\3\2\2\2\16X\3\2\2\2\20a\3\2\2\2\22g\3\2\2\2\24q\3\2\2\2\26"+
		"\u0085\3\2\2\2\30\u0087\3\2\2\2\32\u0094\3\2\2\2\34\u0098\3\2\2\2\36\u009c"+
		"\3\2\2\2 \"\5\f\7\2! \3\2\2\2\"%\3\2\2\2#!\3\2\2\2#$\3\2\2\2$&\3\2\2\2"+
		"%#\3\2\2\2&\'\5\16\b\2\'\3\3\2\2\2(,\5\6\4\2),\5\b\5\2*,\5\n\6\2+(\3\2"+
		"\2\2+)\3\2\2\2+*\3\2\2\2,\5\3\2\2\2-\61\7\17\2\2.\61\7\20\2\2/\61\7\21"+
		"\2\2\60-\3\2\2\2\60.\3\2\2\2\60/\3\2\2\2\61\7\3\2\2\2\62\63\7\23\2\2\63"+
		"\64\7\24\2\2\64\65\7\16\2\2\65D\5\4\3\2\66\67\7\23\2\2\67<\5\6\4\289\7"+
		"\n\2\29;\5\6\4\2:8\3\2\2\2;>\3\2\2\2<:\3\2\2\2<=\3\2\2\2=?\3\2\2\2><\3"+
		"\2\2\2?@\7\24\2\2@A\7\16\2\2AB\5\4\3\2BD\3\2\2\2C\62\3\2\2\2C\66\3\2\2"+
		"\2D\t\3\2\2\2EF\7\f\2\2FK\5\6\4\2GH\7\n\2\2HJ\5\6\4\2IG\3\2\2\2JM\3\2"+
		"\2\2KI\3\2\2\2KL\3\2\2\2LN\3\2\2\2MK\3\2\2\2NO\7\r\2\2OP\7\16\2\2PQ\5"+
		"\4\3\2Q\13\3\2\2\2RS\7\23\2\2ST\7\22\2\2TU\7\13\2\2UV\5\4\3\2VW\7\24\2"+
		"\2W\r\3\2\2\2XY\7\23\2\2YZ\5\20\t\2Z[\7\24\2\2[\17\3\2\2\2\\b\5\34\17"+
		"\2]b\5\22\n\2^b\5\24\13\2_b\5\26\f\2`b\5\30\r\2a\\\3\2\2\2a]\3\2\2\2a"+
		"^\3\2\2\2a_\3\2\2\2a`\3\2\2\2b\21\3\2\2\2cd\7\4\2\2dh\5\32\16\2ef\7\22"+
		"\2\2fh\5\32\16\2gc\3\2\2\2ge\3\2\2\2h\23\3\2\2\2ij\7\5\2\2jk\5\32\16\2"+
		"kl\5\32\16\2lr\3\2\2\2mn\7\22\2\2no\5\32\16\2op\5\32\16\2pr\3\2\2\2qi"+
		"\3\2\2\2qm\3\2\2\2r\25\3\2\2\2st\7\6\2\2tu\5\32\16\2uy\5\32\16\2vx\5\32"+
		"\16\2wv\3\2\2\2x{\3\2\2\2yw\3\2\2\2yz\3\2\2\2z\u0086\3\2\2\2{y\3\2\2\2"+
		"|}\7\22\2\2}~\5\32\16\2~\u0082\5\32\16\2\177\u0081\5\32\16\2\u0080\177"+
		"\3\2\2\2\u0081\u0084\3\2\2\2\u0082\u0080\3\2\2\2\u0082\u0083\3\2\2\2\u0083"+
		"\u0086\3\2\2\2\u0084\u0082\3\2\2\2\u0085s\3\2\2\2\u0085|\3\2\2\2\u0086"+
		"\27\3\2\2\2\u0087\u0088\7\7\2\2\u0088\u008d\7\22\2\2\u0089\u008a\7\3\2"+
		"\2\u008a\u008c\7\22\2\2\u008b\u0089\3\2\2\2\u008c\u008f\3\2\2\2\u008d"+
		"\u008b\3\2\2\2\u008d\u008e\3\2\2\2\u008e\u0090\3\2\2\2\u008f\u008d\3\2"+
		"\2\2\u0090\u0091\5\32\16\2\u0091\31\3\2\2\2\u0092\u0095\5\16\b\2\u0093"+
		"\u0095\5\34\17\2\u0094\u0092\3\2\2\2\u0094\u0093\3\2\2\2\u0095\33\3\2"+
		"\2\2\u0096\u0099\7\22\2\2\u0097\u0099\5\36\20\2\u0098\u0096\3\2\2\2\u0098"+
		"\u0097\3\2\2\2\u0099\35\3\2\2\2\u009a\u009d\7\b\2\2\u009b\u009d\7\t\2"+
		"\2\u009c\u009a\3\2\2\2\u009c\u009b\3\2\2\2\u009d\37\3\2\2\2\22#+\60<C"+
		"Kagqy\u0082\u0085\u008d\u0094\u0098\u009c";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}