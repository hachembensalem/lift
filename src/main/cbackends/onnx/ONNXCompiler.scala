package cbackends.onnx

import cbackends.common.CBackendsCompilerTrait
import cbackends.common.common_cast.CbackendCAST.SourceFile
import cbackends.onnx.lowering.LoweringONNXIR2LiftIR
import core.generator.GenericAST.{Block, CVarWithType}
import ir.ast.Lambda
import lift.arithmetic.ArithExpr

object ONNXCompiler extends CBackendsCompilerTrait{

  override def lowerIR2CAST(lambda: Lambda,
                            memoryDeclaredInSignature: Map[String, (CVarWithType, ArithExpr)],
                            path: String,
                            files: List[String]
                           ): List[SourceFile] = {

    List(new SourceFile(path, files(0), Block() ) )

  }

  //compile a lambda
  override def !(onnx_ir: Lambda, path: String, files: List[String]): Unit = {

    val lift_ir = LoweringONNXIR2LiftIR(onnx_ir)

    /*
    println("1.traditional compiler process")

    typeCheck(lambda)
    memorySpaceInference(lambda)
    loopVarInference(lambda)
    memoryAlloc(lambda)
    val finalMemoryAllocated = finalMemoryAllocationAnalysis(lambda)
    inputView(lambda)
    outputView(lambda)

    val listOfSourceFiles = lowerIR2CAST(lambda, finalMemoryAllocated, path, files)

    castPrinter(listOfSourceFiles) */

    println("n.compiler done")

  }

}