#!/usr/bin/env python3
"""
Optimization Progress Tracker

Tracks multiple benchmark runs within a single optimization task folder.
Each optimization (e.g., "bitmap_pooling") gets its own folder where you can
accumulate multiple benchmark results and compare them over time.

Usage:
    python optimization_tracker.py collect bitmap_pooling benchmark.json
    python optimization_tracker.py compare bitmap_pooling
    python optimization_tracker.py list bitmap_pooling
"""

import json
import datetime
import os
import subprocess
import shutil
from pathlib import Path
from typing import Dict, List, Any, Optional

# ANSI color codes
class Colors:
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    GRAY = '\033[90m'
    WHITE = '\033[97m'
    BOLD = '\033[1m'
    RESET = '\033[0m'

class OptimizationTracker:
    def __init__(self, results_dir: str = "benchmark_results"):
        self.results_dir = Path(results_dir)
        self.results_dir.mkdir(exist_ok=True)
    
    def collect_run(self, optimization_name: str, benchmark_json_path: str) -> Dict[str, Any]:
        """Collect a new benchmark run for the optimization"""
        if not Path(benchmark_json_path).exists():
            raise FileNotFoundError(f"Benchmark file not found: {benchmark_json_path}")
        
        # Create/ensure optimization directory exists
        optimization_dir = self.results_dir / optimization_name
        optimization_dir.mkdir(exist_ok=True)
        
        # Generate filename with run number
        existing_runs = len(list(optimization_dir.glob("*.json")))
        run_number = existing_runs + 1
        
        git_commit = self._get_git_commit()
        timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
        
        # Create descriptive filename: optimization_run{N}_timestamp_commit.json
        filename = f"{optimization_name}_run{run_number:02d}_{timestamp}_{git_commit[:8]}.json"
        destination_path = optimization_dir / filename
        
        # Copy benchmark file
        shutil.copy2(benchmark_json_path, destination_path)
        
        # Load and parse the benchmark data for summary
        with open(benchmark_json_path) as f:
            benchmark_data = json.load(f)
        
        result_summary = {
            "file_path": str(destination_path),
            "optimization_name": optimization_name,
            "run_number": run_number,
            "timestamp": timestamp,
            "git_commit": git_commit,
            "device_context": self._extract_device_context(benchmark_data),
            "benchmark_count": len(benchmark_data.get("benchmarks", [])),
            "benchmark_names": [b.get("name", "unknown") for b in benchmark_data.get("benchmarks", [])]
        }
        
        self._print_collection_summary(result_summary, existing_runs)
        
        return result_summary
    
    def compare_optimization_runs(self, optimization_name: str, show_all_metrics: bool = False):
        """Compare all runs within an optimization folder"""
        optimization_dir = self.results_dir / optimization_name
        if not optimization_dir.exists():
            print(f"❌ No optimization folder found: {optimization_name}")
            return
        
        # Get all JSON files sorted by creation time
        json_files = sorted(optimization_dir.glob("*.json"), key=lambda x: x.stat().st_mtime)
        if not json_files:
            print(f"📭 No benchmark files found in {optimization_name}")
            return
        
        print(f"\n{Colors.BOLD}Optimization Progress: {optimization_name}{Colors.RESET}")
        print("=" * 100)
        
        # Load all benchmark data
        run_data = []
        for json_file in json_files:
            try:
                with open(json_file) as f:
                    data = json.load(f)
                    data['_file_name'] = json_file.name
                    run_data.append(data)
            except (json.JSONDecodeError, IOError) as e:
                print(f"⚠️  Warning: Could not load {json_file.name}: {e}")
                continue
        
        if not run_data:
            print("❌ No valid benchmark data found")
            return
        
        # Check device consistency and show warning if needed
        self._check_device_consistency(run_data)
        
        # Extract all metrics from first run to establish structure
        first_run = run_data[0]
        all_metrics = self._extract_all_metrics_from_run(first_run)
        
        if not all_metrics:
            print("❌ No metrics found in benchmark data")
            return
        
        # Filter metrics if requested
        if not show_all_metrics:
            # Show only time metrics by default (most important for optimization)
            time_metrics = {k: v for k, v in all_metrics.items() if k.endswith('SumMs')}
            if time_metrics:
                all_metrics = time_metrics
        
        # Print header with fixed spacing
        header_parts = [f"{'Metric':<50}"]
        for i, data in enumerate(run_data):
            header_parts.append(f"{'Run' + str(i+1):>12}")
            if i > 0:
                header_parts.append(f"{'Change':>18}")
        print("".join(header_parts))
        
        # Print separator
        separator_length = 50 + len(run_data) * 12 + (len(run_data) - 1) * 18
        print("-" * separator_length)
        
        # Print each metric
        for metric_name, (benchmark_name, _) in all_metrics.items():
            display_name = metric_name
            if len(display_name) > 48:
                display_name = display_name[:45] + "..."
            
            # Start with metric name
            row_parts = [f"{display_name:<50}"]
            
            # Get baseline value (first run)
            baseline_value = self._get_metric_value(run_data[0], benchmark_name, metric_name)
            
            for i, data in enumerate(run_data):
                current_value = self._get_metric_value(data, benchmark_name, metric_name)
                
                # Format the value with color and fixed width
                colored_value = self._format_value(current_value, metric_name)
                row_parts.append(colored_value)
                
                if i > 0:  # Add change indicator for runs after first
                    change_text = self._format_change_indicator(current_value, baseline_value, metric_name)
                    if change_text.strip():
                        row_parts.append(change_text)
                    else:
                        row_parts.append(f"{'':<18}")
            
            print("".join(row_parts))
        
        # Print summary
        print("-" * separator_length)
        self._print_optimization_summary(run_data, all_metrics)
    
    def _check_device_consistency(self, run_data: List[Dict]):
        """Check if all runs were on the same device and warn if not"""
        if len(run_data) < 2:
            return
        
        # Extract device info from all runs
        devices = []
        for data in run_data:
            context = data.get("context", {})
            build = context.get("build", {})
            
            device_info = {
                "model": build.get("model", "unknown"),
                "brand": build.get("brand", "unknown"), 
                "android_sdk": build.get("version", {}).get("sdk", 0),
                "cpu_cores": context.get("cpuCoreCount", 0),
                "cpu_max_freq": context.get("cpuMaxFreqHz", 0)
            }
            devices.append(device_info)
        
        # Check if all devices are the same
        first_device = devices[0]
        all_same = True
        differences = []
        
        for i, device in enumerate(devices[1:], 1):
            if device["model"] != first_device["model"]:
                differences.append(f"Run {i+1}: {device['brand']} {device['model']} vs Run 1: {first_device['brand']} {first_device['model']}")
                all_same = False
            elif device["android_sdk"] != first_device["android_sdk"]:
                differences.append(f"Run {i+1}: Android SDK {device['android_sdk']} vs Run 1: Android SDK {first_device['android_sdk']}")
                all_same = False
            elif abs(device["cpu_max_freq"] - first_device["cpu_max_freq"]) > 100000000:  # 100MHz tolerance
                differences.append(f"Run {i+1}: CPU {device['cpu_max_freq']//1000000}MHz vs Run 1: CPU {first_device['cpu_max_freq']//1000000}MHz")
                all_same = False
        
        if not all_same:
            print(f"{Colors.YELLOW}⚠️  DEVICE INCONSISTENCY WARNING{Colors.RESET}")
            print(f"{Colors.YELLOW}   Results from different hardware may not be comparable:{Colors.RESET}")
            for diff in differences:
                print(f"{Colors.YELLOW}   • {diff}{Colors.RESET}")
            print(f"{Colors.YELLOW}   Consider running all tests on the same device for accurate comparison.{Colors.RESET}")
            print()
        else:
            # Show device info for consistency
            device = first_device
            print(f"📱 Device: {device['brand']} {device['model']} (Android SDK {device['android_sdk']}, {device['cpu_cores']} cores)")
            print()
    
    def list_optimization_runs(self, optimization_name: str):
        """List all runs for an optimization"""
        optimization_dir = self.results_dir / optimization_name
        if not optimization_dir.exists():
            print(f"❌ No optimization folder found: {optimization_name}")
            return
        
        json_files = sorted(optimization_dir.glob("*.json"), key=lambda x: x.stat().st_mtime)
        if not json_files:
            print(f"📭 No runs found for optimization: {optimization_name}")
            return
        
        print(f"\n📊 Runs for optimization: {Colors.BOLD}{optimization_name}{Colors.RESET}")
        print("=" * 80)
        
        for i, json_file in enumerate(json_files):
            # Extract info from filename
            parts = json_file.stem.split('_')
            run_number = parts[1] if len(parts) > 1 and parts[1].startswith('run') else f"run{i+1:02d}"
            timestamp = parts[2] if len(parts) > 2 else "unknown"
            git_commit = parts[3] if len(parts) > 3 else "unknown"
            
            # Format timestamp for display
            try:
                dt = datetime.datetime.strptime(timestamp, '%Y%m%d_%H%M%S')
                formatted_time = dt.strftime('%Y-%m-%d %H:%M:%S')
            except:
                formatted_time = timestamp
            
            file_size = json_file.stat().st_size / 1024  # KB
            
            print(f"{run_number:>6} | {formatted_time} | {git_commit:>10} | {file_size:>6.1f}KB | {json_file.name}")
        
        print("=" * 80)
        print(f"Total runs: {len(json_files)}")
    
    def _extract_all_metrics_from_run(self, benchmark_data: Dict) -> Dict[str, tuple]:
        """Extract all metrics from a benchmark run"""
        all_metrics = {}
        
        benchmarks = benchmark_data.get("benchmarks", [])
        for benchmark in benchmarks:
            benchmark_name = benchmark.get("name", "unknown")
            metrics = benchmark.get("metrics", {})
            
            for metric_name in metrics.keys():
                all_metrics[metric_name] = (benchmark_name, metric_name)
        
        return all_metrics
    
    def _get_metric_value(self, benchmark_data: Dict, benchmark_name: str, metric_name: str) -> float:
        """Extract metric value from benchmark data"""
        benchmarks = benchmark_data.get("benchmarks", [])
        for benchmark in benchmarks:
            if benchmark.get("name") == benchmark_name:
                metrics = benchmark.get("metrics", {})
                if metric_name in metrics:
                    return metrics[metric_name].get("median", 0.0)
        return 0.0
    
    def _format_value(self, value: float, metric_name: str) -> str:
        """Format a value for display with fixed width padding"""
        if value < 1:
            formatted = f"{value:.3f}"
        elif value < 10:
            formatted = f"{value:.2f}"
        else:
            formatted = f"{value:.1f}"
        
        # Pad to fixed width (12 characters) and add color
        padded = f"{formatted:>12}"
        return f"{Colors.WHITE}{padded}{Colors.RESET}"
    
    def _format_change_indicator(self, current: float, baseline: float, metric_name: str) -> str:
        """Format change with absolute difference and percentage following the specified rules"""
        if baseline == 0:
            return ""
        
        if current == baseline:
            padded_zero = f"{'0.0':>18}"
            return f"{Colors.GRAY}{padded_zero}{Colors.RESET}"
        
        # Calculate absolute difference and percentage change
        abs_diff = current - baseline
        percent_change = (abs_diff / baseline) * 100
        
        # For time metrics, determine if this is faster (positive improvement) or slower (negative)
        is_time_metric = metric_name.endswith('SumMs')
        
        if is_time_metric:
            # For time metrics: lower values are better (faster)
            # So negative abs_diff = faster (positive change), positive abs_diff = slower (negative change)
            is_faster = abs_diff < 0
            is_slower = abs_diff > 0
        else:
            # For non-time metrics, we'll treat it more neutrally
            is_faster = False
            is_slower = False
        
        # Apply color rules
        if is_time_metric:
            if is_faster:  # Faster (improvement)
                abs_percent = abs(percent_change)
                if abs_percent < 15:
                    color = Colors.GRAY  # Insignificant, likely within margin of error
                else:
                    color = Colors.GREEN  # Meaningful speedup (≥15%)
                arrow = "↓"
            else:  # Slower (regression)
                abs_percent = abs(percent_change)
                if abs_percent < 15:
                    color = Colors.YELLOW  # Minor slowdown
                elif abs_percent >= 30:
                    color = Colors.RED  # Significant slowdown
                else:  # 15-30%
                    color = Colors.RED  # Conservative: treat as significant
                arrow = "↑"
        else:
            # For non-time metrics, use neutral coloring
            if abs(percent_change) < 5:
                color = Colors.GRAY
                arrow = "≈"
            else:
                color = Colors.BLUE
                arrow = "↑" if abs_diff > 0 else "↓"
        
        # Format: +/-X.X (↑/↓Y%) with fixed width padding
        sign = "+" if abs_diff > 0 else ""
        change_text = f"{sign}{abs_diff:.1f} ({arrow}{abs(percent_change):.1f}%)"
        padded_change = f"{change_text:>18}"  # Fixed width 18 characters
        return f"{color}{padded_change}{Colors.RESET}"
    
    def _print_optimization_summary(self, run_data: List[Dict], all_metrics: Dict):
        """Print optimization progress summary"""
        if len(run_data) < 2:
            print("ℹ️  Need at least 2 runs to show progress")
            return
        
        first_run = run_data[0]
        latest_run = run_data[-1]
        
        print(f"\n{Colors.BOLD}Progress Summary (Run 1 → Run {len(run_data)}):{Colors.RESET}")
        
        # Find most significant improvements
        improvements = []
        for metric_name, (benchmark_name, _) in all_metrics.items():
            if not metric_name.endswith('SumMs'):  # Focus on time metrics
                continue
                
            first_value = self._get_metric_value(first_run, benchmark_name, metric_name)
            latest_value = self._get_metric_value(latest_run, benchmark_name, metric_name)
            
            if first_value > 0:
                change_percent = ((latest_value - first_value) / first_value) * 100
                improvements.append((metric_name, first_value, latest_value, change_percent))
        
        if not improvements:
            print("No time metrics found for comparison")
            return
        
        # Sort by improvement (most improved first)
        improvements.sort(key=lambda x: x[3])  # Sort by change_percent
        
        # Show header for key changes table
        print(f"\n{'Metric':<50}{'Before':>12}{'After':>12}{'Change':>18}")
        print("-" * (50 + 12 + 12 + 18))
        
        # Show significant changes
        shown_any = False
        for metric_name, first_val, latest_val, change_pct in improvements:
            if abs(change_pct) > 1:  # Only show significant changes
                # Use the same formatting rules as the main table
                change_display = self._format_change_indicator(latest_val, first_val, metric_name)
                
                # Truncate metric name if too long
                display_name = metric_name if len(metric_name) <= 48 else metric_name[:45] + "..."
                
                # Format values with fixed width and color (consistent with main table)
                before_val = f"{Colors.WHITE}{first_val:>12.1f}{Colors.RESET}"
                after_val = f"{Colors.WHITE}{latest_val:>12.1f}{Colors.RESET}"
                
                # Print with fixed layout
                row_parts = [
                    f"{display_name:<50}",
                    before_val,
                    after_val,
                    change_display
                ]
                print("".join(row_parts))
                shown_any = True
        
        if not shown_any:
            print("No significant changes detected (>1% threshold)")
        
        print("-" * (50 + 12 + 12 + 18))
    
    def _extract_device_context(self, benchmark_data: Dict) -> Dict[str, Any]:
        """Extract device and system context"""
        context = benchmark_data.get("context", {})
        build = context.get("build", {})
        
        return {
            "device_model": build.get("model", "unknown"),
            "device_brand": build.get("brand", "unknown"),
            "android_version": build.get("version", {}).get("sdk", 0),
        }
    
    def _get_git_commit(self) -> str:
        """Get current git commit hash"""
        try:
            result = subprocess.run(
                ['git', 'rev-parse', '--short', 'HEAD'], 
                capture_output=True, 
                text=True,
                cwd=Path(__file__).parent.parent
            )
            
            if result.returncode != 0:
                return "unknown"
            
            commit_hash = result.stdout.strip()
            
            # Check for uncommitted changes
            status_result = subprocess.run(
                ['git', 'status', '--porcelain'], 
                capture_output=True, 
                text=True,
                cwd=Path(__file__).parent.parent
            )
            
            if status_result.returncode == 0:
                uncommitted_lines = status_result.stdout.strip().split('\n') if status_result.stdout.strip() else []
                actual_changes = [line for line in uncommitted_lines if line and not 'benchmark_results/' in line]
                has_uncommitted = bool(actual_changes)
            else:
                has_uncommitted = False
            
            return f"{commit_hash}{'-dirty' if has_uncommitted else ''}"
            
        except Exception:
            return "unknown"
    
    def _print_collection_summary(self, summary: Dict[str, Any], existing_runs: int):
        """Print summary of collected run"""
        print(f"\n✅ Collected Run #{summary['run_number']} for optimization: {summary['optimization_name']}")
        print(f"📁 Stored in: {summary['file_path']}")
        print(f"📱 Device: {summary['device_context']['device_brand']} {summary['device_context']['device_model']}")
        print(f"🔄 Git commit: {summary['git_commit']}")
        print(f"🧪 Benchmarks: {summary['benchmark_count']} ({', '.join(summary['benchmark_names'])})")
        
        if existing_runs > 0:
            print(f"📊 Total runs in this optimization: {existing_runs + 1}")
            print(f"💡 Use 'compare {summary['optimization_name']}' to see progress")

