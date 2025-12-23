package ru.zagrebin.culinaryblog.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Utility class to monitor network connectivity changes
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"

    /**
     * Returns a Flow that emits true when network is available, false otherwise
     */
    fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            private val networks = mutableSetOf<Network>()
            
            override fun onAvailable(network: Network) {
                networks.add(network)
                Log.d(TAG, "Network available: $network, total: ${networks.size}")
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                networks.remove(network)
                Log.d(TAG, "Network lost: $network, remaining: ${networks.size}")
                trySend(networks.isNotEmpty())
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, callback)
        
        // Send current state immediately
        val isConnected = isNetworkAvailable(context)
        Log.d(TAG, "Initial network state: $isConnected")
        trySend(isConnected)
        
        awaitClose {
            Log.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Check if network is currently available
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
