package com.example.remusic.utils

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

object ID3TagUtils {
    fun tagAudioFile(
        file: File,
        title: String,
        artist: String,
        coverBytes: ByteArray?,
        mimeType: String = "image/jpeg"
    ): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM, "Remusic Download")
            
            if (coverBytes != null) {
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = coverBytes
                artwork.mimeType = mimeType
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
