package com.pda.screenshotmatcher2.views.interfaces

/**
 * Interface implemented by views that require manual garbage collection.
 *
 * Listeners, which are registered in a view, need to be removed manually. Otherwise memory leaks will occur.
 */
interface GarbageView {
    /**
     * Clear all listeners and other variables that are not needed anymore.
     */
    fun clearGarbage()
}