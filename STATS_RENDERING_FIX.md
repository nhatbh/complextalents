# Stats Rendering Fix - Accumulated Stat Display

## Problem
The `StatsTabUI` was not displaying the accumulated/purchased stat ranks when rendering the stat cards. The UI showed pending upgrades correctly but failed to load the actual player stat data.

### Root Cause
`StatsTabUI` was attempting to read stat data from the **client-side player capability** which had timing/synchronization issues:

```java
// OLD CODE - unreliable
player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(data -> {
    int realCurrentRank = data.getStatRank(type);
    // ...
});
```

The problem:
1. Client-side capabilities can have race conditions
2. The capability might not be fully initialized when the UI first renders
3. No persistent cache meant repeated lookups could fail

### How StatModifierApplier Gets the Data (Reference)
The server uses `StatModifierApplier` which applies modifiers directly from the **server-side capability** through `GeneralStatsData`:

```java
// Server-side (always works)
public static void applyStatModifier(Player player, StatType type, int rank) {
    // Uses server-side GeneralStatsData which is guaranteed to be available
    double totalValue = type.getYieldPerRank() * rank;
    // Applies attribute modifiers with the calculated value
}
```

The sync packet (`StatsDataSyncPacket`) sends this data to the client, but it was only updating the capability, not a client-side cache.

## Solution
Created a **dedicated client-side stats cache** that acts as a single source of truth for rendered UI:

### New Files Created

#### 1. `ClientStatsData.java` (Client Cache)
**Location**: `src/main/java/com/complextalents/stats/client/ClientStatsData.java`

Provides:
- `getStatRank(StatType)` - Get accumulated stat rank (replaces capability lookup)
- `updateStatRanks(Map<StatType, Integer>)` - Called by sync packet
- `getAllRanks()` - Get all stats at once
- `reset()` - Clear on disconnect

```java
// Client-side cache initialization
@OnlyIn(Dist.CLIENT)
public class ClientStatsData {
    private static final Map<StatType, Integer> statRanks = new EnumMap<>(StatType.class);

    public static int getStatRank(StatType type) {
        return statRanks.getOrDefault(type, 0);
    }
}
```

#### 2. `ClientStatsEventHandler.java` (Cleanup)
**Location**: `src/main/java/com/complextalents/stats/client/ClientStatsEventHandler.java`

Listens for player logout and resets the cache to prevent stale data.

### Modified Files

#### 1. `StatsDataSyncPacket.java`
**Change**: Updated `handleClientSide()` to use the new cache

```java
@OnlyIn(Dist.CLIENT)
private static void handleClientSide(Map<StatType, Integer> statRanks) {
    // Update client-side stats cache (NEW)
    com.complextalents.stats.client.ClientStatsData.updateStatRanks(statRanks);

    // Also update capability for compatibility
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc.player != null) {
        // ... existing code ...
    }
}
```

#### 2. `StatsTabUI.java`
**Change**: Replaced capability lookup with cache lookup

```java
// OLD
public void update() {
    statEntries.clear();
    player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(data -> {
        int realCurrentRank = data.getStatRank(type);
        // ...
    });
}

// NEW
public void update() {
    statEntries.clear();
    int realCurrentRank = com.complextalents.stats.client.ClientStatsData.getStatRank(type);
    // ...
}
```

## Data Flow

### Before Patch
```
Server: GeneralStatsData.setStatRank()
           ↓
StatsDataSyncPacket sent
           ↓
Client: Updates capability (unreliable)
           ↓
StatsTabUI reads capability (FAILS)
```

### After Patch
```
Server: GeneralStatsData.setStatRank()
           ↓
StatsDataSyncPacket sent
           ↓
Client: ClientStatsData.updateStatRanks() ← Single source of truth
           ↓
StatsTabUI reads cache (SUCCEEDS) ✓
           ↓
Stat values render correctly
```

## Key Benefits

1. **Reliable** - Client cache is updated before rendering, no race conditions
2. **Fast** - Direct map lookup instead of capability navigation
3. **Simple** - Clean separation between server authority and client cache
4. **Safe** - Cache resets on logout to prevent stale data between logins
5. **Compatible** - Server-side `StatModifierApplier` continues working unchanged

## Testing

The fix should be tested by:

1. Creating a new character and purchasing stats
2. Verify the accumulated stat value displays correctly in `StatsTabUI`
3. Log out and back in - stats should persist
4. Switch between accounts/players - cache should clear properly
5. Purchase more stats and verify the displayed accumulated value increases

## Technical Details

### Sync Packet Flow
When server modifies stats via `GeneralStatsData.setStatRank()`:

1. Server updates its internal `Map<StatType, Integer> ranks`
2. Server calls `sync()` which sends `StatsDataSyncPacket`
3. Packet is decoded on client with all stat ranks
4. `ClientStatsData.updateStatRanks()` is called
5. Client cache is now up-to-date
6. UI renders using `ClientStatsData.getStatRank()`

### Why This Works

The server never trusts the client for actual stat values. The client cache is **read-only** from the UI perspective - it only gets updated when the server sends new data. This maintains server authority while providing a reliable client-side rendering source.

### Comparison with StatModifierApplier

`StatModifierApplier` on the server:
- Reads from server-side `GeneralStatsData` capability
- Applies attribute modifiers to player
- Runs with complete information

`StatsTabUI` on the client (now):
- Reads from `ClientStatsData` cache
- Renders stat values and UI elements
- Uses synced data from server

Both are now consistent because they use the same data (synced from server).
