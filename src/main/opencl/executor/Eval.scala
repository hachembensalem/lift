package opencl.executor

import ir.Lambda
import scala.reflect.runtime._
import scala.tools.reflect.ToolBox

/**
 * Uses Scala reflection API to evaluate a String containing Scala code
 * This object assumes that the given String evaluates to on object of Type Lambda
 *
 * This object can be used to interface with other languages, like C, or compile source code stored in a file
 */
object Eval {
  def apply(code: String): Lambda = {
    val mirror = universe.runtimeMirror(getClass.getClassLoader)
    val tb = mirror.mkToolBox()
    val tree = tb.parse(s"""
                          |import arithmetic._
                          |import ir._
                          |import opencl.ir._
                          |${code}
                         """.stripMargin)
    tb.eval(tree).asInstanceOf[Lambda]
  }
}
