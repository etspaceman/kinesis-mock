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

package kinesis.mock.retry

import scala.concurrent.duration.FiniteDuration

import cats.Monad
import cats.MonadError
import cats.syntax.all.*

/*
 * Partially applied classes
 */

class RetryingOnFailuresPartiallyApplied[A]:
  def apply[M[_]](
      policy: RetryPolicy[M],
      wasSuccessful: A => M[Boolean],
      onFailure: (A, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(using
      M: Monad[M],
      S: Sleep[M]
  ): M[A] = M.tailRecM(RetryStatus.NoRetriesYet) { status =>
    action.flatMap { a =>
      retryingOnFailuresImpl(policy, wasSuccessful, onFailure, status, a)
    }
  }

class RetryingOnSomeErrorsPartiallyApplied[A]:
  def apply[M[_], E](
      policy: RetryPolicy[M],
      isWorthRetrying: E => M[Boolean],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(using
      ME: MonadError[M, E],
      S: Sleep[M]
  ): M[A] = ME.tailRecM(RetryStatus.NoRetriesYet) { status =>
    ME.attempt(action).flatMap { attempt =>
      retryingOnSomeErrorsImpl(
        policy,
        isWorthRetrying,
        onError,
        status,
        attempt
      )
    }
  }

class RetryingOnAllErrorsPartiallyApplied[A]:
  def apply[M[_], E](
      policy: RetryPolicy[M],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(using
      ME: MonadError[M, E],
      S: Sleep[M]
  ): M[A] =
    retryingOnSomeErrors[A].apply[M, E](policy, _ => ME.pure(true), onError)(
      action
    )

class RetryingOnFailuresAndSomeErrorsPartiallyApplied[A]:
  def apply[M[_], E](
      policy: RetryPolicy[M],
      wasSuccessful: A => M[Boolean],
      isWorthRetrying: E => M[Boolean],
      onFailure: (A, RetryDetails) => M[Unit],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(using
      ME: MonadError[M, E],
      S: Sleep[M]
  ): M[A] =
    ME.tailRecM(RetryStatus.NoRetriesYet) { status =>
      ME.attempt(action).flatMap {
        case Right(a) =>
          retryingOnFailuresImpl(policy, wasSuccessful, onFailure, status, a)
        case attempt =>
          retryingOnSomeErrorsImpl(
            policy,
            isWorthRetrying,
            onError,
            status,
            attempt
          )
      }
    }

class RetryingOnFailuresAndAllErrorsPartiallyApplied[A]:
  def apply[M[_], E](
      policy: RetryPolicy[M],
      wasSuccessful: A => M[Boolean],
      onFailure: (A, RetryDetails) => M[Unit],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(using
      ME: MonadError[M, E],
      S: Sleep[M]
  ): M[A] =
    retryingOnFailuresAndSomeErrors[A]
      .apply[M, E](
        policy,
        wasSuccessful,
        _ => ME.pure(true),
        onFailure,
        onError
      )(
        action
      )

sealed trait NextStep

object NextStep:
  case object GiveUp extends NextStep

  final case class RetryAfterDelay(
      delay: FiniteDuration,
      updatedStatus: RetryStatus
  ) extends NextStep
