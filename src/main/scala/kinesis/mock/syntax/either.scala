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

package kinesis.mock.syntax

object either extends KinesisMockEitherSyntax

trait KinesisMockEitherSyntax {
  implicit def toKinesisMockEitherTupleOps[L, T1, T2](
      e: Either[L, (T1, T2)]
  ): KinesisMockEitherSyntax.KinesisMockEitherTupleOps[L, T1, T2] =
    new KinesisMockEitherSyntax.KinesisMockEitherTupleOps(e)
}

object KinesisMockEitherSyntax {
  final class KinesisMockEitherTupleOps[L, T1, T2](
      private val e: Either[L, (T1, T2)]
  ) extends AnyVal {
    def sequenceWithDefault(default: T1): (T1, Either[L, T2]) =
      e.fold(e => (default, Left(e)), { case (t1, t2) => (t1, Right(t2)) })
  }
}
