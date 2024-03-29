package kinesis.mock.retry

import scala.concurrent.duration.FiniteDuration

import cats.Monad
import cats.MonadError
import cats.syntax.all._

/*
 * Partially applied classes
 */

class RetryingOnFailuresPartiallyApplied[A] {
  def apply[M[_]](
      policy: RetryPolicy[M],
      wasSuccessful: A => M[Boolean],
      onFailure: (A, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(implicit
      M: Monad[M],
      S: Sleep[M]
  ): M[A] = M.tailRecM(RetryStatus.NoRetriesYet) { status =>
    action.flatMap { a =>
      retryingOnFailuresImpl(policy, wasSuccessful, onFailure, status, a)
    }
  }
}

class RetryingOnSomeErrorsPartiallyApplied[A] {
  def apply[M[_], E](
      policy: RetryPolicy[M],
      isWorthRetrying: E => M[Boolean],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(implicit
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
}

class RetryingOnAllErrorsPartiallyApplied[A] {
  def apply[M[_], E](
      policy: RetryPolicy[M],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(implicit
      ME: MonadError[M, E],
      S: Sleep[M]
  ): M[A] =
    retryingOnSomeErrors[A].apply[M, E](policy, _ => ME.pure(true), onError)(
      action
    )
}

class RetryingOnFailuresAndSomeErrorsPartiallyApplied[A] {
  def apply[M[_], E](
      policy: RetryPolicy[M],
      wasSuccessful: A => M[Boolean],
      isWorthRetrying: E => M[Boolean],
      onFailure: (A, RetryDetails) => M[Unit],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(implicit
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
}

class RetryingOnFailuresAndAllErrorsPartiallyApplied[A] {
  def apply[M[_], E](
      policy: RetryPolicy[M],
      wasSuccessful: A => M[Boolean],
      onFailure: (A, RetryDetails) => M[Unit],
      onError: (E, RetryDetails) => M[Unit]
  )(
      action: => M[A]
  )(implicit
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
}

sealed trait NextStep

object NextStep {
  case object GiveUp extends NextStep

  final case class RetryAfterDelay(
      delay: FiniteDuration,
      updatedStatus: RetryStatus
  ) extends NextStep
}
