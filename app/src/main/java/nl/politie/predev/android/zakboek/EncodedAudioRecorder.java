/*
 * Copyright 2015-2016, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.politie.predev.android.zakboek;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Based on https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/EncoderTest.java
 * Requires Android v4.1 / API 16 / JELLY_BEAN
 * TODO: support other formats than FLAC
 */
public class EncodedAudioRecorder extends AbstractAudioRecorder {

    // Stop encoding if output buffer has not been available that many times.
    private static final int MAX_NUM_RETRIES_DEQUEUE_OUTPUT_BUFFER = 500;

    // Time period to dequeue a buffer
    private static final long DEQUEUE_TIMEOUT = 10000;

    // TODO: Use queue of byte[]
    private final byte[] mRecordingEnc;
    private int mRecordedEncLength = 0;
    private int mConsumedEncLength = 0;

    private int mNumBytesSubmitted = 0;
    private int mNumBytesDequeued = 0;

    private String mEncoderType;

    public EncodedAudioRecorder(int audioSource, int sampleRate, String encoderType) {
        super(audioSource, sampleRate);
        try {
            int bufferSize = getBufferSize();
            createRecorder(audioSource, sampleRate, bufferSize);
            int framePeriod = bufferSize / (2 * RESOLUTION_IN_BYTES * CHANNELS);
            createBuffer(framePeriod);
            setState(State.READY);
        } catch (Exception e) {
            if (e.getMessage() == null) {
                handleError("Unknown error occurred while initializing recording");
            } else {
                handleError(e.getMessage());
            }
        }
        // TODO: replace 35 with the max length of the recording
        mRecordingEnc = new byte[RESOLUTION_IN_BYTES * CHANNELS * sampleRate * 35]; // 35 sec raw
        mEncoderType = encoderType;
//        Log.i("encodertype: " + mEncoderType);
    }

