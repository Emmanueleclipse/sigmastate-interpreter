package org.ergoplatform

import scorex.crypto.authds.ADKey
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.interpreter.ProverResult
import sigmastate.serialization.Serializer

import scala.util.Try
import org.ergoplatform.ErgoBox.BoxId
import sigmastate.utils.{ByteReader, ByteWriter}


trait ErgoBoxReader {
  def byId(boxId: ADKey): Try[ErgoBox]
}


trait ErgoLikeTransactionTemplate[IT <: UnsignedInput] {
  val inputs: IndexedSeq[IT]
  val outputCandidates: IndexedSeq[ErgoBoxCandidate]

  require(outputCandidates.size <= Short.MaxValue)

  type IdType <: Array[Byte]

  val id: IdType

  lazy val outputs: IndexedSeq[ErgoBox] =
    outputCandidates.indices.map(idx => outputCandidates(idx).toBox(id, idx.toShort))

  lazy val messageToSign: Array[Byte] =
    ErgoLikeTransaction.flattenedTxSerializer.bytesToSign(inputs.map(_.boxId), outputCandidates)

  lazy val inputIds: IndexedSeq[ADKey] = inputs.map(_.boxId)
}


class UnsignedErgoLikeTransaction(override val inputs: IndexedSeq[UnsignedInput],
                                  override val outputCandidates: IndexedSeq[ErgoBoxCandidate])
  extends ErgoLikeTransactionTemplate[UnsignedInput] {

  override type IdType = Digest32

  override lazy val id: IdType = Blake2b256.hash(messageToSign)

  def toSigned(proofs: IndexedSeq[ProverResult]): ErgoLikeTransaction = {
    require(proofs.size == inputs.size)
    val ins = inputs.zip(proofs).map { case (ui, proof) => Input(ui.boxId, proof) }
    new ErgoLikeTransaction(ins, outputCandidates)
  }
}

object UnsignedErgoLikeTransaction {
  def apply(inputs: IndexedSeq[UnsignedInput], outputCandidates: IndexedSeq[ErgoBoxCandidate]) =
    new UnsignedErgoLikeTransaction(inputs, outputCandidates)
}

/**
  * Fully signed transaction
  *
  * @param inputs
  * @param outputCandidates
  */
class ErgoLikeTransaction(override val inputs: IndexedSeq[Input],
                          override val outputCandidates: IndexedSeq[ErgoBoxCandidate])
  extends ErgoLikeTransactionTemplate[Input] {

  require(outputCandidates.length <= Short.MaxValue, s"${Short.MaxValue} is the maximum number of outputs")

  override type IdType = Digest32

  override lazy val id: IdType = Blake2b256.hash(messageToSign)

  override def equals(obj: Any): Boolean = obj match {
    case tx: ErgoLikeTransaction => this.id sameElements tx.id  //we're ignoring spending proofs here
    case _ => false
  }
}


object ErgoLikeTransaction {

  val TransactionIdSize: Short = 32

  def apply(inputs: IndexedSeq[Input], outputCandidates: IndexedSeq[ErgoBoxCandidate]) =
    new ErgoLikeTransaction(inputs, outputCandidates)

  def apply(ftx: FlattenedTransaction): ErgoLikeTransaction =
    new ErgoLikeTransaction(ftx.inputs, ftx.outputCandidates)

  case class FlattenedTransaction(inputs: IndexedSeq[Input],
                                  outputCandidates: IndexedSeq[ErgoBoxCandidate])

  object FlattenedTransaction {
    def apply(tx: ErgoLikeTransaction): FlattenedTransaction =
      FlattenedTransaction(tx.inputs, tx.outputCandidates)
  }

  object flattenedTxSerializer extends Serializer[FlattenedTransaction, FlattenedTransaction] {

    def bytesToSign(inputs: IndexedSeq[ADKey],
                    outputCandidates: IndexedSeq[ErgoBoxCandidate]): Array[Byte] = {
      //todo: set initial capacity
      val w = Serializer.startWriter()

      val inputsCount = inputs.size.toShort
      val outputsCount = outputCandidates.size.toShort

      w.putUShort(inputsCount)
      inputs.foreach { i =>
        w.putBytes(i)
      }
      w.putUShort(outputsCount)
      outputCandidates.foreach{ c =>
        ErgoBoxCandidate.serializer.serializeBody(c, w)
      }

      w.toBytes
    }

    def bytesToSign(tx: UnsignedErgoLikeTransaction): Array[Byte] =
      bytesToSign(tx.inputs.map(_.boxId), tx.outputCandidates)

    def bytesToSign(tx: ErgoLikeTransaction): Array[Byte] =
      bytesToSign(tx.inputs.map(_.boxId), tx.outputCandidates)

    override def serializeBody(ftx: FlattenedTransaction, w: ByteWriter): Unit = {
      w.putBytes(bytesToSign(ftx.inputs.map(_.boxId), ftx.outputCandidates))
      ftx.inputs.map(_.spendingProof).foreach { proof =>
        ProverResult.serializer.serializeBody(proof, w)
      }
    }

    override def parseBody(r: ByteReader): FlattenedTransaction = {
      val inputsCount = r.getUShort()
      val inputs = (0 until inputsCount).map { _ =>
        ADKey @@ r.getBytes(BoxId.size)
      }
      val outsCount = r.getUShort()
      val outputs = (0 until outsCount).map { _ =>
        ErgoBoxCandidate.serializer.parseBody(r)
      }

      val proofs = (0 until inputsCount).map { _ =>
        ProverResult.serializer.parseBody(r)
      }

      val signedInputs = inputs.zip(proofs).map { case (inp, pr) =>
        Input(inp, pr)
      }
      FlattenedTransaction(signedInputs, outputs)
    }
  }

  object serializer extends Serializer[ErgoLikeTransaction, ErgoLikeTransaction] {

    override def serializeBody(tx: ErgoLikeTransaction, w: ByteWriter): Unit =
      flattenedTxSerializer.serializeBody(FlattenedTransaction(tx), w)

    override def parseBody(r: ByteReader): ErgoLikeTransaction =
      ErgoLikeTransaction(flattenedTxSerializer.parseBody(r))
  }
}
