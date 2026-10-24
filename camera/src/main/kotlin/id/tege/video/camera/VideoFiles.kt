package id.tege.video.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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
                check(audioStart>=0 && abs(videoStart-audioStart)<=500_000L) { "Awal audio/video tidak sinkron. Berkas sementara dipertahankan." }
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
    fun publish(context: Context, file: File, name: String, report: JSONObject): SavedVideo {
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,name)
            put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/CherryVideo")
            put(MediaStore.Video.Media.IS_PENDING,1)
        }
        val uri=checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values))
        try {
            checkNotNull(resolver.openOutputStream(uri,"w")).use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING,0) },null,null)
            File(context.filesDir,"last-recording.json").writeText(report.put("name",name).toString(2))
            file.delete()
            return SavedVideo(uri,name,report)
        } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
    }
}
