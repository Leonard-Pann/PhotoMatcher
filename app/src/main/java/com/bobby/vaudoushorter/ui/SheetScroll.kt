package com.bobby.vaudoushorter.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Material 3 1.4 settles the sheet with the list's leftover fling velocity, even
 * at the expanded anchor. A strong upward velocity overshoots that anchor and
 * feeds back into the sheet's inset/anchor updates, causing continuous bouncing.
 *
 * Intercept only the upward remainder AFTER the list has flung. Drag deltas,
 * list inertia and downward velocity still reach Material's normal handlers.
 */
internal object SheetScrollConnection : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0f, y = available.y.coerceAtMost(0f))
}

internal fun Modifier.stableSheetScroll(): Modifier = nestedScroll(SheetScrollConnection)
