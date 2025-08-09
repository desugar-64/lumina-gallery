package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaManager

/**
 * Geometry utilities for media hex visualization.
 * Contains functions for hit testing, coordinate transformations, and geometric calculations.
 */

/**
 * Finds the topmost animatable media item at the given position.
 * Uses rotation-aware hit testing to handle rotated media items.
 *
 * @param position The position to test in world coordinates
 * @param hexGridLayout The hex grid layout containing media items
 * @param animationManager Manager for animatable media items
 * @return The animatable media item at the position, or null if none found
 */
fun findAnimatableMediaAtPosition(
    position: Offset,
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    animationManager: AnimatableMediaManager
): AnimatableMediaItem? {
    // Iterate in reverse order to check topmost (last drawn) items first
    for (hexCellWithMedia in hexGridLayout.hexCellsWithMedia.reversed()) {
        for (mediaWithPosition in hexCellWithMedia.mediaItems.reversed()) {
            val animatableItem = animationManager.getOrCreateAnimatable(mediaWithPosition)
            val bounds = animatableItem.mediaWithPosition.absoluteBounds
            val rotationAngle = animatableItem.currentRotation // Use current animated rotation

            if (rotationAngle == 0f) {
                if (bounds.contains(position)) {
                    return animatableItem
                }
            } else {
                val center = bounds.center
                val transformedPosition = transformPointWithInverseRotation(
                    point = position,
                    center = center,
                    rotationDegrees = rotationAngle
                )

                if (bounds.contains(transformedPosition)) {
                    return animatableItem
                }
            }
        }
    }
    return null
}

/**
 * Transforms a point by applying the inverse of a rotation transformation.
 * This is used for hit testing on rotated media items.
 *
 * @param point The point to transform
 * @param center The center of rotation
 * @param rotationDegrees The rotation angle in degrees (positive = clockwise)
 * @return The transformed point
 */
fun transformPointWithInverseRotation(
    point: Offset,
    center: Offset,
    rotationDegrees: Float
): Offset = with(Matrix()) {
    translate(center.x, center.y)
    rotateZ(-rotationDegrees)
    translate(-center.x, -center.y)
    map(point)
}
