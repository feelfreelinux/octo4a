package com.octo4a.repository

import android.content.Context
import android.system.Os
import android.util.Pair
import com.octo4a.BuildConfig
import com.octo4a.utils.getArchString
import com.octo4a.utils.log
import com.octo4a.utils.setPassword
import com.octo4a.utils.waitAndPrintOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.URL
import java.util.ArrayList
import java.util.zip.ZipInputStream

interface BootstrapRepository {
    suspend fun setupBootstrap()
    fun runCommand(command: String, prooted: Boolean = true, root: Boolean = true): Process
    fun ensureHomeDirectory()
    fun resetSSHPassword(newPassword: String)
    val isBootstrapInstalled: Boolean
    val isSSHConfigured: Boolean
}

class BootstrapRepositoryImpl(private val githubRepository: GithubRepository, val context: Context) : BootstrapRepository {
    companion object {
        private val FILES_PATH = "/data/data/com.octo4a/files"
        val PREFIX_PATH = "$FILES_PATH/bootstrap"
        val HOME_PATH = "$FILES_PATH/home"
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

                log { "Downloading bootstrap ${release?.tagName}" }

                val STAGING_PREFIX_PATH = "${FILES_PATH}/bootstrap-staging"
                val STAGING_PREFIX_FILE = File(STAGING_PREFIX_PATH)

                if (STAGING_PREFIX_FILE.exists()) {
                    deleteFolder(STAGING_PREFIX_FILE)
                }

                val buffer = ByteArray(8096)
                val symlinks = ArrayList<Pair<String, String>>(50)

                val urlPrefix = asset?.browserDownloadUrl

                ZipInputStream(URL(urlPrefix).openStream()).use { zipInput ->
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
                log { "Bootstrap extracted, setting it up..." }
                runCommand("chmod -R +x ./root/*", prooted = false).waitAndPrintOutput()
                runCommand("sh install-bootstrap.sh", prooted = false).waitAndPrintOutput()
                runCommand("sh add-user.sh octoprint", prooted = false).waitAndPrintOutput()
                runCommand("cat /etc/motd").waitAndPrintOutput()

                // Setup ssh
                runCommand("apk add openssh-server").waitAndPrintOutput()
                runCommand("echo \"PermitRootLogin yes\" >> /etc/ssh/sshd_config").waitAndPrintOutput()
                runCommand("ssh-keygen -A").waitAndPrintOutput()

                // Turn ssh on for easier debug
                if (BuildConfig.DEBUG) {
                    runCommand("passwd").setPassword("octoprint")
                    runCommand("passwd octoprint").setPassword("octoprint")
                    runCommand("/usr/sbin/sshd -p 2137")
                }

                log { "Bootstrap installation done" }

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
            return File("${HOME_PATH}/.termux_authinfo").exists()
        }

    override fun resetSSHPassword(newPassword: String) {
        if (isSSHConfigured) {
            File("${HOME_PATH}/.termux_authinfo").delete()
        }
        runCommand("passwd").setPassword(newPassword)
    }

    override val isBootstrapInstalled: Boolean
        get() = File("$FILES_PATH/bootstrap/bootstrap").exists()
}