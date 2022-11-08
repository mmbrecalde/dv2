// Copyright 2019 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.mediapipemultihandstracking;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.mediapipemultihandstracking.basic.BasicActivity;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.formats.proto.RectProto;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketCallback;
import com.google.mediapipe.framework.PacketGetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main activity of MediaPipe multi-hand tracking app.
 */
public class MainActivity extends BasicActivity {
    private static final String TAG = "MainActivity";

    private static final String OUTPUT_HAND_RECT = "hand_rects_from_palm_detections";
    private List<NormalizedLandmarkList> multiHandLandmarks;

    private static final String INPUT_NUM_HANDS_SIDE_PACKET_NAME = "num_hands";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";
    // Max number of hands to detect/process.
    private static final int NUM_HANDS = 1;

    private TextView gesture;
    private TextView result;

    private long timestamp;
    String sentence = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gesture = findViewById(R.id.gesture);
        result = findViewById(R.id.resultString);
        timestamp = System.currentTimeMillis();

        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        Map<String, Packet> inputSidePackets = new HashMap<>();
        inputSidePackets.put(INPUT_NUM_HANDS_SIDE_PACKET_NAME, packetCreator.createInt32(NUM_HANDS));
        processor.setInputSidePackets(inputSidePackets);

        // keep screen on and only portrait mode
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    Log.d(TAG, "Received multi-hand landmarks packet.");
                    multiHandLandmarks =
                            PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());

                    runOnUiThread(() -> {
                        String letter = handGestureCalculator(multiHandLandmarks);
                        gesture.setText(letter);
                        if (timestamp + 1000 < System.currentTimeMillis()
                                && !letter.equals("No hand detected")
                                && !letter.equals("no gesture")){
                            addToSentence(letter);
                            timestamp = System.currentTimeMillis();
                        }
                    });
                    Log.d(
                            TAG,
                            "[TS:"
                                    + packet.getTimestamp()
                                    + "] "
                                    + getMultiHandLandmarksDebugString(multiHandLandmarks));
                });

        processor.addPacketCallback(
                OUTPUT_HAND_RECT
                , new PacketCallback() {
                    @Override
                    public void process(Packet packet) {

                        List<RectProto.NormalizedRect> normalizedRectsList = PacketGetter.getProtoVector(packet, RectProto.NormalizedRect.parser());

                        try {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    gesture.setText(handGestureMoveCalculator(normalizedRectsList));
                                    if (timestamp + 1000 < System.currentTimeMillis()
                                            && !handGestureMoveCalculator(normalizedRectsList).equals("No hand detected")
                                            && !handGestureMoveCalculator(normalizedRectsList).equals("no gesture")){
                                        addToSentence(handGestureMoveCalculator(normalizedRectsList));
                                        timestamp = System.currentTimeMillis();
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    private String getMultiHandLandmarksDebugString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks";
        }
        String multiHandLandmarksStr = "Number of hands detected: " + multiHandLandmarks.size() + "\n";
        int handIndex = 0;
        for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
            multiHandLandmarksStr +=
                    "\t#Hand landmarks for hand[" + handIndex + "]: " + landmarks.getLandmarkCount() + "\n";
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                multiHandLandmarksStr +=
                        "\t\tLandmark ["
                                + landmarkIndex
                                + "]: ("
                                + landmark.getX()
                                + ", "
                                + landmark.getY()
                                + ", "
                                + landmark.getZ()
                                + ")\n";
                ++landmarkIndex;
            }
            ++handIndex;
        }
        return multiHandLandmarksStr;
    }

    private String handGestureCalculator(List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "";
        }
        // Different conditions for each of the finger positions
        boolean indexStraightUp = false;
        boolean indexStraightDown = false;
        boolean middleStraightUp = false;
        boolean middleStraightDown = false;
        boolean ringStraightUp = false;
        boolean ringStraightDown = false;
        boolean pinkyStraightUp = false;
        boolean pinkyStraightDown = false;
        boolean thumbIsOpen = false;
        boolean thumbIsBend = false;

        for (LandmarkProto.NormalizedLandmarkList landmarks : multiHandLandmarks) {
            List<LandmarkProto.NormalizedLandmark> landmarkList = landmarks.getLandmarkList();

            //Logging to the console the X-axis points that make the base of the palm and do not move like the ones on the fingers
            Log.d("Palm base", "" + landmarkList.get(0).getX() + " " + landmarkList.get(1).getX() + " " + landmarkList.get(2).getX() + " " + landmarkList.get(17).getX());
            //Logging to the console the Y-axis points that make the base of the palm and do not move like the ones on the fingers
            Log.d("Palm base", "" + landmarkList.get(0).getY() + " " + landmarkList.get(1).getY() + " " + landmarkList.get(2).getY() + " " + landmarkList.get(17).getY());

            /*1st CONDITION
             * To identify when a finger is straight up or straight down.
             * Each of the following conditions allowed me to create the state straightUp on each finger.
             * INDEX_FINGER */
            if (landmarkList.get(8).getY() < landmarkList.get(7).getY()
                    && landmarkList.get(7).getY() < landmarkList.get(6).getY()
                    && landmarkList.get(6).getY() < landmarkList.get(5).getY()){
                indexStraightUp = true;
            }else if (getEuclideanDistanceAB(landmarkList.get(8).getX(),landmarkList.get(8).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY())
                    < getEuclideanDistanceAB(landmarkList.get(5).getX(),landmarkList.get(5).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY())){
                indexStraightDown = true;
            }

            /*MIDDLE_FINGER */
            if (landmarkList.get(12).getY() < landmarkList.get(11).getY()
                    && landmarkList.get(11).getY() < landmarkList.get(10).getY()
                    && landmarkList.get(10).getY() < landmarkList.get(9).getY()){
                middleStraightUp = true;
            }else if (getEuclideanDistanceAB(landmarkList.get(12).getX(),landmarkList.get(12).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY()) <
                    getEuclideanDistanceAB(landmarkList.get(9).getX(),landmarkList.get(9).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY())){
                middleStraightDown = true;
            }

            /*RING_FINGER */
            if (landmarkList.get(16).getY() < landmarkList.get(15).getY()
                    && landmarkList.get(15).getY() < landmarkList.get(14).getY()
                    && landmarkList.get(14).getY() < landmarkList.get(13).getY()){
                ringStraightUp = true;
            } else if (getEuclideanDistanceAB(landmarkList.get(16).getX(),landmarkList.get(16).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY()) <
                    getEuclideanDistanceAB(landmarkList.get(13).getX(),landmarkList.get(13).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY())){
                ringStraightDown = true;
            }
            /*PINKY_FINGER */
            if (landmarkList.get(20).getY() < landmarkList.get(19).getY()
                    && landmarkList.get(19).getY() < landmarkList.get(18).getY()
                    && landmarkList.get(18).getY() < landmarkList.get(17).getY()){
                pinkyStraightUp = true;
            } else if (getEuclideanDistanceAB(landmarkList.get(20).getX(),landmarkList.get(20).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY()) <
                    getEuclideanDistanceAB(landmarkList.get(17).getX(),landmarkList.get(17).getY(), landmarkList.get(0).getX(), landmarkList.get(0).getY())){
                pinkyStraightDown = true;
            }
            /*THUMB */
            if (getEuclideanDistanceAB(landmarkList.get(4).getX(), landmarkList.get(4).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY())
                    < getEuclideanDistanceAB(landmarkList.get(3).getX(), landmarkList.get(3).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY())){
                thumbIsBend = true;
            }else {
                thumbIsOpen = true;
            }

            // Hand gesture recognition conditions for each letter
            if (indexStraightDown && middleStraightDown && ringStraightDown
                    && pinkyStraightDown && thumbIsOpen
                    && arePointsNear(landmarkList.get(4), landmarkList.get(6))
                    && landmarkList.get(4).getX() < landmarkList.get(6).getX())
                return "A"; // final ok
            else if(indexStraightUp && middleStraightUp
                    && ringStraightUp && pinkyStraightUp &&
                    !(landmarkList.get(0).getX() > landmarkList.get(17).getX()) &&
                    getEuclideanDistanceAB(landmarkList.get(4).getX(), landmarkList.get(4).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY())
                            < getEuclideanDistanceAB(landmarkList.get(3).getX(), landmarkList.get(3).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY()))
                return "B"; // final ok
            else if (!indexStraightUp && !middleStraightUp && !ringStraightUp && !pinkyStraightUp && thumbIsOpen && !arePointsNear(landmarkList.get(4), landmarkList.get(8))
                    && !arePointsNear(landmarkList.get(4), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(12), landmarkList.get(16))
                    && !arePointsNear(landmarkList.get(4), landmarkList.get(16))
                    && !arePointsNear(landmarkList.get(4), landmarkList.get(20))
                    && arePointsNear(landmarkList.get(16), landmarkList.get(20))
                    && landmarkList.get(0).getX() > landmarkList.get(17).getX())
                return "C"; // final ok
            else if(arePointsNear(landmarkList.get(4), landmarkList.get(8)) && thumbIsOpen
                    && arePointsNear(landmarkList.get(4), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(12), landmarkList.get(16))
                    && arePointsNear(landmarkList.get(4), landmarkList.get(16))
                    && arePointsNear(landmarkList.get(4), landmarkList.get(20))
                    && arePointsNear(landmarkList.get(16), landmarkList.get(20))
                    && (landmarkList.get(0).getX() > landmarkList.get(17).getX() || landmarkList.get(0).getX() < landmarkList.get(17).getX())
                    && getEuclideanDistanceAB(landmarkList.get(7).getX(), landmarkList.get(7).getY(), landmarkList.get(2).getX(), landmarkList.get(2).getY())
                    > getEuclideanDistanceAB(landmarkList.get(8).getX(), landmarkList.get(8).getY(), landmarkList.get(4).getX(), landmarkList.get(4).getY()))
                return "O"; // final ok
            else if(arePointsNear(landmarkList.get(8), landmarkList.get(5))
                    && arePointsNear(landmarkList.get(12), landmarkList.get(9))
                    && arePointsNear(landmarkList.get(16), landmarkList.get(13))
                    && arePointsNear(landmarkList.get(20), landmarkList.get(17))
                    && landmarkList.get(0).getX() < landmarkList.get(17).getX()
                    && thumbIsBend && !indexStraightDown && !middleStraightDown
                    && !ringStraightDown && !pinkyStraightDown)
                return "E"; // final ok
            else if (middleStraightUp && ringStraightUp && pinkyStraightUp
                    && arePointsNear(landmarkList.get(4), landmarkList.get(8)))
                return "F"; // final ok
            else if (getEuclideanDistanceAB(landmarkList.get(4).getX(), landmarkList.get(4).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY())
                    < getEuclideanDistanceAB(landmarkList.get(3).getX(), landmarkList.get(3).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY()) &&
                    indexStraightDown && middleStraightDown && ringStraightDown
                    && pinkyStraightUp)
                return "I"; // final ok
            else if(thumbIsOpen && landmarkList.get(4).getX() >= landmarkList.get(5).getX() &&
                    landmarkList.get(4).getX() <= landmarkList.get(9).getX() &&
                    indexStraightUp && middleStraightUp && ringStraightDown && pinkyStraightDown &&
                    getEuclideanDistanceAB(landmarkList.get(8).getX(), landmarkList.get(8).getY(), landmarkList.get(12).getX(), landmarkList.get(12).getY())
                            > getEuclideanDistanceAB(landmarkList.get(5).getX(), landmarkList.get(5).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY()) &&
                    arePointsNear(landmarkList.get(3), landmarkList.get(5)) ||
                    indexStraightUp && middleStraightUp
                            && getEuclideanDistanceAB(landmarkList.get(8).getX(), landmarkList.get(8).getY(), landmarkList.get(12).getX(), landmarkList.get(12).getY())
                            > getEuclideanDistanceAB(landmarkList.get(5).getX(), landmarkList.get(5).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY())
                            && arePointsNear(landmarkList.get(2), landmarkList.get(5)) && landmarkList.get(0).getY() > landmarkList.get(17).getY()
                            && landmarkList.get(0).getX() > landmarkList.get(17).getX() && !pinkyStraightUp)
                return "K"; // final ok + has another variation
            else if(!arePointsNear(landmarkList.get(4), landmarkList.get(7)) && !(landmarkList.get(0).getX() > landmarkList.get(17).getX()) && !thumbIsBend && thumbIsOpen && indexStraightUp && middleStraightDown && ringStraightDown && pinkyStraightDown)
                return "L"; // final ok
            else if(arePointsNear(landmarkList.get(4), landmarkList.get(6)) && middleStraightDown && ringStraightDown && pinkyStraightDown
                    && !(arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(12), landmarkList.get(16)))
                    && !(landmarkList.get(0).getX() > landmarkList.get(20).getX()))
                return "T"; // final ok
            else if(landmarkList.get(20).getY() < landmarkList.get(2).getY() && getEuclideanDistanceAB(landmarkList.get(4).getX(), landmarkList.get(4).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY())
                    < getEuclideanDistanceAB(landmarkList.get(3).getX(), landmarkList.get(3).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY()) && indexStraightDown && middleStraightDown
                    && ringStraightDown && pinkyStraightDown
                    && arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(12), landmarkList.get(16))
                    && arePointsNear(landmarkList.get(16), landmarkList.get(20))
                    && !(landmarkList.get(20).getY() > landmarkList.get(2).getY()))
                return "S";
            else if(landmarkList.get(20).getY() > landmarkList.get(16).getY()
                    && !(landmarkList.get(0).getX() > landmarkList.get(17).getX())
                    && arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && arePointsNear(landmarkList.get(12), landmarkList.get(16))
                    && !(landmarkList.get(16).getY() > landmarkList.get(2).getY()) &&
                    landmarkList.get(20).getY() > landmarkList.get(2).getY()
                    && !(getEuclideanDistanceAB(landmarkList.get(4).getX(), landmarkList.get(4).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY())
                    < getEuclideanDistanceAB(landmarkList.get(3).getX(), landmarkList.get(3).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY())))
                return "M"; // final ok
            else if(landmarkList.get(20).getY() > landmarkList.get(2).getY() && !(landmarkList.get(0).getX() > landmarkList.get(20).getX()) && !indexStraightUp && !middleStraightUp && !ringStraightUp && !pinkyStraightUp && !(landmarkList.get(0).getX() > landmarkList.get(17).getX()) && landmarkList.get(16).getY() > landmarkList.get(2).getY()
                    && getEuclideanDistanceAB(landmarkList.get(4).getX(), landmarkList.get(4).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY())
                    < getEuclideanDistanceAB(landmarkList.get(3).getX(), landmarkList.get(3).getY(), landmarkList.get(13).getX(), landmarkList.get(13).getY()))
                return "N"; // final ok
            else if(thumbIsBend && indexStraightUp
                    && middleStraightUp
                    && landmarkList.get(8).getX() >= landmarkList.get(12).getX()
                    && ringStraightDown
                    && arePointsNear(landmarkList.get(7), landmarkList.get(11))
                    && arePointsNear(landmarkList.get(6), landmarkList.get(10)) &&
                    pinkyStraightDown)
                return "R"; // final ok
            else if(thumbIsBend && indexStraightUp && middleStraightUp
                    && ringStraightDown && pinkyStraightDown &&
                    arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && !(getEuclideanDistanceAB(landmarkList.get(8).getX(), landmarkList.get(8).getY(), landmarkList.get(12).getX(), landmarkList.get(12).getY())
                    > getEuclideanDistanceAB(landmarkList.get(5).getX(), landmarkList.get(5).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY())))
                return "U"; // final ok
            else if(indexStraightUp && middleStraightUp
                    && ringStraightDown && pinkyStraightDown &&
                    getEuclideanDistanceAB(landmarkList.get(8).getX(), landmarkList.get(8).getY(), landmarkList.get(12).getX(), landmarkList.get(12).getY())
                            > getEuclideanDistanceAB(landmarkList.get(5).getX(), landmarkList.get(5).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY())
                    && !arePointsNear(landmarkList.get(8), landmarkList.get(12)))
                return "V"; // final ok
            else if(indexStraightUp && middleStraightUp
                    && ringStraightUp && pinkyStraightDown && arePointsNear(landmarkList.get(4), landmarkList.get(20)))
                return "W"; //final ok
            else if(thumbIsOpen && indexStraightDown && middleStraightDown
                    && ringStraightDown && pinkyStraightUp)
                return "Y"; // final ok
            else if(!indexStraightUp && landmarkList.get(20).getX() < landmarkList.get(19).getX()
                    && landmarkList.get(19).getX() < landmarkList.get(18).getX()
                    && landmarkList.get(0).getX() > landmarkList.get(17).getX()
                    && landmarkList.get(0).getX() < landmarkList.get(1).getX())
                return "J"; // needs polishing
            else if(landmarkList.get(4).getY() < landmarkList.get(3).getY()
                    && landmarkList.get(3).getY() < landmarkList.get(2).getY()
                    && landmarkList.get(8).getY() < landmarkList.get(5).getY()
                    && landmarkList.get(12).getY() < landmarkList.get(9).getY()
                    && landmarkList.get(16).getY() < landmarkList.get(13).getY()
                    && landmarkList.get(20).getY() < landmarkList.get(17).getY()
                    && landmarkList.get(17).getY() >= landmarkList.get(2).getY())
                return "SPACE"; // final ok
            else if(landmarkList.get(4).getY() > landmarkList.get(3).getY()
                    && landmarkList.get(3).getY() > landmarkList.get(2).getY()
                    && landmarkList.get(8).getY() > landmarkList.get(7).getY()
                    && landmarkList.get(7).getY() > landmarkList.get(6).getY()
                    && landmarkList.get(0).getY() < landmarkList.get(5).getY())
                return "Q"; // final ok
            else if (landmarkList.get(8).getX() < landmarkList.get(7).getX()
                    && landmarkList.get(7).getX() < landmarkList.get(6).getX()
                    && !(landmarkList.get(12).getX() < landmarkList.get(11).getX())
                    && !(landmarkList.get(11).getX() < landmarkList.get(10).getX())
                    && landmarkList.get(0).getX() > landmarkList.get(17).getX()
                    && !(landmarkList.get(12).getY() > landmarkList.get(11).getY()))
                return "G"; // final ok
            else if (arePointsNear(landmarkList.get(8), landmarkList.get(12))
                    && landmarkList.get(0).getX() > landmarkList.get(17).getX() && arePointsNear(landmarkList.get(2), landmarkList.get(5))
                    && landmarkList.get(2).getY() < landmarkList.get(17).getY())
                return "H"; // final ok
            else if (getEuclideanDistanceAB(landmarkList.get(8).getX(), landmarkList.get(8).getY(), landmarkList.get(12).getX(), landmarkList.get(12).getY())
                    > getEuclideanDistanceAB(landmarkList.get(5).getX(), landmarkList.get(5).getY(), landmarkList.get(9).getX(), landmarkList.get(9).getY())
                    && landmarkList.get(0).getX() > landmarkList.get(17).getX()
                    && landmarkList.get(0).getY() < landmarkList.get(17).getY())
                return "P";
            else if(arePointsNear(landmarkList.get(3), landmarkList.get(10)) && !indexStraightUp && arePointsNear(landmarkList.get(8), landmarkList.get(7)) && landmarkList.get(0).getX() > landmarkList.get(17).getX())
                return "X"; // final ok
            else if (!indexStraightDown && indexStraightUp && middleStraightDown && ringStraightDown && pinkyStraightDown && thumbIsBend
                    || arePointsNear(landmarkList.get(4), landmarkList.get(20)) && indexStraightUp && landmarkList.get(0).getX() > landmarkList.get(17).getX())
                return "D"; // final ok + has another variation

        }
        return "";
    }

    float previousXCenter;
    float previousYCenter;
    float previousRectangleHeight;
    boolean frameCounter;

    private String handGestureMoveCalculator(List<RectProto.NormalizedRect> normalizedRectList) {

        RectProto.NormalizedRect normalizedRect = normalizedRectList.get(0);
        float height = normalizedRect.getHeight();
        float centerX = normalizedRect.getXCenter();
        float centerY = normalizedRect.getYCenter();
        if (previousXCenter != 0) {
            double mouvementDistance = getEuclideanDistanceAB(centerX, centerY,
                    previousXCenter, previousYCenter);
            // LOG(INFO) << "Distance: " << mouvementDistance;

            double mouvementDistanceFactor = 0.02; // only large mouvements will be recognized.

            // the height is normed [0.0, 1.0] to the camera window height.
            // so the mouvement (when the hand is near the camera) should be equivalent to the mouvement when the hand is far.
            double mouvementDistanceThreshold = mouvementDistanceFactor * height;
            if (mouvementDistance > mouvementDistanceThreshold) {
                double angle = radianToDegree(getAngleABC(centerX, centerY,
                        previousXCenter, previousYCenter, previousXCenter + 0.1,
                        previousYCenter));
                // LOG(INFO) << "Angle: " << angle;
                if (angle >= -45 && angle < 45) {
                    return "Z";
                } else if (angle >= 45 && angle < 135) {
                    return "Z";
                } else if (angle >= 135 || angle < -135) {
                    return "Z";
                } else if (angle >= -135 && angle < -45) {
                    return "Z";
                }
            }
        }

        previousXCenter = centerX;
        previousYCenter = centerY;

        previousRectangleHeight = height;
        // each odd Frame is skipped. For a better result.
        frameCounter = !frameCounter;

        return "";
    }


    /**
     * This method takes the letter obtained on the sign, and it gets added into the actual
     * sentence on the result view.
     *
     * @param letter String value for the letter obtained from the gesture recognition
     */
    private void addToSentence(String letter){
        if (letter.equals("SPACE"))
            letter = " ";
        sentence = result.getText().toString();
        sentence += letter;
        result.setText(sentence);
    }

    /**
     * This Boolean method calculated the Euclidean distance between 2 points and returns
     * true when the distance is smaller than 0.1, so the points are near.
     *
     * @param point1 X and Y values for Point 1
     * @param point2 X and Y values for Point 2
     * @return Boolean result
     */
    private boolean arePointsNear(LandmarkProto.NormalizedLandmark point1,
                                  LandmarkProto.NormalizedLandmark point2) {
        double distance = getEuclideanDistanceAB(point1.getX(),
                point1.getY(), point2.getX(), point2.getY());
        return distance < 0.1;
    }

    /**
     * The following method calculates the distance between 2 points (A and B) using euclidean distance
     * formula.
     *
     * @param a_x Value of X for the given position of point A
     * @param a_y Value of Y for the given position of point A
     * @param b_x Value of X for the given position of point B
     * @param b_y Value of Y for the given position of point B
     * @return Euclidean distance result
     */
    private double getEuclideanDistanceAB(double a_x, double a_y,
                                          double b_x, double b_y) {
        double dist = Math.pow(a_x - b_x, 2) + Math.pow(a_y - b_y, 2);
        return Math.sqrt(dist);
    }

    /**
     * This method calculates the angle between 3 given points (A,B,C) using the angle between vectors
     * formula. The vector 1 is made with points AB and vector 2 is made with points BC, being point B
     * the vertex.
     *
     * @param a_x Value of X for the given position of A
     * @param a_y Value of Y for the given position of A
     * @param b_x Value of X for the given position of B
     * @param b_y Value of Y for the given position of B
     * @param c_x Value of X for the given position of C
     * @param c_y Value of Y for the given position of C
     * @return Angle in radians
     */
    private double getAngleABC(double a_x, double a_y, double b_x, double b_y, double c_x, double c_y) {
        //Vector 1 (AB)
        double ab_x = b_x - a_x;
        double ab_y = b_y - a_y;
        //Vector 2 (CB)
        double cb_x = b_x - c_x;
        double cb_y = b_y - c_y;

        double dot = (ab_x * cb_x + ab_y * cb_y);   // dot product
        double cross = (ab_x * cb_y - ab_y * cb_x); // cross product

        return Math.atan2(cross, dot);
    }

    /**
     * Method to convert radian to degree results obtained from the getAngleABC method
     * @param radian Value of radians to convert
     * @return Angle in degrees
     */
    private int radianToDegree(double radian) {
        return (int) Math.floor(radian * 180. / Math.PI + 0.5);
    }
}
