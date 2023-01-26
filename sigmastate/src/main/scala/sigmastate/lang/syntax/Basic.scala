package sigmastate.lang.syntax

import fastparse._; import NoWhitespace._
import fastparse.CharPredicates._
import scalan.Nullable
import sigmastate.lang.SourceContext
import sigmastate.lang.exceptions.SigmaException

object Basic {
  val digits = "0123456789"
  def Digit[_:P]: P[Unit] = P( CharPred(digits.contains(_)) )
  val hexDigits: String = digits + "abcdefABCDEF"
  def HexDigit[_:P]: P[Unit] = P( CharPred(hexDigits.contains(_)) )
  def UnicodeEscape[_:P]: P[Unit] = P( "u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit )

  //Numbers and digits
  def HexNum[_:P]: P[Unit] = P( "0x" ~ CharsWhile(hexDigits.contains(_), 1) )
  def DecNum[_:P]: P[Unit] = P( CharsWhile(digits.contains(_), 1) )
  def Exp[_:P]: P[Unit] = P( CharPred("Ee".contains(_)) ~ CharPred("+-".contains(_)).? ~ DecNum )
  def FloatType[_:P]: P[Unit] = P( CharIn("fFdD") )

  def WSChars[_:P]: P[Unit] = P( CharsWhileIn("\u0020\u0009") )
  def Newline[_:P]: P[Unit] = P( StringIn("\r\n", "\n") )
  def Semi[_:P]: P[Unit] = P( ";" | Newline.rep(1) )
  def OpChar[_:P]: P[Unit] = P ( CharPred(isOpChar) )

  def isOpChar(c: Char): Boolean = c match{
    case '!' | '#' | '%' | '&' | '*' | '+' | '-' | '/' |
         ':' | '<' | '=' | '>' | '?' | '@' | '\\' | '^' | '|' | '~' => true
    case _ => isOtherSymbol(c) || isMathSymbol(c)
  }
  def Letter[_:P]: P[Unit] = P( CharPred(c => isLetter(c) | isDigit(c) | c == '$' | c == '_' ) )
  def LetterDigitDollarUnderscore[_:P]: P[Unit] =  P(
    CharPred(c => isLetter(c) | isDigit(c) | c == '$' | c == '_' )
  )
  def Lower[_:P]: P[Unit] = P( CharPred(c => isLower(c) || c == '$' | c == '_') )
  def Upper[_:P]: P[Unit] = P( CharPred(isUpper) )

  def error(msg: String, srcCtx: Option[SourceContext]) = throw new ParserException(msg, srcCtx)
  def error(msg: String, srcCtx: Nullable[SourceContext]) = throw new ParserException(msg, srcCtx.toOption)
}

class ParserException(message: String, source: Option[SourceContext])
  extends SigmaException(message, source)

/**
  * Most keywords don't just require the correct characters to match,
  * they have to ensure that subsequent characters *don't* match in
  * order for it to be a keyword. This enforces that rule for key-words
  * (W) and key-operators (O) which have different non-match criteria.
  */
object Key {
  def W[_:P](s: String) = P( s ~ !Basic.LetterDigitDollarUnderscore )(sourcecode.Name(s"`$s`"), implicitly[P[_]])
  // If the operator is followed by a comment, stop early so we can parse the comment
  def O[_:P](s: String) = P( s ~ (!Basic.OpChar | &("/*" | "//")) )(sourcecode.Name(s"`$s`"), implicitly[P[_]])
}
