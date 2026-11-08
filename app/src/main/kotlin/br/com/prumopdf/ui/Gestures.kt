package br.com.prumopdf.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Item da grade que está sob o ponto (coordenadas da própria grade). */
fun LazyGridState.indexAt(pos: Offset): Int? = layoutInfo.visibleItemsInfo.firstOrNull {
    pos.x >= it.offset.x && pos.x < it.offset.x + it.size.width &&
        pos.y >= it.offset.y && pos.y < it.offset.y + it.size.height
}?.index

/** Velocidade da rolagem automática perto do alto ou do pé da grade (0 longe das bordas). */
fun edgeSpeed(y: Float, height: Int, edge: Float, max: Float): Float = when {
    y < edge -> -max * ((edge - y) / edge).coerceIn(0.2f, 1f)
    y > height - edge -> max * ((y - (height - edge)) / edge).coerceIn(0.2f, 1f)
    else -> 0f
}

/**
 * Seleção de vários deslizando o dedo, como no app Fotos: arrastar na horizontal a partir de um item
 * marca (ou desmarca, se ele já estava marcado) todos os itens por onde o dedo passa, inclusive para cima
 * e para baixo depois, com rolagem automática perto das bordas. Arrastar na vertical continua rolando a grade.
 *
 * [onStart] recebe o item onde o dedo começou e devolve o valor a aplicar (true = marcar);
 * [onRange] recebe o primeiro e o último item do trecho e o valor.
 */
@Composable
fun Modifier.dragSelect(
    state: LazyGridState,
    onStart: (index: Int) -> Boolean,
    onRange: (from: Int, to: Int, value: Boolean) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    return this.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = state.indexAt(down.position) ?: return@awaitEachGesture
            var total = Offset.Zero
            var selecting = false
            var value = true
            var pos = down.position
            var last = start
            var auto: Job? = null
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (event.changes.count { it.pressed } > 1) break
                    pos = change.position
                    if (!selecting) {
                        total += change.positionChange()
                        val slop = viewConfiguration.touchSlop
                        if (abs(total.x) > slop && abs(total.x) > abs(total.y) * 1.2f) {
                            selecting = true
                            value = onStart(start)
                            onRange(start, start, value)
                            change.consume()
                            auto = scope.launch {
                                while (isActive) {
                                    val v = edgeSpeed(pos.y, size.height, edge, 28f)
                                    if (v != 0f) {
                                        state.scrollBy(v)
                                        state.indexAt(pos)?.let { idx ->
                                            if (idx != last) {
                                                last = idx
                                                onRange(start, idx, value)
                                            }
                                        }
                                    }
                                    delay(16)
                                }
                            }
                        } else if (abs(total.y) > slop) {
                            break   // rolagem
                        }
                    } else {
                        change.consume()
                        state.indexAt(pos)?.let { idx ->
                            if (idx != last) {
                                last = idx
                                onRange(start, idx, value)
                            }
                        }
                    }
                }
            } finally {
                auto?.cancel()
            }
        }
    }
}

/** Marca os itens de [from] a [to] com [value], e devolve os outros ao que eram antes do arraste. */
class RangeSelection<K>(private val keys: () -> List<K>, private val selected: MutableCollection<K>) {
    private var before: Set<K> = emptySet()

    fun start(index: Int): Boolean {
        before = selected.toSet()
        val k = keys().getOrNull(index) ?: return true
        return k !in before
    }

    fun range(from: Int, to: Int, value: Boolean) {
        val all = keys()
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val target = before.toMutableSet()
        for (i in lo..hi) {
            val k = all.getOrNull(i) ?: continue
            if (value) target += k else target -= k
        }
        selected.retainAll(target)
        for (k in all) if (k in target && k !in selected) selected.add(k)
    }
}
