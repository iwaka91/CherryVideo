package id.tege.video.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import id.tege.video.core.VideoCadence
import id.tege.video.core.VideoProfile
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class SavedVideo(val uri: Uri, val name: String, val report: JSONObject)

object VideoFiles {
    fun inspect(file: File, target: VideoProfile, expectAudio: Boolean, expectedRotation: Int? = null): JSONObject {
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audio=false
            var videoIndex=-1
            var audioIndex=-1
            var format: MediaFormat?=null
            for(i in 0 until extractor.trackCount) {
                val f=extractor.getTrackFormat(i)
                val mime=f.getString(MediaFormat.KEY_MIME).orEmpty()
                if(mime.startsWith("audio/")) { audio=true;audioIndex=i }
                if(mime.startsWith("video/")) { videoIndex=i; format=f }
            }
            check(videoIndex>=0 && format!=null) { "Berkas tidak memiliki track video." }
            val f=format
            check(f.getInteger(MediaFormat.KEY_WIDTH)==target.width && f.getInteger(MediaFormat.KEY_HEIGHT)==target.height) { "Ukuran video tidak sesuai permintaan." }
            check(!expectAudio || audio) { "Track audio tidak tersimpan." }
            extractor.selectTrack(videoIndex)
            val times=ArrayList<Long>()
            while(extractor.sampleTime>=0) { times.add(extractor.sampleTime); if(!extractor.advance()) break }
            check(times.size>=2) { "Video terlalu pendek; belum ada frame yang valid." }
            val videoStart=times.min();val videoEnd=times.max()
            check(videoStart in 0L..1_000_000L) { "Timestamp awal video tidak valid: ${videoStart/1000} ms. Video belum dipublikasikan." }
            var audioStart=-1L;var audioEnd=-1L
            if(audioIndex>=0) {
                extractor.unselectTrack(videoIndex);extractor.selectTrack(audioIndex);extractor.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while(extractor.sampleTime>=0) {
                    val time=extractor.sampleTime
                    if(audioStart<0) audioStart=time
                    audioEnd=maxOf(audioEnd,time)
                    if(!extractor.advance()) break
                }
                check(audioStart>=0 && abs(videoStart-audioStart)<=500_000L) { "Awal audio/video tidak sinkron: video ${videoStart/1000} ms, audio ${audioStart/1000} ms. Berkas sementara dipertahankan." }
                check(abs(videoEnd-audioEnd)<=1_000_000L) { "Durasi audio/video tidak sinkron: video ${videoEnd/1000} ms, audio ${audioEnd/1000} ms. Berkas sementara dipertahankan." }
            }
            val metadata=MediaMetadataRetriever()
            val durationMs: Long;val rotation: Int
            try {
                metadata.setDataSource(file.absolutePath)
                durationMs=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                rotation=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } finally { metadata.release() }
            check(durationMs>0 && abs(durationMs*1000-maxOf(videoEnd,audioEnd))<=1_000_000L) { "Durasi kontainer tidak sesuai frame aktual." }
            check(expectedRotation==null || rotation==expectedRotation) { "Orientasi rekaman tidak sesuai permintaan." }
            val cadence=VideoCadence.inspect(times,target.fps)
            return JSONObject().put("width",target.width).put("height",target.height).put("targetFps",target.fps)
                .put("actualFps",cadence.fps).put("frames",cadence.frames).put("maxGapMs",cadence.maxGapMs)
                .put("steadyCadence",cadence.steady).put("audio",audio).put("mime",f.getString(MediaFormat.KEY_MIME))
                .put("videoStartUs",videoStart).put("videoEndUs",videoEnd).put("audioStartUs",audioStart).put("audioEndUs",audioEnd)
                .put("containerDurationMs",durationMs).put("rotation",rotation)
                .put("bytes",file.length()).put("scope","Encoded file evidence; not sensor exposure or visual-quality certification")
        } finally { extractor.release() }
    }
    fun publish(context: Context, file: File, name: String, report: JSONObject, relativePath: String = "Movies/CherryVideo", recordAsLast: Boolean = true): SavedVideo {
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,name)
            put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,relativePath)
            put(MediaStore.Video.Media.IS_PENDING,1)
        }
        val uri=checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values))
        try {
            checkNotNull(resolver.openOutputStream(uri,"w")).use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING,0) },null,null)
            if(recordAsLast) File(context.filesDir,"last-recording.json").writeText(report.put("name",name).toString(2))
            file.delete()
            return SavedVideo(uri,name,report)
        } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
    }
    /** JPEG in Pictures/<folder>, for "Foto" while recording. */
    fun publishPhoto(context: Context, bitmap: android.graphics.Bitmap, name: String, folder: String): Uri {
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,name)
            put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/$folder")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        try {
            checkNotNull(resolver.openOutputStream(uri,"w")).use { out -> check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,out)) { "JPEG gagal." } }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            return uri
        } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
    }
    /** Peak level per 100 ms of a saved video (for "Rapikan bagian diam"), kept inside the app. */
    fun saveLevels(context: Context, name: String, levels: IntArray) {
        val dir=File(context.filesDir,"levels").apply { mkdirs() }
        File(dir,"$name.json").writeText(org.json.JSONArray(levels.toList()).toString())
        // Keep the newest 60 logs only.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
    }
    fun loadLevels(context: Context, name: String): IntArray? = runCatching {
        val array=org.json.JSONArray(File(File(context.filesDir,"levels"),"$name.json").readText())
        IntArray(array.length()) { array.getInt(it) }
    }.getOrNull()
    /** Folder of a saved video, so an edited copy lands next to it. */
    fun relativePathOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri,arrayOf(MediaStore.Video.Media.RELATIVE_PATH),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: "Movies/CherryVideo/"
}

