package backends.spatial.common.ir.view

import backends.common.view._
import backends.spatial.accel.ir.pattern.{AbstractSpFold, MapAccumSeq, ReduceSeq, AbstractSpForeach}
import backends.spatial.common.ir._
import backends.spatial.common.ir.view.MemoryAccessInfoSp.MemoryAccessInfoSp
import ir.ast.{AbstractMap, Expr, FPattern, FunCall, Get, Lambda, Param, UserFun}
import ir.{ArrayType, Memory, TupleType, Type}
import lift.arithmetic.{ArithExpr, Cst, Var}

import scala.collection.immutable.ListMap

object MemoryAccessInfoSp {
  type MemoryAccessInfoSp = collection.immutable.ListMap[Memory, List[SingleAccess]]
  def apply(): MemoryAccessInfoSp = collection.immutable.ListMap()
}

class AccessInfoSp(var accessInf: MemoryAccessInfoSp,
                   var collection: Seq[AccessInfoSp]) extends AccessInfo {

  override def toString = s"AccessInfoSp($accessInf, $collection)"

  def apply(newAccess: SingleAccess, usedMemories: Seq[Memory]): AccessInfoSp = {
    if (collection.isEmpty) {
      AccessInfoSp(accessInf.map {
        case (mem: Memory, accessInfo: List[SingleAccess]) =>
          if (usedMemories.contains(mem))
          // TODO: verify ordering of accessInfo
            mem -> (newAccess :: accessInfo)
          else
            mem -> accessInfo
      })
    } else {
      AccessInfoSp(collection.map(aInf => AccessInfoSp(aInf.accessInf.map {
        case (mem: Memory, accessInfo: List[SingleAccess]) =>
          if (usedMemories.contains(mem))
          // TODO: verify ordering of accessInfo
            mem -> (newAccess :: accessInfo)
          else
            mem -> accessInfo
      })))
    }
  }
}

object AccessInfoSp {

  def apply() = new AccessInfoSp(accessInf = MemoryAccessInfoSp(), collection = Seq())
  def apply(accessInf: MemoryAccessInfoSp) = new AccessInfoSp(collection.immutable.ListMap() ++ accessInf, Seq())
  def apply(collection: Seq[AccessInfoSp]) = new AccessInfoSp(MemoryAccessInfoSp(), collection)
}

/**
 * Helper object for building views.
 *
 * Determines dimensionality and length of each dimension of arrays in all memories.
 *
 * The essential difference to the OpenCL-specific BuildDepthInfoCL is in scoping. The OpenCL version
 * maintains a list of accesses per address space globally, growing them while traversing the AST top-down.
 * Once a memory is found, accesses from all outer loops are taken into account. TODO: confirm
 * For Spatial, we maintain a list of memories and accumulate accesses for each memory separately.
 * When collecting accesses to a memory, we take into account only the accesses made using iteration
 * variables of loops that are inside the scope of the memory. The loops outside the scope do not produce accesses.
 * Consider the following example:
 * Foreach(..) { i =>
 *    val a = SRAM(N)
 *    Foreach(N) { j =>
 *     f(a(j)) } }
 * In Spatial, we would collect only the access made using variable "j". In OpenCL, "a" would be global
 * to the whole kernel and both "i" and "j" would be recorded as accesses.
 */
object BuildDepthInfoSp {

  /**
   * Determine the dimensions, variables and lengths.
   *
   * @param expr Starting expression.
   */
  def apply(expr: Expr): Unit = {
    (new BuildDepthInfoSp)visitAndBuildDepthInfo(expr, MemoryAccessInfoSp())
  }
}

private class BuildDepthInfoSp() {

  private def visitAndBuildDepthInfo(expr: Expr, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    val result = expr match {
      case call: FunCall => buildDepthInfoFunCall(call, memoryAccessInfo)
      case e: Expr =>
        e.inputDepth = getAccessInfo(Seq(expr.mem), memoryAccessInfo)
        e.accessInf.asInstanceOf[AccessInfoSp]
    }

    expr.accessInf = result
    result
  }

