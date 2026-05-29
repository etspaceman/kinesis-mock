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

package kinesis.mock

import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class AccountSettingsIntegrationTests extends AwsFunctionalTests:

  fixture().test(
    "It should describe and update account settings"
  ) { resources =>
    for
      initial <- resources.kinesisClient
        .describeAccountSettings(
          DescribeAccountSettingsRequest.builder().build()
        )
        .toIO
      _ <- resources.kinesisClient
        .updateAccountSettings(
          UpdateAccountSettingsRequest
            .builder()
            .minimumThroughputBillingCommitment(
              MinimumThroughputBillingCommitmentInput
                .builder()
                .status(MinimumThroughputBillingCommitmentInputStatus.ENABLED)
                .build()
            )
            .build()
        )
        .toIO
      enabled <- resources.kinesisClient
        .describeAccountSettings(
          DescribeAccountSettingsRequest.builder().build()
        )
        .toIO
      _ <- resources.kinesisClient
        .updateAccountSettings(
          UpdateAccountSettingsRequest
            .builder()
            .minimumThroughputBillingCommitment(
              MinimumThroughputBillingCommitmentInput
                .builder()
                .status(MinimumThroughputBillingCommitmentInputStatus.DISABLED)
                .build()
            )
            .build()
        )
        .toIO
      disabled <- resources.kinesisClient
        .describeAccountSettings(
          DescribeAccountSettingsRequest.builder().build()
        )
        .toIO
    yield
      assertEquals(
        Option(initial.minimumThroughputBillingCommitment()),
        None
      )
      assertEquals(
        Option(enabled.minimumThroughputBillingCommitment())
          .map(_.statusAsString()),
        Some("ENABLED")
      )
      assertEquals(
        Option(disabled.minimumThroughputBillingCommitment()),
        None
      )
  }
