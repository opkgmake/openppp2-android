package supersocksr.ppp.android.utils

import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.serialization.Serializable
import supersocksr.ppp.android.openppp2.Macro

@Serializable
data class UserConfig(
  val name: String = "",
  val server: Address = Address.unsafeParse("ppp://127.0.0.1:1080"),
  val static_server: Address = Address.unsafeParse("192.168.0.24:20000"),
  val guid: String = "Random",
  val tun_address: Address? = Address.unsafeParse("10.0.0.214"),
  val tun_subnet: String = "255.255.255.0",
  val tun_ipv6: String? = null,
  val tun_ipv6_prefix: Int = 64,
  val dns_servers: List<String> = listOf("8.8.8.8", "1.1.1.1", "2001:4860:4860::8888"),
  val routes_v4: List<String> = listOf("0.0.0.0/0", "128.0.0.0/1"),
  val routes_v6: List<String> = listOf("::/0"),
  val allow_apps: List<String> = emptyList(),
  val disallow_apps: List<String> = emptyList(),
  val mtu: Int = Macro.MTU,
) {
  fun validate() {
    // supplement
    if (!server.hasScheme()) {
      server.scheme = "ppp"
    }
    // check
    if (server.scheme == "ppp" && !server.hasPort()) {
      throw IllegalArgumentException("ppp server port cannot be empty")
    }
    if (!static_server.hasPort()) {
      throw IllegalArgumentException("static_server port cannot be empty")
    }
    if (tun_address == null) {
      throw IllegalArgumentException("tun address cannot be empty")
    }
    if (!tun_subnet.isValidIpv4()) {
      throw IllegalArgumentException("invalid IPv4 subnet: $tun_subnet")
    }
    if (tun_ipv6_prefix !in 0..128) {
      throw IllegalArgumentException("invalid IPv6 prefix length: $tun_ipv6_prefix")
    }
    tun_ipv6?.let {
      if (it.isNotBlank() && !it.isValidIpAddress()) {
        throw IllegalArgumentException("invalid IPv6 address: $it")
      }
    }
    dns_servers.forEach {
      if (it.isNotBlank() && !it.isValidIpAddress()) {
        throw IllegalArgumentException("invalid dns server: $it")
      }
    }
    routes_v4.forEach {
      if (it.isNotBlank() && !it.isValidRoute()) {
        throw IllegalArgumentException("invalid ipv4 route: $it")
      }
    }
    routes_v6.forEach {
      if (it.isNotBlank() && !it.isValidRoute()) {
        throw IllegalArgumentException("invalid ipv6 route: $it")
      }
    }
    if (mtu < 1280 || mtu > 9000) {
      throw IllegalArgumentException("mtu out of range: $mtu")
    }
  }
}

private fun String.isValidIpAddress(): Boolean {
  return try {
    InetAddress.getByName(this)
    true
  } catch (_: UnknownHostException) {
    false
  }
}

private fun String.isValidIpv4(): Boolean {
  return try {
    val inetAddress = InetAddress.getByName(this)
    inetAddress.address.size == 4
  } catch (_: Exception) {
    false
  }
}

private fun String.isValidRoute(): Boolean {
  val parts = split('/')
  if (parts.size != 2) return false
  val prefix = parts[1].toIntOrNull() ?: return false
  val address = parts[0]
  val inet = try {
    InetAddress.getByName(address)
  } catch (_: Exception) {
    return false
  }
  val maxPrefix = if (inet.address.size == 4) 32 else 128
  return prefix in 0..maxPrefix
}
