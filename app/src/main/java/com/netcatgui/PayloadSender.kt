package com.netcatgui

import java.net.Socket
import java.net.SocketTimeoutException

object PayloadSender {

    data class Result(val success: Boolean, val message: String)

    fun send(ip: String, port: Int, data: ByteArray): Result {
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, port), 5000)
                socket.soTimeout = 10000

                val out = socket.getOutputStream()
                out.write(data)
                out.flush()
            }
            Result(true, "OK")
        } catch (e: SocketTimeoutException) {
            Result(false, "Timeout: no se pudo conectar a $ip:$port")
        } catch (e: java.net.ConnectException) {
            Result(false, "Conexión rechazada en $ip:$port")
        } catch (e: java.net.UnknownHostException) {
            Result(false, "Host desconocido: $ip")
        } catch (e: Exception) {
            Result(false, e.message ?: "Error desconocido")
        }
    }
}
