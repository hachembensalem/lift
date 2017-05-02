import java.nio.file.Files._
import java.nio.file.Paths._
import java.util.Calendar

import ir.ast.UserFun
import opencl.executor.Executor
import opencl.ir._

import scala.util.parsing.json.JSON

/**
  * Created by s1569687 on 28/02/17.
  */
package object nn {
  /* Types and data structures */

  case class Shape(in: Int = 0, out: Int = 0,
                   w: Int = 0, h: Int = 0,
                   l0: Int = 0, l1: Int = 0,
                   ch: Int = 0,
                   var wPadded: Int = 0,
                   var hPadded: Int = 0) {
    def wNonPadded: Int = w
    def hNonPadded: Int = h

    @throws(classOf[java.lang.UnsupportedOperationException])
    def s: Int = if (w == h) w else {
      throw new java.lang.UnsupportedOperationException
      0
    }

    def sPadded: Int = if (wPadded == hPadded) wPadded else {
      throw new java.lang.UnsupportedOperationException
      0
    }

//    def setSPadded(sNew: Int): Unit = if (wPadded == hPadded) {
//      wPadded = sNew
//      hPadded = sNew
//    } else throw new java.lang.UnsupportedOperationException
  }

  case class PaddedArray[T](var nonPadded: T) {
    var padded: T = _
  }

  type Array2D[T] = Array[Array[T]]
  type Array3D[T] = Array[Array[Array[T]]]
  type Array4D[T] = Array[Array[Array[Array[T]]]]
  type Array5D[T] = Array[Array[Array[Array[Array[T]]]]]
  type Array6D[T] = Array[Array[Array[Array[Array[Array[T]]]]]]


  /* Variables */

  val localMemSize: Int = Executor.getDeviceLocalMemSize.toInt
  val maxWorkGroupSize: Int = Executor.getDeviceMaxWorkGroupSize.toInt
  val deviceName: String = Executor.getDeviceName


  /* Functions */

  // Activation functions
  val ReLU: UserFun = UserFun("ReLU", "x", "{ return(max(0.0f, x)); }", Float, Float)
  val Linear: UserFun = id

  def loadJSON5D(json_file_path: String): Array5D[Float] = {
    /* Load an array from a JSON file */
    val source = scala.io.Source.fromFile(json_file_path)
    val jsonString = source.getLines mkString "\n"
    source.close()
    val json:Option[Any] = JSON.parseFull(jsonString)
    val w_list: List[List[List[List[List[Double]]]]] =
      json.get.asInstanceOf[List[List[List[List[List[Double]]]]]]

    // Convert from List[List[Double]] to Array2D[Float]
    val w_arr = Array.fill[Array4D[Float]](w_list.length)(
      Array.fill[Array3D[Float]](w_list.head.length)(
        Array.fill[Array2D[Float]](w_list.head.head.length)(
          Array.fill[Array[Float]](w_list.head.head.head.length)(
            Array.fill[Float](w_list.head.head.head.head.length)(0)))))

    var aline = Array.fill[Double](w_list.head.length)(0)
    for (i <- w_list.indices) {
      for (j <- w_list(i).indices) {
        for (k <- w_list(i)(j).indices) {
          for (l <- w_list(i)(j)(k).indices) {
            aline = w_list(i)(j)(k)(l).to[Array]
            for (m <- aline.indices) {
              w_arr(i)(j)(k)(l)(m) = aline(m).toFloat
            }
          }
        }
      }
    }
    w_arr
  }

  def loadJSON4D(json_file_path: String): Array4D[Float] = {
    /* Load an array from a JSON file */
    val source = scala.io.Source.fromFile(json_file_path)
    val jsonString = source.getLines mkString "\n"
    source.close()
    val json:Option[Any] = JSON.parseFull(jsonString)
    val w_list: List[List[List[List[Double]]]] = json.get.asInstanceOf[List[List[List[List[Double]]]]]

    // Convert from List[List[Double]] to Array2D[Float]
    val w_arr = Array.fill[Array3D[Float]](w_list.length)(
      Array.fill[Array2D[Float]](w_list.head.length)(
        Array.fill[Array[Float]](w_list.head.head.length)(
          Array.fill[Float](w_list.head.head.head.length)(0))))

    var aline = Array.fill[Double](w_list.head.length)(0)
    for (i <- w_list.indices) {
      for (j <- w_list(i).indices) {
        for (k <- w_list(i)(j).indices) {
          aline = w_list(i)(j)(k).to[Array]
          for (l <- aline.indices) {
            w_arr(i)(j)(k)(l) = aline(l).toFloat
          }
        }
      }
    }
    w_arr
  }

  def loadJSON3D(json_file_path: String): Array3D[Float] = {
    /* Load an array from a JSON file */
    val source = scala.io.Source.fromFile(json_file_path)
    val jsonString = source.getLines mkString "\n"
    source.close()
    val json:Option[Any] = JSON.parseFull(jsonString)
    val w_list: List[List[List[Double]]] = json.get.asInstanceOf[List[List[List[Double]]]]

    // Convert from List[List[Double]] to Array2D[Float]
    val w_arr = Array.fill[Array2D[Float]](w_list.length)(
      Array.fill[Array[Float]](w_list.head.length)(
        Array.fill[Float](w_list.head.head.length)(0)))
    var aline = Array.fill[Double](w_list.head.length)(0)
    for (i <- w_list.indices) {
      for (j <- w_list(i).indices) {
        aline = w_list(i)(j).to[Array]
        for (k <- aline.indices) {
          w_arr(i)(j)(k) = aline(k).toFloat
        }
      }
    }
    w_arr
  }

  def loadJSON2D(json_file_path: String): Array2D[Float] = {
    /* Load an array from a JSON file */
    val source = scala.io.Source.fromFile(json_file_path)
    val jsonString = source.getLines mkString "\n"
    source.close()
    val json:Option[Any] = JSON.parseFull(jsonString)
    val w_list: List[List[Double]] = json.get.asInstanceOf[List[List[Double]]]

    // Convert from List[List[Double]] to Array2D[Float]
    val w_arr = Array.fill[Array[Float]](w_list.length)(Array.fill[Float](w_list.head.length)(0))
    var aline = Array.fill[Double](w_list.head.length)(0)
    for (i <- w_list.indices) {
      aline = w_list(i).to[Array]
      for (j <- aline.indices) {
        w_arr(i)(j) = aline(j).toFloat
      }
    }
    w_arr
  }

  def loadJSON1D(json_file_path: String): Array[Float] = {
    /* Load an array from a JSON file */
    val source = scala.io.Source.fromFile(json_file_path)
    val jsonString = source.getLines mkString "\n"
    source.close()
    val json:Option[Any] = JSON.parseFull(jsonString)
    val w_list: List[Double] = json.get.asInstanceOf[List[Double]]

    // Convert from List[List[Double]] to Array2D[Float]
    val w_arr = Array.fill[Float](w_list.length)(0)
    var aline = Array.fill[Double](w_list.length)(0)
    aline = w_list.to[Array]
    for (i <- aline.indices) {
      w_arr(i) = aline(i).toFloat
    }
    w_arr
  }

  var runnerIsConsole: Boolean = false

  val nnDir: String = {
    // Launching from IntelliJ or from console?
    val intellij_path = System.getProperty("user.dir") + "/../../src/test/nn"
    val console_path = System.getProperty("user.dir") + "/src/test/nn"
    if (exists(get(intellij_path)))
      intellij_path
    else {
      runnerIsConsole = true
      console_path
    }
  }

  def resultsFilename(exp_dir_name: String, n_inputs: Int): String = {
    val now = Calendar.getInstance()
    new String(exp_dir_name + "/results_lift/" +
      "%02d.%02d.%04d-%02d.%02d.%02d.%03d_n%d.csv".format(
        now.get(Calendar.DATE), now.get(Calendar.MONTH), now.get(Calendar.YEAR),
        now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND),
        now.get(Calendar.MILLISECOND), n_inputs))
  }

  def group(arr1d: Array[Float], shape: (Int, Int)): Array2D[Float] = {
    val arr2d = Array.fill[Array[Float]](shape._1)(
      Array.fill[Float](shape._2)(0))
    for (i <- 0 until shape._1; j <- 0 until shape._2) {
      arr2d(i)(j) = arr1d(i * shape._2 + j)
    }
    arr2d
  }

  def group(arr1d: Array[Float], shape: (Int, Int, Int)): Array3D[Float] = {
    val arr3d = Array.fill[Array2D[Float]](shape._1)(
      Array.fill[Array[Float]](shape._2)(
        Array.fill[Float](shape._3)(0)))
    for (i <- 0 until shape._1; j <- 0 until shape._2; k <- 0 until shape._3) {
      arr3d(i)(j)(k) = arr1d(i * shape._2 * shape._3 + j * shape._3 + k)
    }
    arr3d
  }

  def group(arr1d: Array[Float], shape: (Int, Int, Int, Int, Int)): Array5D[Float] = {
    val arr5d = Array.fill[Array4D[Float]](shape._1)(
      Array.fill[Array3D[Float]](shape._2)(
        Array.fill[Array2D[Float]](shape._3)(
          Array.fill[Array[Float]](shape._4)(
            Array.fill[Float](shape._5)(0)))))
    for (i <- 0 until shape._1; j <- 0 until shape._2; k <- 0 until shape._3;
         l <- 0 until shape._4; m <- 0 until shape._5) {
      arr5d(i)(j)(k)(l)(m) = arr1d(i * shape._2 * shape._3 * shape._4 * shape._5 +
        j * shape._3 * shape._4 * shape._5 + k * shape._4 * shape._5 + l * shape._5 + m)
    }
    arr5d
  }
}
