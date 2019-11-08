package backends.spatial.accel.ir.pattern

import ir.ast.{IRNode, Lambda, Lambda1, Pattern}
import ir.interpreter.Interpreter.ValueMap
import ir.{Type, TypeChecker}

/**
 * A pattern for parallelising its argument by wrapping it into a control structure "Pipe".
 * An identity function in Lift.
 * Generates a control structure which defines a schedule:
 * Parallel { .. }
 */
case class Parallel(override val f: Lambda1) extends SchedulingPattern(f) {

  override def copy(f: Lambda): Pattern = Parallel(f)

  override def _visit(prePost: IRNode => IRNode => Unit): Unit = f.visit_pp(prePost)

  override def checkType(argType: Type, setType: Boolean): Type = {
    f.params(0).t = argType
    TypeChecker.check(f.body, setType)
  }

  override def eval(valueMap: ValueMap, args: Any*): Any = args.head
}
