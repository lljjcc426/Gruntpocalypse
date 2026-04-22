package net.spartanb312.grunteon.obfuscator.web

import io.minio.DownloadObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import java.io.ByteArrayInputStream
import java.io.File

class MinioObjectStorageBackend(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    private val bucket: String,
    region: String,
    cacheDir: File
) : ObjectStorageBackend {

    private val minioClient: MinioClient = MinioClient.builder().apply {
        endpoint(endpoint)
        credentials(accessKey, secretKey)
        region(region)
    }.build()

    private val cacheDir = cacheDir.absoluteFile.apply { mkdirs() }

    override fun exists(objectKey: String): Boolean {
        return try {
            val args = StatObjectArgs.builder().apply {
                bucket(bucket)
                `object`(objectKey)
            }.build()
            minioClient.statObject(
                args
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun putObject(objectKey: String, bytes: ByteArray) {
        try {
            val args = PutObjectArgs.builder().apply {
                bucket(bucket)
                `object`(objectKey)
                stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
            }.build()
            minioClient.putObject(
                args
            )
            val cacheFile = resolveCacheFile(objectKey)
            cacheFile.parentFile.mkdirs()
            cacheFile.writeBytes(bytes)
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to upload object to MinIO", exception)
        }
    }

    override fun getObject(objectKey: String): File {
        val cacheFile = resolveCacheFile(objectKey)
        cacheFile.parentFile.mkdirs()
        return try {
            val args = DownloadObjectArgs.builder().apply {
                bucket(bucket)
                `object`(objectKey)
                filename(cacheFile.absolutePath)
                overwrite(true)
            }.build()
            minioClient.downloadObject(
                args
            )
            cacheFile
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to download object from MinIO", exception)
        }
    }

    override fun describe(objectKey: String): ObjectStorageLocation {
        return ObjectStorageLocation(
            storageBackend = "minio",
            bucketName = bucket,
            objectPath = objectKey.replace('\\', '/')
        )
    }

    private fun resolveCacheFile(objectKey: String): File {
        return File(cacheDir, objectKey.replace('\\', '/')).absoluteFile
    }
}
