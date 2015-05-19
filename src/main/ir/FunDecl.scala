package ir

import arithmetic.{Var, ArithExpr}
import opencl.ir._

import language.implicitConversions

abstract class FunDecl(val params: Array[Param]) {

  def isGenerable = false

  def comp(that: Lambda) : CompFunDef = {
    val thisFuns = this match {
      case cf : CompFunDef => cf.funs
      case l : Lambda => Seq(l)
      case _ => Seq(Lambda.FunDefToLambda(this))
    }
    val thatFuns = that match {
      case _ => Seq(that)
    }
    val allFuns = thisFuns ++ thatFuns
    CompFunDef(allFuns:_*)
  }
  def comp(f: FunDecl): CompFunDef = comp(Lambda.FunDefToLambda(f))

  def o(f: Lambda): CompFunDef = comp(f)

  def call(arg: Expr) = apply(arg)
  def call(arg0: Expr, arg1: Expr) = apply(arg0, arg1)
  def call(arg0: Expr, arg1: Expr, arg2: Expr) = apply(arg0, arg1, arg2)

  def $(that: Expr) : FunCall = {
    apply(that)
  }


  def apply(args : Expr*) : FunCall = {
    assert (args.length == params.length)
    new FunCall(this, args:_*)
  }

}

trait isGenerable extends FunDecl {
  override def isGenerable = true
}

object FunDecl {

  def replace(l: Lambda, oldE: Expr, newE: Expr) : Lambda =
    visit (l, (l:Lambda) => {
      if (l.body.eq(oldE)) new Lambda(l.params, newE) else l
    }, (l:Lambda) => l)

  def visit(f: Lambda, pre: (Lambda) => (Lambda), post: (Lambda) => (Lambda)) : Lambda = {

    val newF = pre(f)

    val newBodyFunDef : Expr = newF.body match {
      case call: FunCall => call.f match {
        case l : Lambda => new Lambda( l.params, visit(l, pre, post)(call.args:_*) ).body
        case cfd : CompFunDef => ( new CompFunDef(cfd.params, cfd.funs.map(f => visit(f, pre, post)):_*) )(call.args:_*)
        case ar: AbstractPartRed => ar.getClass.getConstructor(classOf[Lambda],classOf[Value]).newInstance(visit(ar.f, pre, post),ar.init)(call.args:_*)
        case fp: FPattern => fp.getClass.getConstructor(classOf[Lambda]).newInstance(visit(fp.f, pre, post))(call.args:_*)
        case _ => newF.body.copy
      }
      case _ => newF.body.copy
    }

    post(new Lambda(f.params, newBodyFunDef))
  }
}

object CompFunDef {

  def apply(funs: Lambda*) : CompFunDef = {
     new CompFunDef(funs.last.params,funs:_*)
  }

}

case class CompFunDef(override val params : Array[Param], funs: Lambda*) extends FunDecl(params)  with isGenerable {


  override def toString: String = {
    funs.map((f) => f.toString()).reduce((s1, s2) => s1 + " o " + s2)
  }

  override def equals(o: Any) = {
    o match {
      case cf : CompFunDef => funs.seq.equals(cf.funs)
      case _ => false
    }
  }

  override def hashCode() = {
    funs.foldRight(3*79)((f,hash) => hash*f.hashCode())
  }

  /** flatten all the composed functions*/
  def flatten : List[Lambda] = {
    this.funs.foldLeft(List[Lambda]())((ll, f) => {
      f.body match {
        case call: FunCall => call.f match {
            case cf: CompFunDef => ll ++ cf.flatten
            case _ => ll :+ f
          }
        case _ => ll :+ f
      }
    })
  }
}

// Here are just the algorithmic patterns
// For opencl specific patterns see the opencl.ir package

abstract class Pattern(override val params: Array[Param]) extends FunDecl(params)

trait FPattern {
  def f: Lambda
}

abstract class AbstractMap(f:Lambda1) extends Pattern(Array[Param](Param(UndefType))) with FPattern

case class Map(f:Lambda1) extends AbstractMap(f) {
  override def apply(args: Expr*): MapCall = mapCall(args:_*)

  override def $(that: Expr): MapCall = mapCall(that)

  private def mapCall(args: Expr*): MapCall = {
    assert(args.length == 1)
    new MapCall("Map", Var(""), this, args(0))
  }
}

object Map {
  def apply(f: Lambda1, expr: Expr): MapCall = {
    Map(f).mapCall(expr)
  }
}

abstract class GenerableMap(f:Lambda1) extends AbstractMap(f) with isGenerable

