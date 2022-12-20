package sigmastate.helpers

import org.ergoplatform.SigmaConstants.ScriptCostLimit
import org.ergoplatform._
import org.ergoplatform.validation.ValidationRules.CheckSerializableTypeCode
import org.ergoplatform.validation.{ValidationException, ValidationSpecification}
import org.scalacheck.Arbitrary.arbByte
import org.scalacheck.Gen
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Assertion, Matchers, PropSpec}
import scalan.util.BenchmarkUtil
import scalan.{RType, TestContexts, TestUtils}
import scorex.crypto.hash.Blake2b256
import sigmastate.Values.{Constant, ErgoTree, GroupElementConstant, SValue, SigmaBoolean, SigmaPropValue}
import sigmastate.eval.{Evaluation, IRContext, _}
import sigmastate.helpers.TestingHelpers._
import sigmastate.interpreter.ContextExtension.VarBinding
import sigmastate.interpreter.CryptoConstants.EcPointType
import sigmastate.interpreter.ErgoTreeEvaluator.DefaultProfiler
import sigmastate.interpreter.Interpreter.ScriptEnv
import sigmastate.interpreter._
import sigmastate.lang.{CompilerSettings, SigmaCompiler, Terms}
import sigmastate.serialization.SigmaSerializer
import sigmastate.{JitCost, SOption, SType, TestsBase}

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.DynamicVariable

