grammar Expr;

expr
    : (constant_decl)*
      sexpr
    ;

type
    : primitive_type
    | function_type
    | array_type
    ;

primitive_type
    : INT_TY      #intTy
    | BOOL_TY     #boolTy
    | STRING_TY   #stringTy
    ;

function_type
    : LPAR RPAR ARROW codomain=type                                                                #nullFuncTy
    | LPAR domain+=primitive_type (CROSS_PROD domain+=primitive_type)* RPAR ARROW codomain=type    #naryFuncTy
    ;

array_type
    : L_BRACKET domain+=primitive_type (CROSS_PROD domain+=primitive_type)* R_BRACKET ARROW codomain=type
    ;

constant_decl
    : LPAR id=IDENTIFIER COLON ty=type RPAR
    ;

sexpr
    : LPAR sexpr_types RPAR
    ;

sexpr_types
    : atom
    | unary_expr
    | binary_expr
    | nary_expr
    | quant_expr
    ;

unary_expr
    : op=UNARY_OP oper=sexpr_arg   #buildinUnOp
    | op=IDENTIFIER oper=sexpr_arg #uninterpUnOp
    ;

binary_expr
    : op=BINARY_OP left=sexpr_arg right=sexpr_arg  #buildinBinOp
    | op=IDENTIFIER left=sexpr_arg right=sexpr_arg #uninterpBinOp
    ;

nary_expr
    : op=NARY_OP ops+=sexpr_arg ops+=sexpr_arg ops+=sexpr_arg*    #buildinNOp
    | op=IDENTIFIER ops+=sexpr_arg ops+=sexpr_arg ops+=sexpr_arg* #uninterpNOp
    ;

quant_expr
    : qKind=QUANT_KIND quant+=IDENTIFIER (',' quant+=IDENTIFIER)* body=sexpr_arg
    ;

sexpr_arg
    : sexpr
    | atom
    ;

atom
    : op=IDENTIFIER  #nullaryApp
    | prim_const     #primConst
    ;

prim_const
    : c=INTEGER_CONST  #intConst
    // TODO
    //| STRING_CONST
    | c=BOOL_CONST     #boolConst
    ;

UNARY_OP
    : 'neg'
    ;

BINARY_OP
    :  '/'
    | 'rem'
    | 'mod'
    | '='
    | '>='
    | '>'
    | '-->'
    | '<='
    | '<'
    | 'select'
    ;

NARY_OP
    : 'and'
    | 'or'
    | '+'
    | '-'
    | '*'
    | 'store'
    ;

QUANT_KIND
    : 'exist'
    | 'forall'
    ;

INTEGER_CONST
    : '0' | ('1' .. '9')('0' .. '9')*
    ;

BOOL_CONST
    : 'TRUE'
    | 'FALSE'
    ;

CROSS_PROD
    : 'x'
    ;

COLON
    : ':'
    ;

L_BRACKET
    : '['
    ;

R_BRACKET
    : ']'
    ;

ARROW
    : '->'
    ;

INT_TY
    : 'Int'
    ;

BOOL_TY
    : 'Bool'
    ;

STRING_TY
    : 'String'
    ;

// The ? afte .+ is REALLY important. It converts the to
// be non-greedy (match until the first closing '|')
IDENTIFIER
    : '|' .+? '|'
    ;

LPAR
    : '('
    ;

RPAR
    : ')'
    ;

DECL
    : 'decl'
    ;

WS
   : [ \r\n\t]+ -> skip
   ;