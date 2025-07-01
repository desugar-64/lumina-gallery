#!/usr/bin/env python3
"""
Atlas Performance Timeline Chart Generator

Generates simple HTML reports with SVG charts showing atlas performance over time.

Usage:
    python atlas_timeline_chart.py
    python atlas_timeline_chart.py --timeline-file benchmark_results/atlas_timeline.json
"""

import json
import datetime
from pathlib import Path
from typing import List, Dict, Tuple, Optional

class AtlasTimelineChart:
    def __init__(self, timeline_file: str = "benchmark_results/atlas_timeline.json"):
        self.timeline_file = Path(timeline_file)
        
        # Chart dimensions
        self.chart_width = 800
        self.chart_height = 300
        self.margin = 60
        
        # Performance targets (updated for aggressive optimization)
        self.target_time_ms = 300.0  # 300ms aggressive target
        self.baseline_time_ms = 1600.0  # ~1.6 second actual baseline
    
    def generate_html_report(self, output_file: str = "benchmark_results/atlas_performance_report.html"):
        """Generate simple HTML report with timeline chart"""
        timeline = self._load_timeline()
        
        if not timeline:
            self._generate_empty_report(output_file)
            return
        
        # Extract performance data
        performance_data = self._extract_performance_data(timeline)
        
        # Generate chart
        svg_chart = self._create_timeline_chart(performance_data)
        
        # Generate summary table
        summary_table = self._create_summary_table(timeline)
        
        # Generate conclusion
        conclusion = self._generate_conclusion(timeline, performance_data)
        
        # Create complete HTML
        html_content = self._create_html_template(svg_chart, summary_table, conclusion, len(timeline))
        
        with open(output_file, 'w') as f:
            f.write(html_content)
        
        print(f"📊 Generated performance report: {output_file}")
        print(f"🌐 Open in browser: file://{Path(output_file).absolute()}")
    
    def _load_timeline(self) -> List[Dict]:
        """Load timeline data"""
        if not self.timeline_file.exists():
            return []
        
        try:
            with open(self.timeline_file) as f:
                return json.load(f)
        except (json.JSONDecodeError, FileNotFoundError):
            return []
    
    def _extract_performance_data(self, timeline: List[Dict]) -> List[Dict]:
        """Extract performance data points from timeline"""
        data_points = []
        
        for i, entry in enumerate(timeline):
            # Get main atlas generation time
            atlas_time = entry.get("total_optimization_time", 0)
            if atlas_time == 0:
                # Fallback to zoom test data
                zoom_test = entry.get("zoom_test", {})
                if zoom_test.get("found"):
                    atlas_time = zoom_test.get("profile_metrics", {}).get("AtlasManager.generateAtlasSumMs", 0)
            
            # Get component breakdown
            zoom_test = entry.get("zoom_test", {})
            profile_metrics = zoom_test.get("profile_metrics", {}) if zoom_test.get("found") else {}
            
            data_points.append({
                "index": i,
                "optimization": entry.get("optimization", f"entry_{i}"),
                "timestamp": entry.get("timestamp", ""),
                "atlas_time": atlas_time,
                "bitmap_scaling": profile_metrics.get("PhotoLODProcessor.scaleBitmapSumMs", 0),
                "canvas_rendering": profile_metrics.get("AtlasGenerator.softwareCanvasSumMs", 0),
                "bitmap_loading": profile_metrics.get("PhotoLODProcessor.loadBitmapSumMs", 0),
                "git_commit": entry.get("git_commit", "unknown")
            })
        
        return data_points
    
    def _create_timeline_chart(self, data_points: List[Dict]) -> str:
        """Create enhanced SVG timeline chart with all atlas components"""
        if not data_points:
            return "<p>No data available for chart</p>"
        
        # Chart area dimensions
        plot_width = self.chart_width - 2 * self.margin
        plot_height = self.chart_height - 2 * self.margin
        
        # Atlas component metrics to track (comprehensive performance breakdown)
        component_metrics = {
            # Primary optimization targets
            "PhotoLODProcessor.scaleBitmapSumMs": {"name": "Bitmap Scaling", "color": "#f59e0b"},
            "AtlasGenerator.softwareCanvasSumMs": {"name": "Software Canvas", "color": "#8b5cf6"},
            
            # Supporting atlas operations
            "PhotoLODProcessor.loadBitmapSumMs": {"name": "Bitmap Loading", "color": "#ef4444"},
            "AtlasGenerator.createAtlasBitmapSumMs": {"name": "Atlas Creation", "color": "#06b6d4"},
            "AtlasManager.generateAtlasSumMs": {"name": "Total Atlas", "color": "#2563eb"},
            
            # Disk I/O Operations (File System Access)
            "PhotoLODProcessor.diskOpenInputStreamSumMs": {"name": "Disk I/O", "color": "#dc2626"},
            
            # Memory I/O Operations (Bitmap Processing in RAM)
            "PhotoLODProcessor.memoryDecodeBoundsSumMs": {"name": "Decode Bounds", "color": "#f97316"},
            "PhotoLODProcessor.memoryDecodeBitmapSumMs": {"name": "Decode Bitmap", "color": "#ea580c"},
            "PhotoLODProcessor.memorySampleSizeCalcSumMs": {"name": "Sample Size", "color": "#fb923c"},
            
            # Hardware-accelerated scaling operations
            "PhotoScaler.scaleSumMs": {"name": "PhotoScaler", "color": "#84cc16"},
            "PhotoScaler.createScaledBitmapSumMs": {"name": "HW Scaling", "color": "#65a30d"},
            
            # Memory management operations
            "Atlas.bitmapAllocateSumMs": {"name": "Mem Alloc", "color": "#10b981"},
            "Atlas.bitmapRecycleSumMs": {"name": "Mem Recycle", "color": "#059669"},
            
            # Texture packing algorithm performance
            "TexturePacker.packAlgorithmSumMs": {"name": "Pack Algorithm", "color": "#3b82f6"},
            "TexturePacker.sortImagesSumMs": {"name": "Image Sorting", "color": "#1d4ed8"},
        }
        
        # Find data ranges across all metrics from timeline
        all_values = []
        timeline = self._load_timeline()
        for entry in timeline:
            zoom_test = entry.get("zoom_test", {})
            if zoom_test.get("found"):
                profile_metrics = zoom_test.get("profile_metrics", {})
                for metric_key in component_metrics.keys():
                    value = profile_metrics.get(metric_key, 0)
                    if value > 0:
                        all_values.append(value)
        
        if not all_values:
            return "<p>No performance data available</p>"
        
        max_time = max(max(all_values), self.target_time_ms * 1.2)
        min_time = 0
        
        # Calculate positions
        def get_x(index: int) -> float:
            if len(data_points) == 1:
                return self.margin + plot_width / 2
            return self.margin + (index / (len(data_points) - 1)) * plot_width
        
        def get_y(time_ms: float) -> float:
            return self.margin + plot_height - ((time_ms - min_time) / (max_time - min_time)) * plot_height
        
        # Start SVG with enhanced styling
        svg_parts = [
            f'<svg width="{self.chart_width}" height="{self.chart_height}" xmlns="http://www.w3.org/2000/svg">',
            '<defs>',
            '<style>',
            '.target-line { fill: none; stroke: #dc2626; stroke-width: 2; stroke-dasharray: 5,5; }',
            '.component-line { fill: none; stroke-width: 2; }',
            '.data-point { stroke: white; stroke-width: 2; r: 4; }',
            '.axis-line { stroke: #6b7280; stroke-width: 1; }',
            '.axis-text { font-family: Arial, sans-serif; font-size: 12px; fill: #374151; }',
            '.chart-title { font-family: Arial, sans-serif; font-size: 16px; font-weight: bold; fill: #111827; }',
            '.legend-text { font-family: Arial, sans-serif; font-size: 12px; fill: #374151; }',
            '.legend-item { font-family: Arial, sans-serif; font-size: 11px; fill: #374151; }',
            '</style>',
            '</defs>'
        ]
        
        # Background
        svg_parts.append(f'<rect width="{self.chart_width}" height="{self.chart_height}" fill="white" stroke="#e5e7eb"/>')
        
        # Title
        svg_parts.append(f'<text x="{self.chart_width/2}" y="25" text-anchor="middle" class="chart-title">Atlas Performance Timeline</text>')
        
        # Y-axis
        svg_parts.append(f'<line x1="{self.margin}" y1="{self.margin}" x2="{self.margin}" y2="{self.margin + plot_height}" class="axis-line"/>')
        
        # X-axis
        svg_parts.append(f'<line x1="{self.margin}" y1="{self.margin + plot_height}" x2="{self.margin + plot_width}" y2="{self.margin + plot_height}" class="axis-line"/>')
        
        # Y-axis labels (time)
        for i in range(5):
            time_value = (max_time / 4) * i
            y_pos = get_y(time_value)
            svg_parts.append(f'<text x="{self.margin - 10}" y="{y_pos + 4}" text-anchor="end" class="axis-text">{time_value:.0f}ms</text>')
            
            # Grid lines
            if i > 0:
                svg_parts.append(f'<line x1="{self.margin}" y1="{y_pos}" x2="{self.margin + plot_width}" y2="{y_pos}" stroke="#f3f4f6" stroke-width="1"/>')
        
        # Target line
        target_y = get_y(self.target_time_ms)
        svg_parts.append(f'<line x1="{self.margin}" y1="{target_y}" x2="{self.margin + plot_width}" y2="{target_y}" class="target-line"/>')
        svg_parts.append(f'<text x="{self.margin + plot_width - 5}" y="{target_y - 5}" text-anchor="end" class="legend-text">Target: {self.target_time_ms:.0f}ms</text>')
        
        # Extract component data from timeline entries  
        component_data = {}
        for metric_key in component_metrics.keys():
            component_data[metric_key] = []
            for point in data_points:
                # Get the metric value from the original timeline data
                timeline_entry = None
                for entry in self._load_timeline():
                    if entry.get("optimization") == point["optimization"]:
                        timeline_entry = entry
                        break
                
                if timeline_entry:
                    zoom_test = timeline_entry.get("zoom_test", {})
                    profile_metrics = zoom_test.get("profile_metrics", {})
                    value = profile_metrics.get(metric_key, 0)
                else:
                    value = 0
                    
                component_data[metric_key].append({
                    "index": point["index"],
                    "value": value,
                    "optimization": point["optimization"]
                })
        
        # Draw component lines (if we have multiple data points)
        if len(data_points) > 1:
            for metric_key, metric_info in component_metrics.items():
                line_points = []
                for data_point in component_data[metric_key]:
                    if data_point["value"] > 0:
                        x = get_x(data_point["index"])
                        y = get_y(data_point["value"])
                        line_points.append(f"{x},{y}")
                
                if len(line_points) > 1:
                    svg_parts.append(f'<polyline points="{" ".join(line_points)}" class="component-line" stroke="{metric_info["color"]}"/>')
        
        # Draw component data points with colors
        for metric_key, metric_info in component_metrics.items():
            for data_point in component_data[metric_key]:
                if data_point["value"] > 0:
                    x = get_x(data_point["index"])
                    y = get_y(data_point["value"])
                    svg_parts.append(f'<circle cx="{x}" cy="{y}" r="3" fill="{metric_info["color"]}" class="data-point">')
                    svg_parts.append(f'<title>{metric_info["name"]}: {data_point["value"]:.1f}ms ({data_point["optimization"]})</title>')
                    svg_parts.append('</circle>')
        
        # Legend
        legend_y = 45
        legend_x_start = self.margin
        for i, (metric_key, metric_info) in enumerate(component_metrics.items()):
            legend_x = legend_x_start + (i * 140)
            if legend_x + 120 > self.chart_width - self.margin:
                # Wrap to next line if needed
                legend_y += 15
                legend_x = legend_x_start
            
            # Legend color box
            svg_parts.append(f'<rect x="{legend_x}" y="{legend_y - 8}" width="10" height="10" fill="{metric_info["color"]}"/>')
            # Legend text
            svg_parts.append(f'<text x="{legend_x + 15}" y="{legend_y}" class="legend-item">{metric_info["name"]}</text>')
        
        # X-axis labels (optimization names)
        for point in data_points:
            x = get_x(point["index"])
            # Rotate text for better fit
            svg_parts.append(f'<text x="{x}" y="{self.margin + plot_height + 20}" text-anchor="middle" class="axis-text" transform="rotate(-45, {x}, {self.margin + plot_height + 20})">{point["optimization"][:10]}</text>')
        
        svg_parts.append('</svg>')
        return ''.join(svg_parts)
    
    def _create_summary_table(self, timeline: List[Dict]) -> str:
        """Create summary table of all entries"""
        if not timeline:
            return "<p>No timeline data available</p>"
        
        table_parts = [
            '<table class="summary-table">',
            '<thead>',
            '<tr>',
            '<th>Optimization</th>',
            '<th>Total Atlas (ms)</th>',
            '<th>Disk I/O (ms)</th>',
            '<th>Bitmap Decode (ms)</th>',
            '<th>Bitmap Scaling (ms)</th>',
            '<th>Software Canvas (ms)</th>',
            '<th>Atlas Creation (ms)</th>',
            '<th>Git Commit</th>',
            '<th>Date</th>',
            '</tr>',
            '</thead>',
            '<tbody>'
        ]
        
        for entry in timeline:
            # Get performance data
            atlas_time = entry.get("total_optimization_time", 0)
            zoom_test = entry.get("zoom_test", {})
            profile_metrics = zoom_test.get("profile_metrics", {}) if zoom_test.get("found") else {}
            
            if atlas_time == 0:
                atlas_time = profile_metrics.get("AtlasManager.generateAtlasSumMs", 0)
            
            # Extract I/O separation metrics
            disk_io = profile_metrics.get("PhotoLODProcessor.diskOpenInputStreamSumMs", 0)
            bitmap_decode = profile_metrics.get("PhotoLODProcessor.memoryDecodeBitmapSumMs", 0) 
            bitmap_scaling = profile_metrics.get("PhotoLODProcessor.scaleBitmapSumMs", 0)
            software_canvas = profile_metrics.get("AtlasGenerator.softwareCanvasSumMs", 0)
            atlas_creation = profile_metrics.get("AtlasGenerator.createAtlasBitmapSumMs", 0)
            
            # Format timestamp
            timestamp = entry.get("timestamp", "")
            try:
                date_obj = datetime.datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
                formatted_date = date_obj.strftime("%Y-%m-%d %H:%M")
            except:
                formatted_date = timestamp[:16] if timestamp else "unknown"
            
            table_parts.extend([
                '<tr>',
                f'<td>{entry.get("optimization", "unknown")}</td>',
                f'<td>{atlas_time:.1f}</td>',
                f'<td>{disk_io:.1f}</td>',
                f'<td>{bitmap_decode:.1f}</td>',
                f'<td>{bitmap_scaling:.1f}</td>',
                f'<td>{software_canvas:.1f}</td>',
                f'<td>{atlas_creation:.1f}</td>',
                f'<td><code>{entry.get("git_commit", "unknown")}</code></td>',
                f'<td>{formatted_date}</td>',
                '</tr>'
            ])
        
        table_parts.extend(['</tbody>', '</table>'])
        return ''.join(table_parts)
    
    def _generate_conclusion(self, timeline: List[Dict], performance_data: List[Dict]) -> str:
        """Generate conclusion analysis"""
        if not timeline or not performance_data:
            return "<p>No data available for analysis</p>"
        
        # Get latest performance
        latest = performance_data[-1]
        latest_time = latest["atlas_time"]
        
        # Get baseline (first entry)
        baseline = performance_data[0]
        baseline_time = baseline["atlas_time"]
        
        conclusion_parts = [f'<h3>Performance Analysis</h3>']
        
        # Current vs Target
        if latest_time > 0:
            target_gap = ((latest_time - self.target_time_ms) / self.target_time_ms) * 100
            
            if latest_time <= self.target_time_ms:
                conclusion_parts.append(f'<p class="success">✅ <strong>Target Achieved!</strong> Current performance ({latest_time:.1f}ms) meets the target of {self.target_time_ms:.0f}ms.</p>')
            else:
                conclusion_parts.append(f'<p class="warning">⏳ <strong>Target Gap:</strong> Current performance ({latest_time:.1f}ms) is {target_gap:.1f}% above target ({self.target_time_ms:.0f}ms).</p>')
        
        # Progress since baseline
        if len(performance_data) > 1 and baseline_time > 0 and latest_time > 0:
            improvement_percent = ((baseline_time - latest_time) / baseline_time) * 100
            
            if improvement_percent > 0:
                conclusion_parts.append(f'<p class="success">📈 <strong>Improvement:</strong> {improvement_percent:.1f}% faster than baseline ({baseline_time:.1f}ms → {latest_time:.1f}ms).</p>')
            elif improvement_percent < 0:
                conclusion_parts.append(f'<p class="warning">📉 <strong>Regression:</strong> {abs(improvement_percent):.1f}% slower than baseline ({baseline_time:.1f}ms → {latest_time:.1f}ms).</p>')
            else:
                conclusion_parts.append(f'<p>🔄 <strong>No Change:</strong> Performance remains at {latest_time:.1f}ms.</p>')
        
        # Component analysis
        if latest["bitmap_scaling"] > 0 or latest["canvas_rendering"] > 0:
            conclusion_parts.append('<p><strong>Component Breakdown (Latest):</strong></p>')
            conclusion_parts.append('<ul>')
            
            if latest["bitmap_scaling"] > 0:
                scaling_percent = (latest["bitmap_scaling"] / latest_time) * 100 if latest_time > 0 else 0
                conclusion_parts.append(f'<li>Bitmap Scaling: {latest["bitmap_scaling"]:.1f}ms ({scaling_percent:.1f}%)</li>')
            
            if latest["canvas_rendering"] > 0:
                canvas_percent = (latest["canvas_rendering"] / latest_time) * 100 if latest_time > 0 else 0
                conclusion_parts.append(f'<li>Canvas Rendering: {latest["canvas_rendering"]:.1f}ms ({canvas_percent:.1f}%)</li>')
            
            conclusion_parts.append('</ul>')
        
        # Recommendations
        conclusion_parts.append('<h4>Next Steps</h4>')
        if latest_time > self.target_time_ms:
            conclusion_parts.append('<ul>')
            if latest["bitmap_scaling"] > latest["canvas_rendering"]:
                conclusion_parts.append('<li>🎯 <strong>Priority:</strong> Optimize bitmap scaling operations (bitmap pool implementation)</li>')
            else:
                conclusion_parts.append('<li>🎯 <strong>Priority:</strong> Optimize canvas rendering (hardware acceleration)</li>')
            conclusion_parts.append('<li>📊 Continue monitoring performance with additional optimizations</li>')
            conclusion_parts.append('</ul>')
        else:
            conclusion_parts.append('<p>🎉 Performance target achieved! Consider monitoring for regressions.</p>')
        
        return ''.join(conclusion_parts)
    
    def _create_html_template(self, svg_chart: str, summary_table: str, conclusion: str, entry_count: int) -> str:
        """Create complete HTML document"""
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Atlas Performance Report</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f9fafb;
            color: #111827;
        }}
        
        .container {{
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            border-radius: 8px;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
            padding: 20px;
        }}
        
        .header {{
            text-align: center;
            margin-bottom: 30px;
            border-bottom: 2px solid #e5e7eb;
            padding-bottom: 20px;
        }}
        
        .header h1 {{
            margin: 0;
            color: #1f2937;
            font-size: 2.5rem;
        }}
        
        .header .subtitle {{
            color: #6b7280;
            font-size: 1.1rem;
            margin-top: 10px;
        }}
        
        .chart-section {{
            margin-bottom: 40px;
            text-align: center;
        }}
        
        .chart-container {{
            display: inline-block;
            border: 1px solid #e5e7eb;
            border-radius: 6px;
            padding: 20px;
            background: #fefefe;
        }}
        
        .summary-table {{
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
            font-size: 14px;
        }}
        
        .summary-table th,
        .summary-table td {{
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #e5e7eb;
        }}
        
        .summary-table th {{
            background-color: #f3f4f6;
            font-weight: 600;
            color: #374151;
        }}
        
        .summary-table tr:hover {{
            background-color: #f9fafb;
        }}
        
        .summary-table code {{
            background-color: #f3f4f6;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'SF Mono', Monaco, monospace;
            font-size: 12px;
        }}
        
        .conclusion {{
            background-color: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 6px;
            padding: 20px;
            margin-top: 30px;
        }}
        
        .conclusion h3 {{
            margin-top: 0;
            color: #1e40af;
        }}
        
        .conclusion .success {{
            color: #059669;
            font-weight: 500;
        }}
        
        .conclusion .warning {{
            color: #d97706;
            font-weight: 500;
        }}
        
        .conclusion ul {{
            margin: 10px 0;
            padding-left: 20px;
        }}
        
        .footer {{
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #e5e7eb;
            text-align: center;
            color: #6b7280;
            font-size: 14px;
        }}
        
        .stats {{
            display: flex;
            justify-content: space-around;
            margin: 20px 0;
            padding: 15px;
            background-color: #f3f4f6;
            border-radius: 6px;
        }}
        
        .stat {{
            text-align: center;
        }}
        
        .stat-value {{
            font-size: 1.5rem;
            font-weight: bold;
            color: #1f2937;
        }}
        
        .stat-label {{
            font-size: 0.9rem;
            color: #6b7280;
            margin-top: 5px;
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Atlas Performance Report</h1>
            <div class="subtitle">Texture System Optimization Tracking</div>
            <div class="stats">
                <div class="stat">
                    <div class="stat-value">{entry_count}</div>
                    <div class="stat-label">Benchmark Runs</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{self.target_time_ms:.0f}ms</div>
                    <div class="stat-label">Performance Target</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{self.baseline_time_ms:.0f}ms</div>
                    <div class="stat-label">Original Baseline</div>
                </div>
            </div>
        </div>
        
        <div class="chart-section">
            <h2>Performance Timeline</h2>
            <div class="chart-container">
                {svg_chart}
            </div>
        </div>
        
        <div class="table-section">
            <h2>Benchmark Results</h2>
            {summary_table}
        </div>
        
        <div class="conclusion">
            {conclusion}
        </div>
        
        <div class="footer">
            <p>Report generated on {timestamp}</p>
            <p>Atlas Texture System Benchmarking • LuminaGallery Project</p>
        </div>
    </div>
</body>
</html>"""
    
    def _generate_empty_report(self, output_file: str):
        """Generate empty report when no data available"""
        html_content = self._create_html_template(
            "<p>No timeline data found. Run benchmark collection first.</p>",
            "<p>No benchmark results available.</p>",
            "<p>No performance data available for analysis.</p>",
            0
        )
        
        with open(output_file, 'w') as f:
            f.write(html_content)
        
        print(f"📊 Generated empty report: {output_file}")
        print("💡 Run benchmark collection to populate data")

if __name__ == "__main__":
    import sys
    import argparse
    
    parser = argparse.ArgumentParser(description="Generate HTML performance report")
    parser.add_argument("--timeline-file", default="benchmark_results/atlas_timeline.json",
                       help="Path to timeline JSON file")
    parser.add_argument("--output-file", default="benchmark_results/atlas_performance_report.html",
                       help="Output HTML file path")
    
    args = parser.parse_args()
    
    chart_generator = AtlasTimelineChart(args.timeline_file)
    chart_generator.generate_html_report(args.output_file)