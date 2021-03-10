package kinesis.mock.cache

import cats.effect.concurrent.Semaphore
import cats.effect._

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
