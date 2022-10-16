package sigmastate.eval

import sigmastate.lang.TransformingSigmaBuilder

import scala.util.Try

trait IRContext extends TreeBuilding with GraphBuilding {
  import SigmaProp._

  private val SigmaM = SigmaPropMethods

  override val builder = TransformingSigmaBuilder

  /** Pass configuration which is used to turn-off constant propagation.
    * @see `beginPass(noCostPropagationPass)`  */
  lazy val noConstPropagationPass = new DefaultPass(
    "noCostPropagationPass",
    Pass.defaultPassConfig.copy(constantPropagation = false))

  val sigmaDslBuilderValue = CostingSigmaDslBuilder

  /** Finds SigmaProp.isProven method calls in the given Lambda `f` */
  def findIsProven[T](f: Ref[Context => T]): Option[Sym] = {
    val Def(Lambda(lam,_,_,_)) = f
    val s = lam.flatSchedule.find(sym => sym.node match {
      case SigmaM.isValid(_) => true
      case _ => false
    })
    s
  }

  /** Checks that if SigmaProp.isProven method calls exists in the given Lambda's schedule,
    * then it is the last operation. */
  def verifyIsProven[T](f: Ref[Context => T]): Try[Unit] = {
    val isProvenOpt = findIsProven(f)
    Try {
      isProvenOpt match {
        case Some(s) =>
          if (f.getLambda.y != s) !!!(s"Sigma.isProven found in none-root position", s)
        case None =>
      }
    }
  }
}

/** IR context to be used by blockchain nodes to validate transactions. */
class RuntimeIRContext extends IRContext {
}

/** IR context to be used by script development tools to compile ErgoScript into ErgoTree bytecode. */
class CompiletimeIRContext extends IRContext {
}

