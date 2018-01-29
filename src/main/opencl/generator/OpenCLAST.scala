package opencl.generator

import generic.ast.GenericAST
import lift.arithmetic.{ArithExpr, Predicate, Var}
import ir.{TupleType, Type, VectorType}
import opencl.ir.{OpenCLAddressSpace, OpenCLMemory, UndefAddressSpace}

import scala.language.implicitConversions
import generic.ast.GenericAST._

object OpenCLAST {

  implicit def exprToStmt(e: Expression): ExpressionStatement = ExpressionStatement(e)

  implicit def predicateToCondExpression(p: Predicate): BinaryExpression = {
    BinaryExpression(
      ArithExpression(p.lhs),
      p.op match {
        case Predicate.Operator.!= => BinaryExpression.Operator.!=
        case Predicate.Operator.< => BinaryExpression.Operator.<
        case Predicate.Operator.<= => BinaryExpression.Operator.<=
        case Predicate.Operator.== => BinaryExpression.Operator.==
        case Predicate.Operator.> => BinaryExpression.Operator.>
        case Predicate.Operator.>= => BinaryExpression.Operator.>=
      },
      ArithExpression(p.rhs)
    )
  }

//  trait

//  case class RequiredWorkGroupSize(localSize: NDRange) extends Attribute

  trait CLAddressSpace {
    val addressSpace : OpenCLAddressSpace
  }

  // trait to define a kernel, to allow us to pattern match on it
  trait Kernel

  case class KernelFunction(name: String, ret: Type, params: List[ParamDecl],
                            body: Block, attribute: Option[Attribute] = None) extends GenericFunction with Kernel

//  case class VarDecl(v: Var,
//                     t: Type,
//                     init: AstNode = null,
//                     addressSpace: OpenCLAddressSpace = UndefAddressSpace,
//                     length: Long = 0) extends Declaration

