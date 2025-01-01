// src/main/scala/blockchain/util/Util.scala

package blockchain.util

import java.time.{Instant, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

object Util {
  // Generate a SHA-256 hash
  def sha256(data: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.digest(data.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  // Convert timestamp to human-readable format
  def timestampToDateTime(timestamp: Long, zone: String = "UTC"): String = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of(zone)))
  }

  // Simple random ID generator for transactions or blocks
  def generateId(): String = java.util.UUID.randomUUID().toString
}
