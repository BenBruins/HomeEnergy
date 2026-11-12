package com.example.energiehuishouding.data.remote.solplanet

import android.util.Base64
import java.net.URLEncoder
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Precision Signer exactly matching the Solplanet Gateway's expected format.
 * Handles empty parameter values correctly (no '=' sign).
 */
object SolplanetSigner {

    /**
     * The verified signature format from server logs.
     * Handles empty values by omitting the '=' sign to match server's StringToSign.
     */
    fun generateSignature(
        method: String,
        path: String,
        queryParams: Map<String, String>,
        appKey: String,
        appSecret: String,
        timestamp: String = "",
        nonce: String = "",
        accept: String = "application/json"
    ): String {
        val pathPart = if (path.startsWith("/")) path else "/$path"
        val sortedParams = TreeMap<String, String>(queryParams)
        
        // CRITICAL FIX: If value is empty, don't add the '=' sign (matches server log: ...?date&key=...)
        val queryStr = sortedParams.entries.joinToString("&") { 
            if (it.value.isEmpty()) it.key else "${it.key}=${it.value}" 
        }
        val fullUrlPath = if (queryStr.isNotEmpty()) "$pathPart?$queryStr" else pathPart
        
        val sb = StringBuilder()
        sb.append(method.uppercase()).append("\n")
        sb.append(accept).append("\n")
        sb.append("\n\n\n") // 3 empty lines for MD5, Content-Type, Date
        
        if (nonce.isNotEmpty() && timestamp.isNotEmpty()) {
            sb.append("X-Ca-Key:$appKey\n")
            sb.append("X-Ca-Nonce:$nonce\n")
            sb.append("X-Ca-Timestamp:$timestamp\n")
        } else {
            sb.append("X-Ca-Key:$appKey\n")
        }
        sb.append(fullUrlPath)
        
        val stringToSign = sb.toString()
        val hmacSha256 = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(appSecret.trim().toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmacSha256.init(secretKey)
        val hashBytes = hmacSha256.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    /**
     * Simple Gateway Signature (Strict alignment with server log).
     */
    fun generateGatewaySignature(
        method: String,
        path: String,
        queryParams: Map<String, String>,
        appKey: String,
        appSecret: String
    ): String {
        val pathPart = if (path.startsWith("/")) path else "/$path"
        val sortedParams = TreeMap<String, String>(queryParams)
        
        // CRITICAL FIX: Match empty parameter logic
        val queryStr = sortedParams.entries.joinToString("&") { 
            if (it.value.isEmpty()) it.key else "${it.key}=${it.value}" 
        }
        val fullUrlPath = if (queryStr.isNotEmpty()) "$pathPart?$queryStr" else pathPart
        
        val sb = StringBuilder()
        sb.append(method.uppercase()).append("\n")
        sb.append("application/json").append("\n")
        sb.append("\n\n\n") 
        sb.append("X-Ca-Key:$appKey\n")
        sb.append(fullUrlPath)

        val hmacSha256 = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(appSecret.trim().toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmacSha256.init(secretKey)
        val hashBytes = hmacSha256.doFinal(sb.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}
