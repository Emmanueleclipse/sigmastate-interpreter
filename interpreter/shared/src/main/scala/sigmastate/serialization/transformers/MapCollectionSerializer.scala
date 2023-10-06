package sigmastate.serialization.transformers

import sigma.ast.global.SValue
import sigma.ast.Value
import sigmastate.lang.Terms._
import sigmastate.serialization.ValueSerializer
import sigmastate.utils.SigmaByteWriter._
import sigmastate.utils.{SigmaByteReader, SigmaByteWriter}
import sigmastate.utxo.MapCollection
import sigma.ast.{SCollection, SFunc, SType}
import sigma.serialization.CoreByteWriter.DataInfo

case class MapCollectionSerializer(cons: (Value[SCollection[SType]], Value[SFunc]) => Value[SType])
  extends ValueSerializer[MapCollection[SType, SType]] {
  import sigma.ast.Operations.MapCollectionInfo._
  override def opDesc = MapCollection
  val thisInfo: DataInfo[SValue] = thisArg
  val fInfo: DataInfo[SValue] = fArg

  override def serialize(obj: MapCollection[SType, SType], w: SigmaByteWriter): Unit =
    w.putValue(obj.input, thisInfo)
      .putValue(obj.mapper, fInfo)

  override def parse(r: SigmaByteReader): Value[SType] = {
    val input = r.getValue().asValue[SCollection[SType]]
    val mapper = r.getValue().asFunc
    cons(input, mapper)
  }

}
