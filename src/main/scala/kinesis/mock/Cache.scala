package kinesis.mock

import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Concurrent, IO}
import cats.syntax.all._

import kinesis.mock.api._
import kinesis.mock.models._

class Cache private (
    ref: Ref[IO, Streams],
    semaphores: CacheSemaphores
) {

  def addTagsToStream(
      req: AddTagsToStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquire.ifM(
      ref.get.flatMap(streams =>
        req.addTagsToStream(streams).traverse(ref.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for AddTagsToStreamRequest"
          )
        )
      )
    )

  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquire.ifM(
      ref.get.flatMap(streams =>
        req
          .removeTagsFromStream(streams)
          .traverse(ref.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for RemoveTagsFromStreamRequest"
          )
        )
      )
    )
}

object Cache {
  def apply(implicit C: Concurrent[IO]): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](Streams.empty)
    semaphores <- CacheSemaphores.create
  } yield new Cache(ref, semaphores)
}

final case class CacheSemaphores private (
    addTagsToStream: Semaphore[IO],
    removeTagsFromStream: Semaphore[IO]
)

object CacheSemaphores {
  def create(implicit C: Concurrent[IO]): IO[CacheSemaphores] = for {
    addTagsToStream <- Semaphore[IO](5)
    removeTagsFromStream <- Semaphore[IO](5)
  } yield new CacheSemaphores(addTagsToStream, removeTagsFromStream)
}
