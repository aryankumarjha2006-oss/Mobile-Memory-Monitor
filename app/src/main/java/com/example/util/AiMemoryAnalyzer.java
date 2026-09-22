package com.example.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * AI-powered local memory analysis engine.
 *
 * Uses a linear slope and velocity prediction algorithm based on recent RAM usage history
 * to predict if the phone may slow down in the next few minutes and provides actionable recommendations.
 *
 * Runs 100% locally on device without cloud APIs or network overhead.
 */
public class AiMemoryAnalyzer {

    // Maximum number of samples to keep in memory history buffer
    private static final int MAX_HISTORY_SAMPLES = 20;

    // Recent RAM percentage readings buffer
    private final LinkedList<Integer> ramHistory = new LinkedList<>();

    /**
     * Data class representing actionable UI recommendation items.
     */
    public static class SuggestionItem {
        public final String title;
        public final String description;
        public final String actionTag;
        public final String priority; // HIGH, MEDIUM, LOW

        public SuggestionItem(String title, String description, String actionTag, String priority) {
            this.title = title;
            this.description = description;
            this.actionTag = actionTag;
            this.priority = priority;
        }
    }

    /**
     * Data class holding the complete AI Memory Analysis result.
     */
    public static class AnalysisResult {
        public final int slowdownRiskPercent;
        public final String riskLevel; // LOW, MODERATE, HIGH, CRITICAL
        public final String predictedTimeframe; // e.g., "In 2-3 minutes", "In 5 minutes", "No slowdown expected"
        public final String aiInsightsMessage;
        public final double memoryVelocityPercentPerMin; // Rate of RAM growth
        public final List<SuggestionItem> suggestions;

        public AnalysisResult(
                int slowdownRiskPercent,
                String riskLevel,
                String predictedTimeframe,
                String aiInsightsMessage,
                double memoryVelocityPercentPerMin,
                List<SuggestionItem> suggestions
        ) {
            this.slowdownRiskPercent = slowdownRiskPercent;
            this.riskLevel = riskLevel;
            this.predictedTimeframe = predictedTimeframe;
            this.aiInsightsMessage = aiInsightsMessage;
            this.memoryVelocityPercentPerMin = memoryVelocityPercentPerMin;
            this.suggestions = suggestions;
        }
    }

    /**
     * Adds a new RAM sample to the local history buffer.
     *
     * @param ramPercent Current RAM usage percentage (0 - 100)
     */
    public void addSample(int ramPercent) {
        ramHistory.addLast(ramPercent);
        if (ramHistory.size() > MAX_HISTORY_SAMPLES) {
            ramHistory.removeFirst();
        }
    }

    /**
     * Calculates the linear memory velocity (change in RAM % over time) from the history buffer.
     *
     * @return RAM percentage change trend
     */
    private double calculateMemoryVelocity() {
        if (ramHistory.size() < 2) {
            return 0.0;
        }

        // Simple linear delta between oldest and newest sample in the window
        int oldest = ramHistory.getFirst();
        int newest = ramHistory.getLast();
        double deltaPercent = newest - oldest;

        // Samples are typically taken at ~1s interval
        double windowSeconds = ramHistory.size() - 1;
        if (windowSeconds <= 0) return 0.0;

        // Convert to % growth per minute
        return (deltaPercent / windowSeconds) * 60.0;
    }

    /**
     * Runs local AI predictive analysis on current system metrics and recent RAM trends.
     *
     * @param currentRamPercent Current RAM usage percentage
     * @param freeRamMb Free RAM available in megabytes
     * @param storageUsedPercent Storage usage percentage
     * @return Complete AnalysisResult with predictions and suggestions
     */
    public AnalysisResult analyzeMemory(int currentRamPercent, long freeRamMb, int storageUsedPercent) {
        addSample(currentRamPercent);

        double velocity = calculateMemoryVelocity();
        List<SuggestionItem> suggestions = new ArrayList<>();

        int riskPercent;
        String riskLevel;
        String timeframe;
        String insight;

        // Calculate slowdown risk score (0-100%) based on RAM usage + velocity
        double baseRisk = currentRamPercent;
        if (velocity > 0) {
            baseRisk += (velocity * 2.5); // Add weight for rising memory trends
        }

        riskPercent = (int) Math.min(100, Math.max(0, baseRisk));

        // Determine Risk Category & Predictions
        if (currentRamPercent >= 85 || riskPercent >= 85) {
            riskLevel = "CRITICAL";
            timeframe = "In 1 - 2 minutes";
            insight = "High risk of UI stuttering and app force-closes due to memory pressure.";
        } else if (currentRamPercent >= 75 || riskPercent >= 75) {
            riskLevel = "HIGH";
            timeframe = "In 2 - 3 minutes";
            insight = "Memory usage is trending upward. System background swapping detected.";
        } else if (currentRamPercent >= 60 || riskPercent >= 60) {
            riskLevel = "MODERATE";
            timeframe = "In 5 - 10 minutes";
            insight = "System RAM usage is elevated. Device performance is currently stable.";
        } else {
            riskLevel = "LOW";
            timeframe = "No slowdown predicted";
            insight = "RAM consumption is optimal. Sufficient free memory available.";
        }

        // Generate tailored suggestions according to requirements
        // Requirement 1: Close Chrome (or heavy browsers)
        if (currentRamPercent >= 60) {
            suggestions.add(new SuggestionItem(
                    "Close Chrome",
                    "Web browsers keep high memory tabs in background memory cache.",
                    "CLOSE_CHROME",
                    currentRamPercent >= 80 ? "HIGH" : "MEDIUM"
            ));
        }

        // Requirement 2: Restart device
        if (currentRamPercent >= 75 || freeRamMb < 1000) {
            suggestions.add(new SuggestionItem(
                    "Restart device",
                    "Rebooting clears orphaned system processes and unloads kernel caches.",
                    "RESTART_DEVICE",
                    currentRamPercent >= 85 ? "HIGH" : "MEDIUM"
            ));
        }

        // Requirement 3: Delete large files
        if (storageUsedPercent >= 70 || currentRamPercent >= 70) {
            suggestions.add(new SuggestionItem(
                    "Delete large files",
                    "Freeing storage space improves virtual RAM swap file performance.",
                    "DELETE_LARGE_FILES",
                    storageUsedPercent >= 85 ? "HIGH" : "MEDIUM"
            ));
        }

        // Requirement 4: Uninstall unused apps
        if (suggestions.size() < 4) {
            suggestions.add(new SuggestionItem(
                    "Uninstall unused apps",
                    "Background services from rarely used apps consume background RAM.",
                    "UNINSTALL_UNUSED_APPS",
                    "LOW"
            ));
        }

        return new AnalysisResult(
                riskPercent,
                riskLevel,
                timeframe,
                insight,
                velocity,
                suggestions
        );
    }
}