/** Lossless cutting (no re-encoding): parts start on keyframes, picture and sound move together. */
object VideoEdit {
    data class Info(val durationUs: Long, val keyframesUs: LongArray)
    fun info(context: Context, uri: Uri): Info {
        val ex=MediaExtractor()
        try {
            ex.setDataSource(context,uri,null)
            val video=(0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
                ?: error("Berkas tidak memiliki video.")
            ex.selectTrack(video)
            val keys=ArrayList<Long>();var last=0L
            while(ex.sampleTime>=0) {
                if(ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0) keys.add(ex.sampleTime)
                last=maxOf(last,ex.sampleTime)
                if(!ex.advance()) break
            }
            return Info(last,keys.toLongArray())
        } finally { ex.release() }
    }
    /** Copies the parts (startUs,endUs) of [uri] one after another into [out]. Returns the new length in microseconds. */
    fun cut(context: Context, uri: Uri, parts: List<LongArray>, out: File): Long {
        require(parts.isNotEmpty()) { "Tidak ada bagian yang dipilih." }
        val ex=MediaExtractor()
        var muxer: MediaMuxer?=null
        try {
            ex.setDataSource(context,uri,null)
            val rotation=MediaMetadataRetriever().let { m ->
                try { m.setDataSource(context,uri);m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0 } finally { m.release() }
            }
            val mux=MediaMuxer(out.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer=it }
            mux.setOrientationHint(rotation)
            val map=HashMap<Int,Int>()
            var size=1024*1024
            for(i in 0 until ex.trackCount) {
                val f=ex.getTrackFormat(i)
                val mime=f.getString(MediaFormat.KEY_MIME).orEmpty()
                if(mime.startsWith("video/") || mime.startsWith("audio/")) {
                    map[i]=mux.addTrack(f);ex.selectTrack(i)
                    if(f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) size=maxOf(size,f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            check(map.isNotEmpty()) { "Tidak ada track yang bisa dipotong." }
            mux.start()
            val buffer=java.nio.ByteBuffer.allocateDirect(maxOf(size,4*1024*1024))
            val info=android.media.MediaCodec.BufferInfo()
            var offset=0L
            for(part in parts) {
                val start=part[0];val end=part[1]
                if(end<=start) continue
                ex.seekTo(start,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while(true) {
                    val time=ex.sampleTime
                    if(time<0 || time>end+2_000_000L) break
                    val track=map[ex.sampleTrackIndex]
                    if(track!=null && time>=start && time<end) {
                        buffer.clear()
                        val n=ex.readSampleData(buffer,0)
                        if(n>0) {
                            val flags=if(ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            info.set(0,n,time-start+offset,flags)
                            mux.writeSampleData(track,buffer,info)
                        }
                    }
                    if(!ex.advance()) break
                }
                offset+=end-start
            }
            mux.stop()
            return offset
        } finally {
            runCatching { muxer?.release() }
            ex.release()
        }
    }
}
