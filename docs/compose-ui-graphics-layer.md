class GraphicsLayer

* * *

Drawing layer used to record drawing commands in a displaylist as well as additional properties that affect the rendering of the display list. This provides an isolation boundary to divide a complex scene into smaller pieces that can be updated individually of one another without recreating the entire scene. Transformations made to a `GraphicsLayer` can be done without re-recording the display list.

Usage of a `GraphicsLayer` requires a minimum of 2 steps.

1. The `GraphicsLayer` must be built, which involves specifying the position alongside a list of drawing commands using `GraphicsLayer.record`

2. The `GraphicsLayer` is then drawn into another destination `Canvas` using `GraphicsLayer.draw`.

Additionally the contents of the displaylist can be transformed when it is drawn into a desintation `Canvas` by specifying either `scaleX`, `scaleY`, `translationX`, `translationY`, `rotationX`, `rotationY`, or `rotationZ`.

The rendered result of the displaylist can also be modified by configuring the `GraphicsLayer.blendMode`, `GraphicsLayer.colorFilter`, `GraphicsLayer.alpha` or `GraphicsLayer.renderEffect`

## Summary

| ### Public functions |
| --- |

| ### Public properties |
| --- |

| ### Extension functions |
| --- |

## Public functions

### record

Cmnandroid

fun record(
density: Density,
layoutDirection: LayoutDirection,
size: IntSize,

): Unit

Constructs the display list of drawing commands into this layer that will be rendered when this `GraphicsLayer` is drawn elsewhere with `drawLayer`.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset

// Build the layer with the density, layout direction and size from the DrawScope
// and position the top left to be 20 pixels from the left and 30 pixels from the top.
// This will the bounds of the layer with a red rectangle
layer.apply {
record { drawRect(Color.Red) }
this.topLeft = IntOffset(20, 30)
}

// Draw the layer into the provided DrawScope
drawLayer(layer)
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

val drawScopeSize = size
val topLeft = IntOffset((drawScopeSize.width / 4).toInt(), (drawScopeSize.height / 4).toInt())
val layerSize = IntSize((drawScopeSize.width / 2).toInt(), (drawScopeSize.height / 2).toInt())

// Build the GraphicsLayer with the specified offset and size that is filled
// with a red rectangle.
layer.apply {
record(size = layerSize) { drawRect(Color.Red) }
this.topLeft = topLeft
// Specify the Xor blend mode here so that layer contents will be shown in the
// destination only if it is transparent, otherwise the destination would be cleared
// by the source pixels. The color of the rect drawn in this layer is irrelevant as only
// the area shown by the layer is important when using the Xor BlendMode
blendMode = BlendMode.Xor
}

// Fill the destination DrawScope with a green rectangle
drawRect(Color.Green)
// Draw the layer into the destination. Due to usage of the xor blend mode on the layer, the
// region occupied by the red rectangle in the layer will be used to clear contents in the
// destination
drawLayer(layer)

// Draw a blue rectangle *underneath* the current destination pixels. Because the
// drawing of the layer cleared the region in the center, this will fill the transparent
// pixels left in this hole to blue while leaving the bordering green pixels alone.
drawRect(Color.Blue, blendMode = BlendMode.DstOver)
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize

// Create a 200 x 200 pixel layer that draws a red square
layer.apply {
record(size = IntSize(200, 200)) { drawRect(Color.Red) }
// Configuring the translationX + Y will translate the red square
// by 100 pixels to the right and 50 pixels from the top when drawn
// into the destination DrawScope
translationX = 100f
translationY = 50f
}

// Draw the layer into the provided DrawScope
drawLayer(layer)

| Parameters |
| --- |
| `density: Density` | `Density` used to assist in conversions of density independent pixels to raw pixels to draw. |
| `layoutDirection: LayoutDirection` | `LayoutDirection` of the layout being drawn in. |
| `size: IntSize` | `Size` of the `GraphicsLayer` |

### setPathOutline

fun setPathOutline(path: Path): Unit

Specifies the given path to be configured as the outline for this `GraphicsLayer`. When `shadowElevation` is non-zero a shadow is produced with an `Outline` created from the provided `path`. Additionally if `clip` is true, the contents of this `GraphicsLayer` will be clipped to this geometry.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.drawLayer

// Create a layer sized to the destination draw scope that is comprised
// of an inset red rectangle
layer.apply {
record { drawRect(Color.Red) }
// Apply a shadow that is clipped to the specified round rect
shadowElevation = 20f
setRoundRectOutline(Offset.Zero, Size(300f, 180f), 30f)
}

