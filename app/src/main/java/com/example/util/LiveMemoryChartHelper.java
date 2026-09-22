package com.example.util;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reusable Java manager for real-time memory monitoring chart using MPAndroidChart.
 * Updates memory used (in GB) every 1 second and retains only the last 30 seconds of data.
 * Features: Interactive Tooltip MarkerView, Live Min/Peak/Avg Stats, Pause/Resume Stream,
 * Curve Mode Toggle, Dynamic Color Schemes (Cyber Dark, Neon Cyber, Clean Light, Amber Gold), and Reset Buffer.
 */
public class LiveMemoryChartHelper {

    /**
     * Color scheme definitions for MPAndroidChart line graph.
     */
    public enum ChartTheme {
        CYBER_DARK("Cyber Dark", "#1E293B", "#0F172A", "#10B981", "#38BDF8", "#334155", "#94A3B8", "#4010B981", "#0010B981", "#F8FAFC", "#94A3B8"),
        NEON_CYBERPUNK("Neon Cyber", "#090D16", "#020617", "#FF007F", "#00FFFF", "#1E293B", "#F1F5F9", "#50FF007F", "#00FF007F", "#FFFFFF", "#A1A1AA"),
        CLEAN_LIGHT("Clean Light", "#F8FAFC", "#F1F5F9", "#2563EB", "#7C3AED", "#CBD5E1", "#334155", "#402563EB", "#002563EB", "#0F172A", "#64748B"),
        AMBER_GOLD("Amber Gold", "#18181B", "#09090B", "#F59E0B", "#FBBF24", "#27272A", "#A1A1AA", "#40F59E0B", "#00F59E0B", "#FAFAFA", "#71717A");

        public final String displayName;
        public final String chartBgColor;
        public final String containerBgColor;
        public final String lineColor;
        public final String pointColor;
        public final String gridColor;
        public final String textColor;
        public final String gradientStart;
        public final String gradientEnd;
        public final String titleTextColor;
        public final String subtitleTextColor;

        ChartTheme(String displayName, String chartBgColor, String containerBgColor, String lineColor,
                   String pointColor, String gridColor, String textColor, String gradientStart,
                   String gradientEnd, String titleTextColor, String subtitleTextColor) {
            this.displayName = displayName;
            this.chartBgColor = chartBgColor;
            this.containerBgColor = containerBgColor;
            this.lineColor = lineColor;
            this.pointColor = pointColor;
            this.gridColor = gridColor;
            this.textColor = textColor;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
            this.titleTextColor = titleTextColor;
            this.subtitleTextColor = subtitleTextColor;
        }
    }

    // Maximum data points visible (30 seconds window)
    private static final int MAX_DATA_POINTS = 30;

    private final Context context;
    private final LineChart lineChart;
    private View llChartRoot;
    private TextView tvChartTitle;
    private TextView tvChartSubtitle;
    private TextView tvCurrentMemory;
    private TextView tvPeakMemory;
    private TextView tvMinMemory;
    private TextView tvAvgMemory;
    private TextView tvLiveStatusBadge;
    private Button btnPauseResume;
    private Button btnToggleCurve;
    private Button btnToggleTheme;
    private Button btnResetData;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private boolean isCubicMode = true;

    // Current Active Chart Theme Scheme
    private ChartTheme currentTheme = ChartTheme.CYBER_DARK;

    // Format for time display on the X-axis (HH:mm:ss)
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public LiveMemoryChartHelper(Context context, View rootView) {
        this.context = context.getApplicationContext();
        this.llChartRoot = rootView.findViewById(R.id.llChartRoot);
        this.tvChartTitle = rootView.findViewById(R.id.tvChartTitle);
        this.tvChartSubtitle = rootView.findViewById(R.id.tvChartSubtitle);
        this.lineChart = rootView.findViewById(R.id.lineChart);
        this.tvCurrentMemory = rootView.findViewById(R.id.tvCurrentMemory);
        this.tvPeakMemory = rootView.findViewById(R.id.tvPeakMemory);
        this.tvMinMemory = rootView.findViewById(R.id.tvMinMemory);
        this.tvAvgMemory = rootView.findViewById(R.id.tvAvgMemory);
        this.tvLiveStatusBadge = rootView.findViewById(R.id.tvLiveStatusBadge);
        this.btnPauseResume = rootView.findViewById(R.id.btnPauseResume);
        this.btnToggleCurve = rootView.findViewById(R.id.btnToggleCurve);
        this.btnToggleTheme = rootView.findViewById(R.id.btnToggleTheme);
        this.btnResetData = rootView.findViewById(R.id.btnResetData);

        setupChartConfiguration();
        setupInteractiveControls();
    }

