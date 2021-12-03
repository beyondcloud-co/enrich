/*
 * Copyright (c) 2012-2021 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.common.enrichments.registry

import cats.Monad
import cats.data.{EitherT, NonEmptyList, ValidatedNel}
import cats.effect.Clock
import cats.implicits._
import io.circe.Json
import com.snowplowanalytics.iglu.client.Client
import com.snowplowanalytics.iglu.client.resolver.registries.RegistryLookup
import com.snowplowanalytics.iglu.core.{SchemaCriterion, SchemaKey, SchemaVer, SelfDescribingData}
import com.snowplowanalytics.snowplow.badrows.FailureDetails.{EnrichmentFailureMessage, EnrichmentInformation}
import com.snowplowanalytics.snowplow.badrows.{BadRow, FailureDetails, Processor}
import com.snowplowanalytics.snowplow.enrich.common.adapters.RawEvent
import com.snowplowanalytics.snowplow.enrich.common.adapters.RawEvent.toRawEvent
import com.snowplowanalytics.snowplow.enrich.common.enrichments.EnrichmentManager
import com.snowplowanalytics.snowplow.enrich.common.enrichments.registry.EnrichmentConf.ShreddedValidatorEnrichmentConf
import com.snowplowanalytics.snowplow.enrich.common.outputs.EnrichedEvent
import com.snowplowanalytics.snowplow.enrich.common.outputs.EnrichedEvent.toPartiallyEnrichedEvent
import com.snowplowanalytics.snowplow.enrich.common.utils.IgluUtils.buildSchemaViolationsBadRow

object ShreddedValidatorEnrichment extends ParseableEnrichment {
  override val supportedSchema: SchemaCriterion =
    SchemaCriterion(
      "com.snowplowanalytics.snowplow.enrichments",
      "shredded_validator_config",
      "jsonschema",
      1,
      0,
      0
    )

  /**
   * Creates an AnonIpEnrichment instance from a Json.
   * @param config The anon_ip enrichment JSON
   * @param schemaKey provided for the enrichment, must be supported by this enrichment
   * @return an AnonIpEnrichment configuration
   */
  override def parse(
    config: Json,
    schemaKey: SchemaKey,
    localMode: Boolean = false
  ): ValidatedNel[String, ShreddedValidatorEnrichmentConf] =
    isParseable(config, schemaKey)
      .flatMap(_ =>
        config.hcursor
          .getOrElse[Boolean]("disable")(false)
          .leftMap(_.getMessage())
      )
      .map(disable => ShreddedValidatorEnrichmentConf(schemaKey, disable))
      .toValidatedNel
}

case class ShreddedValidatorEnrichment(disable: Boolean = false) extends Enrichment {
  def schemaKey: SchemaKey = SchemaKey("com.snowplowanalytics.snowplow", "atomic", "jsonschema", SchemaVer.Full(1, 0, 0))

  def performCheck[F[_]](
    e: EnrichedEvent,
    re: RawEvent,
    processor: Processor,
    client: Client[F, Json]
  )(
    implicit
    M: Monad[F],
    L: RegistryLookup[F],
    C: Clock[F]
  ): EitherT[F, BadRow, Unit] =
    if (!disable)
      e.toShreddedEvent
        .leftMap(err =>
          EnrichmentManager.buildEnrichmentFailuresBadRow(
            NonEmptyList.one(
              FailureDetails.EnrichmentFailure(Some(EnrichmentInformation(schemaKey, identifier = "shredded_validator")),
                                               EnrichmentFailureMessage.Simple(err.getMessage)
              )
            ),
            toPartiallyEnrichedEvent(e),
            toRawEvent(re),
            processor
          )
        )
        .toEitherT[F]
        .flatMap(shreded =>
          client
            .check(
              SelfDescribingData(schemaKey, shreded)
            )
            .leftMap(err =>
              buildSchemaViolationsBadRow(
                NonEmptyList.one(
                  FailureDetails.SchemaViolation.IgluError(schemaKey, err)
                ),
                toPartiallyEnrichedEvent(e),
                toRawEvent(re),
                processor
              )
            )
        )
    else
      EitherT.rightT(())
}
