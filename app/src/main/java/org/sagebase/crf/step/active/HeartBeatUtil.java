/*
 *    Copyright 2018 Sage Bionetworks
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

package org.sagebase.crf.step.active;

import android.graphics.Bitmap;

/**
 * Created by liujoshua on 2/19/2018.
 */

public class HeartBeatUtil {

    public static HeartBeatSample getHeartBeatSample(long timestamp, Bitmap bitmap) {
        HeartBeatSample sample = new HeartBeatSample();
        sample.t = timestamp;

        long r = 0, g = 0, b = 0;
        int intArray[] = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < intArray.length; i++) {
            r += (intArray[i] >> 16) & 0xFF; // Color.red
            g += (intArray[i] >> 8) & 0xFF; // Color.green
            b += (intArray[i] & 0xFF); // Color.blue
        }

        long rawSampleR = r / intArray.length;
        // Per the need to match iOS data (which gives RGB data as 0.0 - 1.0, normalize these values
        sample.r = ((float)r / (float)intArray.length) / 255.0f;
        sample.g = ((float)g / (float)intArray.length) / 255.0f;
        sample.b = ((float)b / (float)intArray.length) / 255.0f;

        fillHsv(sample);

        return sample;
    }

    private static void fillHsv(HeartBeatSample sample) {
        float min = Math.min(sample.r, Math.min(sample.g, sample.b));
        float max = Math.max(sample.r, Math.max(sample.g, sample.b));
        float delta = max - min;

        if (((int)Math.round(delta * 1000.0) == 0) || ((int)Math.round(delta * 1000.0) == 0)) {
            sample.h = -1;
            sample.s = 0;
            sample.v = 0;
            return;
        }

        float hue;
        if (sample.r == max) {
            hue = (sample.g - sample.b) / delta;
        } else if (sample.g == max) {
            hue = 2f + (sample.b - sample.r) / delta;
        } else {
            hue = 4f + (sample.r - sample.g) / delta;
        }
        hue *= 60f;
        if (hue < 0) {
            hue += 360f;
        }

        sample.h = hue;
        sample.s = (delta / max);
        sample.v = max;
    }

    private HeartBeatUtil() {
    }
}
