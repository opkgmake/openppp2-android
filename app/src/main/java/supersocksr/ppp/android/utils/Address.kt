package supersocksr.ppp.android.utils

import kotlinx.serialization.Serializable

@Serializable
data class Address(
  var scheme: String? = null,
  val host: String? = null,
  val additional_ip: String? = null,
  val port: Int? = null,
  val path: String? = null,
) {

  companion object {
    fun parse(input: String): Address? {
      return try {
        unsafeParse(input)
      } catch (e: Exception) {
        null
      }
    }

    fun unsafeParse(input: String): Address {
      val trimmed = input.trim()
      require(trimmed.isNotEmpty()) { "address cannot be empty" }

      var scheme: String? = null
      var remainder = trimmed
      val schemeSeparator = remainder.indexOf("://")
      if (schemeSeparator >= 0) {
        scheme = remainder.substring(0, schemeSeparator).ifBlank { null }
        remainder = remainder.substring(schemeSeparator + 3)
      }

      var path: String? = null
      val slashIndex = remainder.indexOf('/')
      if (slashIndex >= 0) {
        path = remainder.substring(slashIndex).ifBlank { null }
        remainder = remainder.substring(0, slashIndex)
      }

      var hostToken = remainder.trim()
      var literalIp: String? = null

      if (hostToken.contains('[')) {
        val start = hostToken.indexOf('[')
        val end = hostToken.indexOf(']', start + 1)
        require(start >= 0 && end > start) { "invalid ipv6 literal" }
        literalIp = hostToken.substring(start + 1, end)
        hostToken = (hostToken.substring(0, start) + hostToken.substring(end + 1)).trim()
      }

      var host: String? = null
      var port: Int? = null

      if (hostToken.isNotEmpty()) {
        val lastColon = hostToken.lastIndexOf(':')
        if (lastColon > 0 && hostToken.substring(0, lastColon).indexOf(':') == -1) {
          val hostPart = hostToken.substring(0, lastColon)
          val portPart = hostToken.substring(lastColon + 1)
          if (portPart.isNotEmpty()) {
            port = portPart.toInt()
          }
          host = hostPart.ifBlank { null }
        } else if (lastColon == 0) {
          val portPart = hostToken.substring(1)
          if (portPart.isNotEmpty()) {
            port = portPart.toInt()
          }
        } else if (hostToken.contains(':')) {
          literalIp = literalIp ?: hostToken
        } else {
          host = hostToken.ifBlank { null }
        }
      }

      val ip = literalIp?.ifBlank { null }
      return Address(scheme, host, ip, port, path)
    }
  }

  override fun toString(): String {
    val sb = StringBuilder()
    if (scheme != null) sb.append("$scheme://")
    if (host != null) sb.append(host)
    if (additional_ip != null) sb.append("[$additional_ip]")
    if (port != null) sb.append(":$port")
    if (path != null) sb.append(path)
    return sb.toString()
  }

  fun debug() {
    println("scheme: $scheme")
    println("host: $host")
    println("additional_ip: $additional_ip")
    println("port: $port")
    println("path: $path")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Address

    return scheme == other.scheme && host == other.host && port == other.port && path == other.path && additional_ip == other.additional_ip
  }

  fun hasScheme(): Boolean {
    return scheme != null
  }

  fun hasPort(): Boolean {
    return port != null
  }

  fun hasAdditionalIp(): Boolean {
    return additional_ip != null
  }

  fun hasPath(): Boolean {
    return path != null
  }

  fun hasHost(): Boolean {
    return host != null
  }

  override fun hashCode(): Int {
    var result = scheme?.hashCode() ?: 0
    result = 31 * result + (host?.hashCode() ?: 0)
    result = 31 * result + (additional_ip?.hashCode() ?: 0)
    result = 31 * result + (port ?: 0)
    result = 31 * result + (path?.hashCode() ?: 0)
    return result
  }

}