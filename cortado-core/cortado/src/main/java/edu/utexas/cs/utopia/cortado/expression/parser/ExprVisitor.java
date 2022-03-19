// Generated from edu/utexas/cs/utopia/cortado/expression/parser/Expr.g4 by ANTLR 4.9
package edu.utexas.cs.utopia.cortado.expression.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link ExprParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface ExprVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link ExprParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(ExprParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(ExprParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by the {@code intTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntTy(ExprParser.IntTyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code boolTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolTy(ExprParser.BoolTyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code stringTy}
	 * labeled alternative in {@link ExprParser#primitive_type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringTy(ExprParser.StringTyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code nullFuncTy}
	 * labeled alternative in {@link ExprParser#function_type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullFuncTy(ExprParser.NullFuncTyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code naryFuncTy}
	 * labeled alternative in {@link ExprParser#function_type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNaryFuncTy(ExprParser.NaryFuncTyContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#array_type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArray_type(ExprParser.Array_typeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#constant_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstant_decl(ExprParser.Constant_declContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#sexpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSexpr(ExprParser.SexprContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#sexpr_types}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSexpr_types(ExprParser.Sexpr_typesContext ctx);
	/**
	 * Visit a parse tree produced by the {@code buildinUnOp}
	 * labeled alternative in {@link ExprParser#unary_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBuildinUnOp(ExprParser.BuildinUnOpContext ctx);
	/**
	 * Visit a parse tree produced by the {@code uninterpUnOp}
	 * labeled alternative in {@link ExprParser#unary_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUninterpUnOp(ExprParser.UninterpUnOpContext ctx);
	/**
	 * Visit a parse tree produced by the {@code buildinBinOp}
	 * labeled alternative in {@link ExprParser#binary_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBuildinBinOp(ExprParser.BuildinBinOpContext ctx);
	/**
	 * Visit a parse tree produced by the {@code uninterpBinOp}
	 * labeled alternative in {@link ExprParser#binary_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUninterpBinOp(ExprParser.UninterpBinOpContext ctx);
	/**
	 * Visit a parse tree produced by the {@code buildinNOp}
	 * labeled alternative in {@link ExprParser#nary_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBuildinNOp(ExprParser.BuildinNOpContext ctx);
	/**
	 * Visit a parse tree produced by the {@code uninterpNOp}
	 * labeled alternative in {@link ExprParser#nary_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUninterpNOp(ExprParser.UninterpNOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#quant_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQuant_expr(ExprParser.Quant_exprContext ctx);
	/**
	 * Visit a parse tree produced by {@link ExprParser#sexpr_arg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSexpr_arg(ExprParser.Sexpr_argContext ctx);
	/**
	 * Visit a parse tree produced by the {@code nullaryApp}
	 * labeled alternative in {@link ExprParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullaryApp(ExprParser.NullaryAppContext ctx);
	/**
	 * Visit a parse tree produced by the {@code primConst}
	 * labeled alternative in {@link ExprParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimConst(ExprParser.PrimConstContext ctx);
	/**
	 * Visit a parse tree produced by the {@code intConst}
	 * labeled alternative in {@link ExprParser#prim_const}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntConst(ExprParser.IntConstContext ctx);
	/**
	 * Visit a parse tree produced by the {@code boolConst}
	 * labeled alternative in {@link ExprParser#prim_const}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolConst(ExprParser.BoolConstContext ctx);
}