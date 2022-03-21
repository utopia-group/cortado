// Generated from edu/utexas/cs/utopia/cortado/expression/parser/Expr.g4 by ANTLR 4.9
package edu.utexas.cs.utopia.cortado.expression.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ExprParser}.
 */
public interface ExprListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(ExprParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(ExprParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(ExprParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(ExprParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by the {@code intTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 */
	void enterIntTy(ExprParser.IntTyContext ctx);
	/**
	 * Exit a parse tree produced by the {@code intTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 */
	void exitIntTy(ExprParser.IntTyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code boolTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 */
	void enterBoolTy(ExprParser.BoolTyContext ctx);
	/**
	 * Exit a parse tree produced by the {@code boolTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 */
	void exitBoolTy(ExprParser.BoolTyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code stringTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 */
	void enterStringTy(ExprParser.StringTyContext ctx);
	/**
	 * Exit a parse tree produced by the {@code stringTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 */
	void exitStringTy(ExprParser.StringTyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code nullFuncTy}
	 * labeled alternative in {@link ExprParser#function_type}.
	 * @param ctx the parse tree
	 */
	void enterNullFuncTy(ExprParser.NullFuncTyContext ctx);
	/**
	 * Exit a parse tree produced by the {@code nullFuncTy}
	 * labeled alternative in {@link ExprParser#function_type}.
	 * @param ctx the parse tree
	 */
	void exitNullFuncTy(ExprParser.NullFuncTyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code naryFuncTy}
	 * labeled alternative in {@link ExprParser#function_type}.
	 * @param ctx the parse tree
	 */
	void enterNaryFuncTy(ExprParser.NaryFuncTyContext ctx);
	/**
	 * Exit a parse tree produced by the {@code naryFuncTy}
	 * labeled alternative in {@link ExprParser#function_type}.
	 * @param ctx the parse tree
	 */
	void exitNaryFuncTy(ExprParser.NaryFuncTyContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#array_type}.
	 * @param ctx the parse tree
	 */
	void enterArray_type(ExprParser.Array_typeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#array_type}.
	 * @param ctx the parse tree
	 */
	void exitArray_type(ExprParser.Array_typeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#constant_decl}.
	 * @param ctx the parse tree
	 */
	void enterConstant_decl(ExprParser.Constant_declContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#constant_decl}.
	 * @param ctx the parse tree
	 */
	void exitConstant_decl(ExprParser.Constant_declContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#sexpr}.
	 * @param ctx the parse tree
	 */
	void enterSexpr(ExprParser.SexprContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#sexpr}.
	 * @param ctx the parse tree
	 */
	void exitSexpr(ExprParser.SexprContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#sexpr_types}.
	 * @param ctx the parse tree
	 */
	void enterSexpr_types(ExprParser.Sexpr_typesContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#sexpr_types}.
	 * @param ctx the parse tree
	 */
	void exitSexpr_types(ExprParser.Sexpr_typesContext ctx);
	/**
	 * Enter a parse tree produced by the {@code buildinUnOp}
	 * labeled alternative in {@link ExprParser#unary_expr}.
	 * @param ctx the parse tree
	 */
	void enterBuildinUnOp(ExprParser.BuildinUnOpContext ctx);
	/**
	 * Exit a parse tree produced by the {@code buildinUnOp}
	 * labeled alternative in {@link ExprParser#unary_expr}.
	 * @param ctx the parse tree
	 */
	void exitBuildinUnOp(ExprParser.BuildinUnOpContext ctx);
	/**
	 * Enter a parse tree produced by the {@code uninterpUnOp}
	 * labeled alternative in {@link ExprParser#unary_expr}.
	 * @param ctx the parse tree
	 */
	void enterUninterpUnOp(ExprParser.UninterpUnOpContext ctx);
	/**
	 * Exit a parse tree produced by the {@code uninterpUnOp}
	 * labeled alternative in {@link ExprParser#unary_expr}.
	 * @param ctx the parse tree
	 */
	void exitUninterpUnOp(ExprParser.UninterpUnOpContext ctx);
	/**
	 * Enter a parse tree produced by the {@code buildinBinOp}
	 * labeled alternative in {@link ExprParser#binary_expr}.
	 * @param ctx the parse tree
	 */
	void enterBuildinBinOp(ExprParser.BuildinBinOpContext ctx);
	/**
	 * Exit a parse tree produced by the {@code buildinBinOp}
	 * labeled alternative in {@link ExprParser#binary_expr}.
	 * @param ctx the parse tree
	 */
	void exitBuildinBinOp(ExprParser.BuildinBinOpContext ctx);
	/**
	 * Enter a parse tree produced by the {@code uninterpBinOp}
	 * labeled alternative in {@link ExprParser#binary_expr}.
	 * @param ctx the parse tree
	 */
	void enterUninterpBinOp(ExprParser.UninterpBinOpContext ctx);
	/**
	 * Exit a parse tree produced by the {@code uninterpBinOp}
	 * labeled alternative in {@link ExprParser#binary_expr}.
	 * @param ctx the parse tree
	 */
	void exitUninterpBinOp(ExprParser.UninterpBinOpContext ctx);
	/**
	 * Enter a parse tree produced by the {@code buildinNOp}
	 * labeled alternative in {@link ExprParser#nary_expr}.
	 * @param ctx the parse tree
	 */
	void enterBuildinNOp(ExprParser.BuildinNOpContext ctx);
	/**
	 * Exit a parse tree produced by the {@code buildinNOp}
	 * labeled alternative in {@link ExprParser#nary_expr}.
	 * @param ctx the parse tree
	 */
	void exitBuildinNOp(ExprParser.BuildinNOpContext ctx);
	/**
	 * Enter a parse tree produced by the {@code uninterpNOp}
	 * labeled alternative in {@link ExprParser#nary_expr}.
	 * @param ctx the parse tree
	 */
	void enterUninterpNOp(ExprParser.UninterpNOpContext ctx);
	/**
	 * Exit a parse tree produced by the {@code uninterpNOp}
	 * labeled alternative in {@link ExprParser#nary_expr}.
	 * @param ctx the parse tree
	 */
	void exitUninterpNOp(ExprParser.UninterpNOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#quant_expr}.
	 * @param ctx the parse tree
	 */
	void enterQuant_expr(ExprParser.Quant_exprContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#quant_expr}.
	 * @param ctx the parse tree
	 */
	void exitQuant_expr(ExprParser.Quant_exprContext ctx);
	/**
	 * Enter a parse tree produced by {@link ExprParser#sexpr_arg}.
	 * @param ctx the parse tree
	 */
	void enterSexpr_arg(ExprParser.Sexpr_argContext ctx);
	/**
	 * Exit a parse tree produced by {@link ExprParser#sexpr_arg}.
	 * @param ctx the parse tree
	 */
	void exitSexpr_arg(ExprParser.Sexpr_argContext ctx);
	/**
	 * Enter a parse tree produced by the {@code nullaryApp}
	 * labeled alternative in {@link ExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterNullaryApp(ExprParser.NullaryAppContext ctx);
	/**
	 * Exit a parse tree produced by the {@code nullaryApp}
	 * labeled alternative in {@link ExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitNullaryApp(ExprParser.NullaryAppContext ctx);
	/**
	 * Enter a parse tree produced by the {@code primConst}
	 * labeled alternative in {@link ExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterPrimConst(ExprParser.PrimConstContext ctx);
	/**
	 * Exit a parse tree produced by the {@code primConst}
	 * labeled alternative in {@link ExprParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitPrimConst(ExprParser.PrimConstContext ctx);
	/**
	 * Enter a parse tree produced by the {@code intConst}
	 * labeled alternative in {@link ExprParser#prim_const}.
	 * @param ctx the parse tree
	 */
	void enterIntConst(ExprParser.IntConstContext ctx);
	/**
	 * Exit a parse tree produced by the {@code intConst}
	 * labeled alternative in {@link ExprParser#prim_const}.
	 * @param ctx the parse tree
	 */
	void exitIntConst(ExprParser.IntConstContext ctx);
	/**
	 * Enter a parse tree produced by the {@code boolConst}
	 * labeled alternative in {@link ExprParser#prim_const}.
	 * @param ctx the parse tree
	 */
	void enterBoolConst(ExprParser.BoolConstContext ctx);
	/**
	 * Exit a parse tree produced by the {@code boolConst}
	 * labeled alternative in {@link ExprParser#prim_const}.
	 * @param ctx the parse tree
	 */
	void exitBoolConst(ExprParser.BoolConstContext ctx);
}