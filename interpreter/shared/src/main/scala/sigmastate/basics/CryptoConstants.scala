package sigmastate.basics

import java.math.BigInteger
import sigmastate.crypto.Ecp

import scala.util.Random

object CryptoConstants {
  type EcPointType = Ecp

  val EncodedGroupElementLength: Byte = 33

  val dlogGroup: BcDlogGroup = SecP256K1Group

  val secureRandom: Random = dlogGroup.secureRandom

  /** Size of the binary representation of any group element (2 ^ groupSizeBits == <number of elements in a group>) */
  val groupSizeBits: Int = 256

  /** Number of bytes to represent any group element as byte array */
  val groupSize: Int = 256 / 8 //32 bytes

  /** Group order, i.e. number of elements in the group */
  val groupOrder: BigInteger = dlogGroup.order

  /** Length of hash function used in the signature scheme. Blake2b hash function is used. */
  val hashLengthBits = 256

  val hashLength: Int = hashLengthBits / 8

  /** A size of challenge in Sigma protocols, in bits.
    * If this anything but 192, threshold won't work, because we have polynomials over GF(2^192) and no others.
    * So DO NOT change the value without implementing polynomials over GF(2^soundnessBits) first
    * and changing code that calls on GF2_192 and GF2_192_Poly classes!!!
    * We get the challenge by reducing hash function output to proper value.
    */
  implicit val soundnessBits: Int = 192.ensuring(_ < groupSizeBits, "2^t < q condition is broken!")

  def secureRandomBytes(howMany: Int): Array[Byte] = {
    val bytes = new Array[Byte](howMany)
    secureRandom.nextBytes(bytes)
    bytes
  }
}