def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="Track optimization progress with multiple benchmark runs")
    parser.add_argument("--results-dir", default="benchmark_results",
                       help="Directory containing results (default: benchmark_results)")
    
    subparsers = parser.add_subparsers(dest="command", help="Available commands")
    
    # Collect command
    collect_parser = subparsers.add_parser("collect", help="Collect benchmark run for optimization")
    collect_parser.add_argument("optimization_name", help="Name of optimization (e.g., bitmap_pooling)")
    collect_parser.add_argument("benchmark_file", help="Path to benchmark JSON file")
    
    # Compare command
    compare_parser = subparsers.add_parser("compare", help="Compare all runs within optimization")
    compare_parser.add_argument("optimization_name", help="Name of optimization")
    compare_parser.add_argument("--all-metrics", action="store_true", help="Show all metrics, not just time metrics")
    
    # List command
    list_parser = subparsers.add_parser("list", help="List all runs for optimization")
    list_parser.add_argument("optimization_name", help="Name of optimization")
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        return
    
    tracker = OptimizationTracker(args.results_dir)
    
    if args.command == "collect":
        result = tracker.collect_run(args.optimization_name, args.benchmark_file)
        if not result:
            exit(1)
    
    elif args.command == "compare":
        tracker.compare_optimization_runs(args.optimization_name, args.all_metrics)
    
    elif args.command == "list":
        tracker.list_optimization_runs(args.optimization_name)

if __name__ == "__main__":
    main()