drawLayer(layer)

| Parameters |
| --- |
| `path: Path` | Path to be used as the Outline for the `GraphicsLayer` |

### setRectOutline

fun setRectOutline(topLeft: Offset = Offset.Zero, size: Size = Size.Unspecified): Unit

Configures a rectangular outline for this `GraphicsLayer`. By default, `topLeft` is set to `Size.Zero` and `size` is set to `Size.Unspecified` indicating that the outline should match the size of the `GraphicsLayer`. When `shadowElevation` is non-zero a shadow is produced using an `Outline` created from the round rect parameters provided. Additionally if `clip` is true, the contents of this `GraphicsLayer` will be clipped to this geometry.

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.drawLayer

// Create a layer sized to the destination draw scope that is comprised
// of an inset red rectangle
layer.apply {
record { drawRect(Color.Red) }
// Apply a shadow and have the contents of the layer be clipped
// to the size of the layer with a 20 pixel corner radius
shadowElevation = 20f
setRectOutline(size = Size(this.size.width / 2f, this.size.height.toFloat()))
}

| Parameters |
| --- |
| `topLeft: Offset = Offset.Zero` | The top left of the rounded rect outline |
| `size: Size = Size.Unspecified` | The size of the rounded rect outline |

### setRoundRectOutline

fun setRoundRectOutline(
topLeft: Offset = Offset.Zero,
size: Size = Size.Unspecified,
cornerRadius: Float = 0.0f
): Unit

Configures a rounded rect outline for this `GraphicsLayer`. By default, `topLeft` is set to `Size.Zero` and `size` is set to `Size.Unspecified` indicating that the outline should match the size of the `GraphicsLayer`. When `shadowElevation` is non-zero a shadow is produced using an `Outline` created from the round rect parameters provided. Additionally if `clip` is true, the contents of this `GraphicsLayer` will be clipped to this geometry.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.drawLayer

// Create a layer sized to the destination draw scope that is comprised
// of an inset red rectangle
layer.apply {
record { drawRect(Color.Red) }
// Apply a shadow and have the contents of the layer be clipped
// to the size of the layer with a 20 pixel corner radius
shadowElevation = 20f
setRoundRectOutline(cornerRadius = 20f)
}

| Parameters |
| --- |
| `topLeft: Offset = Offset.Zero` | The top left of the rounded rect outline |
| `size: Size = Size.Unspecified` | The size of the rounded rect outline |
| `cornerRadius: Float = 0.0f` | The corner radius of the rounded rect outline |

### toImageBitmap

suspend fun toImageBitmap(): ImageBitmap

Create an `ImageBitmap` with the contents of this `GraphicsLayer` instance. Note that `GraphicsLayer.record` must be invoked first to record drawing operations before invoking this method.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

layer.record(Density(1f), LayoutDirection.Ltr, IntSize(300, 200)) {
val half = Size(size.width / 2, size.height)
drawRect(Color.Red, size = half)
drawRect(Color.Blue, topLeft = Offset(size.width / 2f, 0f), size = half)
}

GlobalScope.launch(Dispatchers.IO) {
val imageBitmap = layer.toImageBitmap()
val outputStream = context.openFileOutput("MyGraphicsLayerImageBitmap.png", MODE_PRIVATE)
imageBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
}

## Public properties

### alpha

var alpha: Float

Alpha of the content of the `GraphicsLayer` between 0f and 1f. Any value between 0f and 1f will be translucent, where 0f will cause the layer to be completely invisible and 1f will be entirely opaque.

// Create a layer sized to the destination draw scope that is comprised
// of an inset red rectangle
layer.apply {
record { inset(20f, 20f) { drawRect(Color.Red) } }
// Renders the content of the layer with 50% alpha when it is drawn
// into the destination
alpha = 0.5f
}

### ambientShadowColor

var ambientShadowColor: Color

Sets the color of the ambient shadow that is drawn when `shadowElevation` 0f.

By default the shadow color is black. Generally, this color will be opaque so the intensity of the shadow is consistent between different graphics layers with different colors.

The opacity of the final ambient shadow is a function of the shadow caster height, the alpha channel of the `ambientShadowColor` (typically opaque), and the android.R.attr.ambientShadowAlpha theme attribute.

Note that this parameter is only supported on Android 9 (Pie) and above. On older versions, this property always returns `Color.Black` and setting new values is ignored.

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize

