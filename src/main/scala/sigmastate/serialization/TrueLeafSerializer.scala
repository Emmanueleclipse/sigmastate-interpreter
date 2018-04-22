package sigmastate.serialization

import sigmastate.SBoolean
import sigmastate.SType.TypeCode
import sigmastate.Values.TrueLeaf
import sigmastate.serialization.ValueSerializer.Position
import OpCodes._

object TrueLeafSerializer extends ValueSerializer[TrueLeaf.type] {
  override val opCode: OpCode = TrueCode

  val typeCode: TypeCode = SBoolean.typeCode

  override def parseBody(bytes: Array[Byte], pos: Position) = (TrueLeaf, 0)

  override def serializeBody(obj: TrueLeaf.type) = Array[Byte]()
}
