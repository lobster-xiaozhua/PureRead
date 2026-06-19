package com.pureread.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.pureread.core.log.PureLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 网络状态观察者。
 *
 * 职责：通过 [ConnectivityManager.NetworkCallback] 监听设备网络变化，并暴露为 [Flow<NetworkState>]。
 *
 * 线程安全：所有回调均在 ConnectivityManager 内部线程触发，状态写入使用 volatile 语义的原子操作。
 */
public class NetworkObserver private constructor(
    private val connectivityManager: ConnectivityManager
) {

    private companion object {
        private const val TAG = "NetworkObserver"
    }

    private val _networkStateFlow = MutableStateFlow(NetworkState.Checking)

    /**
     * 当前网络状态流。
     */
    public val networkStateFlow: Flow<NetworkState> = _networkStateFlow
        .asStateFlow()
        .distinctUntilChanged()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        public override fun onAvailable(network: Network): Unit {
            PureLog.i(TAG, "onAvailable", "网络已恢复")
            _networkStateFlow.value = NetworkState.Available
        }

        public override fun onLost(network: Network): Unit {
            PureLog.w(TAG, "onLost", "网络已断开")
            _networkStateFlow.value = NetworkState.Unavailable
        }

        public override fun onUnavailable(): Unit {
            PureLog.w(TAG, "onUnavailable", "网络不可用")
            _networkStateFlow.value = NetworkState.Unavailable
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        _networkStateFlow.value = getCurrentNetworkState()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    /**
     * 同步获取当前网络是否可用。
     */
    public fun isNetworkAvailable(): Boolean {
        return getCurrentNetworkState() == NetworkState.Available
    }

    private fun getCurrentNetworkState(): NetworkState {
        val network = connectivityManager.activeNetwork ?: return NetworkState.Unavailable
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkState.Unavailable
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            NetworkState.Available
        } else {
            NetworkState.Unavailable
        }
    }

    /**
     * 释放网络监听资源。通常由 Application 生命周期管理调用。
     */
    public fun stop(): Unit {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            PureLog.e(TAG, "stop", e, "注销网络监听失败")
        }
    }

    public companion object {

        /**
         * 创建 [NetworkObserver] 实例。
         *
         * @param context 应用上下文
         * @return NetworkObserver 实例
         */
        @JvmStatic
        public fun create(context: Context): NetworkObserver {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return NetworkObserver(connectivityManager)
        }
    }
}
