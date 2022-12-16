package com.mihailxn.emotionalreaction;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraFocus;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.androidhiddencamera.config.CameraRotation;
import com.cardstackjc.CardStackAdapter;
import com.cardstackjc.CardStackCallback;
import com.cardstackjc.ItemModel;
import com.mihailxn.emotionalreaction.databinding.FragmentHomeBinding;

import com.cardstackview.CardStackLayoutManager;
import com.cardstackview.CardStackListener;
import com.cardstackview.CardStackView;
import com.cardstackview.Direction;
import com.cardstackview.StackFrom;
import com.cardstackview.SwipeableMethod;

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


public class HomeFragment extends HiddenCameraFragment {
    private static final int REQ_CODE_CAMERA_PERMISSION = 1253;

    private FragmentHomeBinding binding;
    private ProgressBar mClassificationProgressBar;

    private static final String TAG = "MainActivity";
    private CardStackLayoutManager manager;
    private CardStackAdapter adapter;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Setting camera configuration
        CameraConfig mCameraConfig = new CameraConfig()
                .getBuilder(getActivity())
                .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                .setCameraResolution(CameraResolution.MEDIUM_RESOLUTION)
                .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                .setImageRotation(CameraRotation.ROTATION_270)
                .setCameraFocus(CameraFocus.AUTO)
                .build();