abstract class AbstractPartRed(f:Lambda2) extends Pattern(Array[Param](Param(UndefType), Param(UndefType))) with FPattern {
  def init: Value = params(0) match { case v: Value => v}
}

abstract class AbstractReduce(f:Lambda2) extends AbstractPartRed(f)

case class Reduce(f: Lambda2) extends AbstractReduce(f) {
  override def apply(args: Expr*) : ReduceCall = reduceCall(args:_*)

  private def reduceCall(args: Expr*): ReduceCall = {
    assert(args.length == 2)
    new ReduceCall(Var("i"), this, args(0), args(1))
  }
}
object Reduce {
  def apply(f: Lambda2, init: Value): Lambda1 = fun((x) => Reduce(f)(init, x))
  def apply(f: Lambda2, init: Value, expr: Expr): ReduceCall = Reduce(f)(init, expr)
}

case class PartRed(f: Lambda2) extends AbstractPartRed(f) with FPattern {
  override def apply(args: Expr*) : ReduceCall = reduceCall(args:_*)

  private def reduceCall(args: Expr*): ReduceCall = {
    assert(args.length == 2)
    new ReduceCall(Var("i"), this, args(0), args(1))
  }
}
object PartRed {
  def apply(f: Lambda2, init: Value): Lambda1 = fun((x) => PartRed(f)(init, x))
  def apply(f: Lambda2, init: Value, expr: Expr): ReduceCall = PartRed(f)(init, expr)
}

case class Join() extends Pattern(Array[Param](Param(UndefType))) with isGenerable

case class Split(chunkSize: ArithExpr) extends Pattern(Array[Param](Param(UndefType))) with isGenerable

case class asScalar() extends Pattern(Array[Param](Param(UndefType))) with isGenerable

case class asVector(len: ArithExpr) extends Pattern(Array[Param](Param(UndefType))) with isGenerable

/*
// TODO: discuss if this should be a Fun again (if so, this has to be replaced in the very first pass before type checking)
case class Vectorize(n: Expr, f: Fun) extends FPattern {
  def isGenerable() = true
  override def copy() = Vectorize(n, f)
}
*/

object Vectorize {
  class Helper(n: ArithExpr) {
    def apply(uf: UserFunDef): UserFunDef = {
      UserFunDef.vectorize(uf, n)
    }

    def apply(v: Value): Value = {
      Value.vectorize(v, n)
    }

    def apply(p: Param): Param = {
      Param.vectorize(p, n)
    }
  }

  def apply(n: ArithExpr): Helper = {
    new Helper(n)
  }
}

case class UserFunDef(name: String, paramNames: Array[String], body: String,
                      inTs: Seq[Type], outT: Type)
  extends FunDecl(inTs.map(Param(_)).toArray) with isGenerable {

  private def namesAndTypesMatch(): Boolean = {

    def checkParam(param: (Type, Any)): Boolean = {
      param match {
        case (_:ScalarType, _: String) => true
        case (_:VectorType, _: String) => true
        case (_:TupleType, _: String)  => true
        case (tt:TupleType, names: Array[String]) =>
          if (tt.elemsT.length != names.length) false
          else (tt.elemsT zip names).forall( {case (t,n) => checkParam( (t,n) )} )
        case _ => false
      }
    }

    checkParam((inT, paramName))
  }

  def paramNamesString: String = {
    def printAny(arg: Any): String = arg match {
      case a: Array[Any] => "Array(" + a.map(printAny).reduce(_+", "+_) + ")"
      case _ => arg.toString
    }

    printAny(paramName)
  }

  if (!namesAndTypesMatch())
    throw new IllegalArgumentException(s"Structure of parameter names ( $paramNamesString ) and the input type ( $inT ) doesn't match!")

  def hasUnexpandedTupleParam: Boolean = {
    def test(param: (Type, Any)): Boolean = {
      param match {
        case (_: TupleType, _: String) => true
        case (_: ScalarType, _: String) => false
        case (_: VectorType, _: String) => false
        case (tt: TupleType, names: Array[String]) =>
          (tt.elemsT zip names).exists({ case (t, n) => test((t, n))})
      }
    }
    test((inT, paramName))
  }

  private def unexpandedParamTupleTypes: Seq[TupleType] = {
    def emit(param: (Type, Any)): Seq[TupleType] = {
      param match {
        case (tt: TupleType, _:String) => Seq(tt)
        case (tt: TupleType, names: Array[Any]) =>
          (tt.elemsT zip names).map({ case (t,n) => emit(t, n)}).flatten
        case _ => Seq()
      }
    }
    emit((inT, paramName))
  }

  def unexpandedTupleTypes: Seq[TupleType] = {
    outT match {
      case tt: TupleType => (unexpandedParamTupleTypes :+ tt).distinct
      case _ => unexpandedParamTupleTypes
    }
  }

  def inT = if (inTs.size == 1) inTs.head else TupleType(inTs:_*)
  def paramName = if (paramNames.length == 1) paramNames.head else paramNames

  override def toString = "UserFun("+ name + ")" // for debug purposes
}

