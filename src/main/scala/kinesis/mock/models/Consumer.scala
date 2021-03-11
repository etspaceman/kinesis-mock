package kinesis.mock.models

import java.time.Instant

final case class Consumer(
    consumerArn: String,
    consumerCreationTimestamp: Instant,
    consumerName: String,
    consumerStatus: ConsumerStatus
)
