# Fix for Duplicate Row Error in Heterogeneous Graph

## Problem
The error `INVALID_ARGUMENT: Row [00:23:12] in mac_prefix_nodes was already inserted in this transaction` occurs when multiple devices share the same attribute value (like MAC prefix "00:23:12"), and we try to create the same attribute node multiple times within the same transaction.

## Root Cause
In the heterogeneous graph model, when processing multiple devices that share the same attribute value:
- Device A has MAC prefix "00:23:12" → Try to create mac_prefix_nodes row for "00:23:12"
- Device B has MAC prefix "00:23:12" → Try to create mac_prefix_nodes row for "00:23:12" again
- This causes a duplicate key error within the same transaction

## Solutions Implemented

### Solution 1: INSERT OR UPDATE (HeterogeneousGraphCommunityDetectionServiceImpl.java)
**Approach**: Use `Mutation.newInsertOrUpdateBuilder()` instead of `Mutation.newInsertBuilder()`

**Pros**:
- Simple fix with minimal code changes
- Handles duplicates gracefully
- Works for both attribute nodes and edges
- Uses official Spanner API method

**Cons**:
- Less efficient - still attempts to insert duplicates
- More mutations in the transaction

**Code Changes**:
```java
// Before (causes duplicate error)
mutations.add(createAttributeNodeMutation("mac_prefix_nodes", device.getMacPrefix(), now));

// After (handles duplicates gracefully)
mutations.add(createAttributeNodeMutationWithIgnore("mac_prefix_nodes", device.getMacPrefix(), now));

private Mutation createAttributeNodeMutationWithIgnore(String tableName, String attributeValue, Instant timestamp) {
    String columnName = getAttributeColumnNameForTable(tableName);
    return Mutation.newInsertOrUpdateBuilder(tableName)  // INSERT OR UPDATE
        .set(columnName).to(attributeValue)
        .set("created_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(timestamp.toEpochMilli() * 1000))
        .build();
}
```

### Solution 2: Collect Unique Values First (OptimizedHeterogeneousGraphCommunityDetectionServiceImpl.java)
**Approach**: Collect all unique attribute values first, then create nodes only for unique values

**Pros**:
- More efficient - no duplicate attempts
- Fewer mutations in transaction
- Better performance for large datasets

**Cons**:
- More complex implementation
- Requires additional memory for collecting unique values

**Code Changes**:
```java
// Collect unique attribute values first
Set<String> uniqueMacPrefixes = new HashSet<>();
for (Device device : devices) {
    if (device.getMacPrefix() != null && !device.getMacPrefix().isEmpty()) {
        uniqueMacPrefixes.add(device.getMacPrefix());
    }
}

// Create attribute nodes (one per unique value)
for (String macPrefix : uniqueMacPrefixes) {
    mutations.add(createAttributeNodeMutation("mac_prefix_nodes", macPrefix, now));
}

// Create device-attribute edges
for (Device device : devices) {
    if (device.getMacPrefix() != null && !device.getMacPrefix().isEmpty()) {
        mutations.add(createDeviceAttributeEdgeMutation("device_mac_prefix_edges", 
            device.getDeviceId(), device.getMacPrefix(), now));
    }
}
```

## Recommendation

**Use Solution 1 (INSERT OR UPDATE)** for:
- Quick fixes
- Small to medium datasets
- When simplicity is preferred

**Use Solution 2 (Collect Unique Values)** for:
- Large datasets (1M+ devices)
- Performance-critical applications
- When you want optimal efficiency

## Example Scenario

**Input Devices**:
```
Device D1: MAC prefix = "00:23:12"
Device D2: MAC prefix = "00:23:12"  (same as D1)
Device D3: MAC prefix = "AA:BB:CC"
```

**Solution 1 Result**:
```
mac_prefix_nodes:
- "00:23:12" (created once, second attempt ignored)
- "AA:BB:CC" (created)

device_mac_prefix_edges:
- D1 → "00:23:12"
- D2 → "00:23:12"
- D3 → "AA:BB:CC"
```

**Solution 2 Result**:
```
mac_prefix_nodes:
- "00:23:12" (created once)
- "AA:BB:CC" (created)

device_mac_prefix_edges:
- D1 → "00:23:12"
- D2 → "00:23:12"
- D3 → "AA:BB:CC"
```

Both solutions produce the same final result, but Solution 2 is more efficient for large datasets.

## Files Modified

1. **HeterogeneousGraphCommunityDetectionServiceImpl.java**:
   - Added `createAttributeNodeMutationWithIgnore()` method
   - Added `createDeviceAttributeEdgeMutationWithIgnore()` method
   - Updated `createAttributeNodesAndEdges()` to use INSERT OR UPDATE

2. **OptimizedHeterogeneousGraphCommunityDetectionServiceImpl.java**:
   - New optimized implementation
   - Collects unique attribute values first
   - Creates nodes only for unique values
   - More efficient for large datasets

## Testing

To test the fix:
1. Use the original implementation with INSERT OR UPDATE for small datasets
2. Use the optimized implementation for large datasets
3. Both should handle duplicate attribute values without errors

The heterogeneous graph model now properly handles devices with shared attribute values without causing duplicate row errors.
