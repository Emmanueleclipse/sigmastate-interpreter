package org.ergoplatform.dsl

import org.ergoplatform.{ErgoLikeContext, ErgoBox}
import org.ergoplatform.ErgoBox.{NonMandatoryRegisterId, BoxId}
import scalan.RType
import sigmastate.SType
import sigmastate.SType.AnyOps
import org.ergoplatform.dsl.ContractSyntax.{Token, TokenId, ErgoScript, Proposition}
import sigmastate.Values.{ErgoTree, Constant}
import sigmastate.eval.{IRContext, CSigmaProp, CostingSigmaDslBuilder, Evaluation}
import sigmastate.interpreter.{ProverResult, CostedProverResult}
import sigmastate.interpreter.Interpreter.ScriptEnv
import special.collection.Coll
import special.sigma.{SigmaProp, SigmaContract, AnyValue, Context, DslSyntaxExtensions, SigmaDslBuilder}

import scala.language.implicitConversions
import scala.util.Try

trait ContractSyntax { contract: SigmaContract =>
  override def builder: SigmaDslBuilder = new CostingSigmaDslBuilder
  val spec: ContractSpec
  val syntax = new DslSyntaxExtensions(builder)
  def contractEnv: ScriptEnv

  /** The default verifier which represents miner's role in verification of transactions.
    * It can be overriden in derived classes. */
  lazy val verifier: spec.VerifyingParty = spec.VerifyingParty("Miner")

  def Coll[T](items: T*)(implicit cT: RType[T]) = builder.Colls.fromItems(items:_*)

  def proposition(name: String, dslSpec: Proposition, scriptCode: String) = {
    val env = contractEnv.mapValues { v =>
      val tV = Evaluation.rtypeOf(v).get
      val treeType = Evaluation.toErgoTreeType(tV)
      val data = Evaluation.fromDslData(v, treeType)
      val elemTpe = Evaluation.rtypeToSType(treeType)
      spec.IR.builder.mkConstant[SType](data.asWrappedType, elemTpe)
    }
    spec.mkPropositionSpec(name, dslSpec, ErgoScript(env, scriptCode))
  }

  def Env(entries: (String, Any)*): ScriptEnv = Map(entries:_*)
}
object ContractSyntax {
  type Proposition = Context => SigmaProp
  type TokenId = Coll[Byte]
  case class ErgoScript(env: ScriptEnv, code: String)
  case class Token(id: TokenId, value: Long)
}

trait SigmaContractSyntax extends SigmaContract with ContractSyntax {
  override def canOpen(ctx: Context): Boolean = ???
}






