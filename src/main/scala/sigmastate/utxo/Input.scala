package sigmastate.utxo

import scorex.crypto.authds.ADKey
import sigmastate.interpreter.SerializedProverResult
import sigmastate.serialization.Serializer
import sigmastate.serialization.Serializer.{Consumed, Position}

import scala.util.Try


class UnsignedInput(val boxId: ADKey)

object UnsignedInput {
  object serializer extends Serializer[UnsignedInput, UnsignedInput] {

    @inline
    override def toBytes(input: UnsignedInput): Array[Byte] = serializeBody(input)

    override def parseBytes(bytes: Array[Byte]): Try[UnsignedInput] =
      Try(parseBody(bytes, 0)._1)

    override def parseBody(bytes: Array[Byte], pos: Position): (UnsignedInput, Consumed) = {
      new UnsignedInput(ADKey @@ bytes.slice(pos, pos+32)) -> 32
    }

    @inline
    override def serializeBody(input: UnsignedInput): Array[Byte] = input.boxId
  }
}

case class Input(override val boxId: ADKey, spendingProof: SerializedProverResult)
  extends UnsignedInput(boxId) {
}

object Input {
  object serializer extends Serializer[Input, Input] {
    override def toBytes(input: Input): Array[Byte] = {
      input.boxId ++ SerializedProverResult.serializer.toBytes(input.spendingProof)
    }

    override def parseBody(bytes: Array[Byte], pos: Position): (Input, Consumed) = {
      val boxId = bytes.slice(pos, pos + 32)
      val (spendingProof, consumed) = SerializedProverResult.serializer.parseBody(bytes, pos + 32)
      Input(ADKey @@ boxId, spendingProof) -> (consumed + 32)
    }
  }
}