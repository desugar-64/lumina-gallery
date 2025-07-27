# Bucket-Based Atlas Management - Detailed Implementation Plan

## Overview
Replace the current `currentAtlases: MutableMap<LODLevel, List<TextureAtlas>>` with a bucket-based system that provides bounded memory usage and context-aware atlas management.

## Current State Analysis
- **Storage**: `currentAtlases: MutableMap<LODLevel, List<TextureAtlas>>`
- **L0 Cache**: `persistentCache: List<TextureAtlas>?` (never cleared)
- **L7 Cleanup**: `cleanupL7AtlasSync()` already exists for selected photo cleanup
- **Context Tracking**: activeCell via `focusedCellWithMedia`, selectedMedia via `selectedMedia`
- **Problem**: After removing `cleanupRedundantAtlases`, we never clean up generated atlases = unlimited memory growth

## Proposed Bucket Architecture

### **Bucket Structure**
```kotlin
class AtlasBucketManager {
    private val l0Bucket: AtlasBucket           // Never cleared
    private val selectedCellBucket: AtlasBucket   // Cleared on cell change/deselect
    private val selectedPhotoBucket: AtlasBucket  // Cleared on photo change/deselect  
    private val generalBucket: AtlasBucket       // FIFO with 2+ LOD levels
}
```

### **1. L0 Bucket (Persistent)**
- **Purpose**: All canvas photos at LEVEL_0 (existing `persistentCache`)
- **Lifecycle**: Never cleared, initialized at app startup
- **Capacity**: Unlimited
- **LOD**: Always LEVEL_0

### **2. Selected Cell Bucket**
- **Purpose**: Current active cell at +1 LOD level  
- **Lifecycle**: Cleared when `focusedCellWithMedia` changes/nulls or zoom triggers LOD change
- **Context**: Maps to `AtlasPriority.ActiveCell`
- **LOD**: Always +1 from visible LOD level (via `getHigherLOD()`)

### **3. Selected Photo Bucket**
- **Purpose**: Explicitly selected photo at maximum quality
- **Lifecycle**: Cleared when `selectedMedia` changes/nulls or mode switches to CELL_MODE
- **Context**: Maps to `AtlasPriority.SelectedPhoto`
- **LOD**: Always LEVEL_7

### **4. General Bucket (Multi-LOD)**
- **Purpose**: Fallback atlases for smooth transitions, maintains 2+ LOD levels
- **Lifecycle**: FIFO eviction when capacity exceeded
- **Context**: Maps to `AtlasPriority.VisibleCells` and overflow from other buckets
- **Strategy**: Keep recent generations closest to current zoom

## Implementation Steps
1. Create `AtlasBucketManager` class to replace current `currentAtlases` storage
2. Implement individual bucket classes with capacity limits and eviction policies
3. Update atlas storage methods in `generateLODIndependently()` to use buckets
4. Update atlas retrieval methods (`getBestPhotoRegion()`, `getCurrentAtlases()`) for cross-bucket search
5. Add context-aware cleanup triggers based on UI state changes
6. Modify existing cleanup methods (extend `cleanupL7AtlasSync` pattern)
7. Update `applyUpfrontDeduplication()` to work across all buckets

## Need Clarification On:

### **1. Bucket Capacity Limits**
- How many atlases per LOD level? - as much as needed to fit photos of current target
- **General Bucket**: How many LOD levels to maintain? (You said "at least two" - should it be 2-3 levels?)

### **2. General Bucket LOD Strategy**
Which LODs should the general bucket prioritize?
- Keep the most recent 2-3 LOD levels generated? - Answer: In general bucket keep current lod + previous. In case new lod generated make current as previous and new as current. Bucket stack or queue content should be sorted from higher to lower low

### **3. Bucket Transitions & Movement**
- When a selected cell becomes general (deselected), do we move its atlas to general bucket? - Answer: Remove, as lower level lod will be triggered to generate.
- Do we allow duplicates across buckets temporarily during transitions? - Answer: Yes
- How do we handle the case where general bucket already has the same atlas? - I'd suggest ignore, and allow duplication for now. 
- Should we use reference counting or just duplicate atlases across buckets? - Allow duplicate.

### **4. Context Change Triggers**
What exactly triggers bucket clearing?
- **Selected Cell**: Clear/Replace when `focusedCellWithMedia` changes or becomes null? - Answer: Yes
- **Selected Cell**: Clear/Replace when LOD changes due to zoom (activeCell gets +1 LOD, so zoom 1.0→2.0 changes L2→L3)? - Answer: Yes
- **Selected Photo**: Clear/Replace when `selectedMedia` changes or becomes null? - Answer: Yes
- **Selected Photo**: Clear/Replace when switching from PHOTO_MODE to CELL_MODE? - Answer: Yes
- **Memory pressure**: Should we have emergency cleanup across all buckets? - Answer: Ignore, let it crash.

