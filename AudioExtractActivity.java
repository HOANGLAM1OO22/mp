package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioExtractActivity extends AppCompatActivity {
    private static final String TAG = "AudioExtractActivity";
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Request READ_EXTERNAL_STORAGE if needed (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE
                );
                return;
            }
        }
        // Example video file path (replace with your actual path or obtain from Intent/URI)
        String videoFilePath = "/sdcard/Movies/sample_video.mp4";
        extractAudioToPCM(videoFilePath);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, 
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String videoFilePath = "/sdcard/Movies/sample_video.mp4";
                extractAudioToPCM(videoFilePath);
            } else {
                Log.e(TAG, "READ_EXTERNAL_STORAGE permission denied.");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void extractAudioToPCM(String videoPath) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);
            int numTracks = extractor.getTrackCount();
            MediaFormat audioFormat = null;
            String audioMime = null;
            int audioTrackIndex = -1;
            // Find the first audio track (MIME type starts with "audio/")
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioFormat = format;
                    audioMime = mime;
                    audioTrackIndex = i;
                    break;
                }
            }
            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found in " + videoPath);
                return;
            }
            extractor.selectTrack(audioTrackIndex);
            int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            Log.d(TAG, "Audio track format: mime=" + audioMime 
                    + ", sampleRate=" + sampleRate 
                    + ", channels=" + channelCount);
            // Create and configure the decoder
            try {
                codec = MediaCodec.createDecoderByType(audioMime);
            } catch (IOException e) {
                Log.e(TAG, "Decoder creation failed for mime: " + audioMime, e);
                return;
            }
            codec.configure(audioFormat, null, null, 0);
            codec.start();

            // Prepare to decode
            BufferInfo info = new BufferInfo();
            List<byte[]> pcmChunks = new ArrayList<>();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            long timeoutUs = 10000;

            Log.d(TAG, "Starting decode loop");
            while (!sawOutputEOS) {
                // Feed input buffer
                if (!sawInputEOS) {
                    int inIndex = codec.dequeueInputBuffer(timeoutUs);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                        // Read sample data into input buffer
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            // End of stream -- send EOS flag to decoder
                            codec.queueInputBuffer(inIndex, 0, 0, 0, 
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                            Log.d(TAG, "Sent input EOS");
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inIndex, 0, sampleSize, 
                                    presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }
                // Retrieve output buffer
                int outIndex = codec.dequeueOutputBuffer(info, timeoutUs);
                if (outIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outIndex);
                    int size = info.size;
                    int offset = info.offset;
                    if (size > 0 && outputBuffer != null) {
                        outputBuffer.position(offset);
                        outputBuffer.limit(offset + size);
                        byte[] chunk = new byte[size];
                        outputBuffer.get(chunk);
                        pcmChunks.add(chunk);
                        outputBuffer.clear();
                    }
                    codec.releaseOutputBuffer(outIndex, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                        Log.d(TAG, "Received output EOS");
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = codec.getOutputFormat();
                    Log.d(TAG, "Output format changed: " + newFormat);
                    // (sample rate and channels should match input format here)
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                }
            }
            Log.d(TAG, "Decoding complete. Total PCM chunks: " + pcmChunks.size());
            // pcmChunks now contains all raw PCM data as byte arrays
        } catch (IOException e) {
            Log.e(TAG, "Error reading video file " + videoPath, e);
        } finally {
            // Release resources
            if (codec != null) {
                codec.stop();
                codec.release();
            }
            if (extractor != null) {
                extractor.release();
            }
        }
    }
}
