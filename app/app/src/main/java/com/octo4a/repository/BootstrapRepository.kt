package com.octo4a.repository

import android.content.Context
import android.os.Build
import android.util.Pair
import com.octo4a.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

interface BootstrapRepository {
    val commandsFlow: SharedFlow<String>
    suspend fun downloadBootstrap()
    suspend fun extractBootstrap()
    fun runCommand(
        command: String,
        prooted: Boolean = true,
        root: Boolean = true,
        bash: Boolean = false
    ): Process

    fun ensureHomeDirectory()
    fun resetSSHPassword(newPassword: String)

    fun selectReleaseForInstallation(release: GithubRelease)

    val isBootstrapInstalled: Boolean
    val isSSHConfigured: Boolean
    val bootstrapVersion: String
}

class BootstrapRepositoryImpl(
    private val logger: LoggerRepository,
    private val githubRepository: GithubRepository,
    val context: Context
) : BootstrapRepository {
    companion object {
        private val FILES_PATH = "/data/data/com.octo4a/files"
        val PREFIX_PATH = "$FILES_PATH/bootstrap"
        val HOME_PATH = "$FILES_PATH/home"
    }

    val filesPath: String by lazy { context.getExternalFilesDir(null).absolutePath }
    private var _commandsFlow = MutableSharedFlow<String>(100)
    private var _selectedGitHubRelease: GithubRelease? = null
    override val commandsFlow: SharedFlow<String>
        get() = _commandsFlow

    private fun shouldUsePre5Bootstrap(): Boolean {
        if (getArchString() != "arm" && getArchString() != "i686") {
            return false
        }

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
    }

    override fun selectReleaseForInstallation(release: GithubRelease) {
        _selectedGitHubRelease = release
    }

    override suspend fun downloadBootstrap() {
        withContext(Dispatchers.IO) {
            val prefixFile = File(PREFIX_PATH)
            if (prefixFile.isDirectory) {
                return@withContext
            }

            try {
                if (_selectedGitHubRelease == null) {
                    logger.log(this) { "No release selected for installation" }
                    return@withContext
                }
                val arch = getArchString()
                val asset = _selectedGitHubRelease!!.assets.firstOrNull { asset -> asset.name.contains(arch) }
                logger.log(this) { "Arch: $arch" }

                val STAGING_PREFIX_PATH = "${FILES_PATH}/bootstrap-staging"
                val STAGING_PREFIX_FILE = File(STAGING_PREFIX_PATH)

                if (STAGING_PREFIX_FILE.exists()) {
                    deleteFolder(STAGING_PREFIX_FILE)
                }

                val buffer = ByteArray(8096)
                val symlinks = ArrayList<Pair<String, String>>(50)

                val urlPrefix = asset!!.browserDownloadUrl
                logger.log(this) { "Downloading bootstrap ${_selectedGitHubRelease?.tagName} from $urlPrefix" }

                val sslcontext = SSLContext.getInstance("TLSv1")
                sslcontext.init(null, null, null)
                val noSSLv3Factory: SSLSocketFactory = TLSSocketFactory()

                HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory)
                val connection: HttpsURLConnection =
                    URL(urlPrefix).openConnection() as HttpsURLConnection
                connection.sslSocketFactory = noSSLv3Factory
                connection.connect()
                val code = connection.responseCode
                logger.log(this) { "Request to ${connection.url} returned status code $code" }
                if (code > 399) {

                    throw RuntimeException(
                        "Fetching ${connection.url} failed with status code $code"
                    )
                }
                ZipInputStream(connection.inputStream).use { zipInput ->
                    var zipEntry = zipInput.nextEntry
                    while (zipEntry != null) {
                        logger.log(this) { "Zip got file ${zipEntry.name}"}
                        val zipEntryName = zipEntry.name
                        val targetFile = File(STAGING_PREFIX_PATH, zipEntryName)
                        val isDirectory = zipEntry.isDirectory

                        ensureDirectoryExists(if (isDirectory) targetFile else targetFile.parentFile)

                        if (!isDirectory) {
                            FileOutputStream(targetFile).use { outStream ->
                                var readBytes = zipInput.read(buffer)
                                while ((readBytes) != -1) {
                                    outStream.write(buffer, 0, readBytes)
                                    readBytes = zipInput.read(buffer)
                                }
                            }
                        }
                        zipEntry = zipInput.nextEntry
                    }
                }

                if (!STAGING_PREFIX_FILE.renameTo(prefixFile)) {
                    throw RuntimeException("Unable to rename staging folder")
                }

                return@withContext
            } catch (e: Exception) {
                throw (e)
            } finally {
            }
        }

    }

    override suspend fun extractBootstrap() {
        withContext(Dispatchers.IO) {
            logger.log(this) { "Bootstrap downloaded, extracting it..." }
            runCommand("ls", prooted = false).waitAndPrintOutput(logger)

            // First call inside of proot will automatically extract the bootstrap
            runCommand("ls").waitAndPrintOutput(logger)
            logger.log(this) { "Bootstrap extracted, setting it up..." }

            runCommand("chmod -R 755 /mnt/external/").waitAndPrintOutput(
                logger
            )

            runCommand("cp -rf /home/octoprint/extensions /mnt/external/").waitAndPrintOutput(
                logger
            )

            runCommand("chown root:root /mnt/external/").waitAndPrintOutput(
                logger
            )
            runCommand("mkdir -p /mnt/external/.octoprint/plugins").waitAndPrintOutput(logger)
            runCommand("cp /home/octoprint/comm-fix.py /mnt/external/.octoprint/plugins").waitAndPrintOutput(
                logger
            )
        }
    }

    private fun ensureDirectoryExists(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw RuntimeException("Unable to create directory: " + directory.absolutePath)
        }
    }

    /** Delete a folder and all its content or throw. Don't follow symlinks.  */
    @Throws(IOException::class)
    fun deleteFolder(fileOrDirectory: File) {
        if (fileOrDirectory.canonicalPath == fileOrDirectory.absolutePath && fileOrDirectory.isDirectory) {
            val children = fileOrDirectory.listFiles()

            if (children != null) {
                for (child in children) {
                    deleteFolder(child)
                }
            }
        }

        if (!fileOrDirectory.delete()) {
            throw RuntimeException("Unable to delete " + (if (fileOrDirectory.isDirectory) "directory " else "file ") + fileOrDirectory.absolutePath)
        }
    }

    override fun runCommand(
        command: String,
        prooted: Boolean,
        root: Boolean,
        bash: Boolean
    ): Process {
        logger.log { ">$command" }
        val FILES = "/data/data/com.octo4a/files"
        val directory = File(filesPath)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val pb = ProcessBuilder()
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = "$FILES/bootstrap"
        pb.environment()["LANG"] = "'en_US.UTF-8'"
        pb.environment()["PWD"] = "$FILES/bootstrap"
        logger.log(this) {filesPath}
        pb.environment()["EXTRA_BIND"] =
            "-b ${filesPath}:/mnt/external -b /data/data/com.octo4a/files/serialpipe:/dev/ttyOcto4a"
//        pb.environment()["PATH"] =
//            "/sbin:/system/sbin:/product/bin:/apex/com.android.runtime/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
        pb.directory(File("$FILES/bootstrap"))
        var user = "root"
        if (!root) user = "octoprint"
        if (prooted) {
            // run inside proot
            val shell = if (bash) "/bin/bash" else "/bin/sh"
            pb.command("sh", "entrypoint.sh", user, shell, "-c", command)
        } else {
            pb.command("sh", "-c", command)
        }
        return pb.start()
    }

    override fun ensureHomeDirectory() {
//        val homeFile = File(HOME_PATH)
//        if (!homeFile.exists()) {
//            homeFile.mkdir()
//        }
    }

    override val isSSHConfigured: Boolean
        get() {
            return File("/data/data/com.octo4a/files/bootstrap/bootstrap/home/octoprint/.ssh_configured").exists()
        }

    override val bootstrapVersion: String
        get() = runCommand("cat build-version.txt", prooted = false).getOutputAsString()

    override fun resetSSHPassword(newPassword: String) {
        logger.log(this) { "Deleting password just in case" }
        try {
            runCommand("passwd -d octoprint").waitAndPrintOutput(logger)
        } catch (e: java.lang.Exception) {
            logger.log(this) { "Failed to delete password: $e" }
        }

        runCommand("passwd octoprint").setPassword(newPassword)
        try {
            runCommand("passwd -d root").waitAndPrintOutput(logger)
        } catch (e: java.lang.Exception) {
            logger.log(this) { "Failed to delete password: $e" }
        }
        runCommand("passwd root").setPassword(newPassword)
        runCommand("touch .ssh_configured", root = false)
        runCommand("touch .ssh_configured", root = true)
    }

    override val isBootstrapInstalled: Boolean
        get() = File("$FILES_PATH/bootstrap/bootstrap").exists()
}