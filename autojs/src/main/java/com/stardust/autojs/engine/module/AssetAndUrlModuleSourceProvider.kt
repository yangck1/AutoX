package com.stardust.autojs.engine.module

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.stardust.autojs.engine.encryption.ScriptEncryption.decrypt
import com.stardust.autojs.script.EncryptedScriptFileHeader
import com.stardust.autojs.script.EncryptedScriptFileHeader.isValidFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mozilla.javascript.commonjs.module.provider.ModuleSource
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProviderBase
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI

class AssetAndUrlModuleSourceProvider(
    context: Context,
    list: List<URI> = emptyList()
) : ModuleSourceProviderBase() {
    val mContext = context
    private val okHttpClient = OkHttpClient.Builder().followRedirects(true).build()
    private val contentResolver: ContentResolver = context.contentResolver
    private val moduleSources: List<URI> = list

    companion object {
        private const val assetRoot: String = "file:/android_asset"
        val MODULE_DIR: URI = URI.create("file:/android_asset/modules")
        val NPM_MODULE_DIR: URI = URI.create("file:/android_asset/modules/npm")
        fun toAssetUri(assetDirPath: String): URI {
            return URI.create("$assetRoot/$assetDirPath")
        }
    }

    //初始化脚本以及启动文件只会从此方法加载模块，子模块加载没有以"./"或"../"开头的模块也会从此方法加载
    override fun loadFromPrivilegedLocations(moduleId: String, validator: Any?): ModuleSource? {
        //println("加载私有模块：$moduleId")
        val uri = if (moduleId.startsWith("/")) {
            File(moduleId).toURI()
        } else if (moduleId.startsWith("http://") || moduleId.startsWith("https://")) {
            URI.create(moduleId)
        } else null
        if (uri != null) {
            return loadFromUri(uri, File(uri.path).parentFile?.toURI(), validator)
        }
        for (baseUri in moduleSources) {
            val sourceUri = URI.create("$baseUri/$moduleId")
            val moduleSource = loadFromUri(sourceUri, baseUri, validator)
            if (moduleSource != null) {
                return moduleSource
            }
        }
        return null
    }

    //这里处理node_module目录的模块
    override fun loadFromFallbackLocations(moduleId: String, validator: Any?): ModuleSource? {
        return super.loadFromFallbackLocations(moduleId, validator)
    }

    //子模块以相对路径加载时调用此方法
    override fun loadFromUri(uri: URI, base: URI?, validator: Any?): ModuleSource? {
        var uri = uri
        if (uri.scheme == null) uri = File(uri.path).toURI()
        //println("加载模块：$uri")
        if (uri.scheme == "http" || uri.scheme == "https") {
            return loadFromHttp(uri, base, validator)
        }
        val moduleSource = loadAt(uri, base, validator) ?: loadAt(
            File(uri.path + ".js").toURI(), base, validator
        )
        if (moduleSource != null) {
            return moduleSource
        }
        //尝试从目录加载
        //尝试读取package.json指定的文件
        val mainFile: URI? = try {
            val packageFile = File(uri.path, "package.json")
            val json = Gson().fromJson<Map<String, Any>>(
                InputStreamReader(packageFile.inputStream()),
                Map::class.java
            )
            val main = json["main"] as String
            packageFile.toURI().resolve(main)
        } catch (e: Exception) {
            null
        }
        mainFile?.let {
            //若package.json中main入口读取成功则重新执行模块加载
            return loadFromUri(it,uri,validator)
        }?: return loadAt(File(uri.path, "index.js").toURI(),uri,validator)
    }

    private fun loadAt(uri: URI, base: URI?, validator: Any?): ModuleSource? {
        if (uri.scheme == "http" || uri.scheme == "https") {
            return loadFromHttp(uri, base, validator)
        }
        return try {
            val inputStream = if (uri.path.startsWith("/android_asset/")) {
                mContext.assets.open(uri.path.replace("/android_asset/", ""))
            } else contentResolver.openInputStream(Uri.parse(uri.toString()))
            if (inputStream != null) {
                createModuleEncryptionSource(inputStream, uri, base, validator)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun createModuleEncryptionSource(
        inputStream: InputStream,
        uri: URI,
        base: URI?,
        validator: Any?,
    ): ModuleSource {
        val bytes = ByteArray(inputStream.available())
        inputStream.read(bytes)
        inputStream.close()
        val i = if (isValidFile(bytes)) {
            val clearText = decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE, bytes.size)
            ByteArrayInputStream(clearText)
        } else ByteArrayInputStream(bytes)
        return createModuleSource(i, uri, base, validator)
    }

    private fun loadFromHttp(uri: URI, base: URI?, validator: Any?): ModuleSource? {
        return try {
            Request.Builder().url(uri.toString()).build().let { request ->
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return null
                }
                response.body?.let {
                    val charset = it.contentType()?.charset()?.toString() ?: "utf-8"
                    return createModuleSource(it.byteStream(), uri, base, validator, charset)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createModuleSource(
        inputStream: InputStream,
        uri: URI,
        base: URI?,
        validator: Any?,
        charset: String? = null
    ): ModuleSource {
        val id = if (uri.scheme == "file") {
            URI.create(uri.path)
        } else uri
        return ModuleSource(
            InputStreamReader(inputStream, charset ?: "utf-8"),
            null,
            id,
            base,
            validator
        )
    }
}