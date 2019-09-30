package backends.spatial.accel

import backends.spatial.SpatialAST.SpatialAddressSpaceOperator
import backends.spatial.ir.{RegMemory, SRAMMemory, SpatialAddressSpace, UndefAddressSpace}
import core.generator.GenericAST
import core.generator.GenericAST.{AstNode, ExpressionT, MutableBlockT, Pipe, StatementT, VarDeclT, VarRefT}
import core.generator.PrettyPrinter._
import ir.{ArrayType, ScalarType, Type}
import lift.arithmetic.NotEvaluableToIntException._
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import utils.Printer

object SpatialAccelAST {

  case class SpatialVarDecl(v: GenericAST.CVar,
                            t: Type,
                            init: Option[AstNode] = None,
                            addressSpace: SpatialAddressSpace = UndefAddressSpace)
  extends VarDeclT with SpatialAddressSpaceOperator {
    val length = 0 // Use multidimensional shape in the type instead

    def _visitAndRebuild(pre: (AstNode) => AstNode, post: (AstNode) => AstNode) : AstNode = {
      SpatialVarDecl(v.visitAndRebuild(pre, post).asInstanceOf[GenericAST.CVar], t,
        init match {
          case Some(i) => Some(i.visitAndRebuild(pre, post))
          case None => None
        },  addressSpace)
    }

    def _visit(pre: (AstNode) => Unit, post: (AstNode) => Unit) : Unit = {
      v.visitBy(pre, post)
      init match {
        case Some(i) => i.visitBy(pre, post)
        case None =>
      }
    }

    override def print(): Doc = t match {
      case _: ArrayType =>
        addressSpace match {
          case RegMemory => throw new NotImplementedException() // TODO: unroll Reg memory
          case SRAMMemory =>
            val baseType = Type.getBaseType(t)

            s"val ${Printer.toString(v.v)} = $addressSpace[${Printer.toString(baseType)}]" <>
              "(" <> Type.getLengths(t).map(Printer.toString).reduce(_ <> ", " <> _) <> ")"
          case _ => throw new NotImplementedError()
        }

      case _ =>
        val baseType = Type.getBaseType(t)

        s"$addressSpace[${Printer.toString(baseType)}]" <>
          ((addressSpace, init) match {
            case (RegMemory, Some(initNode)) => "(" <> initNode.print() <> ")"
            case (RegMemory,None) => empty
            case (SRAMMemory, _) => "(1)"
            case _ => throw new NotImplementedError() // TODO
          })
    }
  }

  trait CounterT extends ExpressionT {
    val min: ExpressionT
    val max: ExpressionT
    val stride: ExpressionT
    val factor: ExpressionT

    override def visit[T](z: T)(visitFun: (T, AstNode) => T): T = {
      z |>
        (visitFun(_, this)) |>
        (min.visit(_)(visitFun)) |>
        (max.visit(_)(visitFun)) |>
        (stride.visit(_)(visitFun)) |>
        (factor.visit(_)(visitFun))
    }

    override def print(): Doc = {
      min.print <> text("until") <> max.print <> text("by") <>
        stride.print <> text("par") <> factor.print
    }
  }

  case class Counter(min: ExpressionT,
                     max: ExpressionT,
                     stride: ExpressionT,
                     factor: ExpressionT) extends CounterT {
    def _visitAndRebuild(pre: (AstNode) => AstNode,  post: (AstNode) => AstNode) : AstNode = {
      Counter(min.visitAndRebuild(pre, post).asInstanceOf[ExpressionT],
        max.visitAndRebuild(pre, post).asInstanceOf[ExpressionT],
        stride.visitAndRebuild(pre, post).asInstanceOf[ExpressionT],
        factor.visitAndRebuild(pre, post).asInstanceOf[ExpressionT])
    }

    def _visit(pre: (AstNode) => Unit, post: (AstNode) => Unit) : Unit = {
      min.visitBy(pre, post)
      max.visitBy(pre, post)
      stride.visitBy(pre, post)
      factor.visitBy(pre, post)
    }
  }

  trait ReduceT extends StatementT {
    val accum: AstNode
    val counter: List[CounterT]
    val mapFun: MutableBlockT
    val reduceFun: MutableBlockT

    override def visit[T](z: T)(visitFun: (T, AstNode) => T): T = {
      z |>
        (visitFun(_, this)) |>
        // Visit internal expressions of a for loop
        (accum.visit(_)(visitFun)) |>
        (counter.foldLeft(_) {
          case (acc, node) => node.visit(acc)(visitFun)
        }) |>
        (mapFun.visit(_)(visitFun)) |>
        (reduceFun.visit(_)(visitFun))
    }

    override def print(): Doc = {
      text("Reduce(") <> accum.print <> text(")") <>
        text("(") <> counter.map(_.print()).reduce(_ <> text(",") <> _) <> text(")") <>
        mapFun.print <> reduceFun.print
    }
  }

  case class Reduce(accum: AstNode,
                    counter: List[CounterT],
                    mapFun: MutableBlockT,
                    reduceFun: MutableBlockT) extends ReduceT {
    def _visitAndRebuild(pre: (AstNode) => AstNode, post: (AstNode) => AstNode): AstNode = {
      Reduce(accum.visitAndRebuild(pre, post),
        counter.map(_.visitAndRebuild(pre, post).asInstanceOf[CounterT]),
        mapFun.visitAndRebuild(pre, post).asInstanceOf[MutableBlockT],
        reduceFun.visitAndRebuild(pre, post).asInstanceOf[MutableBlockT])
    }

    override def _visit(pre: (AstNode) => Unit, post: (AstNode) => Unit): Unit = {
      accum.visitBy(pre, post)
      counter.foreach(_.visitBy(pre, post))
      mapFun.visitBy(pre, post)
      reduceFun.visitBy(pre, post)
    }
  }
}