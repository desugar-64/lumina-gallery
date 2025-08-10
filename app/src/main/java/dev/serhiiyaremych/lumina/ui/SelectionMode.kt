package dev.serhiiyaremych.lumina.ui

/**
 * Selection mode enum to differentiate between cell-level and photo-level selections.
 * Controls whether focus animations should trigger from FocusedCellPanel selections.
 */
enum class SelectionMode {
    /**
     * Cell mode: User selected a cell or selected from panel while in cell mode.
     * Panel selections do NOT trigger focus animations.
     */
    CELL_MODE,

    /**
     * Photo mode: User clicked photo directly on canvas or zoomed in significantly.
     * Panel selections DO trigger focus animations to new photos.
     */
    PHOTO_MODE
}
