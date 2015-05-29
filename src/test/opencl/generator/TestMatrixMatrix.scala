package opencl.generator

import arithmetic.Var
import benchmarks.MatrixMultiplication
import opencl.executor._
import org.junit.Assert._
import org.junit.{AfterClass, BeforeClass, Test}
import opencl.ir._
import opencl.ir.CompositePatterns._
import ir._
import ir.UserFunDef._

import scala.reflect.ClassTag

object TestMatrixMatrix {
  @BeforeClass def before() {
    Executor.loadLibrary()
    println("Initialize the executor")
    Executor.init()
  }

  @AfterClass def after() {
    println("Shutdown the executor")
    Executor.shutdown()
  }
}

class TestMatrixMatrix {

  @Test def MATRIX_MATRIX_SIMPLE() {

    val Msize = 256
    val Ksize = 64
    val Nsize = 512
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        MapWrg(fun( Arow =>
          Barrier() o MapLcl(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(Arow, Bcol)
          )) $ B
        )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def MATRIX_MATRIX_SIMPLER() {

    val Msize = 64
    val Ksize = 128
    val Nsize = 256
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        MapGlb(fun( Arow =>
          MapSeq(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o MapSeq(mult) $ Zip(Arow, Bcol)
          )) $ B
        )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def MATRIX_MATRIX_2D_GLOBAL_ID() {

    val Msize = 512
    val Ksize = 512
    val Nsize = 512
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val f1 = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        MapGlb(0)(fun( Arow =>
          MapGlb(1)(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(Arow, Bcol)
          )) $ B
        )) $ A
      })

