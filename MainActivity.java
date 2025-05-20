package com.example.myapplication;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.VideoView;
import android.media.MediaPlayer;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private List<File> videoFiles = new ArrayList<>();
    private int currentVideoIndex = 0;
    private String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        videoView = findViewById(R.id.videoView);

//        //Thư mục chứa video (ví dụ thư mục Movies trong bộ nhớ trong)
//        File videoDir = new File(Environment.getExternalStorageDirectory(), "Movies");
//
//        if (videoDir.exists() && videoDir.isDirectory()) {
//            File[] allFiles = videoDir.listFiles((dir, name) ->
//                    name.endsWith(".mp4") || name.endsWith(".3gp") || name.endsWith(".mkv"));
//
//            if (allFiles != null) {
//                videoFiles = Arrays.asList(allFiles);
//            }
//        }
//
//        if (!videoFiles.isEmpty()) {
//            playVideo(currentVideoIndex);
//        }
        String dstPath = "/storage/emulated/0/Test/output.pcm";

        try {
            List<byte[]> pcmData = getAudioFromVideo();
            Log.i(TAG, "size: "+ pcmData.size());
            File pcmFile = new File(dstPath);
            saveAsPcmFile(pcmData, pcmFile);

        } catch (IOException e) {
            Log.e(TAG, "Error when create pcmData");
            throw new RuntimeException(e);
        }
    }

    public void saveAsPcmFile(List<byte[]> pcmData, File outputFile) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            for (byte[] chunk : pcmData) {
                out.write(chunk);
            }
            out.flush();
        }
    }

    private void playVideo(int index) {
        if (index >= 0 && index < videoFiles.size()) {
            Uri videoUri = Uri.fromFile(videoFiles.get(index));
            videoView.setVideoURI(videoUri);
            videoView.start();

            videoView.setOnCompletionListener(mp -> {
                currentVideoIndex++;
                if (currentVideoIndex < videoFiles.size()) {
                    playVideo(currentVideoIndex);
                }
            });
        }
    }

    private List<byte[]> getAudioFromVideo() throws IOException {
        String videoPath = "/storage/emulated/0/Test/test.mp4";

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(videoPath);

        int audioTrackIndex = -1;
        MediaFormat format = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i;
                break;
            }
        }

        if (audioTrackIndex == -1) {
            throw new RuntimeException("No audio track found in " + videoPath);
        }

        extractor.selectTrack(audioTrackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);


        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();

        List<byte[]> pcmDataList = new ArrayList<>();
        boolean isEOS = false;
        long timeoutUs = 10000;

        while (!isEOS) {
            // Feed input buffer
            int inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                int sampleSize = extractor.readSampleData(inputBuffer, 0);

                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    isEOS = true;
                } else {
                    long presentationTimeUs = extractor.getSampleTime();
                    decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                    extractor.advance();
                }
            }

            // Get decoded PCM output
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                byte[] pcmChunk = new byte[bufferInfo.size];
                outputBuffer.get(pcmChunk);
                outputBuffer.clear();

                pcmDataList.add(pcmChunk);
                decoder.releaseOutputBuffer(outputBufferIndex, false);

                outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
            }
        }


        decoder.stop();
        decoder.release();
        extractor.release();

        return pcmDataList;
    }
}
