package com.octo4a.octoprint
import android.system.Os
import android.util.Log
import android.util.Pair
import java.io.*
import java.net.URL
import java.util.ArrayList
import java.util.zip.ZipInputStream

class BootstrapUtils {
    companion object {
        private val FILES_PATH = "/data/data/com.octo4a/files"
        val PREFIX_PATH = "$FILES_PATH/usr"
        val HOME_PATH = "$FILES_PATH/home"

        fun setupBootstrap(whenDone: () -> Unit) {
            val PREFIX_FILE = File(PREFIX_PATH)
            if (PREFIX_FILE.isDirectory) {
                whenDone()
                return
            }

            try {
                val STAGING_PREFIX_PATH = "$FILES_PATH/usr-staging"
                val STAGING_PREFIX_FILE = File(STAGING_PREFIX_PATH)

                if (STAGING_PREFIX_FILE.exists()) {
                    deleteFolder(STAGING_PREFIX_FILE)
                }

                val buffer = ByteArray(8096)
                val symlinks = ArrayList<Pair<String, String>>(50)
                var urlPrefix = "https://raw.githubusercontent.com/feelfreelinux/octo4a/master/termux-based-approach/bootstrap-"
                urlPrefix += if (System.getProperty("os.arch") == "aarch64") {
                    "aarch64.zip"
                } else {
                    "arm.zip"
                }

                urlPrefix = "https://transfer.sh/13LGOy/bootstrap-aarch64.zip"

                ZipInputStream(URL(urlPrefix).openStream()).use { zipInput ->
                    var zipEntry = zipInput.nextEntry
                    while (zipEntry != null) {
                        if (zipEntry.name == "SYMLINKS.txt") {
                            val symlinksReader = BufferedReader(InputStreamReader(zipInput))
                            var line = symlinksReader.readLine()
                            while (line != null) {
                                val parts = line.split("←".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                if (parts.size != 2)
                                    throw RuntimeException("Malformed symlink line: $line")
                                val oldPath = parts[0]
                                var newPath = STAGING_PREFIX_PATH + "/" + parts[1]
                                newPath = newPath.replace("./", "")
                                symlinks.add(Pair.create(oldPath, newPath))

                                ensureDirectoryExists(File(newPath).parentFile)
                                line = symlinksReader.readLine()
                            }
                        } else {
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
                                if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") || zipEntryName.startsWith("lib/apt/methods")) {
                                    Os.chmod(targetFile.absolutePath, 448)
                                }
                            }
                        }
                        zipEntry = zipInput.nextEntry
                    }
                }

                if (symlinks.isEmpty())
                    throw RuntimeException("No SYMLINKS.txt encountered")
                for (symlink in symlinks) {
                    Os.symlink(symlink.first, symlink.second)
                }

                if (!STAGING_PREFIX_FILE.renameTo(PREFIX_FILE)) {
                    throw RuntimeException("Unable to rename staging folder")
                }
                Log.v("ASD", "donn")

                whenDone()
            } catch (e: Exception) {
                throw(e)

            } finally {
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

        // Runs bash command with correct env
        fun runBashCommand(command: String): Process {
            val FILES = "/data/data/com.octo4a/files"

            val pb = ProcessBuilder()
            pb.redirectErrorStream(true)
            pb.environment()["PREFIX"] = "$FILES/usr"
            pb.environment()["HOME"] = "$FILES/home"
            pb.environment()["LD_LIBRARY_PATH"] = "$FILES/usr/lib"
            pb.environment()["PWD"] = "$FILES/home"
            pb.environment()["PATH"] =
                "/data/data/com.octo4a/files/usr/bin:/data/data/com.octo4a/files/usr/bin/applets:/data/data/com.octo4a/files/usr/bin:/data/data/com.octo4a/files/usr/bin/applets:/sbin:/system/sbin:/product/bin:/apex/com.android.runtime/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
            pb.environment()["LANG"] = "'en_US.UTF-8'"
            pb.redirectErrorStream(true)

            pb.command("/data/data/com.octo4a/files/usr/bin/bash", "-c", "cd $FILES/home && $command")
            return pb.start()
        }

        fun ensureHomeDirectory() {
            val homeFile = File(HOME_PATH)
            if (!homeFile.exists()) {
                homeFile.mkdir()
            }
        }

        val isBootstrapInstalled: Boolean
            get() {
                return File(PREFIX_PATH).isDirectory
            }
    }
}