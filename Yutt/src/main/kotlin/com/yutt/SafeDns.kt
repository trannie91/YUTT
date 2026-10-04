package com.yutt

import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address

class SafeDns : Dns {
    // Địa chỉ IP Cloudflare Anycast của yuthanhthien.top
    private val staticHosts = mapOf(
        "www.yuthanhthien.top" to listOf("104.21.59.198", "172.67.183.39"),
        "yuthanhthien.top" to listOf("104.21.59.198", "172.67.183.39")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val hostLower = hostname.lowercase()
        val directIps = staticHosts[hostLower]
        if (!directIps.isNullOrEmpty()) {
            val resolved = directIps.mapNotNull { ip ->
                try {
                    InetAddress.getByName(ip)
                } catch (e: Exception) {
                    null
                }
            }
            if (resolved.isNotEmpty()) {
                return resolved
            }
        }

        return try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            // Lọc bỏ triệt để các địa chỉ loopback (::1, 127.0.0.1, 0.0.0.0) do adblock hoặc chặn DNS gây ra
            val valid = systemAddresses.filter { addr ->
                !addr.isLoopbackAddress && !addr.isAnyLocalAddress && addr.hostAddress != "0.0.0.0" && addr.hostAddress != "::1"
            }
            if (valid.isNotEmpty()) {
                // Ưu tiên IPv4 trước để giải quyết lỗi kết nối IPv6 trên Android TV Box
                valid.sortedBy { if (it is Inet4Address) 0 else 1 }
            } else {
                systemAddresses
            }
        } catch (e: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
