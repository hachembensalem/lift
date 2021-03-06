package ir

import lift.arithmetic.ArithExpr
import scala.language.implicitConversions

package object ast {

  class IndexFunction(val f: (ArithExpr, Type) => ArithExpr)

  implicit def apply(f: (ArithExpr, Type) => ArithExpr): IndexFunction = new IndexFunction(f)

  // predefined reorder functions ...
  val transposeFunction = (outerSize: ArithExpr, innerSize: ArithExpr) => (i: ArithExpr, t: Type) => {
    val col = (i % innerSize) * outerSize
    val row = i / innerSize

    row + col
  }

  val transpose = (i: ArithExpr, t: Type) => {
    t match {
      case ArrayTypeWSWC(ArrayTypeWSWC(_, ns,nc), ms,mc) if ns==nc & ms==mc =>
        transposeFunction(ms, ns)(i, t)
      case _ => throw new IllegalArgumentException
    }
  }

  case class Shift(i: ArithExpr) extends IndexFunction(shiftRightGeneric(i))

  val reverse = (i: ArithExpr, t: Type) => {
      val n = Type.getLength(t)

    n - 1 - i
  }

  val shiftRightGeneric = (shiftAmount: ArithExpr) => (i: ArithExpr, t: Type) => {
    val n = Type.getLength(t)
    (i + shiftAmount) - n*(i / (n-1))
  }

  val shiftRight: (ArithExpr, Type) => ArithExpr = shiftRightGeneric(1)
  val shift2Right: (ArithExpr, Type) => ArithExpr = shiftRightGeneric(2)

  val reorderStride = (s:ArithExpr) => (i: ArithExpr, t:Type) => {
    val n = Type.getLength(t) /^ s
    (i / n) + s * (i % n)
  }

  case class ReorderWithStride(s: ArithExpr) extends IndexFunction(reorderStride(s)) {

    def canEqual(other: Any): Boolean = other.isInstanceOf[ReorderWithStride]

    override def equals(other: Any): Boolean = other match {
      case that: ReorderWithStride =>
        (that canEqual this) &&
          s == that.s
      case _ => false
    }

    override def hashCode(): Int = {
      val state = Seq(s)
      state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
    }
  }
}