// Create a 200 x 200 pixel layer that draws a red square
layer.apply {
record(size = IntSize(200, 200)) { drawRect(Color.Red) }
// Apply a shadow with specified colors that has an elevation of 20f when this layer is
// drawn into the destination DrawScope.
shadowElevation = 20f
ambientShadowColor = Color.Cyan
spotShadowColor = Color.Magenta
}

### blendMode

var blendMode: BlendMode

BlendMode to use when drawing this layer to the destination in `drawLayer`. The default is `BlendMode.SrcOver`. Any value other than `BlendMode.SrcOver` will force this `GraphicsLayer` to use an offscreen compositing layer for rendering and is equivalent to using `CompositingStrategy.Offscreen`.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

// Draw a blue rectangle *underneath* the current destination pixels. Because the
// drawing of the layer cleared the region in the center, this will fill the transparent
// pixels left in this hole to blue while leaving the bordering green pixels alone.
drawRect(Color.Blue, blendMode = BlendMode.DstOver)

### cameraDistance

var cameraDistance: Float

Sets the distance along the Z axis (orthogonal to the X/Y plane on which layers are drawn) from the camera to this layer. The camera's distance affects 3D transformations, for instance rotations around the X and Y axis. If the rotationX or rotationY properties are changed and this view is large (more than half the size of the screen), it is recommended to always use a camera distance that's greater than the height (X axis rotation) or the width (Y axis rotation) of this view.

The distance of the camera from the drawing plane can have an affect on the perspective distortion of the layer when it is rotated around the x or y axis. For example, a large distance will result in a large viewing angle, and there will not be much perspective distortion of the view as it rotates. A short distance may cause much more perspective distortion upon rotation, and can also result in some drawing artifacts if the rotated view ends up partially behind the camera (which is why the recommendation is to use a distance at least as far as the size of the view, if the view is to be rotated.)

The distance is expressed in pixels and must always be positive. Default value is `DefaultCameraDistance`

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer

layer.apply {
record { drawRect(Color.Yellow) }
// Rotates the yellow rect 45f clockwise relative to the y axis
rotationY = 45f
cameraDistance = 5.0f
}

### clip

var clip: Boolean

