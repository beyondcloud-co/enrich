package com.snowplowanalytics.snowplow.enrich.common.enrichments.registry

import cats.implicits._
import cats.effect.{Blocker, Clock, IO}
import org.specs2.matcher.MustMatchers.{left => _, right => _}
import org.specs2.mutable.Specification

import com.snowplowanalytics.iglu.client.Client
import com.snowplowanalytics.iglu.client.resolver.registries.Registry
import com.snowplowanalytics.snowplow.enrich.common.SpecHelpers
import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer}
import com.snowplowanalytics.snowplow.badrows.Processor
import com.snowplowanalytics.snowplow.enrich.common.EtlPipeline
import com.snowplowanalytics.snowplow.enrich.common.adapters.AdapterRegistry
import com.snowplowanalytics.snowplow.enrich.common.enrichments.EnrichmentRegistry
import com.snowplowanalytics.snowplow.enrich.common.loaders.CollectorPayload

import io.circe.literal._
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext

class ShreddedValidatorEnrichmentSpec extends Specification {
  implicit val ioClock: Clock[IO] = Clock.create[IO]
  val adapterRegistry = new AdapterRegistry()
  val enrichmentReg = EnrichmentRegistry[IO]()
  val disabledEnrichReg = EnrichmentRegistry[IO](shreddedValidator = Some(ShreddedValidatorEnrichment(disable = true)))
  val igluCentral = Registry.IgluCentral
  val client = Client.parseDefault[IO](json"""
      {
        "schema": "iglu:com.snowplowanalytics.iglu/resolver-config/jsonschema/1-0-1",
        "data": {
          "cacheSize": 500,
          "repositories": [
            {
              "name": "Iglu Central",
              "priority": 0,
              "vendorPrefixes": [ "com.snowplowanalytics" ],
              "connection": {
                "http": {
                  "uri": "http://iglucentral.com"
                }
              }
            },
            {
              "name": "Iglu Central - GCP Mirror",
              "priority": 1,
              "vendorPrefixes": [ "com.snowplowanalytics" ],
              "connection": {
                "http": {
                  "uri": "http://mirror01.iglucentral.com"
                }
              }
            }
          ]
        }
      }
      """)
  val processor = Processor("sce-test-suite", "1.0.0")
  val dateTime = DateTime.now()
  val blocker: Blocker = Blocker.liftExecutionContext(ExecutionContext.global)

  def processEvents(e: CollectorPayload): IO[Boolean] =
    EtlPipeline
      .processEvents[IO](
        adapterRegistry,
        enrichmentReg,
        client.rethrowT.unsafeRunSync(),
        processor,
        dateTime,
        Some(e).validNel
      )
      .map(_.forall(p => p.isValid))

  def processEventsDisabled(e: CollectorPayload): IO[Boolean] =
    EtlPipeline
      .processEvents[IO](
        adapterRegistry,
        disabledEnrichReg,
        client.rethrowT.unsafeRunSync(),
        processor,
        dateTime,
        Some(e).validNel
      )
      .map(_.forall(p => p.isValid))

  val body = SpecHelpers.toSelfDescJson(
    """[{"tv":"ios-0.1.0","p":"mob","e":"se"}]""",
    "payload_data"
  )

  val longStr = "s" * 500
  val fatBody = SpecHelpers.toSelfDescJson(
    s"""[{"tv":"$longStr","p":"mob","e":"se" , "tr_ci": "$longStr"}]""",
    "payload_data"
  )

  val ApplicationJsonWithCapitalCharset = "application/json; charset=UTF-8"
  object Snowplow {
    private val api: (String) => CollectorPayload.Api = version => CollectorPayload.Api("com.snowplowanalytics.snowplow", version)
    val Tp2 = api("tp2")
  }

  object Shared {
    val api = CollectorPayload.Api("com.statusgator", "v1")
    val source = CollectorPayload.Source("clj-tomcat", "UTF-8", None)
    val context = CollectorPayload.Context(
      DateTime.parse("2013-08-29T00:18:48.000+00:00").some,
      "37.157.33.123".some,
      None,
      None,
      Nil,
      None
    )
  }

  val payload = CollectorPayload(
    Snowplow.Tp2,
    SpecHelpers.toNameValuePairs("tv" -> "0", "nuid" -> "123"),
    ApplicationJsonWithCapitalCharset.some,
    body.some,
    Shared.source,
    Shared.context
  )

  val fatPayload = CollectorPayload(
    Snowplow.Tp2,
    SpecHelpers.toNameValuePairs("tv" -> "0", "nuid" -> "123"),
    ApplicationJsonWithCapitalCharset.some,
    fatBody.some,
    Shared.source,
    Shared.context
  )
//  val oversized = CollectorPayload(api, Nil, None, None, source, context)

  "config should parse" >> {
    val configJson = json"""{
      "disable": true
      }"""
    val schemaKey = SchemaKey(
      "com.snowplowanalytics.snowplow.enrichments",
      "shredded_validator_config",
      "jsonschema",
      SchemaVer.Full(1, 0, 0)
    )
    val config = ShreddedValidatorEnrichment.parse(configJson, schemaKey)
    config.map(_.disable).getOrElse(false) must beTrue

  }

  "disabled enrichment should let oversized data though" >> { processEventsDisabled(fatPayload).unsafeRunSync() must beTrue }
  "invalid payload should create BadRow" >> { processEvents(fatPayload).unsafeRunSync() must beFalse }
  "valid payload should not throw BadRaw" >> { processEvents(payload).unsafeRunSync() must beTrue }
}
