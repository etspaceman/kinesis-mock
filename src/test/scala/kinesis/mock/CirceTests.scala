package kinesis.mock

import scala.reflect.ClassTag

import cats.Eq
import cats.syntax.all._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._

trait CirceTests extends munit.ScalaCheckSuite {
  def identityLawTest[A: Encoder: Decoder: Arbitrary: Eq](implicit
      loc: munit.Location,
      CT: ClassTag[A]
  ): Unit =
    property(s"Circe Identity Laws Test for ${CT.runtimeClass.getName}") {
      forAll { a: A =>
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
          .fold(_.toString, _.toString)}"
      }

    }

}
