package com.example.findus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.HashMap;
import java.util.Map;

public class ModifiedImageView extends SubsamplingScaleImageView{
    private Map<String, PointF> pins = new HashMap<String, PointF>();
    private Bitmap bitmapPin;

    public ModifiedImageView(Context context) {
        this(context, null);
    }

    public ModifiedImageView(Context context, AttributeSet attr) {
        super(context, attr);
        // initialise(); //this crashes the system
    }

    public boolean setPin(String name, PointF sPin) {
        if (this.pins.containsKey(name)) {
            return false;
        } else {
            this.pins.put(name, sPin);
            initialise();
            invalidate();
            return true;
        }
    }

    public boolean hasPins() {
        return !(this.pins.isEmpty());
    }

    public PointF getPinCoords(String name) {
        if (this.pins.containsKey(name)) {
            return this.pins.get(name);
        } else {
            return null;
        }
    }

    public void removePins() {
        if (!this.pins.isEmpty()) {
            pins.clear();
        }
        // No need to initialize since this is only called when calibrating one pin i.e. only
        // one pin will be placed at any given time
    }

    private void initialise() {
        float density = getResources().getDisplayMetrics().densityDpi;
        bitmapPin = BitmapFactory.decodeResource(this.getResources(), R.drawable.location_pin_120);
        float w = (density/630f) * bitmapPin.getWidth();
        float h = (density/630f) * bitmapPin.getHeight();
        bitmapPin = Bitmap.createScaledBitmap(bitmapPin, (int)w, (int)h, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Don't draw bitmapPin before image is ready so it doesn't move around during setup.
        if (!isReady()) {
            return;
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        for (PointF curr_pin : pins.values()) { // Can convert for single pin use
            if (curr_pin != null && bitmapPin != null) {
                PointF convertedPin = sourceToViewCoord(curr_pin);
                float coord_x = convertedPin.x - (bitmapPin.getWidth() / 2);
                float coord_y = convertedPin.y - (bitmapPin.getHeight());
                canvas.drawBitmap(bitmapPin, coord_x, coord_y, paint);
            }
        }
    }
}