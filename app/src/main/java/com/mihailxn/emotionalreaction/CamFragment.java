package com.mihailxn.emotionalreaction;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.CameraError;
import com.androidhiddencamera.HiddenCameraFragment;
import com.androidhiddencamera.HiddenCameraUtils;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraFocus;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.androidhiddencamera.config.CameraRotation;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.mihailxn.emotionalreaction.databinding.FragmentCamBinding;
import com.mihailxn.emotionalreaction.utils.SortingHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CamFragment extends HiddenCameraFragment {
    private static final int REQ_CODE_CAMERA_PERMISSION = 1253;


    private @NonNull FragmentCamBinding binding;

    private ProgressBar mClassificationProgressBar;
    private ImageView mImageView;
    private ExpandableListView mClassificationExpandableListView;


    private Map<String, List<Pair<String, String>>> mClassificationResult;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        binding = FragmentCamBinding.inflate(inflater, container, false);

        mClassificationResult = new LinkedHashMap<>();

        //Setting camera configuration
        CameraConfig mCameraConfig = new CameraConfig()
                .getBuilder(getActivity())
                .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                .setCameraResolution(CameraResolution.MEDIUM_RESOLUTION)
                .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                .setImageRotation(CameraRotation.ROTATION_270)
                .setCameraFocus(CameraFocus.AUTO)
                .build();

        //Check for the camera permission for the runtime
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            //Start camera preview
            startCamera(mCameraConfig);
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA},
                    REQ_CODE_CAMERA_PERMISSION);
        }

        mClassificationProgressBar = binding.classificationProgressBar;
        mImageView = binding.imageView;
        mClassificationExpandableListView = binding.classificationExpandableListView;

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.takePhotoButton.setOnClickListener(view1 -> {
            //Take picture using the camera without preview.
            takePicture();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //binding = null;
    }


    @Override
    public void onImageCapture(@NonNull File imageFile) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        //Display the image to the image view
        mImageView.setImageBitmap(bitmap);

        clearClassificationExpandableListView();
        mClassificationResult.clear();
        setCalculationStatusUI(true);
        detectFaces(bitmap);
    }

    @Override
    public void onCameraError(@CameraError.CameraErrorCodes int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                // Camera open failed. Probably because another application
                // is using the camera
                Toast.makeText(getContext(), R.string.error_cannot_open, Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_IMAGE_WRITE_FAILED:
                // Image write failed. Please check if you have provided WRITE_EXTERNAL_STORAGE permission
                Toast.makeText(getContext(), R.string.error_cannot_write, Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_CAMERA_PERMISSION_NOT_AVAILABLE:
                // Camera permission is not available
                // Ask for the camera permission before initializing it.
                Toast.makeText(getContext(), R.string.error_cannot_get_permission, Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_OVERDRAW_PERMISSION:
                // Display information dialog to the user with steps to grant "Draw over other app"
                // permission for the app.
                HiddenCameraUtils.openDrawOverPermissionSetting(getContext());
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_FRONT_CAMERA:
                Toast.makeText(getContext(), R.string.error_not_having_camera, Toast.LENGTH_LONG).show();
                break;
        }
    }


    private void clearClassificationExpandableListView() {
        Map<String, List<Pair<String, String>>> emptyMap = new LinkedHashMap<>();
        ClassificationExpandableListAdapter adapter =
                new ClassificationExpandableListAdapter(emptyMap);

        mClassificationExpandableListView.setAdapter(adapter);
    }

    private void detectFaces(Bitmap imageBitmap) {
        FirebaseVisionFaceDetectorOptions faceDetectorOptions =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.NO_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
                        .setMinFaceSize(0.1f)
                        .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance()
                .getVisionFaceDetector(faceDetectorOptions);


        final FirebaseVisionImage firebaseImage = FirebaseVisionImage.fromBitmap(imageBitmap);

        Task<List<FirebaseVisionFace>> result =
                faceDetector.detectInImage(firebaseImage)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<FirebaseVisionFace>>() {
                                    // When the search for faces was successfully completed
                                    @Override
                                    public void onSuccess(List<FirebaseVisionFace> faces) {
                                        Bitmap imageBitmap = firebaseImage.getBitmap();
                                        // Temporary Bitmap for drawing
                                        Bitmap tmpBitmap = Bitmap.createBitmap(
                                                imageBitmap.getWidth(),
                                                imageBitmap.getHeight(),
                                                imageBitmap.getConfig());

                                        // Create an image-based canvas
                                        Canvas tmpCanvas = new Canvas(tmpBitmap);
                                        tmpCanvas.drawBitmap(
                                                imageBitmap,
                                                0,
                                                0,
                                                null);

                                        Paint paint = new Paint();
                                        paint.setStrokeWidth(10);
                                        paint.setTextSize(48);
                                        String[] mainResult;

                                        // Coefficient for indentation of face number
                                        final float textIndentFactor = 0.1f;

                                        // If at least one face was found
                                        if (!faces.isEmpty()) {
                                            // faceId - face text number
                                            int faceId = 1;

                                            for (FirebaseVisionFace face : faces) {
                                                Rect faceRect = getInnerRect(
                                                        face.getBoundingBox(),
                                                        imageBitmap.getWidth(),
                                                        imageBitmap.getHeight());

                                                // Draw a rectangle around a face
                                                paint.setColor(ResourcesCompat
                                                        .getColor(getResources(),
                                                                R.color.colorPrimary, null));
                                                paint.setStyle(Paint.Style.STROKE);
                                                tmpCanvas.drawRect(faceRect, paint);


                                                // Get subarea with a face
                                                Bitmap faceBitmap = Bitmap.createBitmap(
                                                        imageBitmap,
                                                        faceRect.left,
                                                        faceRect.top,
                                                        faceRect.width(),
                                                        faceRect.height());

                                                mainResult = classifyEmotions(faceBitmap, faceId);

                                                paint.setColor(Color.WHITE);

                                                // Draw a face number in a rectangle
                                                paint.setStyle(Paint.Style.FILL);
                                                tmpCanvas.drawText(
                                                        Integer.toString(faceId),
                                                        faceRect.left +
                                                                faceRect.width() * textIndentFactor,
                                                        faceRect.bottom -
                                                                faceRect.height() * textIndentFactor,
                                                        paint);
                                                // Draw a main result in a rectangle
                                                tmpCanvas.drawText(
                                                        mainResult[0],
                                                        faceRect.left +
                                                                faceRect.width() * textIndentFactor,
                                                        faceRect.top +
                                                                faceRect.height() * textIndentFactor,
                                                        paint);
                                                // Draw a percent result in a rectangle
                                                tmpCanvas.drawText(
                                                        mainResult[1],
                                                        faceRect.left +
                                                                faceRect.width() * textIndentFactor,
                                                        faceRect.top +
                                                                faceRect.height() * 2
                                                                        * textIndentFactor,
                                                        paint);

                                                faceId++;
                                            }

                                            // Set the image with the face designations
                                            mImageView.setImageBitmap(tmpBitmap);

                                            ClassificationExpandableListAdapter adapter =
                                                    new ClassificationExpandableListAdapter(mClassificationResult);

                                            mClassificationExpandableListView.setAdapter(adapter);

                                            // If single face, then immediately open the list
                                            if (faces.size() == 1) {
                                                mClassificationExpandableListView.expandGroup(0);
                                            }
                                            // If no faces are found
                                        } else {
                                            Toast.makeText(
                                                    getActivity(),
                                                    getString(R.string.faceless),
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        }

                                        setCalculationStatusUI(false);
                                    }
                                })
                        .addOnFailureListener(
                                e -> {

                                    e.printStackTrace();

                                    setCalculationStatusUI(false);
                                });
    }

    @SuppressLint("DefaultLocale")
    private String[] classifyEmotions(Bitmap imageBitmap, int faceId) {
        Map<String, Float> result = MainActivity.mClassifier.classify(imageBitmap, true);

        // Sort by increasing probability
        LinkedHashMap<String, Float> sortedResult =
                (LinkedHashMap<String, Float>) SortingHelper.sortByValues(result);

        ArrayList<String> reversedKeys = new ArrayList<>(sortedResult.keySet());
        // Change the order to get a decrease in probabilities
        Collections.reverse(reversedKeys);

        ArrayList<Pair<String, String>> faceGroup = new ArrayList<>();
        for (String key : reversedKeys) {
            @SuppressLint("DefaultLocale") String percentage = String.format("%.1f%%", sortedResult.get(key) * 100);
            faceGroup.add(new Pair<>(key, percentage));
        }

        String groupName = getString(R.string.face) + " " + faceId;
        mClassificationResult.put(groupName, faceGroup);

        String mainResult = reversedKeys.get(0);
        return new String[]{mainResult, String.format("%.1f %%", sortedResult.get(mainResult) * 100)};
    }

    // Get a rectangle that lies inside the image area
    private Rect getInnerRect(Rect rect, int areaWidth, int areaHeight) {
        Rect innerRect = new Rect(rect);

        if (innerRect.top < 0) {
            innerRect.top = 0;
        }
        if (innerRect.left < 0) {
            innerRect.left = 0;
        }
        if (rect.bottom > areaHeight) {
            innerRect.bottom = areaHeight;
        }
        if (rect.right > areaWidth) {
            innerRect.right = areaWidth;
        }

        return innerRect;
    }


    // Change the interface depending on the status of calculations
    private void setCalculationStatusUI(boolean isCalculationRunning) {
        if (isCalculationRunning) {
            mClassificationProgressBar.setVisibility(ProgressBar.VISIBLE);
            binding.takePhotoButton.setEnabled(false);
        } else {
            mClassificationProgressBar.setVisibility(ProgressBar.INVISIBLE);
            binding.takePhotoButton.setEnabled(true);
        }
    }

}