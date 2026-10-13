package com.soltini.app.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * DeviceFileManager - voice-driven file control over the phone's whole shared storage
 * (Downloads, DCIM, Documents, WhatsApp media ... anything under /storage/emulated/0).
 *
 * Needs "All files access". Without it every action returns `needs_permission` and opens the
 * system page where the user can grant it.
 *
 * Safety:
 *  - delete always needs `confirmed=true` (the model must ask the user first)
 *  - delete moves to a trash folder (undo with action=restore) unless permanent=true
 *  - edits keep a backup copy of the original
 *  - storage root, Android/, and the standard top-level folders are protected
 */
class DeviceFileManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceFileManager"
        private const val FILE_PROVIDER = "com.soltini.app.fileprovider"
        private const val MAX_LIST = 100
        private const val MAX_SEARCH = 40
        private val TEXT_EXT = setOf(
            "txt", "md", "json", "csv", "log", "xml", "html", "htm", "js", "ts", "kt", "java", "py",
            "c", "cpp", "h", "css", "yml", "yaml", "ini", "cfg", "conf", "sh", "srt", "vcf", "tsv", "sql"
        )
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "amr")
        private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "rtf")
    }

    private val root: File get() = Environment.getExternalStorageDirectory()

    private val trashDir: File by lazy {
        (context.getExternalFilesDir("myra_trash") ?: File(context.filesDir, "myra_trash")).also { it.mkdirs() }
    }
    private val trashIndex: File get() = File(trashDir, "index.json")

    // ───────────────────────── Permission ─────────────────────────

    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Opens the "All files access" page for this app. */
    fun requestAccess(): JSONObject {
        if (hasAccess()) return ok("status", "granted", "message", "All files access is already granted.")
        return try {
            val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(i) } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ok("status", "needs_permission", "message", "I opened the All files access page. Please switch it on for Myra, then ask me again.")
        } catch (e: Exception) {
            err("Could not open the permission page: ${e.message}")
        }
    }

    // ───────────────────────── Entry point ─────────────────────────

    fun execute(args: JSONObject): JSONObject {
        val action = args.optString("action", "").lowercase(Locale.ROOT).trim()
        if (action.isBlank()) return err("action is required (list, info, search, create_folder, create_file, read, write, append, edit, delete, restore, move, copy, rename, open, latest_photos, request_access)")
        if (action == "request_access") return requestAccess()
        if (!hasAccess()) {
            val r = requestAccess()
            r.put("status", "needs_permission")
            return r
        }
        return try {
            when (action) {
                "list", "ls", "list_folder" -> list(args)
                "info", "details" -> info(args)
                "search", "find" -> search(args)
                "create_folder", "mkdir", "new_folder" -> createFolder(args)
                "create_file", "new_file", "create" -> createFile(args)
                "read", "read_file", "view" -> read(args)
                "write", "overwrite" -> write(args, append = false)
                "append" -> write(args, append = true)
                "edit", "replace" -> edit(args)
                "delete", "remove" -> delete(args)
                "restore", "undo", "undo_delete" -> restore(args)
                "move" -> moveOrCopy(args, copy = false)
                "copy" -> moveOrCopy(args, copy = true)
                "rename" -> rename(args)
                "open" -> open(args)
                "latest_photos", "recent_photos", "latest_images" -> latestPhotos(args)
                else -> err("Unknown action '$action'.")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Security: ${e.message}")
            err("Android blocked access: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Action $action failed: ${e.message}", e)
            err("File action failed: ${e.message}")
        }
    }

    // ───────────────────────── Path resolution ─────────────────────────

    private fun matchChild(dir: File, name: String): String {
        if (!dir.isDirectory) return name
        val names = dir.list() ?: return name
        names.firstOrNull { it == name }?.let { return it }
        names.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return it }
        val norm = name.lowercase(Locale.ROOT).replace(Regex("[\\s_\\-]+"), "")
        names.firstOrNull { it.lowercase(Locale.ROOT).replace(Regex("[\\s_\\-]+"), "") == norm }?.let { return it }
        return name
    }

    fun resolve(input: String): File {
        var p = input.trim().removeSurrounding("\"").removeSurrounding("'").replace("\\", "/")
        if (p.isBlank()) return root
        val lower = p.lowercase(Locale.ROOT)
        if (lower.startsWith("/storage/emulated/0") || lower.startsWith("/sdcard") || lower.startsWith("/storage/self/primary")) {
            val rel = p.substringAfter("/0", "").ifBlank { p.removePrefix("/sdcard").removePrefix("/storage/self/primary") }
            p = rel.trim('/')
        } else if (p.startsWith("/")) {
            return File(p)
        }
        for (prefix in listOf("~/", "sdcard/", "internal storage/", "internal/", "phone/", "./")) {
            if (p.lowercase(Locale.ROOT).startsWith(prefix)) { p = p.substring(prefix.length); break }
        }
        p = p.trim('/')
        if (p.isEmpty()) return root

        val segs = p.split('/').filter { it.isNotBlank() }.toMutableList()
        var cur = root
        val first = segs.first().lowercase(Locale.ROOT)
        val alias: String? = when (first) {
            "download", "downloads" -> "Download"
            "document", "documents", "docs" -> "Documents"
            "dcim" -> "DCIM"
            "camera" -> if (File(root, "DCIM/Camera").exists()) "DCIM/Camera" else "DCIM"
            "picture", "pictures", "photos", "images" -> "Pictures"
            "screenshots", "screenshot" -> when {
                File(root, "Pictures/Screenshots").exists() -> "Pictures/Screenshots"
                File(root, "DCIM/Screenshots").exists() -> "DCIM/Screenshots"
                else -> "Pictures/Screenshots"
            }
            "music", "songs" -> "Music"
            "movie", "movies", "videos" -> "Movies"
            "ringtones" -> "Ringtones"
            "whatsapp" -> if (File(root, "Android/media/com.whatsapp/WhatsApp").exists()) "Android/media/com.whatsapp/WhatsApp" else "WhatsApp"
            "root", "internal storage", "storage", "sdcard", "phone" -> ""
            else -> null
        }
        if (alias != null) {
            segs.removeAt(0)
            if (alias.isNotEmpty()) {
                for (part in alias.split('/')) cur = File(cur, matchChild(cur, part))
            }
        }
        for (s in segs) {
            if (s == "." ) continue
            if (s == "..") continue // never allow escaping upwards
            cur = File(cur, matchChild(cur, s))
        }
        return cur
    }

    private fun canonical(f: File): String = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }

    private fun isProtected(f: File): Boolean {
        val c = canonical(f)
        val r = canonical(root)
        if (c == r) return true
        val protectedTop = listOf(
            "Android", "Download", "Documents", "DCIM", "Pictures", "Music", "Movies",
            "Ringtones", "Alarms", "Notifications", "Podcasts"
        )
        if (protectedTop.any { c == "$r/$it" }) return true
        if (c.startsWith("$r/Android/data") || c.startsWith("$r/Android/obb")) return true
        if (c.startsWith(canonical(trashDir))) return false
        return false
    }

    private fun pathArg(args: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = args.optString(k, "")
            if (v.isNotBlank()) return v
        }
        return ""
    }

    // ───────────────────────── Actions ─────────────────────────

    private fun describe(f: File): JSONObject = JSONObject().apply {
        put("name", f.name)
        put("path", f.absolutePath)
        put("type", if (f.isDirectory) "folder" else "file")
        if (f.isFile) put("size_kb", f.length() / 1024)
        else put("items", f.list()?.size ?: 0)
        put("modified", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(f.lastModified())))
    }

    private fun list(args: JSONObject): JSONObject {
        val dir = resolve(pathArg(args, "path", "folder", "name"))
        if (!dir.exists()) return err("Folder not found: ${dir.absolutePath}")
        if (!dir.isDirectory) return err("${dir.name} is a file, not a folder.")
        val kids = (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
        val arr = JSONArray()
        kids.take(MAX_LIST).forEach { arr.put(describe(it)) }
        return ok("status", "success", "path", dir.absolutePath, "total", kids.size, "items", arr)
    }

    private fun info(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name"))
        if (!f.exists()) return err("Not found: ${f.absolutePath}")
        return ok("status", "success", "info", describe(f))
    }

    private fun matchesType(f: File, type: String): Boolean {
        val ext = f.extension.lowercase(Locale.ROOT)
        return when (type) {
            "image", "photo", "picture" -> ext in IMAGE_EXT
            "video" -> ext in VIDEO_EXT
            "audio", "music", "song" -> ext in AUDIO_EXT
            "document", "doc" -> ext in DOC_EXT
            "" -> true
            else -> ext == type.trim('.')
        }
    }

    private fun search(args: JSONObject): JSONObject {
        val base = resolve(pathArg(args, "path", "folder"))
        val query = pathArg(args, "query", "name").lowercase(Locale.ROOT)
        val type = args.optString("type", "").lowercase(Locale.ROOT)
        if (query.isBlank() && type.isBlank()) return err("query or type is required")
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<File>()
        val rootCanon = canonical(root)
        fun walk(dir: File, depth: Int) {
            if (out.size >= MAX_SEARCH * 3 || depth > 10) return
            val kids = dir.listFiles() ?: return
            for (k in kids) {
                val c = canonical(k)
                if (c.startsWith("$rootCanon/Android/data") || c.startsWith("$rootCanon/Android/obb")) continue
                val nm = k.name.lowercase(Locale.ROOT)
                if (tokens.all { nm.contains(it) } && (k.isDirectory || matchesType(k, type)) && (type.isBlank() || k.isFile)) out.add(k)
                if (k.isDirectory && !k.name.startsWith(".")) walk(k, depth + 1)
            }
        }
        walk(base, 0)
        val top = out.sortedByDescending { it.lastModified() }.take(MAX_SEARCH)
        val arr = JSONArray(); top.forEach { arr.put(describe(it)) }
        return ok("status", "success", "total", top.size, "results", arr)
    }

    private fun createFolder(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "folder"))
        if (f.exists()) return ok("status", "already_exists", "path", f.absolutePath)
        if (isProtected(f)) return err("That location is protected.")
        return if (f.mkdirs()) ok("status", "created", "path", f.absolutePath) else err("Could not create folder ${f.absolutePath}")
    }

    private fun createFile(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "file"))
        if (isProtected(f)) return err("That location is protected.")
        if (f.exists() && !args.optBoolean("overwrite", false)) {
            return err("${f.name} already exists. Say overwrite to replace it, or use append/edit.")
        }
        f.parentFile?.mkdirs()
        f.writeText(args.optString("content", ""))
        return ok("status", "created", "path", f.absolutePath, "size_kb", f.length() / 1024)
    }

    private fun looksTextual(f: File): Boolean {
        if (f.extension.lowercase(Locale.ROOT) in TEXT_EXT) return true
        return try {
            f.inputStream().use { s ->
                val buf = ByteArray(1024); val n = s.read(buf)
                n <= 0 || buf.take(n).none { it.toInt() == 0 }
            }
        } catch (_: Exception) { false }
    }

    private fun read(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "file"))
        if (!f.exists()) return err("File not found: ${f.absolutePath}")
        if (f.isDirectory) return list(args)
        val ext = f.extension.lowercase(Locale.ROOT)
        if (ext in IMAGE_EXT) return err("${f.name} is a photo. Use read_photo to see what is in it.")
        if (!looksTextual(f)) return err("${f.name} is not a plain text file (.$ext), so I cannot read it directly.")
        val max = args.optInt("max_chars", 8000).coerceIn(500, 40000)
        val text = f.readText()
        val clipped = text.length > max
        return ok("status", "success", "path", f.absolutePath, "content", text.take(max), "truncated", clipped, "total_chars", text.length)
    }

    private fun write(args: JSONObject, append: Boolean): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "file"))
        if (isProtected(f) || f.isDirectory) return err("Cannot write to ${f.name}.")
        val content = args.optString("content", "")
        f.parentFile?.mkdirs()
        if (f.exists() && !append) backup(f)
        if (append) f.appendText(content) else f.writeText(content)
        return ok("status", if (append) "appended" else "written", "path", f.absolutePath, "size_kb", f.length() / 1024)
    }

    private fun edit(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "file"))
        if (!f.isFile) return err("File not found: ${f.absolutePath}")
        if (isProtected(f)) return err("That file is protected.")
        if (!looksTextual(f)) return err("${f.name} is not a text file; I can only edit text files.")
        val find = args.optString("find", "")
        if (find.isEmpty()) return err("find (the text to replace) is required.")
        val replace = args.optString("replace", "")
        val original = f.readText()
        if (!original.contains(find)) return err("The text '$find' was not found in ${f.name}.")
        val count = original.split(find).size - 1
        val backupPath = backup(f)
        f.writeText(original.replace(find, replace))
        return ok("status", "edited", "path", f.absolutePath, "replacements", count, "backup", backupPath ?: "")
    }

    private fun backup(f: File): String? = try {
        val dir = File(trashDir, "edit_backups").also { it.mkdirs() }
        val b = File(dir, "${f.name}.${System.currentTimeMillis()}.bak")
        f.copyTo(b, overwrite = true); b.absolutePath
    } catch (_: Exception) { null }

    // ── delete / restore (trash) ──

    private fun countItems(f: File): Int {
        if (f.isFile) return 1
        var n = 0
        f.walkTopDown().maxDepth(12).forEach { if (it.isFile) n++ }
        return n
    }

    private fun readIndex(): JSONArray = try {
        if (trashIndex.exists()) JSONArray(trashIndex.readText()) else JSONArray()
    } catch (_: Exception) { JSONArray() }

    private fun moveToTrash(f: File): File? {
        val id = System.currentTimeMillis().toString()
        val dest = File(trashDir, "$id-${f.name}")
        val moved = f.renameTo(dest) || run {
            try {
                if (f.isDirectory) f.copyRecursively(dest, overwrite = true) else f.copyTo(dest, overwrite = true)
                f.deleteRecursively()
            } catch (_: Exception) { false }
        }
        if (!moved || !dest.exists()) return null
        val idx = readIndex()
        idx.put(JSONObject().apply {
            put("id", id); put("name", f.name); put("original", f.absolutePath)
            put("trashed", dest.absolutePath); put("time", System.currentTimeMillis())
        })
        trashIndex.writeText(idx.toString())
        return dest
    }

    private fun delete(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "file", "folder"))
        if (!f.exists()) return err("Not found: ${f.absolutePath}")
        if (isProtected(f)) return err("${f.name} is a protected location and cannot be deleted.")
        val permanent = args.optBoolean("permanent", false)
        if (!args.optBoolean("confirmed", false)) {
            val n = countItems(f)
            return ok(
                "status", "confirmation_required",
                "path", f.absolutePath, "type", if (f.isDirectory) "folder" else "file", "files_inside", n,
                "message", "Ask the user: 'Delete ${f.name}" + (if (f.isDirectory) " with $n file(s) inside" else "") +
                    "?' Only after a clear yes call device_files again with action=delete, the same path and confirmed=true."
            )
        }
        if (permanent) {
            val okDel = if (f.isDirectory) f.deleteRecursively() else f.delete()
            return if (okDel) ok("status", "deleted_permanently", "path", f.absolutePath) else err("Could not delete ${f.name}.")
        }
        val trashed = moveToTrash(f) ?: return err("Could not delete ${f.name}.")
        return ok("status", "deleted", "path", f.absolutePath, "message", "Moved to Myra's trash. Say 'restore ${f.name}' to undo.", "trash", trashed.name)
    }

    private fun restore(args: JSONObject): JSONObject {
        val idx = readIndex()
        if (idx.length() == 0) return err("Trash is empty - nothing to restore.")
        val want = pathArg(args, "name", "path").lowercase(Locale.ROOT)
        var pick = -1
        for (i in idx.length() - 1 downTo 0) {
            val o = idx.getJSONObject(i)
            if (want.isBlank() || o.optString("name").lowercase(Locale.ROOT).contains(want.substringAfterLast('/'))) { pick = i; break }
        }
        if (pick < 0) return err("Nothing in trash matches '$want'.")
        val entry = idx.getJSONObject(pick)
        val src = File(entry.getString("trashed"))
        val target = File(entry.getString("original"))
        if (!src.exists()) return err("The trashed copy is gone.")
        if (target.exists()) return err("${target.name} already exists at the original location.")
        target.parentFile?.mkdirs()
        val moved = src.renameTo(target) || try {
            if (src.isDirectory) src.copyRecursively(target, true) else src.copyTo(target, true)
            src.deleteRecursively(); true
        } catch (_: Exception) { false }
        if (!moved) return err("Could not restore ${target.name}.")
        val newIdx = JSONArray()
        for (i in 0 until idx.length()) if (i != pick) newIdx.put(idx.get(i))
        trashIndex.writeText(newIdx.toString())
        return ok("status", "restored", "path", target.absolutePath)
    }

    // ── move / copy / rename ──

    private fun moveOrCopy(args: JSONObject, copy: Boolean): JSONObject {
        val src = resolve(pathArg(args, "path", "source", "name"))
        val destArg = pathArg(args, "destination", "dest", "to")
        if (destArg.isBlank()) return err("destination is required")
        if (!src.exists()) return err("Not found: ${src.absolutePath}")
        if (!copy && isProtected(src)) return err("${src.name} is protected and cannot be moved.")
        var dest = resolve(destArg)
        if (dest.isDirectory) dest = File(dest, src.name)
        if (isProtected(dest)) return err("Destination is protected.")
        if (dest.exists() && !args.optBoolean("overwrite", false)) return err("${dest.name} already exists at the destination.")
        dest.parentFile?.mkdirs()
        if (copy) {
            if (src.isDirectory) src.copyRecursively(dest, overwrite = true) else src.copyTo(dest, overwrite = true)
        } else {
            if (!src.renameTo(dest)) {
                if (src.isDirectory) src.copyRecursively(dest, overwrite = true) else src.copyTo(dest, overwrite = true)
                src.deleteRecursively()
            }
        }
        return ok("status", if (copy) "copied" else "moved", "from", src.absolutePath, "to", dest.absolutePath)
    }

    private fun rename(args: JSONObject): JSONObject {
        val src = resolve(pathArg(args, "path", "name"))
        val newName = args.optString("new_name", "").trim()
        if (newName.isBlank() || newName.contains('/')) return err("new_name is required (a name, not a path).")
        if (!src.exists()) return err("Not found: ${src.absolutePath}")
        if (isProtected(src)) return err("${src.name} is protected.")
        val dest = File(src.parentFile, newName)
        if (dest.exists()) return err("$newName already exists.")
        return if (src.renameTo(dest)) ok("status", "renamed", "from", src.name, "to", newName, "path", dest.absolutePath)
        else err("Rename failed.")
    }

    // ── open ──

    private fun open(args: JSONObject): JSONObject {
        val f = resolve(pathArg(args, "path", "name", "file", "folder"))
        if (!f.exists()) return err("Not found: ${f.absolutePath}")
        return try {
            if (f.isDirectory) {
                val rel = f.absolutePath.removePrefix(root.absolutePath).trim('/')
                val docUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$rel")
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(i) } catch (_: Exception) {
                    context.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                ok("status", "opened", "path", f.absolutePath)
            } else {
                val uri = FileProvider.getUriForFile(context, FILE_PROVIDER, f)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "*/*"
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(i)
                ok("status", "opened", "path", f.absolutePath)
            }
        } catch (e: Exception) {
            err("No app could open ${f.name}: ${e.message}")
        }
    }

    // ── photos ──

    private fun latestPhotos(args: JSONObject): JSONObject {
        val count = args.optInt("count", 1).coerceIn(1, 10)
        val screenshotsOnly = args.optBoolean("screenshots_only", false)
        val out = JSONArray()
        val proj = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
        val sel = if (screenshotsOnly) "${MediaStore.Images.Media.DATA} LIKE ?" else null
        val selArgs = if (screenshotsOnly) arrayOf("%Screenshot%") else null
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, selArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val dIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (c.moveToNext() && out.length() < count) {
                val path = c.getString(dIdx) ?: continue
                if (!File(path).exists()) continue
                out.put(JSONObject().apply { put("name", File(path).name); put("path", path) })
            }
        }
        if (out.length() == 0) return err("No photos found.")
        return ok("status", "success", "photos", out)
    }

    /** Finds an image file from a path/name, or "latest"; null if none. */
    fun findImage(spec: String): File? {
        val s = spec.trim()
        if (s.isEmpty() || s.lowercase(Locale.ROOT) in setOf("latest", "last", "recent", "latest photo", "last photo", "newest")) {
            val r = latestPhotos(JSONObject().put("count", 1))
            return r.optJSONArray("photos")?.optJSONObject(0)?.optString("path")?.let { File(it) }
        }
        if (s.lowercase(Locale.ROOT).contains("screenshot") && !s.contains('/')) {
            val r = latestPhotos(JSONObject().put("count", 1).put("screenshots_only", true))
            r.optJSONArray("photos")?.optJSONObject(0)?.optString("path")?.let { return File(it) }
        }
        val direct = resolve(s)
        if (direct.isFile && direct.extension.lowercase(Locale.ROOT) in IMAGE_EXT) return direct
        val found = search(JSONObject().put("query", s).put("type", "image"))
        return found.optJSONArray("results")?.optJSONObject(0)?.optString("path")?.let { File(it) }
    }

    /** Down-scales a photo (max 1600px) into a cache file and returns a content:// Uri the vision parser can read. */
    fun prepareImageForVision(file: File): Uri? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val dir = File(context.cacheDir, "sketches").also { it.mkdirs() }
            val out = File(dir, "vision_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            FileProvider.getUriForFile(context, FILE_PROVIDER, out)
        } catch (e: Exception) {
            Log.w(TAG, "prepareImageForVision failed: ${e.message}")
            null
        }
    }

    // ───────────────────────── JSON helpers ─────────────────────────

    private fun ok(vararg pairs: Any): JSONObject = JSONObject().apply {
        var i = 0
        while (i < pairs.size - 1) { put(pairs[i].toString(), pairs[i + 1]); i += 2 }
    }

    private fun err(msg: String): JSONObject = JSONObject().apply { put("status", "error"); put("error", msg) }
}
