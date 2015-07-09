package opencl.generator

import java.awt.image.BufferedImage
import java.io.{IOException, File}
import javax.imageio.ImageIO

import apart.arithmetic.Var
import benchmarks.{MolecularDynamics, BlackScholes}
import ir._
import ir.UserFunDef._
import opencl.executor.{Execute, Executor}
import opencl.ir._
import org.junit.Assert._
import org.junit.{AfterClass, BeforeClass, Test}

object TestBenchmark {
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

class TestBenchmark {
  // A mix between AMD and nVidia versions, with the CND function inlined in the UserFunDef
  @Test def blackScholes(): Unit = {

    val inputSize = 512
    val input = Array.fill(inputSize)(util.Random.nextFloat())

    val gold = BlackScholes.runScala(input)

    val kernel = fun(
      ArrayType(Float, Var("N")),
      inRand => MapGlb(BlackScholes.blackScholesComp) $ inRand
    )

    val (output: Array[Float], runtime) = Execute(inputSize)(kernel, input)

    assertArrayEquals(gold, output, 0.01f)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)
  }

  @Test def kMeansMembership(): Unit = {
    val inputSize = 512
    val k = 16

    val pointsX = Array.fill(inputSize)(util.Random.nextFloat())
    val pointsY = Array.fill(inputSize)(util.Random.nextFloat())
    val centresX = Array.fill(k)(util.Random.nextFloat())
    val centresY = Array.fill(k)(util.Random.nextFloat())
    val indices = Array.range(0, k)

    val distance = UserFunDef("dist", Array("x", "y", "a", "b", "id"), "{ Tuple t = {(x - a) * (x - a) + (y - b) * (y - b), id}; return t; }", Seq(Float, Float, Float, Float, Int), TupleType(Float, Int))
    val minimum = UserFunDef("minimum", Array("x", "y"), "{ return x._0 < y._0 ? x : y; }", Seq(TupleType(Float, Int), TupleType(Float, Int)), TupleType(Float, Int))
    val getSecond = UserFunDef("getSecond", "x", "{ return x._1; }", TupleType(Float, Int), Int)

    val points = pointsX zip pointsY
    val centres = (centresX, centresY, indices).zipped

    val gold = points.map(x => {
      centres
        .map((a, b, id) => ((x._1 - a) * (x._1 - a) + (x._2 - b) * (x._2 - b), id))
        .reduce((p1, p2) => if (p1._1 < p2._1) p1 else p2)
        ._2
    })

    val N = Var("N")
    val K = Var("K")

    val function = fun(
      ArrayType(Float, N),
      ArrayType(Float, N),
      ArrayType(Float, K),
      ArrayType(Float, K),
      ArrayType(Int, K),
      (x, y, a, b, i) => {
        MapGlb(fun(xy => {
          MapSeq(getSecond) o
          toGlobal(MapSeq(idFI)) o
          ReduceSeq(minimum, (scala.Float.MaxValue, -1)) o
          MapSeq(fun(ab => {
            distance(Get(xy, 0), Get(xy, 1), Get(ab, 0), Get(ab, 1), Get(ab, 2))
          })) $ Zip(a, b, i)
        })) $ Zip(x, y)
      }
    )

    println(inputSize)
    println(k)

    val (output: Array[Int], runtime) =
      Execute(inputSize)(function, pointsX, pointsY, centresX, centresY, indices)

    assertArrayEquals(gold, output)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)
  }

  @Test def mandelbrot(): Unit = {
    val inputSize = 512

    val iterations = 100

    val input = Array.range(0, inputSize)

    val gold = input.flatMap(i => {
      input.map(j => {
        val space = 2.0f / inputSize

        var Zr = 0.0f
        var Zi = 0.0f
        val Cr = j * space - 1.5f
        val Ci = i * space - 1.0f

        var ZrN = 0.0f
        var ZiN = 0.0f
        var y = 0
        while (ZiN + ZrN <= 4.0f && y < iterations) {
          Zi = 2.0f * Zr * Zi + Ci
          Zr = ZrN - ZiN + Cr
          ZiN = Zi * Zi
          ZrN = Zr * Zr
          y += 1
        }

        (y * 255) / iterations
      })
    })

    val md = UserFunDef("md", Array("i", "j", "niters", "size"),
      """|{
         |  const float space = 2.0f / size;
         |  float Zr = 0.0f;
         |  float Zi = 0.0f;
         |  float Cr = (j * space - 1.5f);
         |  float Ci = (i * space - 1.0f);
         |  int y = 0;
         |
         |  for (y = 0; y < niters; y++) {
         |    const float ZiN = Zi * Zi;
         |    const float ZrN = Zr * Zr;
         |    if(ZiN + ZrN > 4.0f) break;
         |    Zi *= Zr;
         |    Zi *= 2.0f;
         |    Zi += Ci;
         |    Zr = ZrN - ZiN + Cr;
         |  }
         |  return ((y * 255) / niters);
         |}
         |""".stripMargin, Seq(Int, Int, Int, Int), Int)

    val f = fun(
      ArrayType(Int, Var("N")),
      Int,
      Int,
      (in, niters, size) => MapGlb(fun(i => MapSeq(fun(j => md(i, j, niters, size))) $ in)) $ in
    )

    val (output: Array[Int], runtime) = Execute(inputSize)(f, input, iterations, inputSize)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)


    assertArrayEquals(gold, output)
  }

  private def writeMD(width: Int, height: Int, data: Array[Float], name: String): Unit = {
    val out = new File(name + ".png")
    val img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val r = img.getRaster

    for (i <- 0 until height) {
      for (j <- 0 until width) {
        r.setSample(j, i, 0, data(i * height + j).toByte)
      }
    }

    try {
      ImageIO.write(img, "png", out)
    } catch {
      case e: IOException => e.printStackTrace()
    }
  }

  @Test def nBody(): Unit = {

    val inputSize = 512

    val deltaT = 0.005f
    val espSqr = 500.0f

    // x, y, z, velocity x, y, z, mass
    val input = Array.fill(inputSize)((util.Random.nextFloat(), util.Random.nextFloat(), util.Random.nextFloat(), 0.0f, 0.0f, 0.0f, util.Random.nextFloat()))

    val x = input.map(_._1)
    val y = input.map(_._2)
    val z = input.map(_._3)

    val velX = input.map(_._4)
    val velY = input.map(_._5)
    val velZ = input.map(_._6)

    val mass = input.map(_._7)

    val gold = input.map(x => {
      val acceleration = input.map(y => {
        val r = Array(0.0f, 0.0f, 0.0f)

        r(0) = x._1 - y._1
        r(1) = x._2 - y._2
        r(2) = x._3 - y._3

        val distSqr = r.sum

        val invDist = 1.0f / math.sqrt(distSqr + espSqr)

        val s = invDist * invDist * invDist * y._7

        (s * r(0), s * r(1), s * r(2))
      }).reduce((y, z) => (y._1 + z._1, y._2 + z._2, y._3 + z._3))

      val px = x._4 * deltaT + 0.5f * acceleration._1 * deltaT * deltaT
      val py = x._5 * deltaT + 0.5f * acceleration._2 * deltaT * deltaT
      val pz = x._6 * deltaT + 0.5f * acceleration._3 * deltaT * deltaT

      (x._1 + px.toFloat, x._2 + py.toFloat, x._3 + pz.toFloat, x._4 + acceleration._1.toFloat * deltaT, x._5 + acceleration._2.toFloat * deltaT, x._6 + acceleration._3.toFloat * deltaT, x._7)
    }).map(_.productIterator).reduce(_ ++ _).asInstanceOf[Iterator[Float]].toArray

    val calcAcc = UserFunDef("calcAcc", Array("x1", "y1", "z1", "x2", "y2", "z2", "mass", "espSqr"),
      """|{
         |  float4 r = (x1 - x2, y1 - y2, z1 - z2, 0.0f);
         |  float distSqr = r.x + r.y + r.z;
         |  float invDist = 1.0f / sqrt(distSqr + espSqr);
         |  float invDistCube = invDist * invDist * invDist;
         |  float s = invDistCube * mass;
         |  Tuple acc = {s * r.x, s * r.y, s * r.z};
         |  return acc;
         |}
         | """.stripMargin, Seq(Float, Float, Float, Float, Float, Float, Float, Float), TupleType(Float, Float, Float))
    val reduce = UserFunDef("reduce", Array("x", "y"), "{ Tuple t = {x._0 + y._0, x._1 + y._1, x._2 + y._2}; return t;}", Seq(TupleType(Float, Float, Float), TupleType(Float, Float, Float)), TupleType(Float, Float, Float))
    val update = UserFunDef("update", Array("x", "y", "z", "velX", "velY", "velZ", "mass", "deltaT", "acceleration"),
      """|{
         |  float px = velX * deltaT + 0.5f * acceleration._0 * deltaT * deltaT;
         |  float py = velY * deltaT + 0.5f * acceleration._1 * deltaT * deltaT;
         |  float pz = velZ * deltaT + 0.5f * acceleration._2 * deltaT * deltaT;
         |  Tuple1 t = {x + px, y + py, z + pz, velX + acceleration._0 * deltaT, velY + acceleration._1 * deltaT, velZ + acceleration._2 * deltaT, mass};
         |  return t;
         |}
      """.stripMargin, Seq(Float, Float, Float, Float, Float, Float, Float, Float, TupleType(Float, Float, Float)), TupleType(Float, Float, Float, Float, Float, Float, Float))

    val N = Var("N")

    val function = fun(
      ArrayType(Float, N),
      ArrayType(Float, N),
      ArrayType(Float, N),
      ArrayType(Float, N),
      ArrayType(Float, N),
      ArrayType(Float, N),
      ArrayType(Float, N),
      Float,
      Float,
      (x, y, z, velX, velY, velZ, mass, espSqr, deltaT) =>
        MapGlb(fun(xyz =>
          toGlobal(MapSeq(fun(acceleration =>
            update(Get(xyz, 0), Get(xyz, 1), Get(xyz, 2), Get(xyz, 3), Get(xyz, 4), Get(xyz, 5), Get(xyz, 6), deltaT, acceleration))))
            o ReduceSeq(reduce, (0.0f, 0.0f, 0.0f))
            o MapSeq(fun(abc => calcAcc(Get(xyz, 0), Get(xyz, 1), Get(xyz, 2), Get(abc, 0), Get(abc, 1), Get(abc, 2), Get(abc, 6), espSqr)))
            $ Zip(x, y, z, velX, velY, velZ, mass)
        )) $ Zip(x, y, z, velX, velY, velZ, mass)
    )

    val (output: Array[Float], runtime) =
      Execute(inputSize)(function, x, y, z, velX, velY, velZ, mass, espSqr, deltaT)

    assertArrayEquals(gold, output, 0.0001f)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)
  }

  @Test def md(): Unit = {

    val inputSize = 1024
    val maxNeighbours = 128

    val particlesTuple = Array.fill(inputSize)((
      util.Random.nextFloat() * 20.0f,
      util.Random.nextFloat() * 20.0f,
      util.Random.nextFloat() * 20.0f,
      util.Random.nextFloat() * 20.0f
      ))
    val particles = particlesTuple.map(_.productIterator).reduce(_ ++ _).asInstanceOf[Iterator[Float]].toArray
    val neighbours = MolecularDynamics.buildNeighbourList(particlesTuple, maxNeighbours)
    val cutsq = 16.0f
    val lj1 = 1.5f
    val lj2 = 2.0f

    val gold = MolecularDynamics.mdScala(particlesTuple, neighbours.transpose, cutsq, lj1, lj2).map(_.productIterator).reduce(_ ++ _).asInstanceOf[Iterator[Float]].toArray

    val N = new Var("N")
    val M = new Var("M")

    val f = fun(
      ArrayType(Float4, N),
      ArrayType(ArrayType(Int, M), N),
      Float,
      Float,
      Float,
      (particles, neighbourIds, cutsq, lj1, lj2) =>
        MapGlb(fun(p =>
          toGlobal(MapSeq(Vectorize(4)(id))) o
            ReduceSeq(fun((force, n) =>
              MolecularDynamics.mdCompute.apply(force, Get(p, 0), n, cutsq, lj1, lj2)
            ), Value(0.0f, Float4)) $ Filter(particles, Get(p, 1))
        )) $ Zip(particles, neighbourIds)
    )

    val (output: Array[Float], runtime) =
      Execute(inputSize)(f, particles, neighbours, cutsq, lj1, lj2)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertEquals(output.length, gold.length)

    (output, gold).zipped.map((x, y) => {

      var diff = (x - y) / x

      if (x == 0.0f)
        diff = 0.0f

      math.sqrt(diff * diff).toFloat
    }).zipWithIndex.foreach(x => assertEquals("Error at pos " + x._2, 0.0f, x._1, 0.1f))

  }

  @Test def mdShoc(): Unit = {

    val inputSize = 1024
    val maxNeighbours = 128

    val particlesTuple = Array.fill(inputSize)((
      util.Random.nextFloat() * 20.0f,
      util.Random.nextFloat() * 20.0f,
      util.Random.nextFloat() * 20.0f,
      util.Random.nextFloat() * 20.0f
      ))
    val particles = particlesTuple.map(_.productIterator).reduce(_ ++ _).asInstanceOf[Iterator[Float]].toArray
    val neighbours = MolecularDynamics.buildNeighbourList(particlesTuple, maxNeighbours).transpose
    val cutsq = 16.0f
    val lj1 = 1.5f
    val lj2 = 2.0f

    val gold = MolecularDynamics.mdScala(particlesTuple, neighbours, cutsq, lj1, lj2)
               .map(_.productIterator).reduce(_ ++ _).asInstanceOf[Iterator[Float]].toArray

    val (output: Array[Float], runtime) =
      Execute(inputSize)(MolecularDynamics.shoc, particles, neighbours, cutsq, lj1, lj2)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertEquals(output.length, gold.length)

    (output, gold).zipped.map((x, y) => {

      var diff = (x - y) / x

      if (x == 0.0f)
        diff = 0.0f

      math.sqrt(diff * diff).toFloat
    }).zipWithIndex.foreach(x => assertEquals("Error at pos " + x._2, 0.0f, x._1, 0.1f))

  }

  // Compute single precision a * x + y
  @Test def saxpy(): Unit = {
    // domain size
    val inputSize = 128
    val N = Var("N")

    // Input variables
    val a: Float = 5.0f
    val xs = Array.fill(inputSize)(util.Random.nextFloat())
    val ys = Array.fill(inputSize)(util.Random.nextFloat())

    // Cross validation
    val gold = (xs.map(_ * a), ys).zipped map (_ + _)

    // user function
    val fct = UserFunDef("saxpy", Array("a", "x", "y"),
      " return a * x + y; ",
      Seq(Float, Float, Float), Float)

    // Expression
    val f = fun(
      Float,
      ArrayType(Float, N),
      ArrayType(Float, N),
      (a, xs, ys) => MapGlb(
        fun(xy => fct(
          a, Get(xy, 0), Get(xy, 1)
        ))
      ) $ Zip(xs, ys)
    )

    // execute
    val (output: Array[Float], runtime) = Execute(inputSize)(f, a, xs, ys)

    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.001f)
  }
}
