/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock

import scala.reflect.ClassTag

import cats.Eq
import cats.syntax.all.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder as CDecoder, Encoder as CEncoder}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.*

trait CirceTests extends munit.ScalaCheckSuite:
  def identityLawTest[A: CEncoder: CDecoder: Arbitrary: Eq](using
      loc: munit.Location,
      CT: ClassTag[A]
  ): Unit =
    property(s"Circe Identity Laws Test for ${CT.runtimeClass.getName}") {
      forAll { (a: A) =>
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }

    }
