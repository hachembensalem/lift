package nn.mlp

/**
  * Created by Naums Mogers in July 2016.
  * This file implements the Lift version of the Multilayer Perceptron forward-propagation.
  */

import apart.arithmetic.SizeVar
import ir.ArrayType
import ir.ast._
import opencl.ir._
import opencl.ir.pattern._
import org.junit.Assert._
import opencl.executor.{Execute, Executor}
import org.junit.{AfterClass, BeforeClass, Test}
import scala.util.parsing.json._

object TestMLP {
  @BeforeClass def before(): Unit = {
    Executor.loadLibrary()
    println("Initialize the executor")
    Executor.init()
  }

  @AfterClass def after(): Unit = {
    println("Shutdown the executor")
    Executor.shutdown()
  }
}

class TestMLP {

  def load_2d_float_json(json_file_name: String): Array[Array[Float]] = {
    /* Load an array from a JSON file */

    val source = scala.io.Source.fromFile(System.getProperty("user.dir") + "/../../src/test/nn/mlp/" + json_file_name)
    val jsonString = source.getLines mkString "\n"
    source.close()
    val json:Option[Any] = JSON.parseFull(jsonString)
    var w_list:List[List[Double]] = json.get.asInstanceOf[List[List[Double]]]

    // Convert from List[List[Double]] to Array[Array[Float]]
    var i = 0
    var w_arr = Array.fill[Array[Float]](w_list.length)(Array.fill[Float](w_list.head.length)(0))
    var aline = Array.fill[Double](w_list.head.length)(0)
    var j = 0
    for (i <- w_list.indices) {
      aline = w_list(i).to[Array]
      for (j <- aline.indices) {
        w_arr(i)(j) = aline(j).toFloat
      }
    }
    w_arr // return
  }

  val layer_idim = SizeVar("layer_idim")
  val layer_odim = SizeVar("layer_odim")
  val idim = SizeVar("idim")
  val hdim1 = SizeVar("hdim1")
  val hdim2 = SizeVar("hdim2")
  val odim = SizeVar("odim")

  def f_layer_seq = fun(
    ArrayType(ArrayType(Float, layer_idim), layer_odim),
    ArrayType(Float, layer_odim),
    ArrayType(Float, layer_idim),
    (W_layer, b_layer, X) => {
      Join() o MapSeq(fun((ws_per_neuron, b_per_neuron) => {
        toGlobal(MapSeq(id)) o
          ReduceSeq(add, id(b_per_neuron)) o
          MapSeq(mult) $
          Zip(X, ws_per_neuron)
      })) $ Zip(W_layer, b_layer)
    }
  )

  val mlp_fprop_seq = fun(
    ArrayType(ArrayType(Float, idim), hdim1), ArrayType(Float, hdim1),
    ArrayType(ArrayType(Float, hdim1), hdim2), ArrayType(Float, hdim2),
    ArrayType(ArrayType(Float, hdim2), odim), ArrayType(Float, odim),
    ArrayType(Float, idim),
    (W1, b1, W2, b2, Wout, bout, X) => {
      f_layer_seq(Wout, bout, f_layer_seq(W2, b2, f_layer_seq(W1, b1, X)))}
  )

  // Test values
  val input_W1 = Array(Array(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f),
                       Array(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.0f),
                       Array(0.2f, 0.3f, 0.4f, 0.5f, 0.0f, 0.1f),
                       Array(0.3f, 0.4f, 0.5f, 0.0f, 0.1f, 0.2f))

