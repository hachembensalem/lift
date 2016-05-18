package ir


import apart.arithmetic.SizeVar
import ir.ast.{Value, Zip, fun}
import opencl.ir._
import opencl.ir.pattern.{MapSeq, ReduceSeq, toGlobal, toPrivate}
import org.junit.Assert._
import org.junit.Test

class TestMemory {

  @Test
  def mapSeqId(): Unit = {
    val msid = MapSeq(id)
    val lambda = fun(ArrayType(Float, 16), (A) => msid $ A)
    TypeChecker(lambda)
    InferOpenCLAddressSpace(lambda)

    assertEquals(GlobalMemory, lambda.body.addressSpaces)
  }

  @Test(expected = classOf[UnexpectedAddressSpaceException])
  def mapSeqReturnPrivate(): Unit = {
    val msidGlbToPrv = MapSeq(id)
    val lambda = fun(ArrayType(Float, 16), (A) => toPrivate(msidGlbToPrv) $ A)
    TypeChecker(lambda)
    InferOpenCLAddressSpace(lambda)
  }

  @Test
  def mapSeqPrivateGlobal(): Unit = {
    val msidPrvToGlb = MapSeq(id)
    val msidGlbToPrv = MapSeq(id)
    val lambda = fun(ArrayType(Float, 16), (A) =>
      toGlobal(msidPrvToGlb) o toPrivate(msidGlbToPrv) $ A)

    TypeChecker(lambda)
    InferOpenCLAddressSpace(lambda)

    assertEquals(GlobalMemory, lambda.body.addressSpaces)

    assertEquals(PrivateMemory, msidGlbToPrv.f.body.addressSpaces)

    assertEquals(GlobalMemory, msidGlbToPrv.f.params(0).addressSpaces)

    assertEquals(GlobalMemory, msidPrvToGlb.f.body.addressSpaces)

    assertEquals(PrivateMemory, msidPrvToGlb.f.params(0).addressSpaces)
  }

  @Test
  def test(): Unit = {
    val uf = MapSeq(plusOne)
    val f = fun(
      ArrayType(ArrayType(Float, 4), SizeVar("N")),
      input => toGlobal(MapSeq(MapSeq(id))) o
        ReduceSeq(fun((acc, elem) => MapSeq(add) o fun(elem => Zip(acc, uf $ elem)) $ elem),
          Value(0.0f, ArrayType(Float, 4))) $ input
    )
    TypeChecker(f)
    InferOpenCLAddressSpace(f)

    assertEquals(PrivateMemory, uf.f.body.addressSpaces)

  }


  @Test
  def testPrivateArray(): Unit = {
    val uf = MapSeq(plusOne)

    val f = fun(
      ArrayType(ArrayType(Float, 4), SizeVar("N")),
      input => toGlobal(MapSeq(MapSeq(id))) o
        ReduceSeq(fun((acc, elem) => MapSeq(add) o fun(elem => Zip(acc, uf $ elem)) $ elem),
          Value(0.0f, ArrayType(Float, 4))) $ input
    )
    TypeChecker(f)
    InferOpenCLAddressSpace(f)

    assertEquals(PrivateMemory, uf.f.body.addressSpaces)

  }


}