Determines if the `GraphicsLayer` should be clipped to the rectangular bounds specified by `topLeft` and `size`. The default is false, however, contents will always be clipped to their bounds when the GraphicsLayer is promoted off an offscreen rendering buffer (i.e. CompositingStrategy.Offscreen is used, a non-null ColorFilter, RenderEffect is applied or if the BlendMode is not equivalent to BlendMode.SrcOver

### colorFilter

var colorFilter: ColorFilter?

ColorFilter applied when drawing this layer to the destination in `drawLayer`. Setting of this to any non-null will force this `GraphicsLayer` to use an offscreen compositing layer for rendering and is equivalent to using `CompositingStrategy.Offscreen`

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer

// Create a layer with the same configuration as the destination DrawScope
// and draw a red rectangle in the layer
layer.apply {
record { drawRect(Color.Red) }
// Apply a ColorFilter that will tint the contents of the layer to blue
// when it is drawn into the destination DrawScope
colorFilter = ColorFilter.tint(Color.Blue)
}

### compositingStrategy

var compositingStrategy: CompositingStrategy

`CompositingStrategy` determines whether or not the contents of this layer are rendered into an offscreen buffer. This is useful in order to optimize alpha usages with `CompositingStrategy.ModulateAlpha` which will skip the overhead of an offscreen buffer but can generate different rendering results depending on whether or not the contents of the layer are overlapping. Similarly leveraging `CompositingStrategy.Offscreen` is useful in situations where creating an offscreen buffer is preferred usually in conjunction with `BlendMode` usage.

When `blendMode` is anything other than `BlendMode.SrcOver` or `colorFilter` is non-null, `compositingStrategy`'s value will be overridden and is forced to `CompositingStrategy.Offscreen`.

### isReleased

val isReleased: Boolean

Determines if this `GraphicsLayer` has been released. Any attempts to use a `GraphicsLayer` after it has been released is an error.

### layerId

android

val layerId: Long

The ID of the layer. This is used by tooling to match a layer to the associated LayoutNode.

### outline

val outline: Outline

Returns the outline specified by either `setPathOutline` or `setRoundRectOutline`. By default this will return `Outline.Rectangle` with the size of the `GraphicsLayer` specified by `record` or `IntSize.Zero` if `record` was not previously invoked.

### ownerViewId

val ownerViewId: Long

The uniqueDrawingId of the owner view of this graphics layer. This is used by tooling to match a layer to the associated owner View.

### pivotOffset

var pivotOffset: Offset

`Offset` in pixels used as the center for any rotation or scale transformation. If this value is `Offset.Unspecified`, then the center of the `GraphicsLayer` is used relative to `topLeft` and `size`

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize

// Create a 200 x 200 pixel layer that has a red rectangle drawn in the lower right
// corner.
layer.apply {
record(size = IntSize(200, 200)) {
drawRect(Color.Red, topLeft = Offset(size.width / 2f, size.height / 2f))
}
// Scale the layer by 1.5x in both the x and y axis relative to the bottom
// right corner
scaleX = 1.5f
scaleY = 1.5f
pivotOffset = Offset(this.size.width.toFloat(), this.size.height.toFloat())
}

### renderEffect

var renderEffect: RenderEffect?

Configure the `RenderEffect` to apply to this `GraphicsLayer`. This will apply a visual effect to the results of the `GraphicsLayer` before it is drawn. For example if `BlurEffect` is provided, the contents will be drawn in a separate layer, then this layer will be blurred when this `GraphicsLayer` is drawn.

Note this parameter is only supported on Android 12 and above. Attempts to use this Modifier on older Android versions will be ignored.

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.drawLayer

// Create a layer sized to the destination draw scope that is comprised
// of an inset red rectangle
layer.apply {
record { inset(20f, 20f) { drawRect(Color.Red) } }
// Configure a blur to the contents of the layer that is applied
// when drawn to the destination DrawScope
renderEffect = BlurEffect(20f, 20f, TileMode.Decal)
}

### rotationX

var rotationX: Float

The rotation, in degrees, of the contents around the horizontal axis in degrees. Default value is `0`.

layer.apply {
record { drawRect(Color.Yellow) }
// Rotates the yellow rect 45f clockwise relative to the x axis
rotationX = 45f
}

### rotationY

var rotationY: Float

The rotation, in degrees, of the contents around the vertical axis in degrees. Default value is `0`.

### rotationZ

var rotationZ: Float

The rotation, in degrees, of the contents around the Z axis in degrees. Default value is `0`.

### scaleX

var scaleX: Float

The horizontal scale of the drawn area. Default value is `1`.

### scaleY

var scaleY: Float

The vertical scale of the drawn area. Default value is `1`.

### shadowElevation

var shadowElevation: Float

Sets the elevation for the shadow in pixels. With the `shadowElevation` 0f and `Outline` set, a shadow is produced. Default value is `0` and the value must not be negative. Configuring a non-zero `shadowElevation` enables clipping of `GraphicsLayer` content.

Note that if you provide a non-zero `shadowElevation` and if the passed `Outline` is concave the shadow will not be drawn on Android versions less than 10.

### size

val size: IntSize

Size in pixels of the `GraphicsLayer`. By default `GraphicsLayer` contents can draw outside of the bounds specified by `topLeft` and `size`, however, rasterization of this layer into an offscreen buffer will be sized according to the specified size. This is configured by calling `record`

// Build the layer with the density, layout direction from the DrawScope that is
// sized to 200 x 100 pixels and draw a red rectangle that occupies these bounds
layer.record(size = IntSize(200, 100)) { drawRect(Color.Red) }

### spotShadowColor

var spotShadowColor: Color

Sets the color of the spot shadow that is drawn when `shadowElevation` 0f.

The opacity of the final spot shadow is a function of the shadow caster height, the alpha channel of the `spotShadowColor` (typically opaque), and the android.R.attr.spotShadowAlpha theme attribute.

### topLeft

var topLeft: IntOffset

Offset in pixels where this `GraphicsLayer` will render within a provided canvas when `drawLayer` is called.

### translationX

var translationX: Float

Horizontal pixel offset of the layer relative to `topLeft`.x. Default value is `0`.

### translationY

var translationY: Float

Vertical pixel offset of the layer relative to `topLeft`.y. Default value is `0`

## Extension functions

### setOutline

Cmn

fun GraphicsLayer.setOutline(outline: Outline): Unit

Configures an outline for this `GraphicsLayer` based on the provided `Outline` object.

When `GraphicsLayer.shadowElevation` is non-zero a shadow is produced using a provided `Outline`. Additionally if `GraphicsLayer.clip` is true, the contents of this `GraphicsLayer` will be clipped to this geometry.

| Parameters |
| --- |
| `outline: Outline` | an `Outline` to apply for the layer. |

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-23 UTC.

---

