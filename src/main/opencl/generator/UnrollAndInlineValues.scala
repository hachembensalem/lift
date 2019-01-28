package opencl.generator

import core.generator.GenericAST
import core.generator.GenericAST.{ArithExpression, AssignmentExpression, AstNode, BlockMember, CVar, ExpressionStatement, FunctionCall, IfThenElse, MutableBlock, StructConstructor, TernaryExpression, VarRef}
import ir._
import lift.arithmetic.Var
import opencl.generator.OpenCLAST.{OclStore, OclVarDecl, VectorLiteral}
import opencl.ir.PrivateMemory

import scala.collection.mutable._

/**
  * Class for functionality to unroll arrays or inline structs in private memory in the AST
  */

object UnrollValues {

  // anonymous identity function
  val idPostFun = (n: AstNode) => n

  // until there is a better way: get first index from suffix and return the resulting suffix string
  def getIndexSuffix(str: String): (Int, String) = {
    val idx = str.split("_|\\.").filter(_.nonEmpty).lift(0).getOrElse("-1")
    if (idx == "-1") // is there a nicer way to do this ?
    {
      (idx.toInt, "")
    }
    else {
      var suffix = str.split("_", 2).filter(_.nonEmpty)(0).split(idx, 2).mkString
      if (suffix == ".") {
        suffix = "";
      } // TODO: probably a nicer way to do this?
      (idx.toInt, suffix)
    }
  }

  // sometimes need to recreate the tuple we have unrolled ( ie. putting it into global memory )
  def recreateStruct(arr: Array[OclVarDecl], ai: Option[ArithExpression], tt: TupleType): StructConstructor = {
    var varList = Vector[AstNode]()
    // loop over array of oclVarDecls
    for (ocl <- arr) {
      // pull out and create Vector of AstNodes (VarRefs)
      val vR = VarRef(v = ocl.v, arrayIndex = ai)
      varList = varList :+ vR
    }
    StructConstructor(tt, varList)
  }

  // Create a new VarRef referring to the correct unrolled value
  def getCorrectVarRef(v: CVar,
                       s: Option[String] ,
                       ai: Option[ArithExpression],
                      oclVarDeclMap: ListMap[CVar, Array[OclVarDecl]]
                      ): VarRef =
  {
    var vr = VarRef(v, s, ai)
    if (oclVarDeclMap.contains(v))
    {
      val idxSuffix = getIndexSuffix(s.getOrElse(throw new Exception("Unable to find index for " + v.v.name)))
      val ocl = oclVarDeclMap(v)(idxSuffix._1)
      vr = VarRef(ocl.v, Some(idxSuffix._2), ai)
    }
    vr
  }

  def getVarRefList(args: List[GenericAST.AstNode],oclVarDeclMap: ListMap[CVar, Array[OclVarDecl]]) : List[GenericAST.AstNode] =
  {
    var lst = List[GenericAST.AstNode]()
    for (arg <- args)
    {
      arg match
      {
        case VarRef(v_b, s_b, ai_b) =>
          var vrH = getCorrectVarRef(v_b,s_b,ai_b,oclVarDeclMap)
          lst = lst :+ vrH
        case StructConstructor(t, args) =>
          var newargs = Vector[GenericAST.AstNode]()
          for (arg <- args)
          {
            arg match
            {
              case VarRef(v_b, s_b, ai_b) =>
                var vrL = getCorrectVarRef(v_b,s_b,ai_b,oclVarDeclMap)
                newargs = newargs :+ vrL
              case _ =>
                newargs = newargs :+ arg
            }
          }
          lst = lst :+ StructConstructor(t, newargs)

        case an: AstNode => lst = lst :+ an
        case _ =>
          lst = lst :+ arg
      }
    }
    lst
  }