    public LiveMemoryChartHelper(Context context, LineChart lineChart, TextView tvCurrentMemory) {
        this.context = context.getApplicationContext();
        this.lineChart = lineChart;
        this.tvCurrentMemory = tvCurrentMemory;
        setupChartConfiguration();
    }

    /**
     * Initializes MPAndroidChart visual settings, axis styles, limit lines, tooltip, and dataset colors.
     */
    private void setupChartConfiguration() {
        if (lineChart == null) return;

        // General Chart Settings
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        lineChart.getLegend().setTextColor(Color.parseColor(currentTheme.textColor));

        // Setup Custom Interactive MarkerView Tooltip on tap
        CustomMarkerView marker = new CustomMarkerView(context, R.layout.custom_marker_view);
        marker.setChartView(lineChart);
        lineChart.setMarker(marker);

        // Customize X-Axis (Time)
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGranularity(1f); // 1 second interval
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return timeFormat.format(new Date((long) value));
            }
        });

        // Get Total Memory in GB for Y-Axis cap and limit line
        float totalGb = getTotalMemoryInGb();
        float warningThresholdGb = totalGb * 0.80f; // 80% RAM warning threshold

        // Customize Left Y-Axis (Memory Used in GB)
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        if (totalGb > 0) {
            leftAxis.setAxisMaximum((float) Math.ceil(totalGb));
        }

        // Add Dynamic Warning Limit Line
        LimitLine limitLine = new LimitLine(warningThresholdGb, "80% Alert Limit");
        limitLine.setLineWidth(1.5f);
        limitLine.enableDashedLine(10f, 6f, 0f);
        limitLine.setLineColor(Color.parseColor("#F43F5E")); // Coral Red
        limitLine.setTextColor(Color.parseColor("#F43F5E"));
        limitLine.setTextSize(9f);
        leftAxis.removeAllLimitLines();
        leftAxis.addLimitLine(limitLine);

        // Disable Right Y-Axis for cleaner view
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);

        // Configure initial empty data object
        LineData data = new LineData();
        lineChart.setData(data);

        // Apply current active theme
        applyTheme(currentTheme);
    }

    /**
     * Connects click listeners for interactive controls (Pause, Toggle Curve, Theme Toggle, Reset).
     */
    private void setupInteractiveControls() {
        if (btnPauseResume != null) {
            btnPauseResume.setOnClickListener(v -> togglePause());
        }

        if (btnToggleCurve != null) {
            btnToggleCurve.setOnClickListener(v -> toggleCurveMode());
        }

        if (btnToggleTheme != null) {
            btnToggleTheme.setOnClickListener(v -> cycleTheme());
        }

        if (btnResetData != null) {
            btnResetData.setOnClickListener(v -> resetChartBuffer());
        }
    }

    /**
     * Cycles to the next available color scheme theme.
     */
    public void cycleTheme() {
        ChartTheme[] themes = ChartTheme.values();
        int nextOrdinal = (currentTheme.ordinal() + 1) % themes.length;
        applyTheme(themes[nextOrdinal]);
    }

    /**
     * Applies the specified color scheme theme to the MPAndroidChart instance and layout components.
     *
     * @param theme Target ChartTheme to apply
     */
    public void applyTheme(ChartTheme theme) {
        if (theme == null) return;
        this.currentTheme = theme;

        if (llChartRoot != null) {
            llChartRoot.setBackgroundColor(Color.parseColor(theme.containerBgColor));
        }

        if (tvChartTitle != null) {
            tvChartTitle.setTextColor(Color.parseColor(theme.titleTextColor));
        }

        if (tvChartSubtitle != null) {
            tvChartSubtitle.setTextColor(Color.parseColor(theme.subtitleTextColor));
        }

        if (btnToggleTheme != null) {
            btnToggleTheme.setText("Theme: " + theme.displayName.split(" ")[0]);
        }

        if (lineChart != null) {
            lineChart.setBackgroundColor(Color.parseColor(theme.chartBgColor));
            lineChart.getLegend().setTextColor(Color.parseColor(theme.textColor));

            XAxis xAxis = lineChart.getXAxis();
            xAxis.setTextColor(Color.parseColor(theme.textColor));
            xAxis.setGridColor(Color.parseColor(theme.gridColor));

            YAxis leftAxis = lineChart.getAxisLeft();
            leftAxis.setTextColor(Color.parseColor(theme.textColor));
            leftAxis.setGridColor(Color.parseColor(theme.gridColor));

            LineData data = lineChart.getData();
            if (data != null && data.getDataSetCount() > 0) {
                LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
                if (set != null) {
                    set.setColor(Color.parseColor(theme.lineColor));
                    set.setCircleColor(Color.parseColor(theme.pointColor));
                    set.setHighLightColor(Color.parseColor(theme.pointColor));
                    set.setCircleHoleColor(Color.parseColor(theme.chartBgColor));

                    GradientDrawable gradient = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{Color.parseColor(theme.gradientStart), Color.parseColor(theme.gradientEnd)}
                    );
                    set.setFillDrawable(gradient);
                }
                data.notifyDataChanged();
            }

            lineChart.notifyDataSetChanged();
            lineChart.invalidate();
        }
    }

    /**
     * Toggles live telemetry stream pause/resume.
     */
    public void togglePause() {
        isPaused = !isPaused;
        if (btnPauseResume != null) {
            btnPauseResume.setText(isPaused ? "Resume Feed" : "Pause Feed");
        }
        if (tvLiveStatusBadge != null) {
            tvLiveStatusBadge.setText(isPaused ? "❚❚ PAUSED" : "● LIVE 1s");
            tvLiveStatusBadge.setTextColor(isPaused ? Color.parseColor("#F59E0B") : Color.parseColor("#10B981"));
        }
    }

    /**
     * Toggles chart mode between Cubic Bezier curve and Linear / Stepped.
     */
    public void toggleCurveMode() {
        isCubicMode = !isCubicMode;
        if (lineChart == null || lineChart.getData() == null) return;

        LineDataSet set = (LineDataSet) lineChart.getData().getDataSetByIndex(0);
        if (set != null) {
            set.setMode(isCubicMode ? LineDataSet.Mode.CUBIC_BEZIER : LineDataSet.Mode.STEPPED);
            lineChart.notifyDataSetChanged();
            lineChart.invalidate();
        }

        if (btnToggleCurve != null) {
            btnToggleCurve.setText(isCubicMode ? "Smooth" : "Stepped");
        }
    }

    /**
     * Resets the 30-second data buffer and clears entries on chart.
     */
    public void resetChartBuffer() {
        if (lineChart == null || lineChart.getData() == null) return;

        LineData data = lineChart.getData();
        data.clearValues();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

        if (tvCurrentMemory != null) tvCurrentMemory.setText("0.00 GB");
        if (tvPeakMemory != null) tvPeakMemory.setText("0.00 GB");
        if (tvMinMemory != null) tvMinMemory.setText("0.00 GB");
        if (tvAvgMemory != null) tvAvgMemory.setText("0.00 GB");
    }

    /**
     * Creates a styled LineDataSet with current active theme palette.
     *
     * @return Formatted LineDataSet instance
     */
    private LineDataSet createDataSet() {
        LineDataSet set = new LineDataSet(null, "Memory Used (GB)");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.parseColor(currentTheme.lineColor));
        set.setCircleColor(Color.parseColor(currentTheme.pointColor));
        set.setLineWidth(2.5f);
        set.setCircleRadius(4f);
        set.setDrawCircleHole(true);
        set.setCircleHoleColor(Color.parseColor(currentTheme.chartBgColor));
        set.setCircleHoleRadius(2f);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        set.setMode(isCubicMode ? LineDataSet.Mode.CUBIC_BEZIER : LineDataSet.Mode.STEPPED);

        // Highlight Crosshair styling on tap
        set.setHighlightEnabled(true);
        set.setHighLightColor(Color.parseColor(currentTheme.pointColor));
        set.setHighlightLineWidth(1.2f);

        // Modern Gradient Fill under curve
        set.setDrawFilled(true);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(currentTheme.gradientStart), Color.parseColor(currentTheme.gradientEnd)}
        );
        set.setFillDrawable(gradient);

        return set;
    }

    /**
     * Reads current memory usage via ActivityManager and converts used bytes to GB.
     *
     * @return Memory used in Gigabytes (GB)
     */
    private float getUsedMemoryInGb() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }

        long totalBytes = memoryInfo.totalMem;
        long availBytes = memoryInfo.availMem;
        long usedBytes = totalBytes - availBytes;

        return (float) (usedBytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Reads total device RAM in GB.
     */
    private float getTotalMemoryInGb() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
            return (float) (memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0));
        }
        return 8.0f;
    }

    /**
     * Starts adding real-time memory data entries to the chart every 1 second.
     */
    public void startMonitoring() {
        if (isRunning) return;
        isRunning = true;

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                if (!isPaused) {
                    addMemoryEntry();
                }

                // Schedule next execution in 1000 milliseconds (1 second)
                handler.postDelayed(this, 1000);
            }
        };

        handler.post(updateRunnable);
    }

    /**
     * Adds a new entry (timestamp, used RAM GB) to MPAndroidChart and retains only last 30 data points.
     */
    private void addMemoryEntry() {
        if (lineChart == null) return;
        LineData data = lineChart.getData();
        if (data == null) return;

        LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
        if (set == null) {
            set = createDataSet();
            data.addDataSet(set);
        }

        long currentTimeMs = System.currentTimeMillis();
        float usedGb = getUsedMemoryInGb();

        // Add new entry with X = Current Timestamp in ms, Y = Used Memory in GB
        data.addEntry(new Entry(currentTimeMs, usedGb), 0);

        // Enforce max 30 seconds window by removing oldest entries if count exceeds 30
        while (set.getEntryCount() > MAX_DATA_POINTS) {
            set.removeEntry(0);
        }

        // Notify chart that dataset changed
        data.notifyDataChanged();
        lineChart.notifyDataSetChanged();

        // Limit visible X-range and move view to the latest entry
        lineChart.setVisibleXRangeMaximum(MAX_DATA_POINTS);
        lineChart.moveViewToX(currentTimeMs);

        // Update statistics summary (Peak, Min, Avg, Current) over rolling window
        updateLiveStats(set, usedGb);
    }

    /**
     * Calculates rolling Peak, Min, and Average values from current entries in set.
     */
    private void updateLiveStats(LineDataSet set, float currentGb) {
        if (set == null || set.getEntryCount() == 0) return;

        float peak = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;
        float sum = 0f;
        int count = set.getEntryCount();

        for (int i = 0; i < count; i++) {
            Entry e = set.getEntryForIndex(i);
            if (e != null) {
                float y = e.getY();
                if (y > peak) peak = y;
                if (y < min) min = y;
                sum += y;
            }
        }

        float avg = count > 0 ? (sum / count) : currentGb;

        if (tvCurrentMemory != null) {
            tvCurrentMemory.setText(String.format(Locale.getDefault(), "%.2f GB", currentGb));
        }
        if (tvPeakMemory != null) {
            tvPeakMemory.setText(String.format(Locale.getDefault(), "%.2f GB", peak));
        }
        if (tvMinMemory != null) {
            tvMinMemory.setText(String.format(Locale.getDefault(), "%.2f GB", min));
        }
        if (tvAvgMemory != null) {
            tvAvgMemory.setText(String.format(Locale.getDefault(), "%.2f GB", avg));
        }
    }

    /**
     * Stops the real-time update loop handler.
     */
    public void stopMonitoring() {
        isRunning = false;
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    public ChartTheme getCurrentTheme() {
        return currentTheme;
    }
}
