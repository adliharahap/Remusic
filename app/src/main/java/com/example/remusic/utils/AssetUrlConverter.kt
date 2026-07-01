package com.example.remusic.utils

/**
 * Utility untuk mengkonversi URL asset CDN jsDelivr ke URL raw GitHub
 * sebagai alternatif fallback jika CDN tidak dapat diakses.
 *
 * Contoh konversi:
 * CDN:  https://cdn.jsdelivr.net/gh/SimalayaAssets/AssetsAudio@main/uploads/xxx/cover_xxx.jpeg
 * RAW:  https://raw.githubusercontent.com/SimalayaAssets/AssetsAudio/main/uploads/xxx/cover_xxx.jpeg
 */
object AssetUrlConverter {

    private const val JSDELIVR_PREFIX = "https://cdn.jsdelivr.net/gh/"
    private const val RAW_GITHUB_PREFIX = "https://raw.githubusercontent.com/"

    /**
     * Mengecek apakah URL adalah jsDelivr CDN URL milik SimalayaAssets.
     */
    fun isJsDelivrUrl(url: String?): Boolean {
        return url?.startsWith(JSDELIVR_PREFIX) == true
    }

    /**
     * Mengkonversi jsDelivr CDN URL ke raw GitHub URL.
     *
     * Format jsDelivr : https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}/{path}
     * Format raw GH   : https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
     *
     * @return raw GitHub URL jika URL valid, atau null jika bukan jsDelivr URL
     */
    fun toRawGithubUrl(cdnUrl: String?): String? {
        if (cdnUrl == null) return null
        if (!isJsDelivrUrl(cdnUrl)) return null

        return try {
            // Ambil bagian setelah prefix "https://cdn.jsdelivr.net/gh/"
            // Contoh: "SimalayaAssets/AssetsAudio@main/uploads/xxx/cover_xxx.jpeg"
            val afterPrefix = cdnUrl.removePrefix(JSDELIVR_PREFIX)

            // Pisahkan owner/repo@branch dari path-nya
            // Format: "{owner}/{repo}@{branch}/{rest...}"
            // Cari posisi '@' untuk memisahkan bagian repo dari branch
            val atIndex = afterPrefix.indexOf('@')
            if (atIndex == -1) return null

            // ownerRepo = "SimalayaAssets/AssetsAudio"
            val ownerRepo = afterPrefix.substring(0, atIndex)

            // branchAndPath = "main/uploads/xxx/cover_xxx.jpeg"
            val branchAndPath = afterPrefix.substring(atIndex + 1)

            // Bangun URL raw GitHub:
            // https://raw.githubusercontent.com/SimalayaAssets/AssetsAudio/main/uploads/xxx/cover_xxx.jpeg
            "$RAW_GITHUB_PREFIX$ownerRepo/$branchAndPath"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mendapatkan URL alternatif untuk sebuah URL.
     * Jika URL adalah jsDelivr, kembalikan raw GitHub URL-nya.
     * Jika bukan, kembalikan null.
     *
     * @return fallback URL atau null jika tidak ada alternatif
     */
    fun getFallbackUrl(originalUrl: String?): String? {
        return if (isJsDelivrUrl(originalUrl)) {
            toRawGithubUrl(originalUrl)
        } else {
            null
        }
    }
}