  def unrollPrivateMemoryArrayValues(node: AstNode): AstNode =
  {
    // map to keep track of the unrolled variables from private memory arrays
    var oclVarDeclMap = ListMap[CVar, Array[OclVarDecl]]()

    val preFunctionForUnrollingArrays = (n: AstNode) => n match {
      case mb: MutableBlock =>
        // create new vector for our mb
        var nodeVector = Vector[AstNode with BlockMember]()
        mb.content.map(node =>
          node match {
            // if it's a variable declaration:
            case ovd: OclVarDecl =>
              ovd.addressSpace match
              {
                case PrivateMemory =>
                  ovd.t match
                  {
                    case ArrayType(ty) =>
                      // loop over size of array and create new OclVarDecls for each "unrolled value"
                      oclVarDeclMap += (ovd.v -> Array[OclVarDecl]())
                      for (i <- 1 to ovd.length.toInt)
                      {
                        var oclVDtmp = OclVarDecl(CVar(Var(ovd.v.v.name + "_" + i)), ty, ovd.init, 0, PrivateMemory)
                        // push them back in new vector
                        nodeVector = nodeVector :+ oclVDtmp
                        // and add them to our "map" to reference later
                        oclVarDeclMap += (ovd.v -> (oclVarDeclMap(ovd.v) :+ oclVDtmp))
                      }
                   /* case ArrayTypeWS => */ // TODO: Add functionality
                   /* case ArrayTypeWSWC => */ // TODO: Add functionality
                   /* case ArrayTypeWC => */ // TODO: Add functionality
                    case _ => nodeVector = nodeVector :+ ovd
                  }
                case _ => nodeVector = nodeVector :+ ovd
              }
            // otherwise just push back in new Vector
            case an: AstNode => nodeVector = nodeVector :+ an
          }
        )
        // return block with new vector values
        MutableBlock(nodeVector, mb.global)
      case ExpressionStatement(e) => e match
      {
        case AssignmentExpression(lhs, rhs) => (lhs, rhs) match
        {
          case (VarRef(v1, s1, ai1), VarRef(v2, s2, ai2)) =>
            if (oclVarDeclMap.contains(v1) && !oclVarDeclMap.contains(v2))
            {
              val idxSuffix = getIndexSuffix(s1.getOrElse(throw new Exception("Unable to find index for " + v1.v.name)))
              // need to update the variable for v1, v2 stays the same
              val lhsOcl = oclVarDeclMap(v1)(idxSuffix._1)
              val lhs = VarRef(lhsOcl.v, Some(idxSuffix._2), ai1)
              val rhs = VarRef(v2, s2, ai2)
              ExpressionStatement(AssignmentExpression(lhs, rhs))
            }
            else if (oclVarDeclMap.contains(v2) && !oclVarDeclMap.contains(v1))
            {
              // need to update the variable for v2, v1 stays the same
              val idxSuffix = getIndexSuffix(s2.getOrElse(throw new Exception("Unable to find index for " + v1.v.name)))
              val lhs = VarRef(v2, s2, ai2)
              val rhsOcl = oclVarDeclMap(v2)(idxSuffix._1)
              val rhs = VarRef(rhsOcl.v, s2, ai2)
              ExpressionStatement(AssignmentExpression(lhs, rhs))
            }
            else if (oclVarDeclMap.contains(v1) && oclVarDeclMap.contains(v2))
            {
              val idxSuffix1 = getIndexSuffix(s1.getOrElse(throw new Exception("Unable to find index for " + v1.v.name)))
              val lhsOcl = oclVarDeclMap(v1)(idxSuffix1._1)
              val lhs = VarRef(lhsOcl.v, Some(idxSuffix1._2), ai1)
              val idxSuffix2 = getIndexSuffix(s2.getOrElse(throw new Exception("Unable to find index for " + v1.v.name)))
              val rhsOcl = oclVarDeclMap(v2)(idxSuffix2._1)
              val rhs = VarRef(rhsOcl.v, Some(idxSuffix2._2), ai2)
              ExpressionStatement(AssignmentExpression(lhs, rhs))
            }
            else // nothing to be unrolled - yay!
            {
              ExpressionStatement(AssignmentExpression(VarRef(v1, s1, ai1), VarRef(v2, s2, ai2)))
            }
          case (VarRef(v, s, ai), FunctionCall(f, args)) =>
            val vr = getCorrectVarRef(v,s,ai,oclVarDeclMap)
            // update args list with new values of VarRefs
            val lst = getVarRefList(args,oclVarDeclMap)
            ExpressionStatement(AssignmentExpression(vr, FunctionCall(f, lst)))
          case (VarRef(v, s, ai), TernaryExpression(cond, trueExpr, falseExpr)) =>
            val vr = getCorrectVarRef(v,s,ai,oclVarDeclMap)
            ExpressionStatement(AssignmentExpression(vr, TernaryExpression(cond, trueExpr, falseExpr)))

          case (VarRef(v, s, ai), StructConstructor(t, args)) =>
            val vr = getCorrectVarRef(v,s,ai,oclVarDeclMap)
            ExpressionStatement(AssignmentExpression(vr, StructConstructor(t, args)))
          case _ => ExpressionStatement(e)
        }
        case _ => ExpressionStatement(e)
      }
      case StructConstructor(t, args) =>
        var newargs = Vector[GenericAST.AstNode]()
        for (arg <- args) {
          arg match {
            case VarRef(v_b, s_b, ai_b) =>
              var vr = getCorrectVarRef(v_b,s_b,ai_b,oclVarDeclMap)
              newargs = newargs :+ vr
            case _ =>
              newargs = newargs :+ arg
          }
        }
        StructConstructor(t, newargs)

      case v: VectorLiteral =>
        var newargs = List[GenericAST.VarRef]()
        for (arg <- v.vs)
        {
          arg match {
            case VarRef(v_b, s_b, ai_b) =>
              var vr = getCorrectVarRef(v_b,s_b,ai_b,oclVarDeclMap)
              newargs = newargs :+ vr
            case _ =>
              newargs = newargs :+ arg
          }
        }
        VectorLiteral(v.t, newargs: _*)

      case OclStore(vr, t, v, offset, addressSpace) =>
        var varRef = vr
        var vNew = v
        if (oclVarDeclMap.contains(vr.v))
        {
          val idxSuffix = getIndexSuffix(vr.suffix.getOrElse(throw new Exception("Unable to find index for " + vr.v.v.name)))
          val ocl = oclVarDeclMap(vr.v)(idxSuffix._1)
          varRef = VarRef(ocl.v, Some(idxSuffix._2), vr.arrayIndex)
        }
        v match
        {
          case FunctionCall(f,args) =>
            val lst = getVarRefList(args,oclVarDeclMap)
            vNew = FunctionCall(f, lst)
          case _ => v
        }
        OclStore(varRef, t, vNew, offset, addressSpace)
      case IfThenElse(cond,tb,fb) =>
        var newCond = cond
        cond match
        {
          case VarRef(v, s, ai) =>
            if (oclVarDeclMap.contains(v))
            {
              val idxSuffix = getIndexSuffix(s.getOrElse(throw new Exception("Unable to find index for " + v.v.name)))
              val ocl = oclVarDeclMap(v)(idxSuffix._1)
              newCond = VarRef(ocl.v, Some(idxSuffix._2), ai)
            }
          case _ =>
        }
        IfThenElse(newCond,tb,fb)
      case OclVarDecl(v,t,i,l,as) =>
        var init = i
        i.getOrElse("") match {
          case VarRef(v, s, ai) =>
            if (oclVarDeclMap.contains(v))
            {
              val idxSuffix = getIndexSuffix(s.getOrElse(throw new Exception("Unable to find index for " + v.v.name)))
              val ocl = oclVarDeclMap(v)(idxSuffix._1)
              init = Option(VarRef(ocl.v, Some(idxSuffix._2), ai))
            }
          case _ =>
        }
        OclVarDecl(v,t,init,l,as)
      case VarRef(v, s, ai) =>
        if (oclVarDeclMap.contains(v)) {
          throw new Exception("Unrolling private memory unavailable for variable " + v.v.name + "!")
        }
        else VarRef(v, s, ai)
      case _ => n
    }
    node.visitAndRebuild(preFunctionForUnrollingArrays, idPostFun)
  }

