#!/usr/bin/env python3
"""
Atlas Benchmark Results Collector

Collects benchmark JSON results and maintains a timeline database
for tracking atlas texture system performance over time.

Usage:
    python atlas_benchmark_collector.py benchmark.json "optimization_name"
"""

import json
import datetime
import os
import subprocess
from pathlib import Path
from typing import Dict, List, Any, Optional

class AtlasBenchmarkCollector:
    def __init__(self, results_dir: str = "benchmark_results", profile: str = "atlas"):
        self.results_dir = Path(results_dir)
        self.results_dir.mkdir(exist_ok=True)
        self.profile = profile
        
        # Load profile configuration
        self._load_profile(profile)
        
        # Set timeline file based on profile
        self.timeline_file = self.results_dir / f"{profile}_timeline.json"
    
    def _load_profile(self, profile_name: str):
        """Load benchmark profile configuration"""
        if profile_name == "atlas":
            # Atlas texture system optimization profile
            self.metrics = {
                # Primary optimization targets
                "PhotoLODProcessor.scaleBitmapSumMs": "Bitmap scaling operations",
                "AtlasGenerator.softwareCanvasSumMs": "Software canvas rendering", 
                
                # Supporting atlas metrics
                "PhotoLODProcessor.loadBitmapSumMs": "Bitmap loading I/O",
                "AtlasGenerator.createAtlasBitmapSumMs": "Atlas bitmap creation",
                "AtlasManager.updateVisibleCellsSumMs": "Atlas coordination",
                "AtlasManager.generateAtlasSumMs": "Atlas generation total",
                "AtlasManager.selectLODLevelSumMs": "LOD level selection",
                
                # Disk I/O Operations (File System Access)
                "PhotoLODProcessor.diskOpenInputStreamSumMs": "Disk I/O - ContentResolver file access",
                "PhotoLODProcessor.diskReadFileHeaderSumMs": "Disk I/O - File header reading",
                
                # Memory I/O Operations (Bitmap Processing in RAM)  
                "PhotoLODProcessor.memoryDecodeBoundsSumMs": "Memory I/O - Bitmap bounds decoding",
                "PhotoLODProcessor.memoryDecodeBitmapSumMs": "Memory I/O - Full bitmap decoding",
                "PhotoLODProcessor.memorySampleSizeCalcSumMs": "Memory I/O - Sample size calculation",
                
                # Hardware-accelerated scaling operations
                "PhotoScaler.scaleSumMs": "PhotoScaler main operations",
                "PhotoScaler.createScaledBitmapSumMs": "Hardware-accelerated bitmap scaling",
                "PhotoScaler.createCroppedBitmapSumMs": "Bitmap cropping operations",
                "PhotoScaler.calculateDimensionsSumMs": "Size calculation algorithms",
                
                # Memory management operations
                "Atlas.bitmapAllocateSumMs": "Bitmap memory allocation",
                "Atlas.bitmapRecycleSumMs": "Bitmap memory recycling",
                "Atlas.atlasCleanupSumMs": "Atlas memory cleanup",
                "Atlas.processedPhotoCleanupSumMs": "Processed photo cleanup",
                
                # Texture packing algorithm performance
                "TexturePacker.packAlgorithmSumMs": "Main texture packing algorithm",
                "TexturePacker.sortImagesSumMs": "Image sorting by height",
                "TexturePacker.packSingleImageSumMs": "Individual image packing",
                "TexturePacker.findShelfFitSumMs": "Shelf fitting algorithm",
                "TexturePacker.createNewShelfSumMs": "New shelf creation"
            }
            self.target_time_ms = 1000.0
            self.baseline_time_ms = 5000.0
        else:
            # Future profiles can be added here when needed
            available_profiles = ["atlas"]
            raise ValueError(f"Unknown profile '{profile_name}'. Available: {available_profiles}")
        
        # Common metrics for all profiles
        self.frame_metrics = [
            "frameDurationCpuMs",
            "frameOverrunMs"
        ]
        
        self.memory_metrics = [
            "memoryGpuMaxKb",
            "memoryHeapSizeMaxKb", 
            "memoryRssAnonMaxKb",
            "memoryRssFileMaxKb"
        ]
    
    def collect_benchmark_result(self, benchmark_json_path: str, optimization_name: str, 
                                 allow_dirty: bool = False) -> Dict[str, Any]:
        """Collect a new benchmark result and add to timeline"""
        if not Path(benchmark_json_path).exists():
            raise FileNotFoundError(f"Benchmark file not found: {benchmark_json_path}")
        
        # Check git state and warn about uncommitted changes
        git_commit = self._get_git_commit()
        if "-dirty" in git_commit and not allow_dirty:
            print("⚠️  WARNING: You have uncommitted changes!")
            print("   This benchmark result may not be reproducible.")
            print("   Consider committing your changes first, or use --allow-dirty flag.")
            print(f"   Git state: {git_commit}")
            
            response = input("\nContinue anyway? (y/N): ").strip().lower()
            if response != 'y':
                print("❌ Benchmark collection cancelled.")
                return {}
        
        # Check for duplicate optimization names
        existing_timeline = self._load_timeline()
        existing_names = [entry["optimization"] for entry in existing_timeline]
        if optimization_name in existing_names:
            print(f"⚠️  WARNING: Optimization name '{optimization_name}' already exists!")
            print("   This will add a new entry, not overwrite the existing one.")
            print("   Existing entries:", existing_names)
            
            response = input(f"\nContinue with duplicate name '{optimization_name}'? (y/N): ").strip().lower()
            if response != 'y':
                suggested_name = f"{optimization_name}_v2"
                print(f"💡 Suggested alternative: '{suggested_name}'")
                return {}
        
        with open(benchmark_json_path) as f:
            benchmark_data = json.load(f)
        
        # Extract metrics from both test types
        zoom_metrics = self._extract_test_metrics(benchmark_data, "atlasGenerationThroughZoomInteractions")
        pan_metrics = self._extract_test_metrics(benchmark_data, "atlasGenerationThroughPanInteractions")
        
        # Create timeline entry
        timeline_entry = {
            "timestamp": datetime.datetime.now().isoformat(),
            "optimization": optimization_name,
            "device_context": self._extract_device_context(benchmark_data),
            "zoom_test": zoom_metrics,
            "pan_test": pan_metrics,
            "total_optimization_time": zoom_metrics["profile_metrics"].get("AtlasManager.generateAtlasSumMs", 0),
            "git_commit": git_commit,
            "benchmark_file": str(Path(benchmark_json_path).name)
        }
        
        # Load existing timeline
        timeline = self._load_timeline()
        timeline.append(timeline_entry)
        
        # Save updated timeline
        with open(self.timeline_file, 'w') as f:
            json.dump(timeline, f, indent=2)
        
        self._print_results_summary(timeline_entry)
        
        return timeline_entry
    
    def _extract_test_metrics(self, benchmark_data: Dict, test_name: str) -> Dict[str, Any]:
        """Extract metrics from a specific test"""
        test_result = None
        for benchmark in benchmark_data.get("benchmarks", []):
            if benchmark["name"] == test_name:
                test_result = benchmark
                break
        
        if not test_result:
            return {"found": False, "atlas_metrics": {}, "frame_metrics": {}, "memory_metrics": {}}
        
        # Extract profile metrics (trace sections)
        profile_metrics = {}
        for metric_name in self.metrics.keys():
            if metric_name in test_result["metrics"]:
                profile_metrics[metric_name] = test_result["metrics"][metric_name]["median"]
            else:
                profile_metrics[metric_name] = 0.0
        
        # Extract frame performance (from sampledMetrics)
        frame_metrics = {}
        sampled_metrics = test_result.get("sampledMetrics", {})
        for metric_name in self.frame_metrics:
            if metric_name in sampled_metrics:
                frame_metrics[metric_name] = {
                    "P50": sampled_metrics[metric_name].get("P50", 0),
                    "P90": sampled_metrics[metric_name].get("P90", 0),
                    "P99": sampled_metrics[metric_name].get("P99", 0)
                }
        
        # Extract memory metrics
        memory_metrics = {}
        for metric_name in self.memory_metrics:
            if metric_name in test_result["metrics"]:
                memory_metrics[metric_name] = test_result["metrics"][metric_name]["median"]
        
        return {
            "found": True,
            "total_runtime_ns": test_result.get("totalRunTimeNs", 0),
            "iterations": test_result.get("repeatIterations", 0),
            "profile_metrics": profile_metrics,
            "frame_metrics": frame_metrics,
            "memory_metrics": memory_metrics
        }
    
    def _extract_device_context(self, benchmark_data: Dict) -> Dict[str, Any]:
        """Extract device and system context"""
        context = benchmark_data.get("context", {})
        build = context.get("build", {})
        
        return {
            "device_model": build.get("model", "unknown"),
            "device_brand": build.get("brand", "unknown"),
            "android_version": build.get("version", {}).get("sdk", 0),
            "cpu_cores": context.get("cpuCoreCount", 0),
            "cpu_max_freq_ghz": context.get("cpuMaxFreqHz", 0) / 1_000_000_000,
            "memory_total_gb": context.get("memTotalBytes", 0) / (1024**3),
            "cpu_locked": context.get("cpuLocked", False),
            "compilation_mode": context.get("compilationMode", "unknown")
        }
    
    def _load_timeline(self) -> List[Dict]:
        """Load existing timeline or create empty one"""
        if self.timeline_file.exists():
            with open(self.timeline_file) as f:
                return json.load(f)
        return []
    
    def _get_git_commit(self) -> str:
        """Get current git commit hash with dirty state detection"""
        try:
            # Get current commit hash
            result = subprocess.run(
                ['git', 'rev-parse', '--short', 'HEAD'], 
                capture_output=True, 
                text=True,
                cwd=Path(__file__).parent.parent
            )
            
            if result.returncode != 0:
                return "unknown"
            
            commit_hash = result.stdout.strip()
            
            # Check for uncommitted changes (excluding benchmark results)
            status_result = subprocess.run(
                ['git', 'status', '--porcelain'], 
                capture_output=True, 
                text=True,
                cwd=Path(__file__).parent.parent
            )
            
            # Filter out changes in benchmark_results/ directory since those are expected outputs
            if status_result.returncode == 0:
                uncommitted_lines = status_result.stdout.strip().split('\n') if status_result.stdout.strip() else []
                # Only count changes that are NOT in benchmark_results/
                actual_changes = [line for line in uncommitted_lines if line and not 'benchmark_results/' in line]
                has_uncommitted = bool(actual_changes)
            else:
                has_uncommitted = False
            
            # Return commit with dirty indicator only for actual code changes
            return f"{commit_hash}{'-dirty' if has_uncommitted else ''}"
            
        except Exception:
            return "unknown"
    
    def _print_results_summary(self, entry: Dict[str, Any]):
        """Print summary of collected results"""
        print(f"\n✅ Collected benchmark result for: {entry['optimization']}")
        print(f"📱 Device: {entry['device_context']['device_brand']} {entry['device_context']['device_model']}")
        print(f"🔄 Git commit: {entry['git_commit']}")
        
        # Zoom test results (optimization performance)
        zoom = entry['zoom_test']
        if zoom['found']:
            total_time = zoom['profile_metrics'].get('AtlasManager.generateAtlasSumMs', 0)
            bitmap_scaling = zoom['profile_metrics'].get('PhotoLODProcessor.scaleBitmapSumMs', 0)
            canvas_rendering = zoom['profile_metrics'].get('AtlasGenerator.softwareCanvasSumMs', 0)
            bitmap_loading = zoom['profile_metrics'].get('PhotoLODProcessor.loadBitmapSumMs', 0)
            
            print(f"\n📊 {self.profile.title()} Performance (Zoom Test):")
            print(f"   Total Generation Time: {total_time:.1f}ms")
            print(f"   Bitmap Loading (I/O): {bitmap_loading:.1f}ms")
            print(f"   Bitmap Scaling: {bitmap_scaling:.1f}ms")
            print(f"   Canvas Rendering: {canvas_rendering:.1f}ms")
            
            # Memory usage
            gpu_memory = zoom['memory_metrics'].get('memoryGpuMaxKb', 0) / 1024
            heap_memory = zoom['memory_metrics'].get('memoryHeapSizeMaxKb', 0) / 1024
            print(f"   GPU Memory: {gpu_memory:.1f}MB")
            print(f"   Heap Memory: {heap_memory:.1f}MB")
        
        # Pan test results (UI performance)
        pan = entry['pan_test']
        if pan['found'] and pan['frame_metrics']:
            frame_duration = pan['frame_metrics'].get('frameDurationCpuMs', {})
            if frame_duration:
                print(f"\n🖼️ UI Performance (Pan Test):")
                print(f"   Frame Duration P50: {frame_duration.get('P50', 0):.1f}ms")
                print(f"   Frame Duration P90: {frame_duration.get('P90', 0):.1f}ms")
                print(f"   Frame Duration P99: {frame_duration.get('P99', 0):.1f}ms")
        
        timeline = self._load_timeline()
        print(f"\n🔄 Timeline entries: {len(timeline)}")
        
        if len(timeline) > 1:
            self._print_improvement_summary(timeline)
    
    def _print_improvement_summary(self, timeline: List[Dict]):
        """Print improvement summary compared to baseline"""
        if len(timeline) < 2:
            return
        
        # Use first entry as baseline
        baseline = timeline[0]
        latest = timeline[-1]
        
        baseline_atlas = baseline['zoom_test']['profile_metrics'].get('AtlasManager.generateAtlasSumMs', 0)
        latest_atlas = latest['zoom_test']['profile_metrics'].get('AtlasManager.generateAtlasSumMs', 0)
        
        if baseline_atlas > 0 and latest_atlas > 0:
            improvement = (baseline_atlas - latest_atlas) / baseline_atlas * 100
            print(f"\n📈 Improvement since baseline ({baseline['optimization']}):")
            print(f"   Atlas Generation: {baseline_atlas:.1f}ms → {latest_atlas:.1f}ms")
            print(f"   Improvement: {improvement:+.1f}%")
            
            # Primary optimization targets
            baseline_scaling = baseline['zoom_test']['profile_metrics'].get('PhotoLODProcessor.scaleBitmapSumMs', 0)
            latest_scaling = latest['zoom_test']['profile_metrics'].get('PhotoLODProcessor.scaleBitmapSumMs', 0)
            
            baseline_canvas = baseline['zoom_test']['profile_metrics'].get('AtlasGenerator.softwareCanvasSumMs', 0)
            latest_canvas = latest['zoom_test']['profile_metrics'].get('AtlasGenerator.softwareCanvasSumMs', 0)
            
            if baseline_scaling > 0:
                scaling_improvement = (baseline_scaling - latest_scaling) / baseline_scaling * 100
                print(f"   Bitmap Scaling: {scaling_improvement:+.1f}%")
            
            if baseline_canvas > 0:
                canvas_improvement = (baseline_canvas - latest_canvas) / baseline_canvas * 100
                print(f"   Canvas Rendering: {canvas_improvement:+.1f}%")
    
    def get_latest_results(self, count: int = 5) -> List[Dict]:
        """Get most recent benchmark results"""
        timeline = self._load_timeline()
        return timeline[-count:] if timeline else []
    
    def get_optimization_comparison(self, baseline_name: str = "baseline") -> Dict[str, Any]:
        """Compare current state against baseline"""
        timeline = self._load_timeline()
        if len(timeline) < 2:
            return {"error": "Need at least 2 benchmark results for comparison"}
        
        # Find baseline
        baseline = next((entry for entry in timeline if baseline_name in entry["optimization"].lower()), None)
        if not baseline:
            baseline = timeline[0]  # Use first entry as baseline
        
        latest = timeline[-1]
        
        comparison = {
            "baseline": baseline,
            "latest": latest,
            "improvements": {}
        }
        
        # Compare atlas metrics
        for metric in self.metrics.keys():
            baseline_time = baseline["zoom_test"]["profile_metrics"].get(metric, 0)
            latest_time = latest["zoom_test"]["profile_metrics"].get(metric, 0)
            
            if baseline_time > 0:
                improvement = (baseline_time - latest_time) / baseline_time * 100
                comparison["improvements"][metric] = {
                    "baseline_ms": baseline_time,
                    "latest_ms": latest_time,
                    "improvement_percent": improvement
                }
        
        return comparison
    
    def list_timeline_entries(self) -> None:
        """List all timeline entries with indices"""
        timeline = self._load_timeline()
        if not timeline:
            print("📭 No timeline entries found.")
            return
        
        print(f"📊 Timeline entries ({len(timeline)} total):")
        print("=" * 80)
        
        for i, entry in enumerate(timeline):
            timestamp = entry.get("timestamp", "unknown")[:19]  # Remove microseconds
            optimization = entry.get("optimization", "unknown")
            git_commit = entry.get("git_commit", "unknown")
            atlas_time = entry.get("total_optimization_time", entry.get("total_atlas_time", 0))
            device = entry.get("device_context", {}).get("device_model", "unknown")
            
            print(f"{i:2d}. {timestamp} | {optimization:20s} | {git_commit:12s} | {atlas_time:6.1f}ms | {device}")
        
        print("=" * 80)
    
    def remove_timeline_entries(self, indices: List[int], backup: bool = True, force: bool = False) -> None:
        """Remove timeline entries by index"""
        timeline = self._load_timeline()
        if not timeline:
            print("📭 No timeline entries to remove.")
            return
        
        # Validate indices
        invalid_indices = [i for i in indices if i < 0 or i >= len(timeline)]
        if invalid_indices:
            print(f"❌ Invalid indices: {invalid_indices}")
            print(f"   Valid range: 0-{len(timeline)-1}")
            return
        
        # Create backup if requested
        if backup:
            backup_file = self.results_dir / f"atlas_timeline_backup_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            with open(backup_file, 'w') as f:
                json.dump(timeline, f, indent=2)
            print(f"💾 Backup created: {backup_file}")
        
        # Show entries to be removed
        print("🗑️  Entries to be removed:")
        for i in sorted(indices):
            entry = timeline[i]
            print(f"   {i}: {entry['optimization']} ({entry.get('git_commit', 'unknown')})")
        
        # Confirm removal
        if not force:
            try:
                response = input(f"\nRemove {len(indices)} entries? (y/N): ").strip().lower()
                if response != 'y':
                    print("❌ Removal cancelled.")
                    return
            except EOFError:
                print("❌ Cannot prompt for confirmation in non-interactive mode. Use --force flag.")
                return
        
        # Remove entries (in reverse order to maintain indices)
        for i in sorted(indices, reverse=True):
            removed = timeline.pop(i)
            print(f"✅ Removed: {removed['optimization']}")
        
        # Save updated timeline
        with open(self.timeline_file, 'w') as f:
            json.dump(timeline, f, indent=2)
        
        print(f"📊 Timeline updated: {len(timeline)} entries remaining")
    
    def clean_experimental_entries(self, backup: bool = True, force: bool = False) -> None:
        """Remove all entries with '-dirty' git commits"""
        timeline = self._load_timeline()
        if not timeline:
            print("📭 No timeline entries found.")
            return
        
        dirty_indices = []
        for i, entry in enumerate(timeline):
            git_commit = entry.get("git_commit", "")
            if "-dirty" in git_commit:
                dirty_indices.append(i)
        
        if not dirty_indices:
            print("✨ No experimental (dirty) entries found.")
            return
        
        print(f"🧪 Found {len(dirty_indices)} experimental entries:")
        for i in dirty_indices:
            entry = timeline[i]
            print(f"   {i}: {entry['optimization']} ({entry.get('git_commit', 'unknown')})")
        
        self.remove_timeline_entries(dirty_indices, backup, force)