trait SigmaTestingCommons extends PropSpec
  with PropertyChecks
  with GeneratorDrivenPropertyChecks
  with Matchers with TestUtils with TestContexts with ValidationSpecification
  with NegativeTesting
  with TestsBase {

  def fakeSelf: ErgoBox = createBox(0, TrueTree)

  def fakeContext: ErgoLikeContext =
    ErgoLikeContextTesting.dummy(fakeSelf, activatedVersionInTests)
        .withErgoTreeVersion(ergoTreeVersionInTests)

  //fake message, in a real-life a message is to be derived from a spending transaction
  val fakeMessage = Blake2b256("Hello World")

  implicit def grElemConvert(leafConstant: GroupElementConstant): EcPointType =
    SigmaDsl.toECPoint(leafConstant.value).asInstanceOf[EcPointType]

  class TestingIRContext extends TestContext with IRContext {
  }

  case class CompiledFunc[A,B]
    (script: String, bindings: Seq[VarBinding], expr: SValue, compiledTree: SValue, func: A => (B, CostDetails))
    (implicit val tA: RType[A], val tB: RType[B]) extends Function1[A, (B, CostDetails)] {
    override def apply(x: A): (B, CostDetails) = func(x)
  }

  /** This value is used as Context.initCost value. The default value is used for most
    * test vectors.
    * Change this value using `withValue` method to test behavior with non-default
    * initial cost.
    */
  protected val initialCostInTests = new DynamicVariable[Long](0)

  def createContexts[A](in: A, bindings: Seq[VarBinding])(implicit tA: RType[A]) = {
    val tpeA = Evaluation.rtypeToSType(tA)
    in match {
      case ctx: CostingDataContext =>
        // the context is passed as function argument (this is for testing only)
        // This is to overcome non-functional semantics of context operations
        // (such as Inputs, Height, etc which don't have arguments and refer to the
        // context implicitly).
        // These context operations are introduced by buildTree frontend function
        // (ctx.HEIGHT method call compiled to Height IR node)
        // -------
        // We add ctx as it's own variable with id = 1
        val ctxVar = Extensions.toAnyValue[special.sigma.Context](ctx)(special.sigma.ContextRType)
        val newVars = if (ctx.vars.length < 2) {
          val vars = ctx.vars.toArray
          val buf = new Array[special.sigma.AnyValue](2)
          Array.copy(vars, 0, buf, 0, vars.length)
          buf(1) = ctxVar
          CostingSigmaDslBuilder.Colls.fromArray(buf)
        } else {
          ctx.vars.updated(1, ctxVar)
        }
        val calcCtx = ctx.copy(vars = newVars)
        calcCtx
      case _ =>
        val box = createBox(0, TrueTree)

        // make sure we are doing tests with the box with is actually serializable
        try roundTripTest(box)(ErgoBox.sigmaSerializer)
        catch {
          case ValidationException(_, r: CheckSerializableTypeCode.type, Seq(SOption.OptionTypeCode), _) =>
          // ignore the problem with Option serialization, but test all the other cases
        }

        val ergoCtx = ErgoLikeContextTesting.dummy(box, activatedVersionInTests)
          .withErgoTreeVersion(ergoTreeVersionInTests)
          .withBindings(1.toByte -> Constant[SType](in.asInstanceOf[SType#WrappedType], tpeA))
          .withBindings(bindings: _*)
        val calcCtx = ergoCtx.toSigmaContext().asInstanceOf[CostingDataContext]
        calcCtx
    }
  }

  def compileTestScript[A]
      (env: ScriptEnv, funcScript: String)
      (implicit tA: RType[A],
                IR: IRContext,
                compilerSettings: CompilerSettings): SValue = {
    val code =
      s"""{
        |  val func = $funcScript
        |  val res = func(getVar[${tA.name}](1).get)
        |  res
        |}
      """.stripMargin

    // The following ops are performed by frontend
    // typecheck, create graphs, compile to Tree
    // The resulting tree should be serializable
    val compiledTree = {
      val compiler = SigmaCompiler(compilerSettings)
      val res = compiler.compile(env, code)
      checkCompilerResult(res)
      if (lowerMethodCallsInTests) res.buildTree
      else {
        compiler.unlowerMethodCalls(res.buildTree)
      }
    }
    compiledTree
  }

  def evalSettings = ErgoTreeEvaluator.DefaultEvalSettings

  def printCostDetails(script: String, details: CostDetails) = {
    val traceLines = SigmaPPrint(details, height = 550, width = 150)
    println(
      s"""------------------------
        |Script: $script
        |$traceLines
        |""".stripMargin)
  }

  def funcJitFromExpr[A: RType, B: RType]
      (funcScript: String, expr: SValue, bindings: VarBinding*)
      (implicit IR: IRContext,
                evalSettings: EvalSettings,
                compilerSettings: CompilerSettings): CompiledFunc[A, B] = {
    val tA = RType[A]
    val f = (in: A) => {
      implicit val cA: ClassTag[A] = tA.classTag
      val sigmaCtx = createContexts(in, bindings)
      val accumulator = new CostAccumulator(
        initialCost = JitCost(0),
        costLimit = Some(JitCost.fromBlockCost(ScriptCostLimit.value)))
      val evaluator = new ErgoTreeEvaluator(
        context = sigmaCtx,
        constants = ErgoTree.EmptyConstants,
        coster = accumulator, evalSettings.profilerOpt.getOrElse(DefaultProfiler), evalSettings)

      val (res, actualTime) = BenchmarkUtil.measureTimeNano(
        evaluator.evalWithCost[B](ErgoTreeEvaluator.EmptyDataEnv, expr))
      val costDetails = if (evalSettings.costTracingEnabled) {
        val trace: Seq[CostItem] = evaluator.getCostTrace()
        val costDetails = TracedCost(trace, Some(actualTime))
        assert(res.cost == costDetails.cost)
        costDetails
      } else
        GivenCost(res.cost, Some(actualTime))

      if (evalSettings.isMeasureScriptTime) {
        evaluator.profiler.addJitEstimation(funcScript, res.cost, actualTime)
      }

      if (evalSettings.isLogEnabled) {
        printCostDetails(funcScript, costDetails)
      }
      (res.value, costDetails)
    }
    val Terms.Apply(funcVal, _) = expr.asInstanceOf[SValue]
    CompiledFunc(funcScript, bindings, funcVal, expr, f)
  }

  def funcJit[A: RType, B: RType]
      (funcScript: String, bindings: VarBinding*)
      (implicit IR: IRContext,
                evalSettings: EvalSettings,
                compilerSettings: CompilerSettings): CompiledFunc[A, B] = {
    val compiledTree = compileTestScript[A](Interpreter.emptyEnv, funcScript)
    funcJitFromExpr(funcScript, compiledTree, bindings:_*)
  }

  protected def roundTripTest[T](v: T)(implicit serializer: SigmaSerializer[T, T]): Assertion = {
    // using default sigma reader/writer
    val bytes = serializer.toBytes(v)
    bytes.nonEmpty shouldBe true
    val r = SigmaSerializer.startReader(bytes)
    val positionLimitBefore = r.positionLimit
    serializer.parse(r) shouldBe v
    r.positionLimit shouldBe positionLimitBefore
  }

  protected def roundTripTestWithPos[T](v: T)(implicit serializer: SigmaSerializer[T, T]): Assertion = {
    val randomBytesCount = Gen.chooseNum(1, 20).sample.get
    val randomBytes = Gen.listOfN(randomBytesCount, arbByte.arbitrary).sample.get.toArray
    val bytes = serializer.toBytes(v)
    serializer.parse(SigmaSerializer.startReader(bytes)) shouldBe v
    serializer.parse(SigmaSerializer.startReader(randomBytes ++ bytes, randomBytesCount)) shouldBe v
  }

  def testReduce(I: Interpreter)(ctx: I.CTX, prop: SigmaPropValue): SigmaBoolean = {
    val ergoTree = ErgoTree.fromProposition(ergoTreeHeaderInTests, prop)
    I.fullReduction(ergoTree, ctx).value
  }

}
