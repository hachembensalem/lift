package ir.ast

import ir._
import ir.interpreter.Interpreter.ValueMap
import lift.arithmetic.ArithExpr
import lift.arithmetic.ArithExpr.Math.Min

/**
 * Zip pattern.
 * Code for this pattern can be generated.
 *
 * The zip pattern has the following high-level semantics:
 *   <code>Zip(2)( [x,,1,,, ..., x,,n,,], [y,,1,,, ..., y,,n,,] )
 *      = [ (x,,1,,, y,,1,,), ..., (x,,n,,, y,,n,,) ]</code>
 * The definitions for `n > 2` are accordingly.
 *
 * The zip pattern has the following type:
 *   `Zip(2) : [a],,i,, -> [b],,i,, -> [a x b],,i,,`
 * The definitions for `n > 2` are accordingly.
 *
 * @param n The number of arrays which are combined. Must be >= 2.
 */
case class Zip(n : Int) extends Pattern(arity = n) with isGenerable {
  override def checkType(argType: Type,
                         setType: Boolean): Type = {
    argType match {
      case tt: TupleType =>
        if (tt.elemsT.length != n) throw new NumberOfArgumentsException

        // make sure all arguments are array types
        val arrayTypes = tt.elemsT.map({
          case (at: ArrayType) => at
          case t => throw new TypeException(t, "ArrayType", this)
        })

        Zip.computeOutType(arrayTypes)

      case _ => throw new TypeException(argType, "TupleType", this)
    }
  }

  override def eval(valueMap: ValueMap, args: Any*): Vector[_] = {
    assert(args.length == arity)
    (n, args) match {
      case (2, Seq(a: Vector[_], b: Vector[_])) => a zip b
//      case (3, a, b, c) =>
      case _ => throw new NotImplementedError()
    }
  }
}

object Zip {
  /**
   * Create an instance of the zip pattern.
   * This function infers the number of arrays which are combined with the zip
   * pattern.
   *
   * @param args The arrays to be combined with the zip pattern.
   * @return An instance of the zip pattern combining the arrays given by `args`
   */
  def apply(args : Expr*) : Expr = {
    assert(args.length >= 2)
    Zip(args.length)(args:_*)
  }

  /**
   * Combination is defined the following way:
   * - If the two array types have a capacity, we keep the minimum value.
   * - If the two array types have a size, we keep the minimum value.
   * - If a size (resp. capacity) is not known in one array type, we drop
   *   it and the result will have no size (resp. capacity).
   */
  private def combineArrayTypes(at1: ArrayType, at2: ArrayType): ArrayType = (at1, at2) match {
    case (ArrayTypeWSWC(_, s1, c1), ArrayTypeWSWC(_, s2, c2)) =>
      ArrayTypeWSWC(UndefType, Min(s1, s2), Min(c1, c2))
    case (ArrayTypeWS(_, s1), ArrayTypeWS(_, s2)) =>
      ArrayTypeWS(UndefType, Min(s1, s2))
    case (ArrayTypeWC(_, c1), ArrayTypeWC(_, c2)) =>
      ArrayTypeWC(UndefType, Min(c1, c2))
    case (ArrayType(_), ArrayType(_)) =>
      ArrayType(UndefType)
  }

  /**
   * Collect the different sizes contained in the array types in order to check
   * that they are all equal
   */
  private def getSizes(arrayTypes: Seq[ArrayType]): Seq[Option[ArithExpr]] = {
    arrayTypes.map({
      case s: Size => Some(s.size)
      case _ => None
    }).distinct
  }

  /**
   * Collect the different capacities contained in the array types in order to
   * check that they are all equal.
   */
  private def getCapacities(arrayTypes: Seq[ArrayType]): Seq[Option[ArithExpr]] = {
    arrayTypes.map({
      case c: Capacity => Some(c.capacity)
      case _ => None
    }).distinct
  }

  /**
   * Compute the type of Zip out the ArrayTypes of its arguments.
   *
   * The potential sizes and capacities are dealt with in the
   * `combineArrayTypes` method above and the element type of the resulting
   * array is a TupleType with all the element types of the input arrays as
   * arguments.
   */
  def computeOutType(arrayTypes: Seq[ArrayType]): ArrayType = {
    // Sanity checks: we allow different sizes and capacities but we warn the
    // user if it occurs.
    val sizes = getSizes(arrayTypes)
    if (sizes.length != 1) println(
      s"Warning: zipping a arrays with different sizes (${sizes.mkString(", ")}).\n"
      + "It may be a mistake."
    )
    val capacities = getCapacities(arrayTypes)
    if (capacities.length != 1) println(
      s"Warning: zipping a arrays with different capacities (${capacities.mkString(", ")}).\n"
      + "It may be a mistake."
    )

    val elemT = TupleType(arrayTypes.map(_.elemT): _*)
    arrayTypes.reduce(combineArrayTypes).replacedElemT(elemT)
  }
}

object Zip3D {

   def apply(arg1: Expr, arg2: Expr) : Expr = {
      Map(Map(\(tuple2 => Zip(tuple2._0, tuple2._1)))) o Map( \(tuple => Zip(tuple._0, tuple._1))) $ Zip(arg1,arg2)
    }

  def apply(arg1: Expr, arg2: Expr, arg3: Expr) : Expr = {
      Map(Map(\(tuple2 => Zip(tuple2._0, tuple2._1, tuple2._2)))) o Map( \(tuple => Zip(tuple._0, tuple._1, tuple._2))) $ Zip(arg1,arg2,arg3)
   }
}

object Zip2D{

  def apply(arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr, arg6: Expr) : Expr = {
    Map(\(tuple => Zip(tuple._0, tuple._1, tuple._2, tuple._3, tuple._4, tuple._5))) $ Zip(arg1, arg2, arg3, arg4, arg5, arg6)
  }

  def apply(arg1: Expr, arg2: Expr) : Expr = {
    Map(\(tuple => Zip(tuple._0, tuple._1))) $ Zip(arg1, arg2)
  }

}
