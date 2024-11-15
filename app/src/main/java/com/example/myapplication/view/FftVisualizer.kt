import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun FftVisualizer(
    magnitudes: List<Float>,
    modifier: Modifier = Modifier,
    sampleRate: Int = 24000
) {
    val numBars = 40 // 柱子的数量，可根据需要调整
    var smoothedData by remember { mutableStateOf(FloatArray(numBars)) }

    if (magnitudes.isEmpty()) {
        // 当magnitudes为空时，将smoothedData重置为全零数组
        smoothedData = FloatArray(numBars) { 0f }
    } else {
        // 创建一个新的列表，忽略直流分量
        val adjustedMagnitudes = magnitudes.toMutableList()
        adjustedMagnitudes[0] = 0f // 将直流分量设置为0

        // 计算最大值，忽略直流分量
        val maxMagnitude = adjustedMagnitudes.maxOrNull() ?: 1f

        // 计算每个频率段的大小
        val binSize = (adjustedMagnitudes.size / numBars).coerceAtLeast(1)

        // 中间位置
        val halfBars = numBars / 2

        // 将FFT数据对称地分配到barData数组，两侧对称
        val barData = FloatArray(numBars)
        for (i in 0 until halfBars) {
            val index = (i + 1) * binSize // 从索引1开始，跳过直流分量
            val magnitude = adjustedMagnitudes.getOrElse(index) { 0f } / maxMagnitude
            val clampedMagnitude = magnitude.coerceIn(0f, 1f)

            // 对称赋值
            barData[halfBars + i] = clampedMagnitude // 右侧
            barData[halfBars - i - 1] = clampedMagnitude // 左侧
        }

        // 如果numBars是奇数，处理中心柱子
        if (numBars % 2 != 0) {
            val centerIndex = adjustedMagnitudes.size / 2
            val magnitude = adjustedMagnitudes.getOrElse(centerIndex) { 0f } / maxMagnitude
            barData[halfBars] = magnitude.coerceIn(0f, 1f)
        }

        // 平滑处理
        val smoothingFactor = 0.6f
        for (i in 0 until numBars) {
            smoothedData[i] = smoothedData[i] * smoothingFactor + barData[i] * (1f - smoothingFactor)
        }
    }

    // 绘制居中对称的柱状图
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width / (numBars + numBars * 0.2f) // 包含间隔
        val barSpacing = barWidth * 0.2f // 柱子之间的间隔

        // 起始X坐标，使柱状图居中
        val totalBarsWidth = numBars * barWidth + (numBars - 1) * barSpacing
        val startX = (width - totalBarsWidth) / 2f

        // 渐变色，增强视觉效果
        val gradient = Brush.verticalGradient(
            colors = listOf(Color.Cyan, Color.Blue),
            startY = 0f,
            endY = height
        )

        for (i in 0 until numBars) {
            val x = startX + i * (barWidth + barSpacing)
            val barHeight = smoothedData[i] * height
            val y = (height - barHeight) / 2f // 垂直方向居中
            drawRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
    }
}