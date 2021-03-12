package kinesis.mock.cache

import cats.effect._
import cats.effect.concurrent.Semaphore

final case class CacheSemaphores private (
    addTagsToStream: Semaphore[IO],
    removeTagsFromStream: Semaphore[IO],
    createStream: Semaphore[IO],
    deleteStream: Semaphore[IO],
    describeLimits: Semaphore[IO],
    describeStream: Semaphore[IO],
    registerStreamConsumer: Semaphore[IO],
    deregisterStreamConsumer: Semaphore[IO],
    describeStreamConsumer: Semaphore[IO],
    describeStreamSummary: Semaphore[IO],
    listShards: Semaphore[IO]
)

object CacheSemaphores {
  def create(implicit C: Concurrent[IO]): IO[CacheSemaphores] = for {
    addTagsToStream <- Semaphore[IO](5)
    removeTagsFromStream <- Semaphore[IO](5)
    createStream <- Semaphore[IO](5)
    deleteStream <- Semaphore[IO](5)
    describeLimits <- Semaphore[IO](1)
    describeStream <- Semaphore[IO](10)
    registerStreamConsumer <- Semaphore[IO](5)
    deregisterStreamConsumer <- Semaphore[IO](5)
    describeStreamConsumer <- Semaphore[IO](10)
    describeStreamSummary <- Semaphore[IO](20)
    listShards <- Semaphore[IO](100)
  } yield new CacheSemaphores(
    addTagsToStream,
    removeTagsFromStream,
    createStream,
    deleteStream,
    describeLimits,
    describeStream,
    registerStreamConsumer,
    deregisterStreamConsumer,
    describeStreamConsumer,
    describeStreamSummary,
    listShards
  )
}