    val f2 = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        MapGlb(0)(MapGlb(1)(toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f))) o
          Map(fun( Arow =>
            Map(fun( Bcol =>
              Zip(Arow, Bcol)
            )) $ B
          )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f1, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.001f)

    val (output2: Array[Float], runtime2) = Execute(Msize * Nsize)(f2, matrixA, matrixB.transpose)

    println("output.size = " + output2.length)
    println("output(0) = " + output2(0))
    println("runtime = " + runtime2)

    assertArrayEquals(gold, output2, 0.001f)
  }

  @Test def tiledMultiplicationScala(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val transposedB = matrixB.transpose

    val tileSize = 4

    val tiledA = matrixA.grouped(tileSize).toArray.map(_.transpose.grouped(tileSize).toArray.map(_.transpose))
    val tiledB = transposedB.grouped(tileSize).toArray.map(_.transpose.grouped(tileSize).toArray.map(_.transpose))

    // Matrix-Multiplication and reduction over tiles fused
    val tiledC = tiledA.map(aRows => {
      tiledB.map(bCols => {
        (aRows, bCols).zipped.foldLeft(Array.ofDim[Float](tileSize, tileSize))((acc, tiles) => {
          val aTile = tiles._1
          val bTile = tiles._2

          val temp = aTile.map(aRow => bTile.map(bRow => (aRow, bRow).zipped.map(_ * _).sum))

          (temp, acc).zipped.map((x, y) => (x, y).zipped.map(_+_))
        })
      })
    })

    val matrixC = tiledC.map(_.transpose.map(_.flatten)).flatten.flatten

    assertArrayEquals(gold, matrixC, 0.001f)

    // Matrix-Multiplication and reduction over tiles separate
    val tiledC2 = tiledA.map(aRows => {
      tiledB.map(bCols => {
        (aRows, bCols).zipped.map((aTile, bTile) => aTile.map(aRow => bTile.map(bRow => (aRow, bRow).zipped.map(_ * _).sum))).
          reduce((acc, tile) => {

          (tile, acc).zipped.map((x, y) => (x, y).zipped.map(_+_))
        })
      })
    })

    val matrixC2 = tiledC2.map(_.transpose.map(_.flatten)).flatten.flatten

    assertArrayEquals(gold, matrixC2, 0.001f)

    // Matrix-Multiplication and reduction over tiles separate, reduction over last dimension, not first
    val tiledC3 = tiledA.map(aRows => {
      tiledB.map(bCols => {
        (aRows, bCols).zipped.map((aTile, bTile) => aTile.map(aRow => bTile.map(bRow => (aRow, bRow).zipped.map(_ * _).sum))).
          transpose.map(_.transpose.map(_.sum))
      })
    })

    val matrixC3 = tiledC3.map(_.transpose.map(_.flatten)).flatten.flatten

    assertArrayEquals(gold, matrixC3, 0.001f)

    // Trying to reuse A
    val matrixC4 = matrixA.map(rowA => (rowA, matrixB).zipped.map((elemA, rowB) => rowB.map(_*elemA)))
      .transpose.fold(Array.ofDim[Float](16, 16))((a, b) =>  (a, b).zipped.map((a, b) => (a, b).zipped.map(_+_))).flatten

    assertArrayEquals(gold, matrixC4, 0.001f)


    val grouped = matrixB.transpose.grouped(4).toArray.map(_.transpose)

    val matrixC5 = matrixA.map(rowA => grouped.map(columnsB => (rowA, columnsB).zipped.
      foldLeft(Array.ofDim[Float](4))((acc, elemRowPair) => (elemRowPair._2.map(_*elemRowPair._1), acc).
      zipped.map(_+_)))).map(_.flatten)

    assertArrayEquals(gold, matrixC5.flatten, 0.001f)

    // Trying to reuse B
    val matrixC6 = matrixA.grouped(4).toArray.map(rowsA => matrixB.transpose.map(colB =>(rowsA.transpose, colB).zipped.
      foldLeft(Array.ofDim[Float](4))((acc, rowElemPair) => (rowElemPair._1.map(_*rowElemPair._2), acc).
      zipped.map(_+_)))).map(_.transpose).flatten

    assertArrayEquals(gold, matrixC6.flatten, 0.001f)

    def reorder[T: ClassTag](array: Array[T], f: Int => Int): Array[T] = {
      val newArray = Array.ofDim[T](array.length)

      for (i <- array.indices) {
        newArray(i) = array(f(i))
      }

      newArray
    }

    val f: (Int) => Int = i => {
      val s = 4
      val n = matrixB.length / s
      (i / n) + s * (i % n)
    }

     // Trying to reuse both

     // B as innermost
     val matrixC7 = matrixA.grouped(4).toArray.map(rowsA =>
       reorder(matrixB.transpose, f).grouped(4).toArray.map(colsB => (rowsA.transpose, colsB.transpose).zipped.
         foldLeft(Array.ofDim[Float](4, 4))((acc, rowElemPair) => (rowElemPair._1.map(elem => rowElemPair._2.map(_ * elem)), acc).
         zipped.map((a, b) => (a, b).zipped.map(_+_))))
     ).map(_.map(_.transpose).flatten.transpose).flatten.map(reorder(_, f))

    assertArrayEquals(gold, matrixC7.flatten, 0.001f)

    // A as innermost
    val matrixC8 = matrixA.grouped(4).toArray.map(rowsA =>
      reorder(matrixB.transpose, f).grouped(4).toArray.map(colsB => (rowsA.transpose, colsB.transpose).zipped.
        foldLeft(Array.ofDim[Float](4, 4))((acc, rowElemPair) => (rowElemPair._2.map(elem => rowElemPair._1.map(_ * elem)), acc).
        zipped.map((a, b) => (a, b).zipped.map(_+_))))
    ).map(_.flatten.transpose).flatten.map(reorder(_, f))

    assertArrayEquals(gold, matrixC8.flatten, 0.001f)
  }

  @Test def mmReuseBothBInnermost(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val workPerThreadN = 4
    val workPerThreadM = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = new Var("N")
    val m = new Var("M")
    val k = new Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, n), k),
      (A, B) =>
        Map(Scatter(IndexFunction.reorderStride(workPerThreadM))) o Join() o Map(TransposeW() o Join() o Map(TransposeW())) o
          MapGlb(fun( rowsA =>
            MapSeq(fun( colsB =>
              toGlobal(MapSeq(MapSeq(id))) o Join() o ReduceSeq(fun((acc, rowElemPair) =>
                MapSeq(fun(pair => MapSeq(add) $ Zip(Get(pair, 0), Get(pair, 1)))) o fun(rowElemPair => Zip(toPrivate(MapSeq(fun(a => MapSeq(fun(b => mult.apply(a, b))) $ Get(rowElemPair, 1)))) $ Get(rowElemPair, 0), acc)) $ rowElemPair
              ), toPrivate(MapSeq(MapSeq(id))) $ Value("0.0f", ArrayType(ArrayType(Float, workPerThreadM), workPerThreadN))) $ Zip(Transpose() $ rowsA, Transpose() $ colsB)
            )) o Split(workPerThreadM) o ReorderStride(workPerThreadM) o Transpose() $ B
          )) o Split(workPerThreadN) $ A
    )

    val (output: Array[Float], _) = Execute(mSize * nSize)(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def rectangularTiles(): Unit = {
    val mSize = 512
    val kSize = 512
    val nSize = 512
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val tileSizeM = 16
    val tileSizeN = tileSizeM
    val tileSizeK = 8
    val workPerThread = 4

    val f = fun(
      ArrayType(ArrayType(Float, M), K), // Transposed
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>
              Join() o Map(TransposeW()) o
                toGlobal(MapLcl(1)(MapLcl(0)(MapSeq(id)))) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                    Barrier() o fun(partial =>
                      MapLcl(1)(fun(pairOfRows =>
                        MapLcl(0)(fun(x => MapSeq(add) $ Zip(Get(x, 0), Get(x, 1))
                        )) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1))
                      )) $ Zip(acc, partial)
                    ) o

                      MapLcl(1)( fun(rowsA =>
                        MapLcl(0)( fun( colB =>
                          Join() o ReduceSeq(fun((acc, rowElemPair) =>
                            MapSeq(add) o fun(rowElemPair =>
                              Zip(
                                toPrivate(MapSeq(fun(a => mult.apply(a, Get(rowElemPair, 1)))
                                )) $ Get(rowElemPair, 0),
                                acc
                              )
                            ) $ rowElemPair
                          ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, workPerThread))
                          ) $ Zip(Transpose() $ rowsA, colB)
                        )) o Transpose()$ Get(pairOfTiles, 1)
                      )) o Split(workPerThread) o Transpose() $ Get(pairOfTiles, 0)

                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id)))
                          $ Get(pairOfTiles, 0),
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id)))
                           $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , MapLcl(1)(MapLcl(0)(MapSeq(id))) $ Value(0.0f,
                    ArrayType(ArrayType(ArrayType(Float, workPerThread), tileSizeM), tileSizeN/workPerThread))
                ) $ Zip(aRows, bCols)

            )) o Transpose() o Tile(tileSizeK, tileSizeN) $ B
            // Tile the matrices
          )) o Transpose() o Tile(tileSizeK, tileSizeM) $ A
      })

    val (output: Array[Float], _) = Execute(tileSizeM, tileSizeN / workPerThread,
      mSize, nSize / workPerThread, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledAndBlockedBInnermost(): Unit = {
    val mSize = 512
    val kSize = 512
    val nSize = 512
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val tileSizeM = 16
    val tileSizeN = tileSizeM
    val tileSizeK = 8
    val workPerThreadN = 4
    val workPerThreadM = 4

    val f = MatrixMultiplication.tiledAndBlockedBInnermost(tileSizeN, tileSizeM, tileSizeK, workPerThreadN, workPerThreadM)

    val (output: Array[Float], _) = Execute(tileSizeM / workPerThreadM, tileSizeN / workPerThreadN,
      mSize / workPerThreadM, nSize / workPerThreadN, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledAndBlockedAInnermost(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val tileSizeM = 8
    val tileSizeN = tileSizeM
    val tileSizeK = 4
    val workPerThreadN = 2
    val workPerThreadM = 2

    val f = fun(
      ArrayType(ArrayType(Float, M), K), // Transposed
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              Map(Scatter(IndexFunction.reorderStride(tileSizeM/workPerThreadM))) o Join() o
                Map(TransposeW() o Join()) o

                toGlobal(MapLcl(1)(MapLcl(0)(MapSeq(MapSeq(id))))) o

                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                    Barrier() o

                      MapLcl(1)( fun(rowsA =>
                        MapLcl(0)( fun( colsB =>
                          Join() o ReduceSeq(fun((acc, rowElemPair) =>
                            MapSeq(fun(pair => MapSeq(add) $ Zip(Get(pair, 0), Get(pair, 1)))) o
                              fun(rowElemPair =>
                                Zip(
                                  Join() o toPrivate(MapSeq(MapSeq(
                                    fun(aArray => MapSeq(fun(b =>
                                      mult.apply(aArray, b)
                                    )) $ Get(rowElemPair, 0))) o toPrivate(MapSeq(id))
                                  )) o Split(1) $ Get(rowElemPair, 1),
                                  acc
                                )
                              ) o fun(rowElemPair =>
                              Tuple(
                                toPrivate(MapSeq(id)) $ Get(rowElemPair, 0),
                                Get(rowElemPair, 1)
                              )) $ rowElemPair
                          ), Get(colsB, 1)
                          ) $ Zip(Transpose() $ Get(rowsA, 0), Transpose() $ Get(colsB, 0))

                        )) $ Zip(Split(workPerThreadM) o ReorderStride(tileSizeM/workPerThreadM) o Transpose() $ Get(pairOfTiles, 1), Get(rowsA, 1))
                      ))  $ Zip(Split(workPerThreadN) o Transpose() $ Get(pairOfTiles, 0), acc)

                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id)))
                          $ Get(pairOfTiles, 0),
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id)))
                          $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , MapLcl(1)(MapLcl(0)(MapSeq(MapSeq(id)))) $ Value(0.0f,
                    ArrayType(ArrayType(ArrayType(ArrayType(Float, workPerThreadM), workPerThreadN), tileSizeM/workPerThreadM), tileSizeN/workPerThreadN))
                ) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Transpose() o Tile(tileSizeK, tileSizeN) $ B
          )) o Transpose() o Tile(tileSizeK, tileSizeM) $ A
      })

    val (output: Array[Float], _) = Execute(tileSizeM / workPerThreadM, tileSizeN / workPerThreadN,
      mSize / workPerThreadM, nSize / workPerThreadN, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmReuseB(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = new Var("N")
    val m = new Var("M")
    val k = new Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, n), k),
      (A, B) =>
        Join() o Map(TransposeW()) o
          MapGlb(fun( rowsA =>
            MapSeq(fun( colB =>
              toGlobal(MapSeq(id)) o Join() o ReduceSeq(fun((acc, rowElemPair) =>
                MapSeq(add) o fun(rowElemPair => Zip(toPrivate(MapSeq(fun(a => mult.apply(a, Get(rowElemPair, 1))))) $ Get(rowElemPair, 0), acc)) $ rowElemPair
              ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, tileSize))) $ Zip(Transpose() $ rowsA, colB)
            )) o Transpose() $ B
          )) o Split(tileSize) $ A
    )

    val (output: Array[Float], _) = Execute(mSize * nSize)(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmReuseA(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = new Var("N")
    val m = new Var("M")
    val k = new Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, n), k),
      (A, B) =>
        Map(Join()) o
          MapGlb(fun(rowA => MapSeq( fun(colsB =>
            toGlobal(MapSeq(id)) o Join() o ReduceSeq(fun((acc, elemRowPair) =>
              MapSeq(add) o fun(elemRowPair =>
                Zip(toPrivate(MapSeq(fun(a => mult.apply(a, Get(elemRowPair, 0))))) $ Get(elemRowPair, 1), acc)
              ) $ elemRowPair
            ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, tileSize))) $ Zip(rowA, colsB)
          )) o Map(Transpose()) o Split(tileSize) o Transpose() $ B
        )) $ A
    )

    val (output: Array[Float], _) = Execute(mSize * nSize)(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledReuseB(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 8
    val blockSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val f = MatrixMultiplication.moreWorkPerThread(tileSize, blockSize)

    val (output: Array[Float], _) = Execute(tileSize, tileSize / blockSize,
      mSize, nSize / blockSize, (true, false))(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledReuseA(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4
    val blockSize = 2

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = new Var("N")
    val M = new Var("M")
    val K = new Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(1)(fun( aRows =>
            MapWrg(0)(fun( bCols =>


              toGlobal(MapLcl(1)(
                Scatter(IndexFunction.reorderStride(blockSize)) o MapLcl(0)(id)
              )) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                    Barrier() o fun(partial =>
                      MapLcl(1)(fun(pairOfRows =>
                        MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1))
                      )) $ Zip(acc, partial)
                    ) o

                      Map(Join()) o // This reorders elements and needs the scatter at the end
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colsB =>
                          Join() o ReduceSeq(fun((acc, elemRowPair) =>
                            MapSeq(add) o fun(elemRowPair =>
                              Zip(
                                toPrivate(MapSeq(fun(a => mult.apply(a, Get(elemRowPair, 0))))) $ Get(elemRowPair, 1),
                                acc
                              )) $ elemRowPair
                            ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, blockSize))
                          ) $ Zip(rowA, colsB)

                          )) o Map(Transpose()) o Split(blockSize) o Transpose() $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 0),
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , MapLcl(1)(MapLcl(0)(id)) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

            )) o Transpose() o Tile(tileSize) $ B
            // Tile the matrices
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], _) = Execute(tileSize/blockSize, tileSize, mSize/blockSize, nSize, (true, false))(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiply(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = new Var("N")
    val m = new Var("M")
    val k = new Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, k), n),
      (A, B) => {
        // Undo the tiling
        Untile() o
        MapWrg(0)(fun( aRows =>
          MapWrg(1)(fun( bCols =>

            // Reduce the partial results (matrices), so that the reduce is innermost
            Barrier() o MapLcl(0)(Join() o MapLcl(1)(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o Join()) o Transpose()) o Transpose() o

            // Multiply all necessary combinations of tiles
            MapSeq(fun( tiles =>
              Barrier() o MapLcl(0)( fun(aTile =>
                MapLcl(1)( fun( bTile =>
                  toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(aTile, bTile)
                )) $ Get(tiles, 1)
              )) $ Get(tiles, 0)
            )) $ Zip(aRows, bCols)

          // Tile the matrices
          )) o Tile(tileSize) $ B
        )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(mSize * nSize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyLocalMemory(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = new Var("N")
    val m = new Var("M")
    val k = new Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, k), n),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              Map(Join()) o
              // Reduce the partial results (matrices), so that the reduce is innermost
              Barrier() o toGlobal(MapLcl(0)(MapLcl(1)(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)))) o
              Map(Map(Join()) o Transpose()) o Transpose() o

                // Multiply all necessary combinations of tiles
                toLocal(MapSeq(fun( tiles =>
                  Barrier() o MapLcl(0)( fun(aTile =>
                    MapLcl(1)( fun( bTile =>
                      toLocal(MapSeq(id)) o ReduceSeq(fun((acc, y) =>
                        multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(aTile, bTile)
                    )) $ Get(tiles, 1)
                  )) $ Get(tiles, 0)
                ) o

                // Copy tiles to local memory
                fun(tiles =>
                  Tuple(
                    Barrier() o toLocal(MapLcl(0)(MapLcl(1)(id))) $ Get(tiles, 0),
                    Barrier() o toLocal(MapLcl(0)(MapLcl(1)(id))) $ Get(tiles, 1)
                  ))
                )) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Tile(tileSize) $ B
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(mSize * nSize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyLocalMemory2(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              toGlobal(MapLcl(1)(MapLcl(0)(id))) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                    Barrier() o fun(partial => MapLcl(1)(fun(pairOfRows => MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1)))) $ Zip(acc, partial) ) o
                      Map(Join()) o
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colB =>
                          toLocal(MapSeq(id) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f)) $ Zip(rowA, colB)
                        )) $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 0),
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , toLocal(MapLcl(1)(MapLcl(0)(id))) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Tile(tileSize) $ B
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(mSize * nSize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyWithTranspose(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              toGlobal(MapLcl(1)(MapLcl(0)(id))) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                    Barrier() o fun(partial => MapLcl(1)(fun(pairOfRows => MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1)))) $ Zip(acc, partial) ) o
                      Map(Join()) o
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colB =>
                          toLocal(MapSeq(id) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f)) $ Zip(rowA, colB)
                        )) o Transpose() $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 0),
                        Barrier() o toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , toLocal(MapLcl(1)(MapLcl(0)(id))) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

            )) o Transpose() o Tile(tileSize) $ B
            // Tile the matrices
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(mSize * nSize)(f, matrixA, matrixB)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyWithTransposeAndPrivate(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val f = MatrixMultiplication.tiled(tileSize)

    val (output: Array[Float], runtime) = Execute(4, 4, mSize, nSize, (true, false))(f, matrixA, matrixB)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def addArrayOfMatrices(): Unit = {
    val mSize = 16
    val kSize = 16
    val numMatrices = 16
    val matrices = Array.fill(numMatrices, mSize, kSize)(util.Random.nextInt(5).toFloat)

    val gold = matrices.reduce((x, y) => (x, y).zipped.map((x, y) =>
                                                             (x, y).zipped.map(_+_))).flatten

    val test = matrices.transpose.flatMap(_.transpose.map(_.sum))

    assertArrayEquals(gold, test, 0.001f)

    val f = fun(
      ArrayType(ArrayType(ArrayType(Float, new Var("M")), new Var("K")), new Var("N")),
      input => {
        MapGlb(0)(MapGlb(1)(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o Transpose()) o
        Transpose() $ input
      }
    )

    val (output: Array[Float], _) = Execute(mSize*kSize)(f, matrices)

    assertArrayEquals(gold, output, 0.001f)
  }

  @Test def MATRIX_MATRIX_2D_GLOBAL_ID_WITH_TRANSPOSE() {

    val Msize = 512
    val Ksize = 512
    val Nsize = 512
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        MapGlb(0)(fun( Arow =>
          MapGlb(1)(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(Arow, Bcol)
          )) o Transpose() $ B
        )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f, matrixA, matrixB)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def MATRIX_MATRIX_2D_TEST() {

    val mSize = 8
    val kSize = 8
    val nSize = 8
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 1 + c * 0) % 10) + 1) * 1.0f)
    //val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 2 + c * 3) % 10) + 1) * 1.0f)
    //val matrixA = Array.tabulate(mSize, kSize)((r, c) => 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => 1.0f)

    val N = Var("N")
    val M = Var("M")
    val K = Var("K")

    val r = 2 // number of rows a single workgroup computes
    val c = 4 // number of columns a single workgroup computes

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        Join() o MapWrg(0)(fun( aRows =>
          TransposeW() o Join() o MapWrg(1)(fun( bCols =>
            Barrier() o MapLcl(0)(fun( bCol =>
              Barrier() o MapLcl(1)(fun( aRow =>
                toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(aRow, bCol)
              )) $ aRows
            )) $ bCols
          )) o Split(c) $ B
        )) o Split(r) $ A
      })

    val (output: Array[Float], runtime) = Execute(8, mSize * nSize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = TestUtils.matrixMatrixMultiply(matrixA, matrixB).flatten

    println("gold:")
    TestUtils.myPrint(gold, mSize)
    println("output:")
    TestUtils.myPrint(output, mSize)

    assertArrayEquals(gold,output,0.0f)

  }


}