  def inlinePrivateMemoryStructValues(node: AstNode): AstNode =
  {
    // map to keep track of the unrolled variables from private structs
    var oclVarDeclMap = ListMap[CVar, Array[OclVarDecl]]()
    // map to keep track of the tuple types of the unrolled tuples - if there is a better way feel free to implement it
    var oclTupleTypeMap = ListMap[CVar, TupleType]()

    val preFunctionForUnrollingStructs = (n: AstNode) => n match {
      case mb: MutableBlock =>
        // create new vector
        var nodeVector = Vector[AstNode with BlockMember]()
        mb.content.map(node =>
          node match {
            // if it's a variable declaration:
            case ovd: OclVarDecl =>
              ovd.t match
              {
                case tt : TupleType =>
                  oclTupleTypeMap += (ovd.v -> tt)
                  ovd.addressSpace match
                  {
                    case PrivateMemory =>
                      // loop over number of elements and create a new variable for each
                      oclVarDeclMap += (ovd.v -> Array[OclVarDecl]())
                      for (i <- 0 until tt.elemsT.length) {
                        val currElem = tt.elemsT(i)
                        var oclVDtmp = OclVarDecl(CVar(Var(ovd.v.v.name + "_" + i)), tt.proj(i), ovd.init, 0, PrivateMemory)
                        if(ovd.init != None)
                          {
                            throw new NotImplementedError("Trying to unroll initialised tuples - there is no method that can currently handle this!")
                          }
                        // and push them back in new vector
                        nodeVector = nodeVector :+ oclVDtmp
                        oclVarDeclMap += (ovd.v -> (oclVarDeclMap(ovd.v) :+ oclVDtmp))
                      }
                    case _ => nodeVector = nodeVector :+ ovd
                  }
                case _ => nodeVector = nodeVector :+ ovd
              }
            // otherwise just push back in new Vector
            case an: AstNode => nodeVector = nodeVector :+ an // won't let me just use "_", please don't ask me to
          }
        )
        // return block with new vector
        MutableBlock(nodeVector, mb.global)
      case ExpressionStatement(e) => e match {
        case AssignmentExpression(lhs, rhs) => (lhs, rhs) match {
          case (VarRef(v1, s1, ai1), VarRef(v2, s2, ai2)) =>
            if (oclVarDeclMap.contains(v1) && !oclVarDeclMap.contains(v2))
            {
              val idxSuffix = getIndexSuffix(s1.getOrElse(""))
              if( idxSuffix._1 < 0 )
              {
                // In this situation, we are not supplied with a suffix, which we must suppose means that we are setting an unrolled tuple equal to another tuple
                // pull out the separated tuple variable (LHS) and set them equal to the appropriate values in the RHS tuple
                throw new NotImplementedError("Assigning unrolled tuple to a tuple - there is no method that can currently handle this!")
              }
              else
              {
                // need to update the variable for v1, v2 stays the same
                val lhsOcl = oclVarDeclMap(v1)(idxSuffix._1)
                val lhs = VarRef(lhsOcl.v, Some(idxSuffix._2), ai1)
                val rhs = VarRef(v2, s2, ai2)
              }
              ExpressionStatement(AssignmentExpression(lhs, rhs))
            }
            else if (oclVarDeclMap.contains(v2) && !oclVarDeclMap.contains(v1)) {
              // need to update the variable for v2, v1 stays the same
              val idxSuffix = getIndexSuffix(s2.getOrElse(""))
              if( idxSuffix._1 < 0 )
              {
                throw new NotImplementedError("Assigning unrolled tuple to a tuple - there is no method that can currently handle this!")
              }
              else
              {
                val lhs = VarRef(v2, s2, ai2)
                val rhsOcl = oclVarDeclMap(v2)(idxSuffix._1)
                val rhs = VarRef(rhsOcl.v, s2, ai2)
              }
              ExpressionStatement(AssignmentExpression(lhs, rhs))
            }
            else if (oclVarDeclMap.contains(v1) && oclVarDeclMap.contains(v2)) {

              val tupleListL = oclVarDeclMap(v1)
              val tupleListR = oclVarDeclMap(v2)
              var nodeVector = Vector[AstNode with BlockMember]()
              // unroll them both
              for(i <- 0 until tupleListL.length )
              {
                val oclL = tupleListL(i).v
                val oclR = tupleListR(i).v
                nodeVector = nodeVector :+ ExpressionStatement(AssignmentExpression(VarRef(oclL.v,s1,ai1),VarRef(oclR.v,s2,ai2)))
              }
              MutableBlock(nodeVector,true)
            }
            else // nothing to be unrolled - yay!
            {
              ExpressionStatement(AssignmentExpression(VarRef(v1, s1, ai1), VarRef(v2, s2, ai2)))
            }
          case (VarRef(v, s, ai), FunctionCall(f, args)) =>
            var vr = VarRef(v, s, ai)
            if (oclVarDeclMap.contains(v))
            {
              val idxSuffix = getIndexSuffix(s.getOrElse(""))
              if (idxSuffix._1 < 0) {
                throw new NotImplementedError("Assigning function return to unrolled tuple - cannot currently be handled!")
              }
              else
              {
                val ocl = oclVarDeclMap(v)(idxSuffix._1)
                vr = VarRef(ocl.v, Some(idxSuffix._2), ai)
              }
            }
            // update args list with new values of VarRefs
            var lst = List[GenericAST.AstNode]()
            for (arg <- args)
            {
              arg match
              {
                case VarRef(v_b, s_b, ai_b) if ai_b.isEmpty =>
                  var vr = VarRef(v_b, s_b, ai_b)
                  if (oclVarDeclMap.contains(v_b))
                  {
                    val idxSuffix = getIndexSuffix(s_b.getOrElse(throw new Exception("Unable to find index for " + v.v.name)))
                    if (idxSuffix._1 < 0) // This means there is no suffix attached - must use whole unrolled Tuple!
                    {
                      var newStruct: AstNode = recreateStruct(oclVarDeclMap(v_b), ai_b, oclTupleTypeMap(v_b))
                      lst = lst :+ newStruct
                    }
                    else
                    {
                      val ocl = oclVarDeclMap(v_b)(idxSuffix._1)
                      vr = VarRef(ocl.v, Some(idxSuffix._2), ai_b)
                      lst = lst :+ vr
                    }
                  }
                  else {
                    lst = lst :+ vr
                  }
                  case StructConstructor(t, args) =>
                    var newargs = Vector[GenericAST.AstNode]()
                    for (arg <- args)
                    {
                      arg match
                      {
                        case VarRef(v_b, s_b, ai_b) if ai_b.isEmpty =>
                          var vr = getCorrectVarRef(v_b,s_b,ai_b,oclVarDeclMap)
                          newargs = newargs :+ vr
                          case _ =>
                            newargs = newargs :+ arg
                      }
                    }
                    lst = lst :+ StructConstructor(t, newargs)
                    case an: AstNode => lst = lst :+ an
              }
            }
            ExpressionStatement(AssignmentExpression(vr, FunctionCall(f, lst)))
          case (VarRef(v, s, ai), TernaryExpression(cond, trueExpr, falseExpr)) =>
            var vr = getCorrectVarRef(v,s,ai,oclVarDeclMap)
            ExpressionStatement(AssignmentExpression(vr, TernaryExpression(cond, trueExpr, falseExpr)))
          case (VarRef(v, s, ai), StructConstructor(t, args)) =>
            var vrOrg = VarRef(v, s, ai)
            if (oclVarDeclMap.contains(v)) {
              var nodeVector = Vector[AstNode with BlockMember]()
              // unroll them both
              for(i <- 0 until args.length )
              {
                  val tupleList = oclVarDeclMap(v)
                  val ocl = tupleList(i)
                  vrOrg = VarRef(ocl.v, s, ai)
                  nodeVector = nodeVector :+ ExpressionStatement(AssignmentExpression(vrOrg, args(i)))
              }
              MutableBlock(nodeVector,true)
            }
            else
            {
              ExpressionStatement(AssignmentExpression(vrOrg, StructConstructor(t, args)))
            }
          case _ => ExpressionStatement(e)
        }
        case _ => ExpressionStatement(e)
      }
      case VarRef(v, s, ai) =>
        if (oclVarDeclMap.contains(v)) {
          throw new Exception("Inlining struct memory unavailable for variable " + v.v.name + "!")
        }
        else VarRef(v, s, ai)
      case _ => n
    }

    node.visitAndRebuild(preFunctionForUnrollingStructs, idPostFun)
  }

}

class UnrollValues
{

}

