# Code Changes - Detailed View

## Overview
Fixed accumulated stat rendering by creating a client-side cache that's updated when sync packets arrive.

---

## File 1: NEW - `ClientStatsData.java`

**Location**: `src/main/java/com/complextalents/stats/client/ClientStatsData.java`

```java
package com.complextalents.stats.client;

import com.complextalents.stats.StatType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side cache for player stat data.
 * Updated when StatsDataSyncPacket is received from the server.
 */
@OnlyIn(Dist.CLIENT)
public class ClientStatsData {
    private static final Map<StatType, Integer> statRanks = new EnumMap<>(StatType.class);

    static {
        // Initialize with defaults
        for (StatType type : StatType.values()) {
            statRanks.put(type, 0);
        }
    }

    /**
     * Get the rank of a specific stat.
     * @param type The stat type
     * @return The rank/level of the stat, 0 if not set
     */
    public static int getStatRank(StatType type) {
        return statRanks.getOrDefault(type, 0);
    }

    /**
     * Update stat ranks from server sync packet.
     * Called by StatsDataSyncPacket.
     */
    public static void updateStatRanks(Map<StatType, Integer> newRanks) {
        statRanks.clear();
        for (StatType type : StatType.values()) {
            statRanks.put(type, newRanks.getOrDefault(type, 0));
        }
    }

    /**
     * Get all stat ranks.
     * @return A copy of all stat ranks
     */
    public static Map<StatType, Integer> getAllRanks() {
        return new EnumMap<>(statRanks);
    }

    /**
     * Clear all stat data (e.g., on disconnect).
     */
    public static void reset() {
        for (StatType type : StatType.values()) {
            statRanks.put(type, 0);
        }
    }
}
```

---

## File 2: NEW - `ClientStatsEventHandler.java`

**Location**: `src/main/java/com/complextalents/stats/client/ClientStatsEventHandler.java`

```java
package com.complextalents.stats.client;

import com.complextalents.TalentsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handler for stat data cleanup.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class ClientStatsEventHandler {

    /**
     * Reset client stats cache when player logs out.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientStatsData.reset();
    }
}
```

---

## File 3: MODIFIED - `StatsDataSyncPacket.java`

**Location**: `src/main/java/com/complextalents/stats/network/StatsDataSyncPacket.java`

### Change Summary
Updated the `handleClientSide()` method to update the new client cache **before** updating the capability.

### Before
```java
@OnlyIn(Dist.CLIENT)
private static void handleClientSide(Map<StatType, Integer> statRanks) {
    // Update client-side player stats capability
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc.player != null) {
        mc.player.getCapability(com.complextalents.stats.capability.GeneralStatsDataProvider.STATS_DATA)
                .ifPresent(statsData -> {
                    for (Map.Entry<StatType, Integer> entry : statRanks.entrySet()) {
                        // Use the internal map directly to avoid re-syncing to server
                        statsData.getAllRanks().put(entry.getKey(), entry.getValue());
                    }
                });
    }
}
```

### After
```java
@OnlyIn(Dist.CLIENT)
private static void handleClientSide(Map<StatType, Integer> statRanks) {
    // Update client-side stats cache
    com.complextalents.stats.client.ClientStatsData.updateStatRanks(statRanks);

    // Also update client-side player stats capability
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc.player != null) {
        mc.player.getCapability(com.complextalents.stats.capability.GeneralStatsDataProvider.STATS_DATA)
                .ifPresent(statsData -> {
                    for (Map.Entry<StatType, Integer> entry : statRanks.entrySet()) {
                        // Use the internal map directly to avoid re-syncing to server
                        statsData.getAllRanks().put(entry.getKey(), entry.getValue());
                    }
                });
    }
}
```

**Key Difference**: Added `ClientStatsData.updateStatRanks(statRanks);` at the start.

---

## File 4: MODIFIED - `StatsTabUI.java`

**Location**: `src/main/java/com/complextalents/client/StatsTabUI.java`

### Change Summary
Replaced the entire `update()` method to use the client cache instead of capability lookups.