  private def buildDepthInfoFunCall(call: FunCall, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {

    val argInf = buildDepthForArgs(call, memoryAccessInfo)

    call.f match {
      case sF: AbstractSpForeach  => buildDepthInfoSpForeachCall(sF, call, argInf, memoryAccessInfo)
      case m: AbstractMap         => buildDepthInfoMapCall(m, call, argInf, memoryAccessInfo)
      case aSF: AbstractSpFold    => buildDepthInfoSpFoldCall(aSF, call, argInf, memoryAccessInfo)
      case r: ReduceSeq           => buildDepthInfoReduceSeqCall(r, call, argInf, memoryAccessInfo)
      case ma: MapAccumSeq        => buildDepthInfoMapAccumSeqCall(ma, call, argInf, memoryAccessInfo)
      case _: UserFun             => buildDepthInfoUserFunCall(call, memoryAccessInfo)
      case _                      =>

        val readMemories = call.args.foldLeft(Seq[SpatialMemory]()){ case (mems, arg) => mems ++ getMemoryAccesses(arg) }
        val writeMemories = getMemoryAccesses(call)

        setDepths(call, readMemories, writeMemories, memoryAccessInfo)

        call.f match {
          case l: Lambda            =>  buildDepthInfoLambda(l, call, argInf, memoryAccessInfo)
          case fp: FPattern         =>  buildDepthInfoLambda(fp.f, call, argInf, memoryAccessInfo)
          case Get(n)               =>  if (argInf.collection.nonEmpty) argInf.collection(n) else argInf
          case _                    =>  argInf
        }
    }
  }

  private def buildDepthInfoUserFunCall(call: FunCall, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    val readMemories = call.args.foldLeft(Seq[SpatialMemory]()){ case (mems, arg) => mems ++ getMemoryAccesses(arg) }
    val writeMemories = getMemoryAccesses(call)

    setDepths(call, readMemories, writeMemories, memoryAccessInfo)

    val updMemoryAccessInfo = call.t match {
      // Update memory access info with sliced access
      case _: ArrayType => updateAccessInf(memoryAccessInfo, readMemories ++ writeMemories, newAccess = None)
      case _ => memoryAccessInfo
    }

    AccessInfoSp(updMemoryAccessInfo)
  }

  private def buildDepthInfoSpForeachCall(sF: AbstractSpForeach, call: FunCall,
                                          l: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    buildDepthInfoMapLikeCall(sF.f, sF.loopVar, call, l, memoryAccessInfo)
  }

  private def buildDepthInfoMapCall(m: AbstractMap, call: FunCall,
                                    l: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    buildDepthInfoMapLikeCall(m.f, m.loopVar, call, l, memoryAccessInfo)
  }

  private def buildDepthInfoMapLikeCall(f: Lambda, loopVar: Var, call: FunCall,
                                        l: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    val readMemories = getMemoryAccesses(call.args.head)

    val inf = getArrayAccessInf(call.args.head.t, loopVar)
    f.params.head.accessInf = l(inf, readMemories)
    buildDepthInfoPatternCall(f.body, call, loopVar, readMemories, memoryAccessInfo)

    if (f.body.isConcrete) // create fresh input view for following function
      AccessInfoSp(memoryAccessInfo)
    else // call.isAbstract, return input
      l
  }

  private def buildDepthInfoSpFoldCall(asf: AbstractSpFold, call: FunCall,
                                       l: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {

    /*** Map depth info ***/
    val mapReadMemories = getMemoryAccesses(call.args(1))
    val mapAccessInfo = getArrayAccessInf(call.args(1).t, asf.mapLoopVar)
    asf.fMap.params.head.accessInf = l.collection(1)(mapAccessInfo, mapReadMemories)

    val mapWriteMemories = getMemoryAccesses(asf.fMap.body)

    // The input memories will be accessed using map iterator variable
    val updMemoryAccessInfo = updateAccessInf(memoryAccessInfo, mapReadMemories, Some(mapAccessInfo))
    // The write memory is local to each map iteration and will be accessed as a whole

    // traverse into call.f
    visitAndBuildDepthInfo(asf.fMap.body, updMemoryAccessInfo)

    setDepths(call, mapReadMemories, mapWriteMemories, updMemoryAccessInfo)


    val fMapAccessInfo = if (asf.fMap.body.isConcrete) // create fresh input view for following function
      AccessInfoSp(updMemoryAccessInfo)
    else // call.isAbstract, return input
      l

    val mapType = call.args(1).t


    /*** Reduce depth info ***/
    // Reduce reads memory that is different from the one fMap writes to
    // (see the comment in SpatialMemoryAllocator on this)
    val reduceReadMemories = getMemoryAccesses(asf.fReduce.params(1))
    val reduceArgAccessInfo = getArrayAccessInf(mapType, asf.reduceLoopVar)
    asf.fReduce.params(0).accessInf = l.collection.head
    asf.fReduce.params(1).accessInf = fMapAccessInfo(reduceArgAccessInfo, reduceReadMemories)

    // The output of the Fold does not add depth (the array of one contract has been deprecated),
    // so memoryAccessInfo does not have to be updated
    val reduceWriteMemories = getMemoryAccesses(call)

    // traverse into call.f
    visitAndBuildDepthInfo(asf.fReduce.body, memoryAccessInfo)

    setDepths(call, reduceReadMemories, reduceWriteMemories, memoryAccessInfo)

    AccessInfoSp(memoryAccessInfo)
  }

  private def buildDepthInfoReduceSeqCall(r: ReduceSeq, call: FunCall,
                                          l: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    val readMemories = getMemoryAccesses(r.f.params(1))
    val argAccessInfo = getArrayAccessInf(call.args(1).t, r.loopVar)

    r.f.params(0).accessInf = l.collection.head
    r.f.params(1).accessInf = l.collection(1)(argAccessInfo, readMemories)

    val writeMemories = getMemoryAccesses(call)

    val bodyAccessInfo = getArrayAccessInf(call.t, Cst(0))

    // The input and intermediate outputs memories will be accessed using map iterator variable
    val updMemoryAccessInfo = updateAccessInf(memoryAccessInfo, readMemories ++ writeMemories, Some(bodyAccessInfo))

    // traverse into call.f
    visitAndBuildDepthInfo(r.f.body, updMemoryAccessInfo)

    setDepths(call, readMemories, writeMemories, updMemoryAccessInfo)

    AccessInfoSp(memoryAccessInfo)
  }

  private def buildDepthInfoMapAccumSeqCall(mapAccum: MapAccumSeq, call: FunCall,
                                            l: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {
    val readMemories = getMemoryAccesses(mapAccum.f.params(1))
    val argAccessInfo = getArrayAccessInf(call.args(1).t, mapAccum.loopVar)

    mapAccum.f.params(0).accessInf = l.collection.head
    mapAccum.f.params(1).accessInf = l.collection(1)(argAccessInfo, readMemories)

    val writeStateMemories = SpatialMemory.getAllMemories(call.mem.asInstanceOf[SpatialMemoryCollection].subMemories(0))
    val writeOutValMemories = SpatialMemory.getAllMemories(call.mem.asInstanceOf[SpatialMemoryCollection].subMemories(1))

    val outStateAccessInfo = getArrayAccessInf(call.t.asInstanceOf[TupleType].elemsT.head, Cst(0))
    // The input and intermediate outputs memories will be accessed using map iterator variable
    val outValAccessInfo = getArrayAccessInf(call.t.asInstanceOf[TupleType].elemsT(1), mapAccum.loopVar)

    val updMemoryAccessInfo = updateAccessInf(
      updateAccessInf(memoryAccessInfo, readMemories ++ writeStateMemories, Some(outStateAccessInfo)),
      readMemories ++ writeOutValMemories, Some(outValAccessInfo))

    // traverse into call.f
    visitAndBuildDepthInfo(mapAccum.f.body, updMemoryAccessInfo)

    setDepths(call, readMemories, writeStateMemories ++ writeOutValMemories, updMemoryAccessInfo)

    AccessInfoSp(memoryAccessInfo)
  }

  private def getMemoryAccesses(expr: Expr) =
    SpatialMemory.getAllMemories(expr.mem.asInstanceOf[SpatialMemory])

  private def buildDepthInfoPatternCall(expr: Expr, call: FunCall, index: ArithExpr,
                                        readMemories: Seq[Memory],
                                        memoryAccessInfo: MemoryAccessInfoSp): Unit = {
    val inf = getArrayAccessInf(call.t, index)
    val writeMemories = getMemoryAccesses(call)

    val updMemoryAccessInfo = updateAccessInf(memoryAccessInfo, readMemories ++ writeMemories, Some(inf))

    // traverse into call.f
    visitAndBuildDepthInfo(expr, updMemoryAccessInfo)

    setDepths(call, readMemories, writeMemories, memoryAccessInfo)

    AccessInfoSp(memoryAccessInfo)
  }

  private def buildDepthForArgs(call: FunCall, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {

    if (call.args.length == 1)
      visitAndBuildDepthInfo(call.args.head, memoryAccessInfo)
    else
      AccessInfoSp(call.args.map((expr: Expr) => visitAndBuildDepthInfo(expr, memoryAccessInfo)))
  }

  private def buildDepthInfoLambda(l: Lambda, call: FunCall,
                                   list: AccessInfoSp, memoryAccessInfo: MemoryAccessInfoSp): AccessInfoSp = {

    if (call.args.length == 1)
      setAccessInfo(list, l.params.head, memoryAccessInfo)
    else
      (list.collection, l.params).zipped.foreach((accessInfo, param) =>
        setAccessInfo(accessInfo, param, memoryAccessInfo))

    visitAndBuildDepthInfo(l.body, memoryAccessInfo)
  }

  /**
   * For new memories, adds them to the memoryAccessInfo collection with one access.
   * For memories already in memoryAccessInfo, adds the new access to the list of existing ones
   *
   * newAccess of None represents sliced access since we cannot represent a slice with a single
   * arithmetic expression that constitutes required part of SingleAccess. SingleAccess could be extended to
   * allow specifying ranges, but in reasoning about depth, we don't need that information anyway.
   */
  protected def updateAccessInf(origMemoryAccessInfo: MemoryAccessInfoSp,
                                accessedMemories: Seq[Memory],
                                newAccess: Option[SingleAccess]): MemoryAccessInfoSp = {
    // Join and remove duplicates
    val memories: Seq[Memory] = (origMemoryAccessInfo.keys ++ accessedMemories).toSet.toSeq
    ListMap(
      memories.map(mem =>
        if (newAccess.isDefined) {
          // Non-sliced access
          if (origMemoryAccessInfo.contains(mem))
          // Existing memories
            (mem, newAccess.get +: origMemoryAccessInfo(mem))
          else
          // New memories
            (mem, List(newAccess.get))
        } else {
          // Sliced access: just create an empty list of accesses to record that the array is being accessed
          // (if accesses to the memory already exist, this will have no effect)
          (mem, List())
        }
      ): _*)
  }

//  protected def restoreAccessInf(accessedMemories: Seq[Memory]): Unit = {
//    accessedMemories.foreach(mem => memoryAccessInfo(mem) = memoryAccessInfo(mem).tail)
//  }

  protected def setDepths(call: FunCall, readMemories: Seq[Memory], writeMemories: Seq[Memory],
                          memoryAccessInfo: MemoryAccessInfoSp): Unit = {
    call.inputDepth = getAccessInfo(writeMemories, memoryAccessInfo)
    call.outputDepth = getAccessInfo(readMemories, memoryAccessInfo)
  }

  protected def setAccessInfo(list: AccessInfoSp, param: Param, memoryAccessInfo: MemoryAccessInfoSp): Unit = {
    val readMemory = getMemoryAccesses(param)

    param.outputDepth = getAccessInfo(readMemory, memoryAccessInfo)
    param.accessInf = list
  }

  /**
   * Choose memories out of the list of accessed memories based on the address space
   * priority rules (TODO: explain priority) and return the access chain of the memory
   * with the deepest access chain out of the filtered memories
   */
  protected def getAccessInfo(accessedMemories: Seq[Memory], memoryAccessInfo: MemoryAccessInfoSp): List[SingleAccess] = {
    val spAccessedMemories = accessedMemories.map(_.asInstanceOf[SpatialMemory])

    val accessedLiteralMem = spAccessedMemories.filter(SpatialMemory.containsLiteralMemory)
    val accessedRegisterMem = spAccessedMemories.filter(SpatialMemory.containsRegMemory)
    val accessedArgOutMem = spAccessedMemories.filter(SpatialMemory.containsArgOutMemory)
    val accessedSRAMMem = spAccessedMemories.filter(SpatialMemory.containsSRAMMemory)
    val accessedDRAMMem = spAccessedMemories.filter(SpatialMemory.containsDRAMMemory)

    // Find the memory with deepest access level. Return its accesses
    val accessedMemoriesOfInterest =
      if (accessedLiteralMem.nonEmpty) accessedLiteralMem
      else if (accessedRegisterMem.nonEmpty) accessedRegisterMem
      else if (accessedArgOutMem.nonEmpty) accessedArgOutMem
      else if (accessedSRAMMem.nonEmpty) accessedSRAMMem
      else if (accessedDRAMMem.nonEmpty) accessedDRAMMem
      else throw new IllegalArgumentException("Unknown or no memory")

    val accessesOfInterest = accessedMemoriesOfInterest.flatMap(mem =>
      if (memoryAccessInfo.contains(mem)) Some(memoryAccessInfo(mem)) else None)

    // Return the deepest access
    accessesOfInterest.toList.sortWith((l, r) => l.length > r.length) match {
      case Nil => List()
      case accesses => accesses.head
    }
  }

  /**
   * Utility function for building a bit of access information about an
   * ArrayType instance
   *
   * @param ty the array type passed as a generic type. We check that it is
   *           indeed an array type below.
   * @param v the variable used to perform the access
   */
  protected def getArrayAccessInf(ty: Type, v: ArithExpr): SingleAccess = {
    ty match {
      case at: ArrayType => (at.replacedElemT, v)
      case _ => throw new IllegalArgumentException(
        s"Cannot compute access information for $ty. ArrayType required."
      )
    }
  }

//  /**
//   * Utility function for generating access info to an array as a whole
//   *
//   * @param ty the array type passed as a generic type. We check that it is
//   *           indeed an array type below.
//   * @param v the variable used to perform the access
//   */
//  protected def getWholeArrayAccessInf(ty: Type, v: ArithExpr): SingleAccess = {
//    ty match {
//      case at: ArrayType => (_ => at, v)
//      case _ => throw new IllegalArgumentException(
//        s"Cannot compute access information for $ty. ArrayType required."
//      )
//    }
//  }
}
