package sigmastate.serialization

import sigmastate.{ArgInfo, SType}
import sigmastate.Values._
import sigmastate.utils.{SigmaByteReader, SigmaByteWriter}
import ValueSerializer._
import sigmastate.util.safeNewArray
import sigmastate.utils.SigmaByteWriter.{DataInfo, U}
import debox.cfor

case class TupleSerializer(cons: Seq[Value[SType]] => Value[SType])
  extends ValueSerializer[Tuple] {
  override def opDesc = Tuple
  val numItemsInfo: DataInfo[U[Byte]] = ArgInfo("numItems", "number of items in the tuple")
  val itemInfo: DataInfo[SValue] = ArgInfo("item_i", "tuple's item in i-th position")

  override def serialize(obj: Tuple, w: SigmaByteWriter): Unit = {
    // TODO refactor: avoid usage of extension method `length`
    val length = obj.length
    w.putUByte(length, numItemsInfo)
    foreach(numItemsInfo.info.name, obj.items) { i =>
      w.putValue(i, itemInfo)
    }
  }

  override def parse(r: SigmaByteReader): Value[SType] = {
    val size = r.getByte()
    val values = safeNewArray[SValue](size) // assume size > 0 so always create a new array
    cfor(0)(_ < size, _ + 1) { i =>
      values(i) = r.getValue()
    }
    cons(values)
  }

}
