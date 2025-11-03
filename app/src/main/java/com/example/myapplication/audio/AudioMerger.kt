package com.example.myapplication.audio

import android.util.Log
import java.io.*
import java.util.ArrayList

class AudioMerger {
    
    companion object {
        private const val TAG = "AudioMerger"
        private const val BUFFER_SIZE = 8192
    }
    
    /**
     * Merge multiple M4A/AAC audio files into a single file
     * Enhanced for chunk-based recording recovery with better error handling
     */
    fun mergeAudioFiles(inputFiles: List<File>, outputFile: File): Boolean {
        if (inputFiles.isEmpty()) {
            Log.e(TAG, "No input files provided for merging")
            return false
        }
        
        Log.d(TAG, "Merging ${inputFiles.size} audio files into ${outputFile.path}")
        
        if (inputFiles.size == 1) {
            // If only one file, just copy it
            return try {
                inputFiles[0].copyTo(outputFile, overwrite = true)
                Log.d(TAG, "Single file copied to output: ${outputFile.path}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error copying single file", e)
                false
            }
        }
        
        // Validate input files first
        if (!validateInputFiles(inputFiles)) {
            return false
        }
        
        return try {
            // Ensure output directory exists
            outputFile.parentFile?.mkdirs()
            
            mergeM4AFiles(inputFiles, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error merging audio files", e)
            false
        }
    }
    
    private fun mergeM4AFiles(inputFiles: List<File>, outputFile: File): Boolean {
        try {
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                
                for ((index, inputFile) in inputFiles.withIndex()) {
                    if (!inputFile.exists()) {
                        Log.w(TAG, "Input file does not exist: ${inputFile.path}")
                        continue
                    }
                    
                    Log.d(TAG, "Processing file ${index + 1}/${inputFiles.size}: ${inputFile.path} (${inputFile.length()} bytes)")
                    
                    FileInputStream(inputFile).use { inputStream ->
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
                
                outputStream.flush()
            }
            
            Log.d(TAG, "Successfully merged ${inputFiles.size} files into ${outputFile.path} (${outputFile.length()} bytes)")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during M4A merge", e)
            return false
        }
    }
    
    /**
     * Validate that all input files exist and are readable
     */
    fun validateInputFiles(inputFiles: List<File>): Boolean {
        if (inputFiles.isEmpty()) {
            Log.e(TAG, "No input files provided")
            return false
        }
        
        for (file in inputFiles) {
            if (!file.exists()) {
                Log.e(TAG, "Input file does not exist: ${file.path}")
                return false
            }
            
            if (!file.canRead()) {
                Log.e(TAG, "Cannot read input file: ${file.path}")
                return false
            }
            
            if (file.length() == 0L) {
                Log.w(TAG, "Input file is empty: ${file.path}")
            }
        }
        
        return true
    }
    
    /**
     * Get total duration estimate for all input files
     * This is an approximation based on file sizes
     */
    fun getEstimatedTotalDuration(inputFiles: List<File>): Long {
        val totalBytes = inputFiles.sumOf { it.length() }
        // Assume ~128 kbps AAC encoding (16 KB/sec)
        val bytesPerSecond = 16 * 1024
        return (totalBytes / bytesPerSecond) * 1000L // Convert to milliseconds
    }
    
    /**
     * Clean up temporary files after merge operation
     */
    fun cleanupTemporaryFiles(files: List<File>) {
        for (file in files) {
            try {
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "Deleted temporary file: ${file.path}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete temporary file: ${file.path}", e)
            }
        }
    }
    
    /**
     * Alternative method using MediaMuxer (more robust but complex)
     * Currently using simple file concatenation for compatibility
     */
    fun mergeWithMediaMuxer(inputFiles: List<File>, outputFile: File): Boolean {
        // TODO: Implement MediaMuxer-based merging for better quality
        // This would require more complex track extraction and muxing
        return mergeAudioFiles(inputFiles, outputFile)
    }
}