        // Check for the camera permission for the runtime
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            // Start camera preview
            startCamera(mCameraConfig);
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA},
                    REQ_CODE_CAMERA_PERMISSION);
        }

        mClassificationProgressBar = binding.classificationHomeProgressBar;
        return binding.getRoot();

    }

    @SuppressLint("UnsafeOptInUsageError")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(HomeFragment.this)
                        .navigate(R.id.action_HomeFragment_to_CamFragment);
            }
        });


        CardStackView cardStackView = binding.cardStackView;
        manager = new CardStackLayoutManager(getContext(), new CardStackListener() {
            @Override
            public void onCardDragging(Direction direction, float ratio) {
                Log.d(TAG, "onCardDragging: d=" + direction.name() + " ratio=" + ratio);
            }

            @Override
            public void onCardSwiped(Direction direction) {
                Log.d(TAG, "onCardSwiped: p=" + manager.getTopPosition() + " d=" + direction);
                takePicture();
                Toast.makeText(getContext(), R.string.photo_taken, Toast.LENGTH_SHORT).show();

                // Paginating
                if (manager.getTopPosition() == adapter.getItemCount() - 5){
                    paginate();
                }

            }

            @Override
            public void onCardRewound() {
                Log.d(TAG, "onCardRewound: " + manager.getTopPosition());
            }

            @Override
            public void onCardCanceled() {
                Log.d(TAG, "onCardRewound: " + manager.getTopPosition());
            }

            @Override
            public void onCardAppeared(View view, int position) {
                TextView tv = view.findViewById(R.id.item_name);
                Log.d(TAG, "onCardAppeared: " + position + ", nama: " + tv.getText());
            }

            @Override
            public void onCardDisappeared(View view, int position) {
                TextView tv = view.findViewById(R.id.item_name);
                Log.d(TAG, "onCardAppeared: " + position + ", nama: " + tv.getText());
            }
        });
        manager.setStackFrom(StackFrom.None);
        manager.setVisibleCount(3);
        manager.setTranslationInterval(8.0f);
        manager.setScaleInterval(0.95f);
        manager.setSwipeThreshold(0.3f);
        manager.setMaxDegree(20.0f);
        manager.setDirections(Direction.FREEDOM);
        manager.setCanScrollHorizontal(true);
        manager.setSwipeableMethod(SwipeableMethod.Manual);
        manager.setOverlayInterpolator(new LinearInterpolator());
        adapter = new CardStackAdapter(addList());
        cardStackView.setLayoutManager(manager);
        cardStackView.setAdapter(adapter);
        cardStackView.setItemAnimator(new DefaultItemAnimator());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void paginate() {
        List<ItemModel> old = adapter.getItems();
        List<ItemModel> baru = new ArrayList<>(addList());
        CardStackCallback callback = new CardStackCallback(old, baru);
        DiffUtil.DiffResult hasil = DiffUtil.calculateDiff(callback);
        adapter.setItems(baru);
        hasil.dispatchUpdatesTo(adapter);
    }

    private List<ItemModel> addList() {
        List<ItemModel> items = new ArrayList<>();

        items.add(new ItemModel(R.drawable.img1_cat, "Милый котик", "", ""));
        items.add(new ItemModel(R.drawable.img2_mem, "А что бы делал ты?", "", "мем"));
        items.add(new ItemModel(R.drawable.img3_cat, "У каждого свой домик", "", ""));
        items.add(new ItemModel(R.drawable.img4_kurs, "Всего 10 лет назад", "курс доллара был около 30", ""));
        items.add(new ItemModel(R.drawable.img5_dog, "А как ты относишься к собакам?", "", ""));
        items.add(new ItemModel(R.drawable.img6_girl, "Красота", "от природы", ""));
        items.add(new ItemModel(R.drawable.img7_gore, "Горе", "", ""));

        items.add(new ItemModel(R.drawable.sample1, "Красота природы", "Индия", ""));
        items.add(new ItemModel(R.drawable.sample2, "Иногда хочется", "ИДТИ КУДА ГЛАЗА ГЛЯДЯТ", ""));
        items.add(new ItemModel(R.drawable.sample3, "А вот и горзонт", "с облаками...", ""));
        items.add(new ItemModel(R.drawable.sample4, "", "", ""));
        items.add(new ItemModel(R.drawable.sample5, "", "", ""));

        items.add(new ItemModel(R.drawable.img1_cat, "Милый котик", "", ""));
        items.add(new ItemModel(R.drawable.img2_mem, "А что бы делал ты?", "", "мем"));
        items.add(new ItemModel(R.drawable.img3_cat, "У каждого свой домик", "", ""));
        items.add(new ItemModel(R.drawable.img4_kurs, "Всего 10 лет назад", "курс доллара был около 30", ""));
        items.add(new ItemModel(R.drawable.img5_dog, "А как ты относишься к собакам?", "", ""));
        items.add(new ItemModel(R.drawable.img6_girl, "Красота", "от природы", ""));
        items.add(new ItemModel(R.drawable.img7_gore, "Горе", "", ""));

        items.add(new ItemModel(R.drawable.sample1, "Красота природы", "Индия", ""));
        items.add(new ItemModel(R.drawable.sample2, "Иногда хочется", "ИДТИ КУДА ГЛАЗА ГЛЯДЯТ", ""));
        items.add(new ItemModel(R.drawable.sample3, "А вот и горзонт", "с облаками...", ""));
        items.add(new ItemModel(R.drawable.sample4, "", "", ""));
        items.add(new ItemModel(R.drawable.sample5, "", "", ""));
        return items;
    }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        setCalculationStatusUI(true);
        detectFaces(bitmap);
    }

    // Change the interface depending on the status of calculations
    private void setCalculationStatusUI(boolean isCalculationRunning) {
        if (isCalculationRunning) {
            mClassificationProgressBar.setVisibility(ProgressBar.VISIBLE);
        } else {
            mClassificationProgressBar.setVisibility(ProgressBar.INVISIBLE);
        }
    }

    @Override
    public void onCameraError(int errorCode) {
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

                                                // Get subarea with a face
                                                Bitmap faceBitmap = Bitmap.createBitmap(
                                                        imageBitmap,
                                                        faceRect.left,
                                                        faceRect.top,
                                                        faceRect.width(),
                                                        faceRect.height());

                                                mainResult = classifyEmotions(faceBitmap, faceId);

                                                faceId++;
                                            }
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
}