if __name__ == "__main__":
    import sys
    import argparse
    
    parser = argparse.ArgumentParser(description="Benchmark results manager")
    parser.add_argument("--results-dir", default="benchmark_results",
                       help="Directory to store results (default: benchmark_results)")
    parser.add_argument("--profile", default="atlas", 
                       help="Optimization profile (default: atlas)")
    
    subparsers = parser.add_subparsers(dest="command", help="Available commands")
    
    # Collect command
    collect_parser = subparsers.add_parser("collect", help="Collect benchmark results")
    collect_parser.add_argument("benchmark_file", help="Path to benchmark JSON file")
    collect_parser.add_argument("optimization_name", help="Name for this optimization")
    collect_parser.add_argument("--allow-dirty", action="store_true", 
                               help="Allow collection with uncommitted git changes")
    
    # List command
    list_parser = subparsers.add_parser("list", help="List timeline entries")
    
    # Remove command
    remove_parser = subparsers.add_parser("remove", help="Remove timeline entries by index")
    remove_parser.add_argument("indices", nargs="+", type=int, help="Indices to remove")
    remove_parser.add_argument("--no-backup", action="store_true", help="Skip backup creation")
    remove_parser.add_argument("--force", action="store_true", help="Skip confirmation prompt")
    
    # Clean command
    clean_parser = subparsers.add_parser("clean", help="Remove experimental (-dirty) entries")
    clean_parser.add_argument("--no-backup", action="store_true", help="Skip backup creation")
    clean_parser.add_argument("--force", action="store_true", help="Skip confirmation prompt")
    
    args = parser.parse_args()
    
    # Default to collect for backward compatibility
    if not args.command and len(sys.argv) >= 3:
        # Old format: script.py file.json "name"
        profile = "atlas"  # Default profile for backward compatibility
        collector = AtlasBenchmarkCollector(args.results_dir, profile)
        result = collector.collect_benchmark_result(
            sys.argv[1], sys.argv[2], 
            allow_dirty="--allow-dirty" in sys.argv
        )
        if not result:
            sys.exit(1)
        sys.exit(0)
    
    if not args.command:
        parser.print_help()
        sys.exit(1)
    
    collector = AtlasBenchmarkCollector(args.results_dir, args.profile)
    
    if args.command == "collect":
        result = collector.collect_benchmark_result(
            args.benchmark_file, 
            args.optimization_name, 
            allow_dirty=args.allow_dirty
        )
        if not result:
            sys.exit(1)
    
    elif args.command == "list":
        collector.list_timeline_entries()
    
    elif args.command == "remove":
        collector.remove_timeline_entries(args.indices, backup=not args.no_backup, force=args.force)
    
    elif args.command == "clean":
        collector.clean_experimental_entries(backup=not args.no_backup, force=args.force)