package analysis

import apart.arithmetic.{Cst, SizeVar}
import ir.ArrayType
import ir.ast._
import opencl.generator._
import opencl.ir._
import opencl.ir.pattern._
import org.junit.Assert._
import org.junit.Test

class TestAccessCounts {

  val N = SizeVar("N")
  val globalSize0 = get_global_size(0)
  val globalSize1 = get_global_size(1)

  val numGroups0 = get_num_groups(0)
  val localSize0 = get_local_size(0)

  @Test
  def simple(): Unit = {

    val f = fun(
      ArrayType(ArrayType(Float, N), N),
      x => MapGlb(MapSeq(id)) $ x
    )

    val accessCounts = AccessCounts(f)
    val newF = accessCounts.substLambda

    assertEquals(N * (N /^ globalSize0), accessCounts.getStores(newF.body.mem))
  }

  @Test
  def simple2(): Unit = {

    val f = fun(
      ArrayType(ArrayType(Float, N), N),
      x => MapGlb(1)(MapGlb(0)(id)) $ x
    )

    val accessCounts = AccessCounts(f)
    val newF = accessCounts.substLambda

    assertEquals((N /^ globalSize1) * (N /^ globalSize0),
      accessCounts.getStores(newF.body.mem))
  }

  @Test
  def moreReads(): Unit = {

    val f = fun(
      ArrayType(Float, N),
      x => MapSeq(add) $ Zip(x,x)
    )

    val accessCounts = AccessCounts(f)
    val newF = accessCounts.substLambda

    assertEquals(N, accessCounts.getStores(newF.body.mem))
    assertEquals(2*N, accessCounts.getLoads(newF.params.head.mem))

    assertEquals(Cst(0), accessCounts.getLoads(newF.body.mem))
    assertEquals(Cst(0), accessCounts.getStores(newF.params.head.mem))
  }

  @Test
  def simpleLocal(): Unit = {
    val f = fun(
      ArrayType(ArrayType(Float, 16), N),
      x => MapWrg(toGlobal(MapLcl(id)) o toLocal(MapLcl(id))) $ x
    )

    val counts = AccessCounts(f)
    val localReads = counts.getLoads(LocalMemory, exact = false)
    val localWrites = counts.getStores(LocalMemory, exact = false)

    assertEquals((N /^ numGroups0) * (Cst(16) /^ localSize0) , localReads)
    assertEquals((N /^ numGroups0) * (Cst(16) /^ localSize0) , localWrites)
  }

  @Test
  def withPattern(): Unit = {

    val f = fun(
      ArrayType(ArrayType(Float, N), N),
      x => MapGlb(1)(MapGlb(0)(id)) o Transpose() $ x
    )

    val accessCounts = AccessCounts(f)

    assertEquals((N /^ globalSize1) * (N /^ globalSize0),
      accessCounts.getLoads(GlobalMemory, UnknownPattern, exact = false))
    assertEquals((N /^ globalSize1) * (N /^ globalSize0),
      accessCounts.getStores(GlobalMemory, CoalescedPattern, exact = false))

    assertEquals(Cst(0),
      accessCounts.getLoads(GlobalMemory, CoalescedPattern, exact = false))
    assertEquals(Cst(0),
      accessCounts.getStores(GlobalMemory, UnknownPattern, exact = false))
  }

  @Test
  def vector(): Unit = {

    val f = fun(
      ArrayType(Float4, N),
      x => MapGlb(0)(idF4) $ x
    )

    val accessCounts = AccessCounts(f)

    // TODO: is the pattern correct?
    assertEquals(N /^ globalSize0,
      accessCounts.vectorLoads(GlobalMemory, UnknownPattern))
    assertEquals(N /^ globalSize0,
      accessCounts.vectorStores(GlobalMemory, UnknownPattern))
  }

  @Test
  def vector2(): Unit = {

    val f = fun(
      ArrayType(Float, 4*N),
      x => asScalar() o MapGlb(0)(idF4) o asVector(4) $ x
    )

    val accessCounts = AccessCounts(f)

    // TODO: is the pattern correct?
    assertEquals(N /^ globalSize0,
      accessCounts.vectorLoads(GlobalMemory, UnknownPattern))
    assertEquals(N /^ globalSize0,
      accessCounts.vectorStores(GlobalMemory, UnknownPattern))
  }
}