### Before
```java
public void update() {
    statEntries.clear();

    player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(data -> {
        int row = 0;
        int col = 0;

        for (StatType type : StatType.values()) {
            int realCurrentRank = data.getStatRank(type);  // ← UNRELIABLE
            int pendingPurchases = cart.getAmount(UpgradeType.STAT, type);

            ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();
            int costPerRank = ClassCostMatrix.getCost(originId, type);

            StatEntryData entry = new StatEntryData();
            entry.type = type;
            entry.row = row;
            entry.col = col;
            entry.realRank = realCurrentRank;
            entry.pendingRank = pendingPurchases;
            entry.costPerRank = costPerRank;

            statEntries.add(entry);

            col++;
            if (col >= STATS_PER_ROW) {
                col = 0;
                row++;
            }
        }
    });
}
```

### After
```java
public void update() {
    statEntries.clear();

    int row = 0;
    int col = 0;

    for (StatType type : StatType.values()) {
        int realCurrentRank = com.complextalents.stats.client.ClientStatsData.getStatRank(type);  // ← RELIABLE
        int pendingPurchases = cart.getAmount(UpgradeType.STAT, type);

        ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();
        int costPerRank = ClassCostMatrix.getCost(originId, type);

        StatEntryData entry = new StatEntryData();
        entry.type = type;
        entry.row = row;
        entry.col = col;
        entry.realRank = realCurrentRank;
        entry.pendingRank = pendingPurchases;
        entry.costPerRank = costPerRank;

        statEntries.add(entry);

        col++;
        if (col >= STATS_PER_ROW) {
            col = 0;
            row++;
        }
    }
}
```

**Key Changes**:
1. Removed the `player.getCapability().ifPresent()` wrapper
2. Changed from `data.getStatRank(type)` to `ClientStatsData.getStatRank(type)`
3. No more lambda/callback structure - straightforward for loop
4. Still has access to everything needed (cart, origin cost matrix, etc.)

---

## How the Fix Works

### Execution Flow

1. **Server sends data**:
   ```
   Player purchases stat → GeneralStatsData.setStatRank() → sync()
   → StatsDataSyncPacket created with stat ranks map
   ```

2. **Packet arrives at client**:
   ```
   StatsDataSyncPacket.handle() → enqueueWork() → handleClientSide()
   ```

3. **Cache is updated**:
   ```
   ClientStatsData.updateStatRanks(statRanks)
   → Clears old map
   → Populates with new values from packet
   → Ready for UI to read
   ```

4. **UI reads from cache**:
   ```
   StatsTabUI.update() is called
   → ClientStatsData.getStatRank(type) returns value
   → Entry data is populated
   → UI renders with correct values
   ```

### Why This Works Better

| Aspect | Old (Capability) | New (Cache) |
|--------|------------------|------------|
| **Thread Safety** | Uncertain timing | Guaranteed order: packet → cache → UI |
| **Availability** | May be null/empty | Always initialized |
| **Performance** | Navigation overhead | Direct map lookup |
| **Reliability** | Race conditions | No external dependencies |
| **State Management** | Implicit | Explicit cleanup on logout |

---

## Integration Points

### 1. Sync Packet Handler
- Calls `ClientStatsData.updateStatRanks()` with stat data from server
- This is the **only** place the cache is modified

### 2. UI Rendering
- Reads from `ClientStatsData.getStatRank()` for display
- Never calls capability or network code

### 3. Event Listener
- Listens for logout event
- Calls `ClientStatsData.reset()` to clear cache
- Prevents stale data between logins

---

## Backward Compatibility

- ✓ Server-side code unchanged (GeneralStatsData, StatModifierApplier)
- ✓ Sync packet format unchanged
- ✓ Capability still updated (for backward compatibility)
- ✓ No breaking changes to public APIs

---

## Testing Scenarios

### Scenario 1: New Player
1. Character created
2. Player purchases stats
3. Check: Stats values display correctly in UI

### Scenario 2: Persistent Data
1. Player logs out with purchased stats
2. Player logs back in
3. Check: Previous stats still displayed
4. Check: Cache loaded from server sync

### Scenario 3: Cache Cleanup
1. Player A logs in with stats
2. Player A logs out
3. Player B logs in
4. Check: Player B sees their stats, not A's

### Scenario 4: Multiple Purchases
1. Player purchases stat rank 1
2. UI updates to show accumulated value
3. Player purchases stat rank 2
4. UI updates to show new accumulated value
5. Check: Display is always in sync with server data
