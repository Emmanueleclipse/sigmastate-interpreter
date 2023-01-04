package special

import java.math.BigInteger

import org.bouncycastle.math.ec.ECPoint
import scalan.RType
import scalan.RType.GeneralType

import scala.reflect.classTag

/** The following implicit values are used as type descriptors of all the predefined Sigma types.
  * @see [[RType]] class
  */
package object sigma {

  implicit val BigIntRType: RType[BigInt] = GeneralType(classTag[BigInt])
  implicit val GroupElementRType: RType[GroupElement] = GeneralType(classTag[GroupElement])
  implicit val SigmaPropRType: RType[SigmaProp] = GeneralType(classTag[SigmaProp])
  implicit val AvlTreeRType:   RType[AvlTree]   = GeneralType(classTag[AvlTree])

  implicit val BoxRType:       RType[Box]       = GeneralType(classTag[Box])
  implicit val ContextRType:   RType[Context]   = GeneralType(classTag[Context])

  implicit val HeaderRType: RType[Header] = GeneralType(classTag[Header])
  implicit val PreHeaderRType: RType[PreHeader] = GeneralType(classTag[PreHeader])

  implicit val AnyValueRType: RType[AnyValue] = RType.fromClassTag(classTag[AnyValue])

  implicit val SigmaContractRType: RType[SigmaContract] = RType.fromClassTag(classTag[SigmaContract])
  implicit val SigmaDslBuilderRType: RType[SigmaDslBuilder] = RType.fromClassTag(classTag[SigmaDslBuilder])

  implicit val BigIntegerRType: RType[BigInteger] = GeneralType(classTag[BigInteger])
  implicit val ECPointRType: RType[ECPoint] = GeneralType(classTag[ECPoint])
}