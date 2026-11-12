package com.ansh.memyclock.time

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NetworkTime {
    val offset = MutableStateFlow<Long?>(null)
    private val servers = listOf("time.google.com", "time.cloudflare.com", "pool.ntp.org")
    private const val EPOCH_GAP = 2_208_988_800L

    fun now() = System.currentTimeMillis() + (offset.value ?: 0)

    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        for (server in servers) {
            val best = (1..3).mapNotNull { runCatching { query(server) }.getOrNull() }.minByOrNull { it.first }
            if (best != null) {
                offset.value = best.second
                return@withContext true
            }
        }
        false
    }

    private fun query(server: String): Pair<Long, Long> = DatagramSocket().use { socket ->
        socket.soTimeout = 2500
        val address = InetAddress.getByName(server)
        val request = ByteArray(48)
        request[0] = 0x23
        val sentWall = System.currentTimeMillis()
        val sentTick = SystemClock.elapsedRealtime()
        write(request, 40, sentWall)
        socket.send(DatagramPacket(request, request.size, address, 123))
        val reply = ByteArray(48)
        socket.receive(DatagramPacket(reply, reply.size))
        val backWall = sentWall + (SystemClock.elapsedRealtime() - sentTick)
        val received = read(reply, 32)
        val transmitted = read(reply, 40)
        if (reply[1].toInt() == 0) error("Server not ready")
        val delay = (backWall - sentWall) - (transmitted - received)
        val shift = ((received - sentWall) + (transmitted - backWall)) / 2
        delay to shift
    }

    private fun read(buffer: ByteArray, at: Int): Long {
        fun word(start: Int) = (0..3).fold(0L) { acc, i -> (acc shl 8) or (buffer[start + i].toLong() and 0xFF) }
        var seconds = word(at)
        if (seconds < 0x80000000L) seconds += 0x100000000L
        return (seconds - EPOCH_GAP) * 1000 + word(at + 4) * 1000 / 0x100000000L
    }

    private fun write(buffer: ByteArray, at: Int, millis: Long) {
        val seconds = millis / 1000 + EPOCH_GAP
        val fraction = (millis % 1000) * 0x100000000L / 1000
        for (i in 0..3) {
            buffer[at + i] = (seconds shr (24 - i * 8)).toByte()
            buffer[at + 4 + i] = (fraction shr (24 - i * 8)).toByte()
        }
    }
}
