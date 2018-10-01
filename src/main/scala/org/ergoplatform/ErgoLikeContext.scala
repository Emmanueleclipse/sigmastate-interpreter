package org.ergoplatform

import org.ergoplatform.ErgoLikeContext.Metadata._
import org.ergoplatform.ErgoLikeContext.{Height, Metadata}
import sigmastate.Values._
import sigmastate._
import sigmastate.interpreter.{Context, ContextExtension}
import sigmastate.serialization.OpCodes
import sigmastate.serialization.OpCodes.OpCode
import sigmastate.utxo.CostTable.Cost

import scala.util.Try

case class BlockchainState(currentHeight: Height, lastBlockUtxoRoot: AvlTreeData)

// todo: write description
class ErgoLikeContext(val currentHeight: Height,
                      val lastBlockUtxoRoot: AvlTreeData,
                      val boxesToSpend: IndexedSeq[ErgoBox],
                      val spendingTransaction: ErgoLikeTransactionTemplate[_ <: UnsignedInput],
                      val self: ErgoBox,
                      val metadata: Metadata,
                      override val extension: ContextExtension = ContextExtension(Map())
                 ) extends Context {
  override def withExtension(newExtension: ContextExtension): ErgoLikeContext =
    ErgoLikeContext(currentHeight, lastBlockUtxoRoot, boxesToSpend, spendingTransaction, self, metadata, newExtension)

  def withTransaction(newSpendingTransaction: ErgoLikeTransactionTemplate[_ <: UnsignedInput]): ErgoLikeContext =
    ErgoLikeContext(currentHeight, lastBlockUtxoRoot, boxesToSpend, newSpendingTransaction, self, metadata, extension)
}

object ErgoLikeContext {
  type Height = Long

  case class Metadata(networkPrefix: NetworkPrefix)

  object Metadata {
    type NetworkPrefix = Byte
    val MainnetNetworkPrefix: NetworkPrefix = 0.toByte
    val TestnetNetworkPrefix: NetworkPrefix = 16.toByte
  }

  def apply(currentHeight: Height,
            lastBlockUtxoRoot: AvlTreeData,
            boxesToSpend: IndexedSeq[ErgoBox],
            spendingTransaction: ErgoLikeTransactionTemplate[_ <: UnsignedInput],
            self: ErgoBox,
            metadata: Metadata = Metadata(TestnetNetworkPrefix),
            extension: ContextExtension = ContextExtension(Map())) =
    new ErgoLikeContext(currentHeight, lastBlockUtxoRoot, boxesToSpend, spendingTransaction, self, metadata, extension)


  def dummy(selfDesc: ErgoBox) = ErgoLikeContext(currentHeight = 0,
    lastBlockUtxoRoot = AvlTreeData.dummy, boxesToSpend = IndexedSeq(),
    spendingTransaction = null, self = selfDesc, metadata = Metadata(networkPrefix = TestnetNetworkPrefix))

  def fromTransaction(tx: ErgoLikeTransaction,
                      blockchainState: BlockchainState,
                      boxesReader: ErgoBoxReader,
                      inputIndex: Int,
                      metadata: Metadata): Try[ErgoLikeContext] = Try {

    val boxes = tx.inputs.map(_.boxId).map(id => boxesReader.byId(id).get)

    val proverExtension = tx.inputs(inputIndex).spendingProof.extension

    ErgoLikeContext(blockchainState.currentHeight,
      blockchainState.lastBlockUtxoRoot,
      boxes,
      tx,
      boxes(inputIndex),
      metadata,
      proverExtension)
  }
}

/** When interpreted evaluates to a IntConstant built from Context.currentHeight */
case object Height extends NotReadyValueLong {
  override val opCode: OpCode = OpCodes.HeightCode

  override def cost[C <: Context](context: C): Long = 2 * Cost.IntConstantDeclaration
}

/** When interpreted evaluates to a collection of BoxConstant built from Context.boxesToSpend */
case object Inputs extends LazyCollection[SBox.type] {
  override val opCode: OpCode = OpCodes.InputsCode

  override def cost[C <: Context](context: C) =
    context.asInstanceOf[ErgoLikeContext].boxesToSpend.map(_.cost).sum + Cost.ConcreteCollection

  val tpe = SCollection(SBox)
}

/** When interpreted evaluates to a collection of BoxConstant built from Context.spendingTransaction.outputs */
case object Outputs extends LazyCollection[SBox.type] {
  override val opCode: OpCode = OpCodes.OutputsCode

  override def cost[C <: Context](context: C) =
    context.asInstanceOf[ErgoLikeContext].spendingTransaction.outputs.map(_.cost).sum + Cost.ConcreteCollection

  val tpe = SCollection(SBox)
}

/** When interpreted evaluates to a AvlTreeConstant built from Context.lastBlockUtxoRoot */
case object LastBlockUtxoRootHash extends NotReadyValueAvlTree {
  override val opCode: OpCode = OpCodes.LastBlockUtxoRootHashCode

  override def cost[C <: Context](context: C) = Cost.AvlTreeConstantDeclaration + 1
}


/** When interpreted evaluates to a BoxConstant built from Context.self */
case object Self extends NotReadyValueBox {
  override val opCode: OpCode = OpCodes.SelfCode

  override def cost[C <: Context](context: C) = context.asInstanceOf[ErgoLikeContext].self.cost
}
