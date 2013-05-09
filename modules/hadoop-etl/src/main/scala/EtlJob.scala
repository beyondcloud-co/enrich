/*
 * Copyright (c) 2012-2013 SnowPlow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow.enrich.hadoop

// Java
import java.io.File
import java.net.URI

// Hadoop
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{Path, FileSystem}
import org.apache.hadoop.filecache.DistributedCache

// Scalaz
import scalaz._
import Scalaz._

// Scalding
import com.twitter.scalding._

// Scala MaxMind GeoIP
import com.snowplowanalytics.maxmind.geoip.IpGeo

// This project
import inputs.CollectorLoader
import enrichments.EnrichmentManager
import outputs.CanonicalOutput

/**
 * Holds constructs to help build the ETL job's data
 * flow (see below).
 */ 
object EtlJob {

  /**
   * A helper method to take a ValidatedMaybeCanonicalInput
   * and flatMap it into a ValidatedMaybeCanonicalOutput.
   *
   * We have to do some unboxing because enrichEvent
   * expects a raw CanonicalInput as its argument, not
   * a MaybeCanonicalInput.
   *
   * @param input The ValidatedMaybeCanonicalInput
   * @return the ValidatedMaybeCanonicalOutput. Thanks to
   *         flatMap, will include any validation errors
   *         contained within the ValidatedMaybeCanonicalInput
   */
  def toCanonicalOutput(geo: IpGeo, input: ValidatedMaybeCanonicalInput): ValidatedMaybeCanonicalOutput = {
    input.flatMap {
      _.cata(EnrichmentManager.enrichEvent(geo, _).map(_.some),
             none.success)
    }
  }

  /**
   * A helper to create a new IpGeo object
   * used for IP location -> geo-location lookups
   *
   * How we source the MaxMind data file depends
   * on whether we are running locally or on HDFS:
   *
   * 1. On HDFS - assume the file is in /cache/GeoLiteCity.dat,
   *    add it to Hadoop's distributed cache and use that
   * 2. On local (test) - use the copy of the file on our
   *    resource path (downloaded for us by SBT)
   *
   * @param file The path to the Maxmind GeoLiteCity.dat file
   * @return an IpGeo object ready to perform IP->geo lookups
   */
  def getGeoIp(file: String): IpGeo = {
    val dbFile = jobConfOption match {
      case Some(conf) => {   // We're on HDFS
        val target = "geoip"
        addToDistCache(conf, file, target)
        "./" + target   
      }
      case None => {         // Local mode
        getClass.getResource("/maxmind/GeoLiteCity.dat").toURI.getPath
      }
    }
    IpGeo(dbFile, memCache = true, lruCache = 20000)
  }

  // This should really be in Scalding
  def jobConfOption(implicit mode : Mode): Option[Configuration] = {
    mode match {
      case Hdfs(_, conf) => Option(conf)
      case _ => None
    }
  }

  /**
   * Adds a file to the DistributedCache as a symbolic link.
   * Only tested with S3.
   *
   * @param conf Our current job Configuration
   * @param source The file to add to the DistributedCache
   * @param target Name of our symbolic link in the cache
   */
  def addToDistCache(conf: Configuration, source: String, target: String) {
    val path = new Path(source).toUri.toString + "#" + target // Convoluted because Path("#") -> "%23"
    DistributedCache.createSymlink(conf)
    DistributedCache.addCacheFile(new URI(path), conf)
  }
}

/**
 * The SnowPlow ETL job, written in Scalding
 * (the Scala DSL on top of Cascading).
 */ 
class EtlJob(args: Args) extends Job(args) {

  // Job configuration. Scalaz recommends using fold()
  // for unpicking a Validation
  val etlConfig = EtlJobConfig.loadConfigFrom(args).fold(
    e => throw FatalEtlError(e),
    c => c)

  // Wait until we're on the nodes to instantiate with lazy
  lazy val loader = CollectorLoader.getLoader(etlConfig.inFormat).fold(
    e => throw FatalEtlError(e),
    c => c)

  lazy val ipGeo = EtlJob.getGeoIp(etlConfig.maxmindFile)

  // Aliases for our job
  val input = MultipleTextLineFiles(etlConfig.inFolder).read
  val goodOutput = Tsv(etlConfig.outFolder)
  val badOutput = JsonLine(etlConfig.badFolder)

  // Do we add a failure trap?
  val trappableInput = etlConfig.exceptionsFolder match {
    case Some(folder) => input.addTrap(Tsv(folder))
    case None => input
  }

  // Scalding data pipeline
  val common = trappableInput
    .map('line -> 'output) { l: String =>
      EtlJob.toCanonicalOutput(ipGeo, loader.toCanonicalInput(l))
    }

  // Handle bad rows
  val bad = common
    .flatMap('output -> 'errors) { o: ValidatedMaybeCanonicalOutput => o.fold(
      e => Some(e.toList), // Nel -> Some(List)
      c => None)
    }
    .project('line, 'errors)
    .write(badOutput) // JSON containing line and error(s)

  // Handle good rows
  val good = common
    .flatMapTo('output -> 'good) { o: ValidatedMaybeCanonicalOutput =>
      o match {
        case Success(Some(s)) => Some(s)
        case _ => None // Drop errors *and* blank rows
      }
    }
    .unpackTo[CanonicalOutput]('good -> '*)
    .discard('page_url, 'page_referrer) // We don't have space to store these raw URLs in Redshift currently
    .write(goodOutput)
}