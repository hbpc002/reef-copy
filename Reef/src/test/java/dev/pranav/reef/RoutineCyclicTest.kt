package dev.pranav.reef

import dev.pranav.reef.data.Routine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutineCyclicTest {

    // --- AppLimit.isCyclic ---

    @Test
    fun appLimit_isCyclic_true_when_bothFieldsSet() {
        val limit = Routine.AppLimit("com.example.app", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = 5)
        assertTrue(limit.isCyclic)
    }

    @Test
    fun appLimit_isCyclic_false_when_cyclicUsageNull() {
        val limit = Routine.AppLimit("com.example.app", 30, cyclicUsageMinutes = null, cyclicLockMinutes = 5)
        assertFalse(limit.isCyclic)
    }

    @Test
    fun appLimit_isCyclic_false_when_cyclicLockNull() {
        val limit = Routine.AppLimit("com.example.app", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = null)
        assertFalse(limit.isCyclic)
    }

    @Test
    fun appLimit_isCyclic_false_when_bothNull() {
        val limit = Routine.AppLimit("com.example.app", 30)
        assertFalse(limit.isCyclic)
    }

    @Test
    fun appLimit_isCyclic_defaultConstructor() {
        val limit = Routine.AppLimit("com.example.app", 30)
        assertFalse(limit.isCyclic)
        assertNull(limit.cyclicUsageMinutes)
        assertNull(limit.cyclicLockMinutes)
    }

    // --- AppLimit equality ---

    @Test
    fun appLimit_equality_sameFields() {
        val a = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 10, cyclicLockMinutes = 3)
        val b = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 10, cyclicLockMinutes = 3)
        assertEquals(a, b)
    }

    @Test
    fun appLimit_equality_differentCyclicUsage() {
        val a = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 10, cyclicLockMinutes = 3)
        val b = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 20, cyclicLockMinutes = 3)
        assertNotEquals(a, b)
    }

    @Test
    fun appLimit_equality_differentCyclicLock() {
        val a = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 10, cyclicLockMinutes = 3)
        val b = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 10, cyclicLockMinutes = 5)
        assertNotEquals(a, b)
    }

    @Test
    fun appLimit_equality_cyclicVsNonCyclic() {
        val a = Routine.AppLimit("com.a", 15, cyclicUsageMinutes = 10, cyclicLockMinutes = 3)
        val b = Routine.AppLimit("com.a", 15)
        assertNotEquals(a, b)
    }

    // --- AppLimit.copy with cyclic fields ---

    @Test
    fun appLimit_copy_preservesCyclicFields() {
        val original = Routine.AppLimit("com.a", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = 5)
        val copied = original.copy()
        assertEquals(original, copied)
        assertEquals(original.cyclicUsageMinutes, copied.cyclicUsageMinutes)
        assertEquals(original.cyclicLockMinutes, copied.cyclicLockMinutes)
    }

    @Test
    fun appLimit_copy_modifiesCyclicFields() {
        val original = Routine.AppLimit("com.a", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = 5)
        val modified = original.copy(cyclicUsageMinutes = 25, cyclicLockMinutes = 10)
        assertEquals(25, modified.cyclicUsageMinutes)
        assertEquals(10, modified.cyclicLockMinutes)
        assertTrue(modified.isCyclic)
    }

    @Test
    fun appLimit_copy_clearsCyclicFields() {
        val original = Routine.AppLimit("com.a", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = 5)
        val cleared = original.copy(cyclicUsageMinutes = null, cyclicLockMinutes = null)
        assertFalse(cleared.isCyclic)
        assertNull(cleared.cyclicUsageMinutes)
        assertNull(cleared.cyclicLockMinutes)
    }

    // --- JSON serialization round-trip (mirrors Routines.parseRoutine / routineToJson) ---

    @Test
    fun appLimit_jsonRoundTrip_cyclicLimit() {
        val limit = Routine.AppLimit("com.example.app", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = 5)
        val json = appLimitToJson(limit)
        val parsed = appLimitFromJson(json)

        assertEquals(limit.packageName, parsed.packageName)
        assertEquals(limit.limitMinutes, parsed.limitMinutes)
        assertEquals(limit.cyclicUsageMinutes, parsed.cyclicUsageMinutes)
        assertEquals(limit.cyclicLockMinutes, parsed.cyclicLockMinutes)
        assertTrue(parsed.isCyclic)
    }

    @Test
    fun appLimit_jsonRoundTrip_nonCyclicLimit() {
        val limit = Routine.AppLimit("com.example.app", 30)
        val json = appLimitToJson(limit)
        val parsed = appLimitFromJson(json)

        assertEquals(limit.packageName, parsed.packageName)
        assertEquals(limit.limitMinutes, parsed.limitMinutes)
        assertNull(parsed.cyclicUsageMinutes)
        assertNull(parsed.cyclicLockMinutes)
        assertFalse(parsed.isCyclic)
    }

    @Test
    fun appLimit_jsonRoundTrip_blockEntirely() {
        val limit = Routine.AppLimit("com.example.app", 0, cyclicUsageMinutes = null, cyclicLockMinutes = null)
        val json = appLimitToJson(limit)
        val parsed = appLimitFromJson(json)

        assertEquals(0, parsed.limitMinutes)
        assertNull(parsed.cyclicUsageMinutes)
        assertNull(parsed.cyclicLockMinutes)
        assertFalse(parsed.isCyclic)
    }

    @Test
    fun appLimit_json_omitsCyclicFields_whenNull() {
        val limit = Routine.AppLimit("com.example.app", 30)
        val json = appLimitToJson(limit)
        assertFalse(json.has("cyclicUsageMinutes"))
        assertFalse(json.has("cyclicLockMinutes"))
    }

    @Test
    fun appLimit_json_includesCyclicFields_whenSet() {
        val limit = Routine.AppLimit("com.example.app", 30, cyclicUsageMinutes = 15, cyclicLockMinutes = 5)
        val json = appLimitToJson(limit)
        assertTrue(json.has("cyclicUsageMinutes"))
        assertEquals(15, json.getInt("cyclicUsageMinutes"))
        assertTrue(json.has("cyclicLockMinutes"))
        assertEquals(5, json.getInt("cyclicLockMinutes"))
    }

    @Test
    fun appLimit_json_parsesAbsentCyclicFieldsAsNull() {
        val json = JSONObject().apply {
            put("packageName", "com.example.app")
            put("limitMinutes", 30)
        }
        val parsed = appLimitFromJson(json)
        assertNull(parsed.cyclicUsageMinutes)
        assertNull(parsed.cyclicLockMinutes)
    }

    @Test
    fun appLimit_json_parsesZeroCyclicFields() {
        val json = JSONObject().apply {
            put("packageName", "com.example.app")
            put("limitMinutes", 30)
            put("cyclicUsageMinutes", 0)
            put("cyclicLockMinutes", 0)
        }
        val parsed = appLimitFromJson(json)
        assertEquals(0, parsed.cyclicUsageMinutes)
        assertEquals(0, parsed.cyclicLockMinutes)
        assertTrue(parsed.isCyclic)
    }

    // --- Routine JSON round-trip with cyclic limits ---

    @Test
    fun routine_jsonRoundTrip_withCyclicLimits() {
        val routine = Routine(
            id = "test-id-1",
            name = "Test Routine",
            schedule = dev.pranav.reef.data.RoutineSchedule(
                type = dev.pranav.reef.data.RoutineSchedule.ScheduleType.MANUAL
            ),
            limits = listOf(
                Routine.AppLimit("com.example.app1", 30),
                Routine.AppLimit("com.example.app2", 60, cyclicUsageMinutes = 20, cyclicLockMinutes = 5),
                Routine.AppLimit("com.example.app3", 45, cyclicUsageMinutes = 10, cyclicLockMinutes = 3)
            )
        )

        val json = routineToJson(routine)
        val parsed = routineFromJson(json)

        assertEquals(routine.id, parsed.id)
        assertEquals(routine.name, parsed.name)
        assertEquals(routine.limits.size, parsed.limits.size)

        // First limit: non-cyclic
        assertEquals("com.example.app1", parsed.limits[0].packageName)
        assertEquals(30, parsed.limits[0].limitMinutes)
        assertNull(parsed.limits[0].cyclicUsageMinutes)
        assertNull(parsed.limits[0].cyclicLockMinutes)
        assertFalse(parsed.limits[0].isCyclic)

        // Second limit: cyclic
        assertEquals("com.example.app2", parsed.limits[1].packageName)
        assertEquals(60, parsed.limits[1].limitMinutes)
        assertEquals(20, parsed.limits[1].cyclicUsageMinutes)
        assertEquals(5, parsed.limits[1].cyclicLockMinutes)
        assertTrue(parsed.limits[1].isCyclic)

        // Third limit: cyclic
        assertEquals("com.example.app3", parsed.limits[2].packageName)
        assertEquals(45, parsed.limits[2].limitMinutes)
        assertEquals(10, parsed.limits[2].cyclicUsageMinutes)
        assertEquals(3, parsed.limits[2].cyclicLockMinutes)
        assertTrue(parsed.limits[2].isCyclic)
    }

    @Test
    fun routine_jsonRoundTrip_mixedLimits() {
        val routine = Routine(
            id = "test-id-2",
            name = "Mixed Limits",
            schedule = dev.pranav.reef.data.RoutineSchedule(
                type = dev.pranav.reef.data.RoutineSchedule.ScheduleType.DAILY,
                timeHour = 9,
                timeMinute = 0
            ),
            limits = listOf(
                Routine.AppLimit("app.cyclic", 0, cyclicUsageMinutes = 25, cyclicLockMinutes = 10),
                Routine.AppLimit("app.total", 120)
            )
        )

        val json = routineToJson(routine)
        val parsed = routineFromJson(json)

        assertEquals(2, parsed.limits.size)

        val cyclic = parsed.limits.find { it.packageName == "app.cyclic" }
        assertNotNull(cyclic)
        assertEquals(25, cyclic!!.cyclicUsageMinutes)
        assertEquals(10, cyclic.cyclicLockMinutes)
        assertTrue(cyclic.isCyclic)

        val total = parsed.limits.find { it.packageName == "app.total" }
        assertNotNull(total)
        assertNull(total!!.cyclicUsageMinutes)
        assertNull(total.cyclicLockMinutes)
        assertFalse(total.isCyclic)
    }

    // --- Helper methods that mirror Routines.kt serialization logic ---

    private fun appLimitToJson(limit: Routine.AppLimit): JSONObject {
        return JSONObject().apply {
            put("packageName", limit.packageName)
            put("limitMinutes", limit.limitMinutes)
            limit.cyclicUsageMinutes?.let { put("cyclicUsageMinutes", it) }
            limit.cyclicLockMinutes?.let { put("cyclicLockMinutes", it) }
        }
    }

    private fun appLimitFromJson(json: JSONObject): Routine.AppLimit {
        return Routine.AppLimit(
            packageName = json.getString("packageName"),
            limitMinutes = json.getInt("limitMinutes"),
            cyclicUsageMinutes = json.optInt("cyclicUsageMinutes").takeIf { json.has("cyclicUsageMinutes") },
            cyclicLockMinutes = json.optInt("cyclicLockMinutes").takeIf { json.has("cyclicLockMinutes") }
        )
    }

    private fun routineToJson(routine: Routine): JSONObject {
        return JSONObject().apply {
            put("id", routine.id)
            put("name", routine.name)
            put("isEnabled", routine.isEnabled)
            put("schedule", JSONObject().apply {
                put("type", routine.schedule.type.name)
                routine.schedule.timeHour?.let { put("timeHour", it) }
                routine.schedule.timeMinute?.let { put("timeMinute", it) }
                put("isRecurring", routine.schedule.isRecurring)
            })
            put("limits", JSONArray().apply {
                routine.limits.forEach { put(appLimitToJson(it)) }
            })
        }
    }

    private fun routineFromJson(json: JSONObject): Routine {
        val scheduleJson = json.getJSONObject("schedule")
        val schedule = dev.pranav.reef.data.RoutineSchedule(
            type = dev.pranav.reef.data.RoutineSchedule.ScheduleType.valueOf(scheduleJson.getString("type")),
            timeHour = scheduleJson.optInt("timeHour").takeIf { scheduleJson.has("timeHour") },
            timeMinute = scheduleJson.optInt("timeMinute").takeIf { scheduleJson.has("timeMinute") },
            isRecurring = scheduleJson.optBoolean("isRecurring", true)
        )
        val limits = json.getJSONArray("limits").let { arr ->
            (0 until arr.length()).map { appLimitFromJson(arr.getJSONObject(it)) }
        }
        return Routine(
            id = json.getString("id"),
            name = json.getString("name"),
            isEnabled = json.getBoolean("isEnabled"),
            schedule = schedule,
            limits = limits
        )
    }
}