  val input_b1 = Array(0.1f, 0.1f, 0.1f, 0.1f)
  val input_W2 = Array(Array(0.0f, 0.1f, 0.2f, 0.3f),
                       Array(0.1f, 0.2f, 0.3f, 0.0f),
                       Array(0.2f, 0.3f, 0.0f, 0.1f),
                       Array(0.3f, 0.0f, 0.1f, 0.2f),
                       Array(0.0f, 0.1f, 0.2f, 0.3f),
                       Array(0.1f, 0.2f, 0.3f, 0.0f),
                       Array(0.2f, 0.3f, 0.0f, 0.1f),
                       Array(0.3f, 0.0f, 0.1f, 0.2f))
  val input_b2 = Array(0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f)
  val input_Wout = Array(Array(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f),
                         Array(0.1f, 0.2f, 0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f),
                         Array(0.2f, 0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f),
                         Array(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.0f, 0.1f, 0.2f),
                         Array(0.4f, 0.5f, 0.6f, 0.7f, 0.0f, 0.1f, 0.2f, 0.3f),
                         Array(0.5f, 0.6f, 0.7f, 0.0f, 0.1f, 0.2f, 0.3f, 0.4f))
  val input_bout = Array(0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f)
  val input_X = Array(6f, 7f, 8f, 9f, 5f, 4f)
  val gold = Array(17.492f, 11.356f, 14.406f, 17.636f, 17.492f, 17.732f)

  @Test
  def MLPSequential(): Unit = {
    val (lift_result: Array[Float], runtime) = Execute(1,1)(mlp_fprop_seq, input_W1, input_b1, input_W2,
                                                              input_b2, input_Wout, input_bout, input_X)
    println(f"1. Everything sequential, runtime: $runtime%1.5f ms")
    //println(lift_result.mkString(", "))
    //println(gold.mkString(", "))

    assertArrayEquals(gold, lift_result, 0.0001f)
    //val W1 = load_2d_float_json("W1.json")
    //val test_images = load_2d_float_json("test_images.json")
  }

  @Test
  def MLPThreeSeqKernels(): Unit = {
    val (output_layer1: Array[Float], runtime_layer1) = Execute(1,1)(f_layer_seq, input_W1, input_b1, input_X)
    val (output_layer2: Array[Float], runtime_layer2) = Execute(1,1)(f_layer_seq, input_W2, input_b2, output_layer1)
    val (lift_result: Array[Float], runtime_layerout) = Execute(1,1)(f_layer_seq, input_Wout, input_bout, output_layer2)
    println(f"2. 3 sequential kernels, runtime: $runtime_layer1%1.5f + $runtime_layer2%1.5f + $runtime_layerout%1.5f = " +
            f"${runtime_layer1 + runtime_layer2 + runtime_layerout}%1.5f ms")

    assertArrayEquals(gold, lift_result, 0.0001f)
  }

  def f_layer(tile_per_neuron_size: Int, tile_per_mult_op_size: Int) = fun(
    ArrayType(ArrayType(Float, layer_idim), layer_odim),
    ArrayType(Float, layer_odim),
    ArrayType(Float, layer_idim),
    (W, B, X) => {
      Join() o MapWrg(MapSeq(fun((ws_per_neuron, b_per_neuron) => {
        toGlobal(MapSeq(id)) o ReduceSeq(add, id(b_per_neuron)) o Join() o MapLcl(
          toLocal(MapSeq(id)) o ReduceSeq(add, 0.0f) o MapSeq(mult)) o
        Split(tile_per_mult_op_size) $ Zip(X, ws_per_neuron)
      }))) o Split(tile_per_neuron_size) $ Zip(W, B)
    }
  )

  @Test
  def MLPThreeSplitKernels(): Unit = {
    val (output_layer1: Array[Float], runtime_layer1) =
      Execute(2, 12)(f_layer(tile_per_neuron_size=1, tile_per_mult_op_size=2), input_W1, input_b1, input_X)
    val (output_layer2: Array[Float], runtime_layer2) =
      Execute(2, 8)(f_layer(tile_per_neuron_size=2, tile_per_mult_op_size=2), input_W2, input_b2, output_layer1)
    val (lift_result: Array[Float], runtime_layerout) =
      Execute(2, 12)(f_layer(tile_per_neuron_size=2, tile_per_mult_op_size=2), input_Wout, input_bout, output_layer2)
    println(f"3. 3 parallel kernels, runtime: $runtime_layer1%1.5f + $runtime_layer2%1.5f + $runtime_layerout%1.5f = " +
      f"${runtime_layer1 + runtime_layer2 + runtime_layerout}%1.5f ms")
    println(output_layer1.mkString(", "))
    println(output_layer2.mkString(", "))
    println(lift_result.mkString(", "))

    assertArrayEquals(gold, lift_result, 0.0001f)
  }
}
