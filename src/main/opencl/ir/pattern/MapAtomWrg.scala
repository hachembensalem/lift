package opencl.ir.pattern

import apart.arithmetic.{PosVar, Var}
import ir.ast._
import ir._
import opencl.ir._

/**
 *
 * @param dim
 * @param f
 */
case class MapAtomWrg(dim: Int, override val f: Lambda1, workVar: Var)
extends AbstractMap(f, "MapAtomWrg", PosVar("w_id")) {
  override def copy(f: Lambda): Pattern = MapAtomWrg(dim, f, PosVar("work_idx"))
  var emitBarrier = true

  var globalTaskIndex: Memory = UnallocatedMemory
}

object MapAtomWrg {
  def apply(f: Lambda1) = new MapAtomWrg(0, f, PosVar("work_idx")) // o is default

  def apply(dim: Int) = (f: Lambda1) => new MapAtomWrg(dim, f, PosVar("work_idx"))
}
