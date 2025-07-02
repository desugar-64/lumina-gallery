#!/usr/bin/env python3
"""
Simple Atlas Performance Metrics Table

Shows benchmark metrics in a clean CLI table format with color-coded improvements.
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Optional

# ANSI color codes
class Colors:
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    MAGENTA = '\033[95m'
    CYAN = '\033[96m'
    WHITE = '\033[97m'
    BOLD = '\033[1m'
    RESET = '\033[0m'

def load_timeline(timeline_file: str) -> List[Dict]:
    """Load timeline data"""
    try:
        with open(timeline_file) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return []

def format_value_with_change(current: float, baseline: Optional[float]) -> str:
    """Format value with percentage change and color coding"""
    if current == 0:
        return f"{current:>8.1f}ms"
    
    if baseline is None or baseline == 0:
        return f"{current:>8.1f}ms"
    
    # Calculate percentage change
    change_percent = ((current - baseline) / baseline) * 100
    
    # Color and emoji based on change
    if change_percent > 5:  # Slower (red)
        color = Colors.RED
        emoji = "📈"
    elif change_percent < -5:  # Faster (green)
        color = Colors.GREEN
        emoji = "📉"
    else:  # Similar (yellow)
        color = Colors.YELLOW
        emoji = "➡️"
    
    return f"{color}{current:>8.1f}ms {emoji}{change_percent:+5.1f}%{Colors.RESET}"

def print_metrics_table(timeline: List[Dict]):
    """Print metrics table to CLI"""
    if not timeline:
        print("No timeline data found.")
        return
    
    # Extract all optimizations
    optimizations = [entry.get("optimization", f"run_{i}") for i, entry in enumerate(timeline)]
    
    # Get baseline values (first entry)
    baseline_entry = timeline[0]
    baseline_zoom = baseline_entry.get("zoom_test", {})
    baseline_metrics = baseline_zoom.get("profile_metrics", {}) if baseline_zoom.get("found") else {}
    
    # Define the metrics we want to track (from BenchmarkLabels.kt)
    tracked_metrics = [
        "PhotoLODProcessor.loadBitmapSumMs",
        "PhotoLODProcessor.scaleBitmapSumMs", 
        "AtlasGenerator.softwareCanvasSumMs",
        "AtlasGenerator.createAtlasBitmapSumMs",
        "PhotoLODProcessor.diskOpenInputStreamSumMs",
        "PhotoLODProcessor.memoryDecodeBitmapSumMs",
        "PhotoScaler.createScaledBitmapSumMs",
        "TexturePacker.packAlgorithmSumMs",
        "AtlasManager.generateAtlasSumMs"
    ]
    
    # Print header
    print(f"\n{Colors.BOLD}Atlas Performance Metrics{Colors.RESET}")
    print("=" * 80)
    
    # Print column headers
    header = f"{'Metric':<40}"
    for opt in optimizations:
        header += f" {opt:>15}"
    print(header)
    print("-" * (40 + len(optimizations) * 16))
    
    # Print each metric row
    for metric in tracked_metrics:
        # Clean up metric name for display
        display_name = metric.replace("SumMs", "").replace(".", " ")
        
        row = f"{display_name:<40}"
        
        for i, entry in enumerate(timeline):
            zoom_test = entry.get("zoom_test", {})
            profile_metrics = zoom_test.get("profile_metrics", {}) if zoom_test.get("found") else {}
            
            current_value = profile_metrics.get(metric, 0)
            baseline_value = baseline_metrics.get(metric, 0) if i > 0 else None
            
            formatted_value = format_value_with_change(current_value, baseline_value)
            row += f" {formatted_value:>15}"
        
        print(row)
    
    # Print memory summary
    print("-" * (40 + len(optimizations) * 16))
    memory_row = f"{'Memory (MB)':<40}"
    
    for i, entry in enumerate(timeline):
        zoom_test = entry.get("zoom_test", {})
        memory_metrics = zoom_test.get("memory_metrics", {}) if zoom_test.get("found") else {}
        
        heap_kb = memory_metrics.get("memoryHeapSizeMaxKb", 0)
        rss_anon_kb = memory_metrics.get("memoryRssAnonMaxKb", 0) 
        rss_file_kb = memory_metrics.get("memoryRssFileMaxKb", 0)
        total_mb = (heap_kb + rss_anon_kb + rss_file_kb) / 1024.0
        
        if i == 0:
            baseline_total = total_mb
            memory_row += f" {total_mb:>11.1f}MB"
        else:
            change_percent = ((total_mb - baseline_total) / baseline_total) * 100 if baseline_total > 0 else 0
            if change_percent > 5:
                color = Colors.RED
                emoji = "📈"
            elif change_percent < -5:
                color = Colors.GREEN
                emoji = "📉"
            else:
                color = Colors.YELLOW
                emoji = "➡️"
            memory_row += f" {color}{total_mb:>7.1f}MB {emoji}{change_percent:+5.1f}%{Colors.RESET}"
    
    print(memory_row)
    
    # Print summary
    print("=" * 80)
    latest_entry = timeline[-1]
    latest_zoom = latest_entry.get("zoom_test", {})
    latest_metrics = latest_zoom.get("profile_metrics", {}) if latest_zoom.get("found") else {}
    
    # Calculate total time
    total_time = (
        latest_metrics.get("PhotoLODProcessor.loadBitmapSumMs", 0) +
        latest_metrics.get("PhotoLODProcessor.scaleBitmapSumMs", 0) +
        latest_metrics.get("AtlasGenerator.softwareCanvasSumMs", 0) +
        latest_metrics.get("AtlasGenerator.createAtlasBitmapSumMs", 0)
    )
    
    print(f"Total Atlas Time: {total_time:.1f}ms")
    print(f"Target: 300ms")
    
    if total_time > 300:
        gap = ((total_time - 300) / 300) * 100
        print(f"{Colors.RED}Gap: {gap:.1f}% above target{Colors.RESET}")
    else:
        print(f"{Colors.GREEN}✅ Target achieved!{Colors.RESET}")

if __name__ == "__main__":
    timeline_file = sys.argv[1] if len(sys.argv) > 1 else "benchmark_results/atlas_timeline.json"
    
    timeline = load_timeline(timeline_file)
    print_metrics_table(timeline)