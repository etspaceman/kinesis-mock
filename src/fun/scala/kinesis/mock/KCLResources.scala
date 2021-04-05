package kinesis.mock

import cats.effect.IO
import fs2.concurrent.InspectableQueue
import software.amazon.kinesis.retrieval.KinesisClientRecord

final case class KCLResources(
    functionalTestResources: KinesisFunctionalTestResources,
    resultsQueue: InspectableQueue[IO, KinesisClientRecord]
)
