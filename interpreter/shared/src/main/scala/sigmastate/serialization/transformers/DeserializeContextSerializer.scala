package sigmastate.serialization.transformers

import sigma.ast._
import sigmastate.serialization.ValueSerializer
import sigmastate.utils.{SigmaByteReader, SigmaByteWriter}
import sigmastate.utxo.DeserializeContext
import sigma.ast.SType
import sigma.serialization.CoreByteWriter.{ArgInfo, DataInfo}
import Operations.DeserializeContextInfo

case class DeserializeContextSerializer(cons: (Byte, SType) => Value[SType])
  extends ValueSerializer[DeserializeContext[SType]] {
  override def opDesc = DeserializeContext
  val typeInfo: DataInfo[SType] = ArgInfo("type", "expected type of the deserialized script")
  val idInfo: DataInfo[Byte] = DeserializeContextInfo.idArg

  override def serialize(obj: DeserializeContext[SType], w: SigmaByteWriter): Unit =
    w.putType(obj.tpe, typeInfo)
      .put(obj.id, idInfo)

  override def parse(r: SigmaByteReader): Value[SType] = {
    val tpe = r.getType()
    val id = r.getByte()
    r.wasDeserialize ||= true // mark the flag
    cons(id, tpe)
  }
}
