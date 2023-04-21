package sigmastate.lang.exceptions

import sigmastate.lang.SourceContext

/** Thrown by TypeSerializer when type prefix <= 0. */
final class InvalidTypePrefix(message: String, source: Option[SourceContext] = None, cause: Option[Throwable] = None)
  extends SerializerException(message, source, cause)

/** Thrown when the current reader position > positionLimit which is set in the Reader.
  * @see [[org.ergoplatform.validation.ValidationRules.CheckPositionLimit]]
  */
final class ReaderPositionLimitExceeded(
  message: String,
  val position: Int,
  val positionLimit: Int,
  source: Option[SourceContext] = None,
  cause: Option[Throwable] = None)
  extends SerializerException(message, source, cause)

/** Thrown when the current depth level > maxDepthLevel which is set in the Reader. */
final class DeserializeCallDepthExceeded(message: String, source: Option[SourceContext] = None, cause: Option[Throwable] = None)
  extends SerializerException(message, source, cause)

/** Thrown by [[org.ergoplatform.validation.ValidationRules.CheckValidOpCode]] validation rule. */
final class InvalidOpCode(message: String, source: Option[SourceContext] = None, cause: Option[Throwable] = None)
  extends SerializerException(message, source, cause)