    public EncodedAudioRecorder(int sampleRate, String encoderType) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate, encoderType);
    }

    public EncodedAudioRecorder() {
        this(DEFAULT_AUDIO_SOURCE, DEFAULT_SAMPLE_RATE, "");
    }

    /**
     * TODO: the MIME should be configurable as the server might not support all formats
     * (returning "Your GStreamer installation is missing a plug-in.")
     * TODO: according to the server docs, for encoded data we do not need to specify the content type
     * such as "audio/x-flac", but it did not work without (nor with "audio/flac").
     */
    public String getWsArgs() {
        if (mEncoderType.equals("audio/x-flac")) {
            // flac encoding is currently not done quite correctly
            // therefore we need to explicitly define the stream type.
            return "?content-type=audio/x-flac";
        } else {
            return "?content-type=";
        }
    }

    public synchronized byte[] consumeRecordingEncAndTruncate() {
        int len = getConsumedEncLength();
        byte[] bytes = getCurrentRecordingEnc(len);
        setRecordedEncLength(0);
        setConsumedEncLength(0);
        return bytes;
    }

    /**
     * @return bytes that have been recorded and encoded since this method was last called
     */
    public synchronized byte[] consumeRecordingEnc() {
        byte[] bytes = getCurrentRecordingEnc(getConsumedEncLength());
        setConsumedEncLength(getRecordedEncLength());
        return bytes;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void recorderLoop(SpeechRecord speechRecord) {

        mNumBytesSubmitted = 0;
        mNumBytesDequeued = 0;


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaFormat format;
            if (mEncoderType.equals("audio/x-flac")) {
                format = MediaFormatFactory.createMediaFormat(MediaFormatFactory.Type.FLAC, getSampleRate());
            } else if (mEncoderType.equals("audio/mp4a-latm")) {
                format = MediaFormatFactory.createMediaFormat(MediaFormatFactory.Type.AAC, getSampleRate());
            } else {
                // AAC is default for encodedAudioRecorder
                format = MediaFormatFactory.createMediaFormat(MediaFormatFactory.Type.AAC, getSampleRate());
            }

            List<String> componentNames = AudioUtils.getEncoderNamesForType(format.getString(MediaFormat.KEY_MIME));
            for (String componentName : componentNames) {

                MediaCodec codec = AudioUtils.createCodec(componentName, format);
                if (codec != null) {
                    recorderEncoderLoop(codec, speechRecord);
                    break; // TODO: we use the first one that is suitable
                }
            }
        }
    }


    private int getConsumedEncLength() {
        return mConsumedEncLength;
    }

    private void setConsumedEncLength(int len) {
        mConsumedEncLength = len;
    }

    private void setRecordedEncLength(int len) {
        mRecordedEncLength = len;
    }

    private int getRecordedEncLength() {
        return mRecordedEncLength;
    }

    private void addEncoded(byte[] buffer) {
        int len = buffer.length;
        if (mRecordingEnc.length >= mRecordedEncLength + len) {
            System.arraycopy(buffer, 0, mRecordingEnc, mRecordedEncLength, len);
            mRecordedEncLength += len;
        } else {
            handleError("RecorderEnc buffer overflow: " + mRecordedEncLength);
        }
    }

    private byte[] getCurrentRecordingEnc(int startPos) {
        int len = getRecordedEncLength() - startPos;
        byte[] bytes = new byte[len];
        System.arraycopy(mRecordingEnc, startPos, bytes, 0, len);
        return bytes;
    }

    /**
     * Copy audio from the recorder into the encoder.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private int queueInputBuffer(MediaCodec codec, ByteBuffer[] inputBuffers, int index, SpeechRecord speechRecord) {
        if (speechRecord == null || speechRecord.getRecordingState() != SpeechRecord.RECORDSTATE_RECORDING) {
            return -1;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();
            int size = inputBuffer.limit();
            byte[] buffer = new byte[size];
            int status = read(speechRecord, buffer);
            if (status < 0) {
                handleError("status = " + status);
                return -1;
            }
            inputBuffer.put(buffer);
            codec.queueInputBuffer(index, 0, size, 0, 0);
            return size;
        }
        return -1;
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        // TODO: take this parameters from the codec info
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD; Not supported in Gstreamer
        int freqIdx = 8;  //16KHz
        int chanCfg = 1;  //Mono

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }

    /**
     * Save the encoded (output) buffer into the complete encoded recording.
     * TODO: copy directly (without the intermediate byte array)
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void dequeueOutputBuffer(MediaCodec codec, ByteBuffer[] outputBuffers, int index, MediaCodec.BufferInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ByteBuffer buffer = outputBuffers[index];
            if (info.size <= buffer.remaining()) {
                int outBitsSize   = info.size;
                int outPacketSize = outBitsSize + 7;

                final byte[] bufferCopied;
                if (mEncoderType.equals("audio/x-flac")) {
                    bufferCopied = new byte[info.size];
                    buffer.get(bufferCopied); // TODO: catch BufferUnderflow;
                } else {
                    // default for now
                    // if (mEncoderType == "audio/mp4a-latm") {
                    bufferCopied = new byte[outPacketSize];
                    addADTStoPacket(bufferCopied, outPacketSize);
                    buffer.get(bufferCopied, 7, outBitsSize);
                }

                // TODO: do we need to clear?
                // on N5: always size == remaining(), clearing is not needed
                // on SGS2: remaining decreases until it becomes less than size, which results in BufferUnderflow
                // (but SGS2 records only zeros anyway)
                //buffer.clear();
                codec.releaseOutputBuffer(index, false);
                addEncoded(bufferCopied);
            } else {
                codec.releaseOutputBuffer(index, false);
            }
        }
    }

    /**
     * Reads bytes from the given recorder and encodes them with the given encoder.
     * Uses the (deprecated) Synchronous Processing using Buffer Arrays.
     * <p/>
     * Encoders (or codecs that generate compressed data) will create and return the codec specific
     * data before any valid output buffer in output buffers marked with the codec-config flag.
     * Buffers containing codec-specific-data have no meaningful timestamps.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void recorderEncoderLoop(MediaCodec codec, SpeechRecord speechRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            codec.start();
            // Getting some buffers (e.g. 4 of each) to communicate with the codec
            ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
            ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();
            boolean doneSubmittingInput = false;
            int numRetriesDequeueOutputBuffer = 0;
            int index;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                if (!doneSubmittingInput) {
                    index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT);
                    if (index >= 0) {
                        int size = queueInputBuffer(codec, codecInputBuffers, index, speechRecord);
                        if (size == -1) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            doneSubmittingInput = true;
                        } else {
                            mNumBytesSubmitted += size;
                        }
                    } else {
                    }
                }
                index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    codecOutputBuffers = codec.getOutputBuffers();
                } else {
                    dequeueOutputBuffer(codec, codecOutputBuffers, index, info);
                    mNumBytesDequeued += info.size;
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
            codec.stop();
            codec.release();
        }
    }
}