### **5. Cross-Bucket Atlas Lookup**
How should `getBestPhotoRegion()` search across buckets?
- **Search Order**: Selected Photo → Selected Cell → General → L0? - Answer: Correct
- **Fallback Strategy**: If photo not found in higher-quality buckets, check lower ones? - Answer: Correct
- **Performance**: Should we maintain a unified lookup index across all buckets? - Answer: Not sure what that mean, probably yes?
- **Caching**: Should we cache lookup results to avoid repeated bucket searches? - Answer: No.

### **6. Memory Management During Transitions**
How do we handle temporary memory spikes?
- When switching selected cell, do we clear old before generating new (gap) or generate new before clearing old (spike)? - Answer: Generate new and than replace the bucket content.
- When zooming triggers LOD change, do we generate new before clearing old? - Answer: Same answer as previous
- Should we have memory pressure detection to force early cleanup? - Answer: Omit, not important yet.
- What's the maximum acceptable memory overhead during transitions? - Answer: Ignore.

### **7. Bucket Persistence & App Lifecycle**
Which buckets survive app lifecycle events?
- **L0 Bucket**: Always persistent (current behavior) - Answer: Yes
- **Other Buckets**: Clear on app background/foreground? - Answer: First clear regular bucker but make sure UI rendering will fallback to L0 and not display gray rect placeholder.
- **Recovery**: How do we rebuild context buckets after app restart? - Answer: L0, and in parallel lods for current zoom or cell/item state.
- **State saving**: Should we persist selected cell/photo context across app restarts? - Answer: No.

### **8. Atlas Sharing & Duplication**
How do we handle atlases that could belong to multiple buckets? - Answer: Allow duplication, as handling different lods might be complex and cause bugs.
- Example: Active cell atlas might also be useful as general fallback - Answer: Active cell bucket should contain only photos from the cell, but if fallback searches for that photo we can use it.
- Example: Selected photo might be in a cell that's also active - Answer: Selected photo is highest lod, allow duplication.
- Do we use reference counting or allow duplication? - Answer: No.
- Memory vs complexity tradeoff preference? - Answer: Ignore.

### **9. Thread Safety & Performance**
- **Locking Strategy**: Individual bucket mutexes vs global atlas mutex (current `atlasMutex`)? - Answer: Make sure modifications from multiple threads dont cause issues.
- **Concurrent Access**: How do we handle multiple LOD generations accessing buckets simultaneously? - Answer: Yes.
- **Lock Granularity**: Fine-grained per-bucket vs coarse-grained manager-level? - Answer: Fine-grained, but you evaluate.

### **10. Eviction & Cleanup Policies**
- **General Bucket**: FIFO vs LRU eviction for general bucket? - Answer: FIFO. But keep content sorted by LOD, from higher to lower.
- **Cleanup Timing**: Immediate cleanup on context change vs batched/delayed cleanup? - Answer: Inset new and clean up old, if you meant bitmap recycle for atlas textures.
- **Partial Cleanup**: Should we support partial bucket clearing (e.g., remove only specific LOD levels)? - Answer: Clean only stuff we need to free room to insert new.

### **11. Monitoring & Debugging**
- **Logging Level**: How detailed should bucket operation logging be? - Answer: Log main functions.
- **Metrics**: Should we track hit/miss rates per bucket for optimization? - Answer: No.
- **Debug Overlay**: Should we extend existing atlas debug overlay to show bucket status? - Answer: Yes.
- **Memory Tracking**: Should we track memory usage per bucket for monitoring? - Answer: No.

### **12. Integration with Existing Systems**
- **AtlasPriority Mapping**: How do we map existing `AtlasPriority` enum to buckets? - Answer: If no need in priority mapping, just delete it.
- **Stream Results**: How do we update `AtlasStreamResult` events for bucket operations? - Answer: Collect all buckets content and send as result to gallery ui state. Or you can decide better option.
- **Upfront Deduplication**: How does `applyUpfrontDeduplication()` work across buckets? - Answer: No, but need keep logic to avoid unnecessary atlas generation(e.g. moving little or zooming little should not trigger regeneration.). Regen only when new lod requested or visible cells changed.
- **Legacy Compatibility**: Do we need to maintain compatibility with existing `currentAtlases` API? - Answer: No, remove legacy.

## Expected Benefits
- ✅ Bounded memory usage per context
- ✅ Predictable cleanup based on user interactions  
- ✅ Preserved LOD chain for smooth transitions
- ✅ Clear semantic boundaries for atlas lifecycle
- ✅ Elimination of complex cleanup logic like `cleanupRedundantAtlases`
- ✅ User interaction-driven memory management
- ✅ Better cache hit rates through context-aware storage
- 