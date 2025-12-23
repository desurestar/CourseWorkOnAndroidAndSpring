package ru.zagrebin.culinaryblog.ui

/**
 * Marks a tab fragment whose content should be refreshed when the tab becomes active.
 */
interface RefreshableTab {
    fun refreshContent()
}
