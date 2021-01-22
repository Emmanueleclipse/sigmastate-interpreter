package sigmastate.utxo

import sigmastate._
import sigmastate.helpers.{ContextEnrichingTestProvingInterpreter, ErgoLikeTestProvingInterpreter, SigmaTestingCommons}
import sigmastate.interpreter._
import sigmastate.lang.Terms._

/**
  * Distributed signatures examples.
  * See EIP-11 for generic signing procedure.
  * In some simple generic procedure is simplified.
  */
class DistributedSigSpecification extends SigmaTestingCommons
  with CrossVersionProps {

  implicit lazy val IR: TestingIRContext = new TestingIRContext

  /**
    * An example test where Alice (A) and Bob (B) are signing an input in a distributed way. A statement which
    * protects the box to spend is "pubkey_Alice && pubkey_Bob". Note that a signature in this case is about
    * a transcript of a Sigma-protocol ((a_Alice, a_Bob), e, (z_Alice, z_Bob)),
    * which is done in non-interactive way (thus "e" is got via a Fiat-Shamir transformation).
    *
    * For that, they are going through following steps:
    *
    * - Bob is generating first protocol message a_Bob and sends it to Alice
    * - Alice forms a hint which contain Bob's commitment "a_Bob", and puts the hint into a hints bag
    * - She proves the statement using the bag, getting the partial protocol transcript
    * (a_Alice, e, z_Alice) as a result and sends "a_Alice" and "z_Alice" to Bob.
    * Please note that "e" is got from both a_Alice and a_Bob.
    *
    * - Bob now also knows a_Alice, so can generate the same "e" as Alice. Thus Bob is generating valid
    * proof ((a_Alice, a_Bob), e, (z_Alice, z_Bob)).
    */
  property("distributed AND (2 out of 2)") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val verifier: ContextEnrichingTestProvingInterpreter = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob)
    val prop = mkTestErgoTree(compile(env, """pubkeyA && pubkeyB""").asSigmaProp)

    val hintsFromBob: HintsBag = proverB.generateCommitments(prop, ctx)
    val bagA = HintsBag(hintsFromBob.realCommitments)

    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    val bagB = proverB.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice))
      .addHint(hintsFromBob.ownCommitments.head)

    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    // Compound proof from Bob is correct
    verifier.verify(prop, ctx, proofBob, fakeMessage).get._1 shouldBe true
  }

  //  3-out-of-3 AND signature
  property("distributed AND (3 out of 3)") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val verifier: ContextEnrichingTestProvingInterpreter = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage
    val pubkeyCarol = proverC.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol)
    val prop = mkTestErgoTree(compile(env, """pubkeyA && pubkeyB && pubkeyC""").asSigmaProp)

    val bobHints = proverB.generateCommitments(prop, ctx)
    val carolHints = proverC.generateCommitments(prop, ctx)

    val dlBKnown: Hint = bobHints.realCommitments.head
    val dlCKnown: Hint = carolHints.realCommitments.head
    val bagA = HintsBag(Seq(dlBKnown, dlCKnown))

    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    val bagC = proverB.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice))
      .addHint(carolHints.ownCommitments.head)
      .addHint(dlBKnown)

    val proofCarol = proverC.prove(prop, ctx, fakeMessage, bagC).get

    val bagB = proverB.bagForMultisig(ctx, prop, proofCarol.proof, Seq(pubkeyAlice, pubkeyCarol))
      .addHint(bobHints.ownCommitments.head)

    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofCarol, fakeMessage).get._1 shouldBe false

    // Compound proof from Bob is correct
    verifier.verify(prop, ctx, proofBob, fakeMessage).get._1 shouldBe true
  }


  /**
    * An example test where Alice (A), Bob (B) and Carol (C) are signing in a distributed way an input, which is
    * protected by 2-out-of-3 threshold multi-signature.
    *
    * A statement which protects the box to spend is "atLeast(2, Coll(pubkeyA, pubkeyB, pubkeyC))".
    *
    * A scheme for multisigning is following:
    *
    *   - Bob is generating first protocol message (commitment to his randomness) "a" and sends it to Alice
    *   - Alice is generating her proof having Bob's "a" as a hint. She then puts Bob's randomness and fill his
    *     response with zero bits. Thus Alice's signature is not valid. Alice is sending her signature to Bob.
    *   - Bob is extracting Alice's commitment to randomness and response, and also Carol's commitment and response.
    *     He's using his randomness from his first step and completes the (valid) signature.
    */
  property("distributed THRESHOLD - 2 out of 3") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage
    val pubkeyCarol = proverC.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol)
    val prop = mkTestErgoTree(
      compile(env, """atLeast(2, Coll(pubkeyA, pubkeyB, pubkeyC))""").asSigmaProp)

    val bobHints = proverB.generateCommitments(prop, ctx)
    val dlBKnown: Hint = bobHints.realCommitments.head

    val bagA = HintsBag(Seq(dlBKnown))
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    val bagB = proverB.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq(pubkeyCarol))
      .addHint(bobHints.ownCommitments.head)

    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    // Compound proof from Bob is correct
    verifier.verify(prop, ctx, proofBob, fakeMessage).get._1 shouldBe true
  }

  /**
    * Distributed threshold signature, 3 out of 4 case.
    */
  property("distributed THRESHOLD - 3 out of 4") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val proverD = new ErgoLikeTestProvingInterpreter
    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage
    val pubkeyCarol = proverC.dlogSecrets.head.publicImage
    val pubkeyDave = proverD.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol, "pubkeyD" -> pubkeyDave)
    val prop = mkTestErgoTree(
      compile(env, """atLeast(3, Coll(pubkeyA, pubkeyB, pubkeyC, pubkeyD))""").asSigmaProp)

    // Alice, Bob and Carol are signing
    val bobHints = proverB.generateCommitments(prop, ctx)
    val dlBKnown: Hint = bobHints.realCommitments.head

    val carolHints = proverC.generateCommitments(prop, ctx)
    val dlCKnown: Hint = carolHints.realCommitments.head

    val bagA = HintsBag(Seq(dlBKnown, dlCKnown))
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    val bagC = proverC.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq(pubkeyDave)) ++
      HintsBag(Seq(dlBKnown, carolHints.ownCommitments.head))
    val proofCarol = proverC.prove(prop, ctx, fakeMessage, bagC).get

    val bagB = (proverB.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq(pubkeyDave)) ++
                proverB.bagForMultisig(ctx, prop, proofCarol.proof, Seq(pubkeyCarol)))
                  .addHint(bobHints.ownCommitments.head)

    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofCarol, fakeMessage).get._1 shouldBe false

    // Compound proof from Bob is correct
    verifier.verify(prop, ctx, proofBob, fakeMessage).get._1 shouldBe true
  }

  /**
    * Distributed threshold signature, 3 out of 4 case, 1 real and 1 simulated secrets are of DH kind.
    */
  property("distributed THRESHOLD - 3 out of 4 - w. DH") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val proverD = new ErgoLikeTestProvingInterpreter
    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dhSecrets.head.publicImage
    val pubkeyCarol = proverC.dhSecrets.head.publicImage
    val pubkeyDave = proverD.dhSecrets.head.publicImage

    val env = Map(
      "pubkeyA" -> pubkeyAlice,
      "pubkeyB" -> pubkeyBob,
      "pubkeyC" -> pubkeyCarol,
      "pubkeyD" -> pubkeyDave)
    val prop = mkTestErgoTree(
      compile(env, """atLeast(3, Coll(pubkeyA, pubkeyB, pubkeyC, pubkeyD))""").asSigmaProp)

    // Alice, Bob and Carol are signing
    val bobHints = proverB.generateCommitments(prop, ctx)
    val dlBKnown: Hint = bobHints.realCommitments.head

    val carolHints = proverC.generateCommitments(prop, ctx)
    val dlCKnown: Hint = carolHints.realCommitments.head

    val bagA = HintsBag(Seq(dlBKnown, dlCKnown))
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    val bagC = proverC.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq(pubkeyDave)) ++
      HintsBag(Seq(dlBKnown, carolHints.ownCommitments.head))
    val proofCarol = proverC.prove(prop, ctx, fakeMessage, bagC).get

    val bagB = (proverB.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq(pubkeyDave)) ++
                proverB.bagForMultisig(ctx, prop, proofCarol.proof, Seq(pubkeyCarol)))
                  .addHint(bobHints.ownCommitments.head)

    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    // Proof generated by Alice without getting Bob's part is not correct
    verifier.verify(prop, ctx, proofCarol, fakeMessage).get._1 shouldBe false

    // Compound proof from Bob is correct
    verifier.verify(prop, ctx, proofBob, fakeMessage).get._1 shouldBe true
  }

  property("distributed THRESHOLD - 2 out of 5") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val proverD = new ErgoLikeTestProvingInterpreter
    val proverE = new ErgoLikeTestProvingInterpreter
    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage
    val pubkeyCarol = proverC.dlogSecrets.head.publicImage
    val pubkeyDave = proverD.dlogSecrets.head.publicImage
    val pubkeyEmma = proverE.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol,
                  "pubkeyD" -> pubkeyDave, "pubkeyE" -> pubkeyEmma)
    val prop = mkTestErgoTree(compile(env,
      """atLeast(2, Coll(pubkeyA, pubkeyB, pubkeyC, pubkeyD, pubkeyE))""").asSigmaProp)

    //Alice and Dave are signing
    val daveHints = proverD.generateCommitments(prop, ctx)
    val dlDKnown: Hint = daveHints.realCommitments.head

    val bagA = HintsBag(Seq(dlDKnown))
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    // Proof generated by Alice without interaction w. Dave is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    val bagD = proverD
                .bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq(pubkeyBob, pubkeyCarol, pubkeyEmma))
                .addHint(daveHints.ownCommitments.head)

    val proofDave = proverD.prove(prop, ctx, fakeMessage, bagD).get
    verifier.verify(prop, ctx, proofDave, fakeMessage).get._1 shouldBe true
  }

  property("distributed THRESHOLD - 4 out of 8 - DH") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val proverD = new ErgoLikeTestProvingInterpreter
    val proverE = new ErgoLikeTestProvingInterpreter
    val proverF = new ErgoLikeTestProvingInterpreter
    val proverG = new ErgoLikeTestProvingInterpreter
    val proverH = new ErgoLikeTestProvingInterpreter
    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dhSecrets.head.publicImage
    val pubkeyBob = proverB.dhSecrets.head.publicImage
    val pubkeyCarol = proverC.dhSecrets.head.publicImage
    val pubkeyDave = proverD.dhSecrets.head.publicImage
    val pubkeyEmma = proverE.dhSecrets.head.publicImage
    val pubkeyFrank = proverF.dhSecrets.head.publicImage
    val pubkeyGerard = proverG.dhSecrets.head.publicImage
    val pubkeyHannah = proverH.dhSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol,
      "pubkeyD" -> pubkeyDave, "pubkeyE" -> pubkeyEmma, "pubkeyF" -> pubkeyFrank,
      "pubkeyG" -> pubkeyGerard, "pubkeyH" -> pubkeyHannah)
    val script = """atLeast(4, Coll(pubkeyA, pubkeyB, pubkeyC, pubkeyD, pubkeyE, pubkeyF, pubkeyG, pubkeyH))"""
    val prop = mkTestErgoTree(compile(env, script).asSigmaProp)

    // Alice, Bob, Gerard, and Hannah are signing, others are simulated

    // first, commitments are needed from real signers

    val aliceHints = proverA.generateCommitments(prop, ctx)
    val dlAKnown: Hint = aliceHints.realCommitments.head
    val secretCmtA: Hint = aliceHints.ownCommitments.head

    val bobHints = proverB.generateCommitments(prop, ctx)
    val dlBKnown: Hint = bobHints.realCommitments.head
    val secretCmtB: Hint = bobHints.ownCommitments.head

    val gerardHints = proverG.generateCommitments(prop, ctx)
    val dlGKnown: Hint = gerardHints.realCommitments.head
    val secretCmtG: Hint = gerardHints.ownCommitments.head

    val hannahHints = proverH.generateCommitments(prop, ctx)
    val secretCmtH: Hint = hannahHints.ownCommitments.head

    val bagH = HintsBag(Seq(dlAKnown, dlBKnown, dlGKnown, secretCmtH))
    val proofHannah = proverH.prove(prop, ctx, fakeMessage, bagH).get

    // Proof generated by Hannah only is not correct
    verifier.verify(prop, ctx, proofHannah, fakeMessage).get._1 shouldBe false

    //hints after the first real proof done.
    val bag1 = proverH
                .bagForMultisig(ctx, prop, proofHannah.proof,
                  Seq(pubkeyHannah),
                  Seq(pubkeyCarol, pubkeyDave, pubkeyEmma, pubkeyFrank))

    //now real proofs can be done in any order
    val bagB = bag1.addHints(secretCmtB, dlAKnown, dlGKnown)
    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get
    val partialBobProofBag = proverB.bagForMultisig(ctx, prop, proofBob.proof, Seq(pubkeyBob), Seq.empty).realProofs.head

    val bagA = bag1.addHints(secretCmtA, dlBKnown, dlGKnown)
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get
    val partialAliceProofBag = proverA.bagForMultisig(ctx, prop, proofAlice.proof, Seq(pubkeyAlice), Seq.empty).realProofs.head

    val bagG = bag1.addHints(secretCmtG, dlAKnown, dlBKnown)
    val proofGerard = proverG.prove(prop, ctx, fakeMessage, bagG).get
    val partialGerardProofBag = proverG.bagForMultisig(ctx, prop, proofGerard.proof, Seq(pubkeyGerard), Seq.empty).realProofs.head

    val bag = bag1
      .addHints(partialAliceProofBag, partialBobProofBag, partialGerardProofBag)
      .addHints(dlAKnown, dlBKnown, dlGKnown)

    val validProofA = proverA.prove(prop, ctx, fakeMessage, bag.addHint(secretCmtA)).get
    verifier.verify(prop, ctx, validProofA, fakeMessage).get._1 shouldBe true

    val validProofB = proverB.prove(prop, ctx, fakeMessage, bag.addHint(secretCmtB)).get
    verifier.verify(prop, ctx, validProofB, fakeMessage).get._1 shouldBe true

    val validProofG = proverG.prove(prop, ctx, fakeMessage, bag.addHint(secretCmtG)).get
    verifier.verify(prop, ctx, validProofG, fakeMessage).get._1 shouldBe true

    val validProofH = proverH.prove(prop, ctx, fakeMessage, bag.addHint(secretCmtH)).get
    verifier.verify(prop, ctx, validProofH, fakeMessage).get._1 shouldBe true

    validProofA.proof.sameElements(validProofB.proof) shouldBe true
    validProofB.proof.sameElements(validProofG.proof) shouldBe true
    validProofG.proof.sameElements(validProofH.proof) shouldBe true
  }

  property("distributed THRESHOLD - (1-out-of-2) and (1-out-of-2) - DLOG and DH") {
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val proverD = new ErgoLikeTestProvingInterpreter

    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage
    val pubkeyCarol = proverC.dhSecrets.head.publicImage
    val pubkeyDave = proverD.dhSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol, "pubkeyD" -> pubkeyDave)
    val script = """(pubkeyA || pubkeyB) && (pubkeyC || pubkeyD)"""
    val prop = mkTestErgoTree(compile(env, script).asSigmaProp)

    //Alice and Dave are signing

    //first, commitments are needed from real signers
    val aliceHints = proverA.generateCommitments(prop, ctx)
    println(aliceHints)
    val secretCmtA: Hint = aliceHints.ownCommitments.head

    val daveHints = proverD.generateCommitments(prop, ctx)
    val dlDKnown: Hint = daveHints.realCommitments.head
    val secretCmtD: Hint = daveHints.ownCommitments.head

    val bagA = HintsBag(Seq(secretCmtA, dlDKnown))
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    // Proof generated by Alice only is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    val bag = proverA.bagForMultisig(ctx, prop, proofAlice.proof,
        Seq(pubkeyAlice, pubkeyDave), Seq(pubkeyBob, pubkeyCarol))

    val validProofD = proverD.prove(prop, ctx, fakeMessage, bag.addHint(secretCmtD)).get
    verifier.verify(prop, ctx, validProofD, fakeMessage).get._1 shouldBe true
  }

  property("distributed THRESHOLD mixed via AND") {
    // atLeast(3, Coll(proveDlog(pkA), proveDlog(pkB), proveDlog(pkC), proveDlog(pkD), proveDlog(pkE))) && (proveDlog(pkB) || proveDlog(pkF))
    val ctx = fakeContext
    val proverA = new ErgoLikeTestProvingInterpreter
    val proverB = new ErgoLikeTestProvingInterpreter
    val proverC = new ErgoLikeTestProvingInterpreter
    val proverD = new ErgoLikeTestProvingInterpreter
    val proverE = new ErgoLikeTestProvingInterpreter
    val proverF = new ErgoLikeTestProvingInterpreter

    val verifier = new ContextEnrichingTestProvingInterpreter

    val pubkeyAlice = proverA.dlogSecrets.head.publicImage
    val pubkeyBob = proverB.dlogSecrets.head.publicImage
    val pubkeyCarol = proverC.dlogSecrets.head.publicImage
    val pubkeyDave = proverD.dlogSecrets.head.publicImage
    val pubkeyEmma = proverE.dlogSecrets.head.publicImage
    val pubkeyFrank = proverF.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyAlice, "pubkeyB" -> pubkeyBob, "pubkeyC" -> pubkeyCarol,
                  "pubkeyD" -> pubkeyDave, "pubkeyE" -> pubkeyEmma, "pubkeyF" -> pubkeyFrank)
    val script =
      """atLeast(3, Coll(pubkeyA, pubkeyB, pubkeyC, pubkeyD, pubkeyE)) && (pubkeyB || pubkeyF)""".stripMargin
    val prop = mkTestErgoTree(compile(env, script).asSigmaProp)
    // Alice, Bob and Emma are signing

    // first, commitments are needed from real signers
    val aliceHints = proverA.generateCommitments(prop, ctx)
    val dlAKnown: Hint = aliceHints.realCommitments.head
    val secretCmtA: Hint = aliceHints.ownCommitments.head

    val bobHints = proverB.generateCommitments(prop, ctx)
    val dlBKnown: Seq[Hint] = bobHints.realCommitments
    val secretCmtB: Seq[Hint] = bobHints.ownCommitments

    val emmaHints = proverE.generateCommitments(prop, ctx)
    val dlEKnown: Hint = emmaHints.realCommitments.head
    val secretCmtE: Hint = emmaHints.ownCommitments.head

    val bagA = HintsBag(Seq(secretCmtA,  dlEKnown) ++ dlBKnown)
    val proofAlice = proverA.prove(prop, ctx, fakeMessage, bagA).get

    // Proof generated by Alice only is not correct
    verifier.verify(prop, ctx, proofAlice, fakeMessage).get._1 shouldBe false

    val bag0 = proverA
      .bagForMultisig(ctx, prop, proofAlice.proof,
        Seq(pubkeyAlice),
        Seq(pubkeyCarol, pubkeyDave, pubkeyFrank))

    //now real proofs can be done in any order
    val bagB = bag0.addHints(dlAKnown, dlEKnown).addHints(secretCmtB :_*)
    val proofBob = proverB.prove(prop, ctx, fakeMessage, bagB).get
    val partialBobProofBag = proverB.bagForMultisig(ctx, prop, proofBob.proof, Seq(pubkeyBob), Seq.empty).realProofs

    val bagE = bag0.addHints(secretCmtE, dlAKnown).addHints(dlBKnown :_*)
    val proofEmma = proverE.prove(prop, ctx, fakeMessage, bagE).get
    val partialEmmaProofBag = proverE.bagForMultisig(ctx, prop, proofEmma.proof, Seq(pubkeyEmma), Seq.empty).realProofs.head

    val bag = bag0
      .addHints(partialBobProofBag: _*)
      .addHints(partialEmmaProofBag)
      .addHints(dlAKnown, dlEKnown).addHints(dlBKnown :_*)
      .addHints(secretCmtB :_*)

    // Bob is generating a valid signature
    val validProofB = proverB.prove(prop, ctx, fakeMessage, bag).get
    verifier.verify(prop, ctx, validProofB, fakeMessage).get._1 shouldBe true
  }

}
