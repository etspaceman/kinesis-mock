package kinesis.mock.models

import kinesis.mock.models.Shard

final case class ShardSemaphoresKey(streamName: StreamName, shard: Shard)
