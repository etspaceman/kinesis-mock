/*
 * Copyright 2021-2026 io.github.etspaceman
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

package kinesis.mock.regexp.data

sealed abstract class Group[A]:

  def compliment: Group[A]

  def intersect(that: Group[A]): Group[A]

  def ++(that: Group[A]): Group[A]

  def --(that: Group[A]): Group[A]

object Group:

  final case class Inclusion[A](values: Set[A]) extends Group[A]:

    override lazy val compliment: Group[A] = Exclusion(values)

    override def intersect(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values.intersect(other))
        case Exclusion(other) => Inclusion(values -- other)

    override def ++(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values ++ other)
        case Exclusion(other) => Exclusion(other -- values)

    override def --(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values -- other)
        case Exclusion(other) => Inclusion(values.intersect(other))

  final case class Exclusion[A](values: Set[A]) extends Group[A]:

    override lazy val compliment: Group[A] = Inclusion(values)

    override def intersect(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values -- other)
        case Exclusion(other) => Exclusion(values ++ other)

    override def ++(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Exclusion(values -- other)
        case Exclusion(other) => Exclusion(values.intersect(other))

    override def --(that: Group[A]): Group[A] =
      that match
        case Exclusion(other) => Exclusion(other -- values)
        case Inclusion(other) => Exclusion(values ++ other)
