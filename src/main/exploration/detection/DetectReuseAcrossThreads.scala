package exploration.detection

import exploration.MemoryMappingRewrite
import ir.ast._
import ir.view._
import lift.arithmetic._
import opencl.ir.OpenCLMemory.getAllMemoryVars

object DetectReuseAcrossThreads {

  def findStrategicLocations(f: Lambda):  Seq[(Expr, Var)] = {
    val strategicLocationsMarked = MemoryMappingRewrite.addIdsForLocal(f)
    getCandidates(strategicLocationsMarked)
  }

  def getCandidates(strategicLocationsMarked: Lambda): Seq[(Expr, Var)] = {
    val reuseCandidates = getReuseCandidates(strategicLocationsMarked)
    val tryHere = reuseCandidates.flatMap(getLocalMemoryCandidates(strategicLocationsMarked, _))
    tryHere
  }

  private def getReuseCandidates(f: Lambda) = {
    val numDimensions = getNumDimensions(f)

    prepareLambda(f)

    val args = Expr.visitWithState(Seq[Expr]())(f.body, {
      case (call@FunCall(_: UserFun | _: VectorizeUserFun, args@_*), seq)
        if !getUserFunName(call.f).startsWith("id") && // TODO: Better way to deal with forcing values into a tuple
          call.context.inMapWrg.count(b => b) == numDimensions
      => seq ++ args
      case (_, seq) => seq
    }).distinct.diff(f.params).filter({
      case FunCall(_: UserFun | _: VectorizeUserFun, _*) => false
      case _: Value => false  // TODO: A copy will have been made here
      case _ => true
    })

    args.filterNot(getNumberOfLocalAccesses(_) >= numDimensions)
  }

  private def getLocalMemoryCandidates(strategicLocationsMarked: Lambda, reuseExpr: Expr) = {
    val sourceView = View.getSubViews(reuseExpr.view).last

    sourceView match {
      case ViewMem(sourceVar, _) =>

        val numDimension = getNumDimensions(strategicLocationsMarked)

        // Find which "strategic" Id location(s) would copy the required variable and is suitable for local memory
        // TODO: Doesn't work for all reuse... Does it matter? Still gets what we care about
        Expr.visitWithState(Seq[(Expr, Var)]())(strategicLocationsMarked.body, {
          case (funCall@FunCall(Id(), _*), seq)
            if getAllMemoryVars(funCall.mem).contains(sourceVar) &&
              !funCall.context.inMapLcl.reduce(_ || _) &&
              funCall.context.inMapWrg.count(b => b) == numDimension
          =>
            seq :+ (funCall, sourceVar)
          case (_, seq) => seq
        })

      case _ => Seq()
    }
  }

  private def getNumberOfLocalAccesses(expr: Expr) = {
    val views = View.getSubViews(expr.view)

    val viewMaps = views.count({
      case ViewMap(_, v, _) => v.toString.contains("l_id")
      case _ => false
    })

    val viewAccesses = views.count({
      case ViewAccess(v, _, _) => v.toString.contains("l_id")
      case _ => false
    })

    viewAccesses - viewMaps
  }
}
