package sigmastate.lang

import fastparse.core.Parsed
import fastparse.core.Parsed.Success
import org.ergoplatform.ErgoAddressEncoder.NetworkPrefix
import sigmastate.SType
import sigmastate.Values.{SValue, Value}
import sigmastate.eval.IRContext
import sigmastate.interpreter.Interpreter.ScriptEnv
import sigmastate.lang.SigmaPredef.PredefinedFuncRegistry
import sigmastate.lang.syntax.ParserException

/**
  * @param networkPrefix    network prefix to decode an ergo address from string (PK op)
  * @param builder          used to create ErgoTree nodes
  * @param lowerMethodCalls if true, then MethodCall nodes are lowered to ErgoTree nodes
  *                         when [[sigmastate.SMethod.irInfo.irBuilder]] is defined. For
  *                         example, in the `coll.map(x => x+1)` code, the `map` method
  *                         call can be lowered to MapCollection node.
  *                         The lowering if preferable, because it is more compact (1 byte
  *                         for MapCollection instead of 3 bytes for MethodCall).
  */
case class CompilerSettings(
    networkPrefix: NetworkPrefix,
    builder: SigmaBuilder,
    lowerMethodCalls: Boolean
)

/** Result of ErgoScript source code compilation.
  * @param env compiler environment used to compile the code
  * @param code ErgoScript source code
  * @param calcF  graph obtained by using old AOT costing based compiler
  * @param compiledGraph graph obtained by using new [[GraphBuilding]]
  * @param calcTree ErgoTree expression obtained from calcF graph.
  * @param buildTree ErgoTree expression obtained from graph created by [[GraphBuilding]]
  */
case class CompilerResult[Ctx <: IRContext](
  env: ScriptEnv,
  code: String,
  calcF: Ctx#Ref[Ctx#Context => Any],
  compiledGraph: Ctx#Ref[Ctx#Context => Any],
  calcTree: SValue,
  buildTree: SValue
)

/** Compiler which compiles ErgoScript source code into ErgoTree.
  * @param settings compilation parameters \
  */
class SigmaCompiler(settings: CompilerSettings) {
  /** Constructs an instance for the given network type and with default settings. */
  def this(networkPrefix: Byte) = this(
    CompilerSettings(networkPrefix, TransformingSigmaBuilder, lowerMethodCalls = true)
  )

  @inline final def builder = settings.builder
  @inline final def networkPrefix = settings.networkPrefix

  /** Parses the given ErgoScript source code and produces expression tree. */
  def parse(x: String): SValue = {
    SigmaParser(x, builder) match {
      case Success(v, _) => v
      case f: Parsed.Failure[_,String] =>
        throw new ParserException(s"Syntax error: $f", Some(SourceContext.fromParserFailure(f)))
    }
  }

  /** Typechecks the given parsed expression and assigns types for all sub-expressions. */
  def typecheck(env: ScriptEnv, parsed: SValue): Value[SType] = {
    val predefinedFuncRegistry = new PredefinedFuncRegistry(builder)
    val binder = new SigmaBinder(env, builder, networkPrefix, predefinedFuncRegistry)
    val bound = binder.bind(parsed)
    val typer = new SigmaTyper(builder, predefinedFuncRegistry, settings.lowerMethodCalls)
    val typed = typer.typecheck(bound)
    typed
  }

  def typecheck(env: ScriptEnv, code: String): Value[SType] = {
    val parsed = parse(code)
    typecheck(env, parsed)
  }

  private[sigmastate] def compileWithoutCosting(env: ScriptEnv, code: String): Value[SType] = {
    val typed = typecheck(env, code)
    val spec = new SigmaSpecializer(builder)
    val ir = spec.specialize(typed)
    ir
  }

  /** Compiles the given ErgoScript source code. */
  def compile(env: ScriptEnv, code: String)(implicit IR: IRContext): CompilerResult[IR.type] = {
    val typed = typecheck(env, code)
    val res = compileTyped(env, typed)
    res.copy(code = code)
  }

  /** Compiles the given typed expression. */
  def compileTyped(env: ScriptEnv, typedExpr: SValue)(implicit IR: IRContext): CompilerResult[IR.type] = {
    val IR.Pair(calcF, costF) = IR.doCosting(env, typedExpr, true)
    val compiledGraph = IR.buildGraph(env, typedExpr)
    val calcTree = IR.buildTree(calcF)
    val compiledTree = IR.buildTree(compiledGraph)
    CompilerResult(env, "<no source code>", calcF, compiledGraph, calcTree, compiledTree)
  }
}

object SigmaCompiler {
  def apply(settings: CompilerSettings): SigmaCompiler =
    new SigmaCompiler(settings)
}
