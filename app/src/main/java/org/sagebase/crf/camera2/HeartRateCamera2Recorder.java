/*
 *    Copyright 2017 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebase.crf.camera2;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Size;
import android.view.Surface;

import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.step.active.recorder.Recorder;
import org.sagebase.crf.camera.CameraSourcePreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by liujoshua on 12/18/2017.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class HeartRateCamera2Recorder extends Recorder {
    private static final Logger LOG = LoggerFactory.getLogger(HeartRateCamera2Recorder.class);

    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;

    private AutoFitTextureView mTextureView;
    private CameraDevice mCameraDevice;


    private Size mPreviewSize;
    private Size mVideoSize;

    private MediaRecorder mMediaRecorder;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;


    public HeartRateCamera2Recorder(CameraSourcePreview cameraSourcePreview,
                                    String identifier, Step step, File outputDirectory) {
        super(identifier, step, outputDirectory);
    }


    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void startVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            createMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata
                    .CONTROL_AF_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata
                    .CONTROL_AF_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 16666666L);

            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    showError("Failed.");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void showError(String message) {

    }

    @Override
    public void start(Context context) {


    }


    @Nullable
    private String selectCamera(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            showError("This device doesn't support Camera2 API.");
            return null;
        }
        try {
            for (String cameraId : manager.getCameraIdList()) {


                CameraCharacteristics cameraCharacteristics
                        = manager.getCameraCharacteristics(cameraId);

                if (!cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                    continue;
                }

                if (CameraCharacteristics.LENS_FACING_BACK !=
                        cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)) {
                    continue;
                }

                StreamConfigurationMap map = cameraCharacteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);


                // resolution
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), 192,
                        144);

                // calculate frame rate
                long durationRecorderNs = map.getOutputMinFrameDuration(MediaRecorder.class,
                        mVideoSize);
                long durationPreviewNs = map.getOutputMinFrameDuration(SurfaceTexture.class,
                        mVideoSize);


                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOG.warn("Failed to access camera", e);
        }
        return null;
    }

    private void setupCamera(Context context, String cameraId, int width, int height) throws
            CameraAccessException {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            showError("This device doesn't support Camera2 API.");
            return;
        }

        CameraCharacteristics characteristics = null;
        characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);


        mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), width, height);
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values.
     *
     * @param choices Size options
     * @param width   The minimum desired width
     * @param height  The minimum desired height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {

        List<Size> isBigEnough = new ArrayList<>();

        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                isBigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (isBigEnough.size() > 0) {
            return Collections.min(isBigEnough, new CompareSizesByArea());
        } else {
            LOG.warn("Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight()
                    - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private MediaRecorder createMediaRecorder() {
        // TODO: uncomment
//the lowest available resolution for the first back camera
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mediaRecorder.setOutputFile(new File(getOutputDirectory(), uniqueFilename + ".mp4")
                .getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
//        mediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        return mediaRecorder;
    }

    @Override
    public void stop() {

    }

    @Override
    public void cancel() {

    }
}