  /** Parameter declaration. These have to be separated from variable
    * declaration since the vectorization has to be handled differently
    */
  case class ParamDecl(name: String, t: Type,
                       addressSpace: OpenCLAddressSpace = UndefAddressSpace,
                       const: Boolean = false) extends GenericAST.ParamDecl(name, t) with CLAddressSpace


//
//  /** A Label, targeted by a corresponding goto
//    *
//    * @param nameVar the name of label to be declared
//    */
//  case class Label(nameVar: Var) extends Declaration
//
//
//  /**
//    * List of nodes enclosed in a bock. This behaves like (and emits) a C block.
//    */
//  case class Block(var content: Vector[AstNode with BlockMember] = Vector.empty,
//                   global: Boolean = false) extends Statement {
//    /** Append a sub-node. Could be any node, including a sub-block.
//      *
//      * @param node The node to add to this block.
//      */
//    def +=(node: AstNode with BlockMember): Unit = {
//      content = content :+ node
//    }
//
//    def add(node: AstNode with BlockMember): Unit = {
//      this.content :+ node
//    }
//
//    def ::(node: AstNode with BlockMember): Unit = {
//      content = node +: content
//    }
//  }
//
//  /**
//    *
//    * @param init      The expression/value initializing the iteration variabel. should either be an ExpressionStatement or VarDecl
//    * @param cond      The condition used in the for loop
//    * @param increment The expression used to increment the iteration variable
//    * @param body      The loop body
//    */
//  case class ForLoop(init: Declaration,
//                     cond: ExpressionStatement,
//                     increment: Expression,
//                     body: Block) extends Statement
//
//  /** An alternative looping construct, using a predicate - a 'while' loop
//    *
//    * @param loopPredicate the predicate the loop tests each iteration
//    * @param body          the body of the loop
//    */
//  case class WhileLoop(loopPredicate: Predicate,
//                       body: Block) extends Statement
//
//  /** An if-then-else set of statements, with two branches.
//    *
//    * @param cond      the condition
//    * @param trueBody  the body evaluated if switchPredicate is true
//    * @param falseBody the body evaluated if switchPredicate is false
//    */
//  case class IfThenElse(cond: Expression,
//                        trueBody: Block,
//                        falseBody: Block = Block()) extends Statement
//
//  /** A goto statement, targeting the label with corresponding name
//    * TODO: Think of a better way of describing goto labels
//    *
//    * @param nameVar the name of the label to go to
//    */
//  case class GOTO(nameVar: Var) extends Statement
//
//  case class Break() extends Statement
//
//  case class Barrier(mem: OpenCLMemory) extends Statement
//
//  case class TypeDef(t: Type) extends Statement
//
//  case class TupleAlias(t: Type, name: String) extends Statement
//
//  case class ExpressionStatement(e: Expression) extends Statement
//
//
//  case class FunctionCall(name: String,
//                          args: List[OpenCLAST.AstNode]) extends Expression
//
//  /** A reference to a declared variable
//    *
//    * @param v          The variable referenced.
//    * @param suffix     An optional suffix appended to the name.
//    *                   Used e.g. for unrolled variables in private memory.
//    * @param arrayIndex Offset used to index from pointers, if any.
//    * @note This uses a String instead of a Var because some nodes (like user
//    *       functions), inject variables from string.
//    */
//  case class VarRef(v: Var,
//                    suffix: String = null,
//                    arrayIndex: ArithExpression = null) extends Expression
//
//  case class Load(v: VarRef,
//                  t: VectorType,
//                  offset: ArithExpression,
//                  openCLAddressSpace: OpenCLAddressSpace) extends Expression
//
//  case class Store(v: VarRef,
//                   t: VectorType,
//                   value: AstNode,
//                   offset: ArithExpression,
//                   openCLAddressSpace: OpenCLAddressSpace) extends Expression
//
//  /** Represent an assignment.
//    *
//    * @param to    Left-hand side.
//    * @param value Right-hand side.
//    * @note Vectors are using Store instead of assignment.
//    */
//  case class AssignmentExpression(to: AstNode, value: AstNode) extends Expression
//
//  /** Wrapper for arithmetic expression
//    *
//    * @param content The arithmetic expression.
//    */
//  case class ArithExpression(var content: ArithExpr) extends Expression
//
//  case class BinaryExpression(lhs: Expression, rhs: Expression, op: BinaryExpression.Operator.Operator)
//    extends Expression
//
//  object BinaryExpression {
//
//    object Operator extends Enumeration {
//      type Operator = Value
//      val + = Value("+")
//      val - = Value("-")
//      val * = Value("*")
//      val / = Value("/")
//      val % = Value("%")
//    }
//  }
//
//  case class CondExpression(lhs: Expression, rhs: Expression, cond: CondExpression.Operator.Operator) extends Expression
//
//  object CondExpression {
//
//    /**
//      * List of comparison operators
//      */
//    object Operator extends Enumeration {
//      type Operator = Value
//      val < = Value("<")
//      val > = Value(">")
//      val <= = Value("<=")
//      val >= = Value(">=")
//      val != = Value("!=")
//      val == = Value("==")
//      val || = Value("||")
//      val && = Value("&&")
//    }
//
//  }
//
//  case class TernaryExpression(cond: CondExpression, trueExpr: Expression, falseExpr: Expression) extends Expression
//
//  /** Force a cast of a variable to the given type. This is used to
//    *
//    * @param v A referenced variable.
//    * @param t The type to cast the variable into.
//    */
//  case class Cast(v: VarRef, t: Type) extends Expression
//
//  case class PointerCast(v: VarRef, t: Type, addressSpace: OpenCLAddressSpace) extends Expression
//
//  case class VectorLiteral(t: VectorType, vs: VarRef*) extends Expression
//
//  case class StructConstructor(t: TupleType, args: Vector[AstNode]) extends Expression
//
//  case class OpenCLExpression(code: String) extends Expression
//
//
//  /** Inline native code block. Used mainly for UserFun, which are currently
//    * represented as strings
//    *
//    * @param code Native code to insert
//    */
//  case class OpenCLCode(code: String) extends AstNode with BlockMember
//
//  /** Inline comment block.
//    *
//    * @param content Comment string*
//    */
//  case class Comment(content: String) extends AstNode with BlockMember
//
//  case class OpenCLExtension(content: String) extends AstNode with BlockMember
//
//
//  def visitExpressionsInBlock(block: Block, fun: Expression => Unit): Unit = {
//    visitExpressionsInNode(block)
//
//    def visitExpressionsInNode(node: AstNode): Unit = {
//      callFunOnExpression(node)
//
//      node match {
//        case e: Expression => visitExpression(e)
//        case s: Statement => visitStatement(s)
//        case d: Declaration => visitDeclaration(d)
//        case Comment(_) | OpenCLCode(_) | OpenCLExtension(_) | RequiredWorkGroupSize(_) =>
//      }
//    }
//
//    def callFunOnExpression(node: AstNode): Unit = {
//      node match {
//        case e: Expression => fun(e)
//        case _: Statement =>
//        case _: Declaration =>
//        case Comment(_) | OpenCLCode(_) | OpenCLExtension(_) | RequiredWorkGroupSize(_) =>
//      }
//    }
//
//    def visitExpression(e: Expression): Unit = e match {
//      case _: ArithExpression =>
//      case _: OpenCLExpression =>
//      case a: AssignmentExpression =>
//        visitExpressionsInNode(a.value)
//        visitExpressionsInNode(a.to)
//      case c: Cast =>
//        visitExpressionsInNode(c.v)
//      case pc : PointerCast =>
//        visitExpressionsInNode(pc.v)
//      case c: CondExpression =>
//        visitExpressionsInNode(c.lhs)
//        visitExpressionsInNode(c.rhs)
//      case BinaryExpression(lhs, rhs, _) =>
//        visitExpressionsInNode(lhs)
//        visitExpressionsInNode(rhs)
//      case TernaryExpression(cond, trueExpr, falseExpr) =>
//        visitExpression(cond)
//        visitExpression(trueExpr)
//        visitExpression(falseExpr)
//      case f: FunctionCall =>
//        f.args.foreach(visitExpressionsInNode)
//      case l: Load =>
//        visitExpressionsInNode(l.v)
//        visitExpressionsInNode(l.offset)
//      case s: Store =>
//        visitExpressionsInNode(s.v)
//        visitExpressionsInNode(s.value)
//        visitExpressionsInNode(s.offset)
//      case s: StructConstructor =>
//        s.args.foreach(visitExpressionsInNode)
//      case v: VarRef =>
//        if (v.arrayIndex != null) visitExpressionsInNode(v.arrayIndex)
//      case v: VectorLiteral =>
//        v.vs.foreach(visitExpressionsInNode)
//    }
//
//    def visitStatement(s: Statement): Unit = s match {
//      case b: Block => b.content.foreach(visitExpressionsInNode)
//      case es: ExpressionStatement => visitExpressionsInNode(es.e)
//      case f: ForLoop =>
//        visitExpressionsInNode(f.init)
//        visitExpressionsInNode(f.cond)
//        visitExpressionsInNode(f.increment)
//        visitExpressionsInNode(f.body)
//      case ifte: IfThenElse =>
//        visitExpressionsInNode(ifte.cond)
//        visitExpressionsInNode(ifte.trueBody)
//        visitExpressionsInNode(ifte.falseBody)
//      case w: WhileLoop =>
//        visitExpressionsInNode(w.loopPredicate)
//        visitExpressionsInNode(w.body)
//      case Barrier(_) | GOTO(_) | TupleAlias(_, _) | TypeDef(_) | Break() =>
//    }
//
//    def visitDeclaration(d: Declaration): Unit = d match {
//      case f: Function => visitExpressionsInNode(f.body)
//      case v: VarDecl => if (v.init != null) visitExpressionsInNode(v.init)
//      case Label(_) | ParamDecl(_, _, _, _) =>
//    }
//  }
//
//  def visitBlocks(node: AstNode, fun: Block => Unit): Unit = {
//    node match {
//      case _: Expression => // there are no blocks inside any expressions
//
//      case s: Statement => s match {
//        case b: Block =>
//          fun(b)
//          b.content.foreach(visitBlocks(_, fun))
//        case fl: ForLoop => visitBlocks(fl.body, fun)
//        case wl: WhileLoop => visitBlocks(wl.body, fun)
//        case ifte: IfThenElse =>
//          visitBlocks(ifte.trueBody, fun)
//          visitBlocks(ifte.falseBody, fun)
//        case GOTO(_) | Barrier(_) | TypeDef(_) | TupleAlias(_, _) | ExpressionStatement(_) | Break() =>
//      }
//
//      case d: Declaration => d match {
//        case f: Function => visitBlocks(f.body, fun)
//        case Label(_) | VarDecl(_, _, _, _, _) | ParamDecl(_, _, _, _) =>
//      }
//
//      case Comment(_) | OpenCLCode(_) | OpenCLExtension(_) | RequiredWorkGroupSize(_) =>
//    }
//  }
//}
