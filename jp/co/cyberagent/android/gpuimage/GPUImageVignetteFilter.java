/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage;

import android.graphics.PointF;
import android.opengl.GLES20;
import android.support.annotation.NonNull;

/**
 * Performs a vignetting effect, fading out the image at the edges
 * x:
 * y: The directional intensity of the vignetting, with a default of x = 0.75, y = 0.5
 */
public class GPUImageVignetteFilter extends GPUImageFilter {
    public static final String VIGNETTING_FRAGMENT_SHADER = "" +
            " uniform sampler2D inputImageTexture;\n" +
            " varying highp vec2 textureCoordinate;\n" +
            " \n" +
            " uniform lowp vec2 vignetteCenter;\n" +
            " uniform lowp vec3 vignetteColor;\n" +
            " uniform highp float vignetteStart;\n" +
            " uniform highp float vignetteEnd;\n" +
            " uniform highp float vignetteWidth;\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "     \n" +
            "     lowp vec3 rgb = texture2D(inputImageTexture, textureCoordinate).rgb;\n" +
            "     lowp float d = distance(gl_FragCoord.xy, vignetteCenter)/vignetteWidth;\n" +
            "     if (d > vignetteEnd) {\n" +
            "         gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     } else {\n" +
            "         lowp float percent = smoothstep(vignetteStart, vignetteEnd, d);\n" +
            "         gl_FragColor = vec4(mix(rgb.x, vignetteColor.x, percent), mix(rgb.y, vignetteColor.y, percent), mix(rgb.z, vignetteColor.z, percent), 1.0);\n" +
            "     }\n" +
            " }";

    private int mVignetteCenterLocation;
    private PointF mVignetteCenter;
    private int mVignetteColorLocation;
    private float[] mVignetteColor;
    private int mVignetteStartLocation;
    private float mVignetteStart;
    private int mVignetteEndLocation;
    private float mVignetteEnd;
    private int mVignetteWidthLocation;
    private float mVignetteWidth = 100f;

    public GPUImageVignetteFilter() {
        this(new PointF(), new float[] {0.0f, 0.0f, 0.0f}, 0f, 0f);
    }

    public GPUImageVignetteFilter(@NonNull final PointF vignetteCenter, final float[] vignetteColor, final float vignetteStart, final float vignetteEnd) {
        super(NO_FILTER_VERTEX_SHADER, VIGNETTING_FRAGMENT_SHADER);
        mVignetteCenter = vignetteCenter;
        mVignetteColor = vignetteColor;
        mVignetteStart = vignetteStart;
        mVignetteEnd = vignetteEnd;
    }

    @Override
    public void onInit() {
        super.onInit();
        mVignetteCenterLocation = GLES20.glGetUniformLocation(getProgram(), "vignetteCenter");
        mVignetteColorLocation = GLES20.glGetUniformLocation(getProgram(), "vignetteColor");
        mVignetteStartLocation = GLES20.glGetUniformLocation(getProgram(), "vignetteStart");
        mVignetteEndLocation = GLES20.glGetUniformLocation(getProgram(), "vignetteEnd");
        mVignetteWidthLocation = GLES20.glGetUniformLocation(getProgram(), "vignetteWidth");

        setVignetteCenter(mVignetteCenter);
        setVignetteColor(mVignetteColor);
        setVignetteStart(mVignetteStart);
        setVignetteEnd(mVignetteEnd);
        setWignetteWidth(mVignetteWidth);
    }

    public void setWignetteWidth(final float vignetteWidth) {
        mVignetteWidth = vignetteWidth;
        setFloat(mVignetteWidthLocation, mVignetteWidth);
    }

    public void setVignetteCenter(@NonNull final PointF vignetteCenter) {
        mVignetteCenter = vignetteCenter;
        setPoint(mVignetteCenterLocation, mVignetteCenter);
    }

    public void setVignetteColor(final float[] vignetteColor) {
        mVignetteColor = vignetteColor;
        setFloatVec3(mVignetteColorLocation, mVignetteColor);
    }
    
    public void setVignetteStart(final float vignetteStart) {
        mVignetteStart = vignetteStart;
        setFloat(mVignetteStartLocation, mVignetteStart);
    }
    
    public void setVignetteEnd(final float vignetteEnd) {
        mVignetteEnd = vignetteEnd;
        setFloat(mVignetteEndLocation, mVignetteEnd);
    }
}
