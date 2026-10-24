package com.shilapi.xcertplay.network

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.net.Socket

/** Detect a vanished peer, without treating a legitimately idle CarPlay screen as a failure. */
internal object TcpLiveness {
    // Linux UAPI <linux/tcp.h>; Android's SDK omits these three OsConstants fields.
    private const val TCP_KEEPIDLE = 4
    private const val TCP_KEEPINTVL = 5
    private const val TCP_KEEPCNT = 6

    fun configure(socket: Socket, diagnostic: (String) -> Unit) =
        configure(socket, ::legacySocketDescriptor, diagnostic)

    internal fun configure(
        socket: Socket,
        legacyDescriptor: (Socket) -> FileDescriptor,
        diagnostic: (String) -> Unit,
    ) {
        try {
            socket.keepAlive = true
            if (Build.VERSION.SDK_INT < 29) {
                // Android 9 fromSocket() shares the socket's FileDescriptor object. Both
                // close() and detachFd() invalidate the live socket. Borrow it directly;
                // never construct an owning wrapper or change this descriptor's ownership.
                configureDescriptor(legacyDescriptor(socket))
            } else {
                // Android 10+ fromSocket() duplicates the descriptor, so this wrapper owns it.
                ParcelFileDescriptor.fromSocket(socket).use { configureDescriptor(it.fileDescriptor) }
            }
            diagnostic("TCP peer health enabled idle=10s interval=3s count=3 deadline=20000ms; no video-idle timeout")
        } catch (failure: Exception) {
            diagnostic("TCP peer health tuning unavailable: ${failure.javaClass.simpleName}")
        }
    }

    private fun legacySocketDescriptor(socket: Socket): FileDescriptor =
        Socket::class.java.getMethod("getFileDescriptor\$").invoke(socket) as FileDescriptor

    private fun configureDescriptor(fd: FileDescriptor) {
        Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, TCP_KEEPIDLE, 10)
        Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, TCP_KEEPINTVL, 3)
        Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, TCP_KEEPCNT, 3)
        Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, OsConstants.TCP_USER_TIMEOUT, 20_000)
    }
}
