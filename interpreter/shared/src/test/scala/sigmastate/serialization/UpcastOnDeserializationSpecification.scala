package sigmastate.serialization

import org.ergoplatform.Outputs
import sigmastate.Values.{ByteConstant, IntConstant, LongConstant}
import sigmastate.lang.CheckingSigmaBuilder
import sigmastate.utxo.ByIndex
import sigmastate.{SInt, SLong, Upcast}

class UpcastOnDeserializationSpecification extends SerializationSpecification {
  import CheckingSigmaBuilder._

  property("Upcast deserialization round trip") {
    forAll(comparisonExprTreeNodeGen, minSuccessful(500)) { tree =>
      roundTripTest(tree)
    }
  }

  property("EQ: Upcast on deserialization") {
    val expr = mkEQ(Upcast(IntConstant(1), SLong), LongConstant(1))
    roundTripTest(expr)
  }

  property("GT: Upcast on deserialization") {
    val expr = mkGT(Upcast(IntConstant(1), SLong), LongConstant(1))
    roundTripTest(expr)
  }

  property("ByIndex: index upcast on deserialization") {
    val expr = ByIndex(Outputs, Upcast(ByteConstant(1), SInt))
    roundTripTest(expr)
  }
}
