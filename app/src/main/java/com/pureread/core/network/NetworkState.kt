package com.pureread.core.network

/**
 * 设备网络连接状态。
 */
public sealed class NetworkState {

    /**
     * 网络可用。
     */
    public data object Available : NetworkState()

    /**
     * 网络不可用。
     */
    public data object Unavailable : NetworkState()

    /**
     * 正在检测中。
     */
    public data object Checking : NetworkState()
}
