package com.example.util;

import android.content.Context;
import android.widget.TextView;

import com.example.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Custom interactive MPAndroidChart tooltip marker showing exact timestamp and memory usage on tap.
 */
public class CustomMarkerView extends MarkerView {

    private final TextView tvMarkerTime;
    private final TextView tvMarkerValue;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvMarkerTime = findViewById(R.id.tvMarkerTime);
        tvMarkerValue = findViewById(R.id.tvMarkerValue);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e != null) {
            long timeMs = (long) e.getX();
            float usedGb = e.getY();

            if (tvMarkerTime != null) {
                tvMarkerTime.setText(timeFormat.format(new Date(timeMs)));
            }
            if (tvMarkerValue != null) {
                tvMarkerValue.setText(String.format(Locale.getDefault(), "%.2f GB", usedGb));
            }
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Center the marker horizontally above the point
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10f);
    }
}