object UserFunDef {
  def apply(name: String, paramName: String, body: String, inT: Type, outT: Type): UserFunDef = {
    UserFunDef(name, Array(paramName), body, Seq(inT), outT)
  }

  def vectorize(uf: UserFunDef, n: ArithExpr): UserFunDef = {
    val name = uf.name + n
    val expectedInT = uf.inTs.map(Type.vectorize(_, n))
    val expectedOutT = Type.vectorize(uf.outT, n)

    // create new user fun
    UserFunDef(name, uf.paramNames, uf.body, expectedInT, expectedOutT)
  }

  val id = UserFunDef("id", "x", "{ return x; }", Float, Float)

  val idI = UserFunDef("id", "x", "{ return x; }", Int, Int)

  val idFI = UserFunDef("id", "x", "{ return x; }", TupleType(Float, Int), TupleType(Float, Int))

  val idFF = UserFunDef("id", "x", "{ return x; }", TupleType(Float, Float), TupleType(Float, Float))

  val absAndSumUp = UserFunDef("absAndSumUp", Array("acc", "x"), "{ return acc + fabs(x); }", Seq(Float, Float), Float)

  val add = UserFunDef("add", Array("x", "y"), "{ return x+y; }", Seq(Float, Float), Float)

  val plusOne = UserFunDef("plusOne", "x", "{ return x+1; }", Float, Float)

  val doubleItAndSumUp = UserFunDef("doubleItAndSumUp", Array("x", "y"), "{ return x + (y * y); }", Seq(Float, Float), Float)

  val sqrtIt = UserFunDef("sqrtIt", "x", "{ return sqrt(x); }", Float, Float)

  val abs = UserFunDef("abs", "x", "{ return x >= 0 ? x : -x; }", Float, Float)

  val neg = UserFunDef("neg", "x", "{ return -x; }", Float, Float)

  val mult = UserFunDef("mult", Array("l", "r"), "{ return l * r; }", Seq(Float, Float), Float)

  val multAndSumUp = UserFunDef("multAndSumUp", Array("acc", "l", "r"),
    "{ return acc + (l * r); }",
    Seq(Float, Float, Float), Float)

  val addPair = UserFunDef(
    "pair",
    Array("x", "y"),
    "{ x._0 = x._0 + y._0;" +
      "x._1 = x._1 + y._1;" +
      "return x; }",
    Seq(TupleType(Float, Float), TupleType(Float, Float)),
    TupleType(Float, Float))

}

case class Iterate(n: ArithExpr, f: Lambda1) extends Pattern(Array[Param](Param(UndefType))) with FPattern with isGenerable {

  override def apply(args: Expr*): IterateCall = iterateCall(args: _*)

  override def $(that: Expr): IterateCall = iterateCall(that)

  private def iterateCall(args: Expr*): IterateCall = {
    assert(args.length == 1)
    new IterateCall(this, args(0))
  }
}

object Iterate {
  def apply(n: ArithExpr): ((Lambda1) => Iterate)  = (f: Lambda1) => Iterate(n ,f)

  def varName(): String = {
    "iterSize"
  }
}

class AccessVar(val array: String, val idx: ArithExpr) extends Var("")

case class Filter() extends FunDecl(Array(Param(UndefType), Param(UndefType))) with isGenerable

object Filter {
  def apply(input: Param, ids: Param): FunCall = {
    Filter()(input, ids)
  }
}

case class Tuple(n: Int) extends FunDecl(Array.fill(n)(Param(UndefType))) with isGenerable

object Tuple {
  def apply(args : Expr*) : FunCall = {
    assert(args.length >= 2)
    Tuple(args.length)(args:_*)
  }
}

case class Zip(n : Int) extends FunDecl(Array.fill(n)(Param(UndefType))) with isGenerable

object Zip {
  def apply(args : Expr*) : FunCall = {
    assert(args.length >= 2)
    Zip(args.length)(args:_*)
  }
}

case class Unzip() extends FunDecl(Array[Param](Param(UndefType))) with isGenerable
