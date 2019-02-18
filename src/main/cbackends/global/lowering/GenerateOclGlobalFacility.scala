package cbackends.global.lowering

import java.util.function.BinaryOperator

import cbackends.host.host_ir.{OclFunCall, ToGPU, ToHost}
import core.generator.GenericAST.{AssignmentExpression, BinaryExpression, BinaryExpressionT, Block, CVarWithType, ClassOrStructType, FunctionCall, FunctionPure, IfThenElseIm, IntConstant, MethodInvocation, RawCode, StringConstant, VarDeclPure, VoidType}
import ir.ast.{Expr, FunCall, Lambda}

object GenerateOclGlobalFacility {

  def generate(expr: Expr, global_decl_cast: Block, global_init_cast: Block): (Block, Block) = {

    expr match {

      case fc@FunCall(_: OclFunCall, args@_*) =>
        //construct global declaration
        val kernel_string_cvar = CVarWithType("kernel_string_" + fc.gid, ClassOrStructType("std::string"))
        val kernel_string_decl = VarDeclPure(kernel_string_cvar, kernel_string_cvar.t)
        val kernel_source_cvar = CVarWithType("kernel_source_" + fc.gid, ClassOrStructType("cl::Program::Sources"))
        val kernel_source_decl = VarDeclPure(kernel_source_cvar, kernel_source_cvar.t)
        val kernel_program_cvar = CVarWithType("kernel_program_" + fc.gid, ClassOrStructType("cl::Program"))
        val kernel_program_decl = VarDeclPure(kernel_program_cvar, kernel_program_cvar.t)
        val kernel_cvar = CVarWithType("kernel_" + fc.gid, ClassOrStructType("cl::Kernel"))
        val kernel_cvar_decl = VarDeclPure(kernel_cvar, kernel_cvar.t)

        val global_decl_for_this_call = Block(Vector(
          kernel_string_decl, kernel_source_decl, kernel_program_decl, kernel_cvar_decl
        ))

        //construct global init
        val kernel_string_init = AssignmentExpression(kernel_string_cvar, FunctionCall("readFile", List(StringConstant('"' + "kernel_" + fc.gid + ".cl" + '"'))))
        val kernel_source_init = AssignmentExpression(kernel_source_cvar, FunctionCall("cl::Program::Sources", List(IntConstant(1),
          FunctionCall("std::make_pair", List(
            MethodInvocation(kernel_string_cvar, "c_str", List()),
            MethodInvocation(kernel_string_cvar, "length", List())
          ))
        )))
        val kernel_program_init = AssignmentExpression(kernel_program_cvar, FunctionCall("cl::Program", List( StringConstant("context") , kernel_source_cvar)))
        val kernel_build_statement = IfThenElseIm(
          BinaryExpression(
            MethodInvocation(kernel_program_cvar, "build", List(StringConstant("{ context }"))),
            BinaryExpressionT.Operator.!=,
            StringConstant("CL_SUCCESS")
          ),
          Block(Vector(RawCode("std::cerr<<"+'"'+"kernel build error"+'"'+"std::endl; exit(1);"))),
          Block()
        )
        val kernel_init = AssignmentExpression(
          kernel_cvar, FunctionCall("cl::Kernel", List(kernel_program_cvar, StringConstant('"'+"KERNEL"+'"')))
        )

        val global_init_for_this_call = Block(Vector(
          kernel_string_init, kernel_source_init, kernel_program_init, kernel_build_statement, kernel_init
        ))

        (global_decl_cast :++ global_decl_for_this_call, global_init_cast :++ global_init_for_this_call)

      case FunCall(_:ToHost|_:ToGPU, arg) => generate(arg, global_decl_cast, global_init_cast)
      case _ => assert(false, "Some other patterns appear in host expression but not implemented, please implement."); (Block(), Block())
    }
  }


  def apply(lambda: Lambda) : (Block, Block) = {


    val (global_decl_cast, global_init_cast) = generate(lambda.body, Block(global = true), Block(global=true))

    val global_init_function = Block(Vector(FunctionPure("lift_init", VoidType(), List(), global_init_cast) ), global = true)

    val global_decl_boilerplates = RawCode(
      """
        |int platformId = 0;
        |int deviceId = 0;
        |
        | std::vector<cl::Platform> allPlatforms;
        | cl::Platform platform;
        | std::vector<cl::Device> allDevices;
        | cl::Device device;
        | cl::Context context;
        | cl::CommandQueue queue;

      """.stripMargin)
    val global_decl_final = global_decl_boilerplates +: global_decl_cast

    (global_decl_final, global_init_function)

  }

}