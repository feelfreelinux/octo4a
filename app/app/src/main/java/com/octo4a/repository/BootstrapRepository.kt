package com.octo4a.repository

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Pair
import com.octo4a.BuildConfig
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
    suspend fun setupBootstrap()
    fun runCommand(command: String, prooted: Boolean = true, root: Boolean = true): Process
    fun ensureHomeDirectory()
    fun resetSSHPassword(newPassword: String)
    val isBootstrapInstalled: Boolean
    val isSSHConfigured: Boolean
}

class BootstrapRepositoryImpl(private val logger: LoggerRepository, private val githubRepository: GithubRepository, val context: Context) : BootstrapRepository {
    companion object {
        private val FILES_PATH = "/data/data/com.octo4a/files"
        val PREFIX_PATH = "$FILES_PATH/bootstrap"
        val HOME_PATH = "$FILES_PATH/home"
    }

    private var _commandsFlow = MutableSharedFlow<String>(100)
    override val commandsFlow: SharedFlow<String>
        get() = _commandsFlow

    private fun shouldUsePre5Bootstrap(): Boolean {
        if (getArchString() != "arm" && getArchString() != "i686") {
            return false
        }

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
    }

    override suspend fun setupBootstrap() {
        withContext(Dispatchers.IO) {
            val PREFIX_FILE = File(PREFIX_PATH)
            if (PREFIX_FILE.isDirectory) {
                return@withContext
            }

            try {
                val bootstrapReleases = githubRepository.getNewestReleases("feelfreelinux/android-linux-bootstrap")
                val arch = getArchString()

                val release = bootstrapReleases.firstOrNull {
                    it.assets.any { asset -> asset.name.contains(arch) }
                }

                val asset = release?.assets?.first { asset -> asset.name.contains(arch)  }

                logger.log(this) { "Downloading bootstrap ${release?.tagName}" }

                val STAGING_PREFIX_PATH = "${FILES_PATH}/bootstrap-staging"
                val STAGING_PREFIX_FILE = File(STAGING_PREFIX_PATH)

                if (STAGING_PREFIX_FILE.exists()) {
                    deleteFolder(STAGING_PREFIX_FILE)
                }

                val buffer = ByteArray(8096)
                val symlinks = ArrayList<Pair<String, String>>(50)

                val urlPrefix = asset!!.browserDownloadUrl
                val sslcontext = SSLContext.getInstance("TLSv1")
                sslcontext.init(null, null, null)
                val noSSLv3Factory: SSLSocketFactory = TLSSocketFactory()

                HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory)
                val connection: HttpsURLConnection = URL(urlPrefix).openConnection() as HttpsURLConnection
                connection.sslSocketFactory = noSSLv3Factory

                ZipInputStream(connection.inputStream).use { zipInput ->
                    var zipEntry = zipInput.nextEntry
                    while (zipEntry != null) {

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

                if (!STAGING_PREFIX_FILE.renameTo(PREFIX_FILE)) {
                    throw RuntimeException("Unable to rename staging folder")
                }
                logger.log(this) { "Bootstrap extracted, setting it up..." }
                runCommand("ls", prooted = false).waitAndPrintOutput(logger)
                runCommand("chmod -R 700 .", prooted = false).waitAndPrintOutput(logger)
                if (shouldUsePre5Bootstrap()) {
                    runCommand("rm -r root && mv root-pre5 root", prooted = false).waitAndPrintOutput(logger)
                }

                runCommand("sh install-bootstrap.sh", prooted = false).waitAndPrintOutput(logger)
                runCommand("sh add-user.sh octoprint", prooted = false).waitAndPrintOutput(logger)
                runCommand("cat /etc/motd").waitAndPrintOutput(logger)

                // Setup ssh
                runCommand("apk add openssh-server bash").waitAndPrintOutput(logger)
                runCommand("echo \"PermitRootLogin yes\" >> /etc/ssh/sshd_config").waitAndPrintOutput(logger)
                runCommand("ssh-keygen -A").waitAndPrintOutput(logger)

                // Turn ssh on for easier debug
                if (BuildConfig.DEBUG) {
//                    runCommand("passwd").setPassword("octoprint")
//                    runCommand("passwd octoprint").setPassword("octoprint")
//                    runCommand("/usr/sbin/sshd -p 2137")
                }

                logger.log(this) { "Bootstrap installation done" }

                return@withContext
            } catch (e: Exception) {
                throw(e)
            } finally {
            }
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

    override fun runCommand(command: String, prooted: Boolean, root: Boolean): Process {
        val FILES = "/data/data/com.octo4a/files"
        val pb = ProcessBuilder()
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = "$FILES/bootstrap"
        pb.environment()["LANG"] = "'en_US.UTF-8'"
        pb.environment()["PWD"] = "$FILES/bootstrap"
        pb.environment()["EXTRA_BIND"] = "-b /data/data/com.octo4a/files/serialpipe:/dev/ttyOcto4a -b /data/data/com.octo4a/files/bootstrap/ioctlHook.so:/home/octoprint/ioctlHook.so"
        pb.environment()["PATH"] = "/sbin:/system/sbin:/product/bin:/apex/com.android.runtime/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
        pb.directory(File("$FILES/bootstrap"))
        var user = "root"
        if (!root) user = "octoprint"
        if (prooted) {
            // run inside proot
            pb.command("sh", "run-bootstrap.sh", user,  "/bin/sh", "-c", command)
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

    override fun resetSSHPassword(newPassword: String) {
        logger.log(this) { "Deleting password just in case" }
        runCommand("passwd -d octoprint").waitAndPrintOutput(logger)
        runCommand("passwd octoprint").setPassword(newPassword)
        runCommand("passwd -d root").waitAndPrintOutput(logger)
        runCommand("passwd root").setPassword(newPassword)
        runCommand("touch .ssh_configured", root = false)
        runCommand("touch .ssh_configured", root = true)
    }

    override val isBootstrapInstalled: Boolean
        get() = File("$FILES_PATH/bootstrap/bootstrap").exists()
}