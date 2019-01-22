package sigmastate.utxo.examples

import sigmastate.interpreter.Interpreter.ScriptNameProp
import org.ergoplatform.ErgoBox.{R4, R5, R6}
import org.ergoplatform._
import scorex.crypto.hash.{Blake2b256, CryptographicHash}
import sigmastate.Values.{BlockValue, ByteArrayConstant, ByteConstant, ConcreteCollection, Constant, ConstantNode, FuncValue, GroupElementConstant, IntConstant, LongConstant, SigmaPropConstant, TaggedBox, TrueLeaf, ValDef, ValUse}
import sigmastate._
import sigmastate.helpers.{ErgoLikeTestProvingInterpreter, SigmaTestingCommons}
import sigmastate.lang.Terms._
import sigmastate.utxo._


class ReversibleTxExampleSpecification extends SigmaTestingCommons {
  implicit lazy val IR = new TestingIRContext

  import ErgoAddressEncoder._

  implicit val ergoAddressEncoder: ErgoAddressEncoder = new ErgoAddressEncoder(TestnetNetworkPrefix)
  /**
    * Reversible Transaction example.
    *
    * Often lack of reversible payments is considered a drawback in Bitcoin. ErgoScript allows us to easily design
    * reversible payments.
    *
    * Use-case:
    *
    *  Consider the hot-wallet of a mining pool or an exchange. Funds withdrawn by customers originate from this hot-wallet.
    *
    *  Since its a hot-wallet, its private key can get compromised. By compromise, we imply that some unauthorized withdraws have occured.
    *  We want to ensure that in the event of such a compromise, we are able to "save" all funds stored in this wallet and move them to
    *  a "safe" address, provided that the breach is discovered within 24 hours of the first unauthorized withdraw.
    *  This is a reasonable assumption.
    *
    *  In order to achieve this, we require that all coins sent via the hot-wallet (both legitimate and by the attacker)
    *  have a 24 hour cooling off period, during which the created UTXO is "locked" and can only be spent by a trusted private key
    *  (which is different from the hot-wallet private key)
    *
    *  Once this period is over, those coins become normal and can only be spent by the customer who withdrew.
    *
    *  This is achieved by storing the hot-wallet funds only in <b>"Reversible Addresses"</b>, a special type of address.
    *
    *  The reversible address is a P2SH address created using a script that encodes our spending condition.
    *  The script requires that any UTXO created by spending this box can only be spent by the trusted party during the locking period.
    *  Thus, all funds sent from such addresses have a temporary lock.
    *
    *  Note that reversible addresses are designed for storing large amount of funds needed for automated withdraws
    *  (such as an exchange hot-wallet). They are NOT designed for storing funds for personal use (such as paying for a coffee).
    *
    * We use the following notation:
    *  Alice is the hot-wallet with public key alicePubKey
    *
    *  Bob with public key bobPubKey is a customer withdrawing from Alice. This is the normal scenario
    *
    *  Carol with public key carolPubKey is the trusted party who can spend during the locking period (i.e., reverse payments)
    *
    * Once alicePubKey is compromised (i.e, a transaction spending from this key is found to be unauthorized), an "Abort procedure"
    * is triggered. After this, all funds sent from alicePubKey are suspect and should be aborted (sent elsewhere). This is done
    * by Carol.
    *
    * For the abort, we require that all locked UTXOs be spent and the funds sent to a secure address (unrelated to alicePubKey).
    *
    * The high-level idea is as follows:
    *  Alice creates a script encoding the "reversible" logic. Lets call this the withdrawScript
    *
    *  She then creates a deposit address for the wallet using a script called depositScript, which requires that the
    *  spending condition generate a single box protected by withdrawScript.
    *
    * Note that only the outputs paying to the above deposit address can be spent in a reversible way.
    * Thus, the wallet must be topped up using only this address.
    *
    */
  property("Evaluation - Reversible Tx Example") {

    val alice = new ErgoLikeTestProvingInterpreter
    val alicePubKey = alice.dlogSecrets.head.publicImage

    val bob = new ErgoLikeTestProvingInterpreter
    val bobPubKey = bob.dlogSecrets.head.publicImage

    val carol = new ErgoLikeTestProvingInterpreter
    val carolPubKey = carol.dlogSecrets.head.publicImage

    val withdrawEnv = Map(
      ScriptNameProp -> "withdrawEnv",
      "carolPubKey" -> carolPubKey // this pub key can reverse payments
    )

    val withdrawScript = compileWithCosting(withdrawEnv,
      """{
        |  val bobPubKey   = SELF.R4[SigmaProp].get     // Bob's key (or script) that Alice sent money to
        |  val bobDeadline = SELF.R5[Int].get           // after this height, Bob gets to spend unconditionally
        |
        |  (bobPubKey && HEIGHT > bobDeadline) ||
        |  (carolPubKey && HEIGHT <= bobDeadline)       // carolPubKey hardwired via withdrawEnv
        |}""".stripMargin).asBoolValue

    val depositEnv = Map(
      ScriptNameProp -> "depositEnv",
      "alicePubKey" -> alicePubKey,
      "withdrawScriptHash" -> Blake2b256(withdrawScript.bytes)
    )

    val depositScript = compileWithCosting(depositEnv,
      """{
        |  alicePubKey &&
        |  OUTPUTS.size == 1 &&
        |  blake2b256(OUTPUTS(0).propositionBytes) == withdrawScriptHash &&
        |  OUTPUTS(0).R5[Int].get >= HEIGHT + 30       // bobDeadline stored in R5. After this height, Bob gets to spend unconditionally
        |}""".stripMargin
    ).asBoolValue

    val depositAddress = Pay2SHAddress(depositScript)
    // The above is a "reversible wallet" address.
    // Payments sent from this wallet are all reversible for a certain time

    val depositAmount = 10
    val depositHeight = 100

    // someone creates a transaction that outputs a box depositing money into the wallet.
    // In the example, we don't create the transaction; we just create a box below


    val depositOutput = ErgoBox(depositAmount, depositAddress.script, depositHeight)

    // Now Alice wants to give Bob some amount from the wallet in a "reversible" way.

    val withdrawAmount = 10
    val withdrawHeight = 101
    val bobDeadline = 150

    val reversibleWithdrawOutput = ErgoBox(withdrawAmount, withdrawScript, withdrawHeight, Nil,
      Map(
        R4 -> SigmaPropConstant(bobPubKey),
        R5 -> IntConstant(bobDeadline)
      )
    )

    //normally this transaction would be invalid (why?), but we're not checking it in this test
    val withdrawTx = ErgoLikeTransaction(IndexedSeq(), IndexedSeq(reversibleWithdrawOutput))

    val withdrawContext = ErgoLikeContext(
      currentHeight = withdrawHeight,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      minerPubkey = ErgoLikeContext.dummyPubkey,
      boxesToSpend = IndexedSeq(depositOutput),
      spendingTransaction = withdrawTx,
      self = depositOutput
    )

    val proofWithdraw = alice.prove(depositEnv, depositScript, withdrawContext, fakeMessage).get.proof

    val verifier = new ErgoLikeTestInterpreter

    verifier.verify(depositEnv, depositScript, withdrawContext, proofWithdraw, fakeMessage).get._1 shouldBe true

    // Possibility 1: Normal scenario
    // Bob spends after bobDeadline. He sends to Dave

    val dave = new ErgoLikeTestProvingInterpreter
    val davePubKey = dave.dlogSecrets.head.publicImage

    val bobSpendAmount = 10
    val bobSpendHeight = 151

    val bobSpendOutput = ErgoBox(bobSpendAmount, davePubKey, bobSpendHeight)

    //normally this transaction would be invalid (why?), but we're not checking it in this test
    val bobSpendTx = ErgoLikeTransaction(IndexedSeq(), IndexedSeq(bobSpendOutput))

    // val fakeSelf: ErgoBox = createBox(0, TrueLeaf)

    val bobSpendContext = ErgoLikeContext(
      currentHeight = bobSpendHeight,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      minerPubkey = ErgoLikeContext.dummyPubkey,
      boxesToSpend = IndexedSeq(reversibleWithdrawOutput),
      spendingTransaction = bobSpendTx,
      self = reversibleWithdrawOutput
    )

    val proofBobSpend = bob.prove(withdrawEnv, withdrawScript, bobSpendContext, fakeMessage).get.proof

    verifier.verify(withdrawEnv, withdrawScript, bobSpendContext, proofBobSpend, fakeMessage).get._1 shouldBe true

    // Possibility 2: Abort scenario
    // carol spends before bobDeadline

    val carolSpendAmount = 10
    val carolSpendHeight = 131

    // Carol sends to Dave
    val carolSpendOutput = ErgoBox(carolSpendAmount, davePubKey, carolSpendHeight)

    //normally this transaction would be invalid (why?), but we're not checking it in this test
    val carolSpendTx = ErgoLikeTransaction(IndexedSeq(), IndexedSeq(carolSpendOutput))

    val carolSpendContext = ErgoLikeContext(
      currentHeight = carolSpendHeight,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      minerPubkey = ErgoLikeContext.dummyPubkey,
      boxesToSpend = IndexedSeq(reversibleWithdrawOutput),
      spendingTransaction = carolSpendTx,
      self = reversibleWithdrawOutput
    )

    val proofCarolSpend = carol.prove(withdrawEnv, withdrawScript, carolSpendContext, fakeMessage).get.proof

    verifier.verify(withdrawEnv, withdrawScript, carolSpendContext, proofCarolSpend, fakeMessage).get._1 shouldBe true

  }
}