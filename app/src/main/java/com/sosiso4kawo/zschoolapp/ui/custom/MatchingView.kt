package com.sosiso4kawo.zschoolapp.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.util.dpToPx

// Интерфейс для уведомления об изменениях
interface MatchingListener {
    fun onMatchChanged(isComplete: Boolean)
}

// Модель для хранения информации об элементах и их позициях
private data class ItemInfo(
    val view: TextView,
    val originalText: String,
    val bounds: RectF = RectF(),
    val center: PointF = PointF(),
    var isLeft: Boolean = true,
    var isMatched: Boolean = false,
    var matchedTo: ItemInfo? = null // Ссылка на сопряженный элемент
)

// Модель для хранения информации о соединении (для отрисовки)
private data class Connection(
    val startItem: ItemInfo,
    val endItem: ItemInfo
)

class MatchingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private var leftItems: List<String> = emptyList()
    private var rightItems: List<String> = emptyList()

    private val leftItemInfos = mutableListOf<ItemInfo>()
    private val rightItemInfos = mutableListOf<ItemInfo>()
    private val connections = mutableListOf<Connection>()

    private var draggingItem: ItemInfo? = null
    private var currentDragPoint: PointF? = null

    var listener: MatchingListener? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black) // Цвет линии
        strokeWidth = context.dpToPx(2).toFloat() // Толщина линии
        style = Paint.Style.STROKE
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black) // Цвет стрелки
        strokeWidth = context.dpToPx(2).toFloat() // Толщина линии стрелки
        style = Paint.Style.FILL_AND_STROKE // Заполненная стрелка
    }

    private val draggingLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY // Цвет линии во время перетаскивания
        strokeWidth = context.dpToPx(2).toFloat()
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f) // Пунктир
    }

    // Размеры и отступы
    private val horizontalSpacing = context.dpToPx(24)
    private val verticalSpacing = context.dpToPx(12)
    private val itemPaddingHorizontal = context.dpToPx(12)
    private val itemPaddingVertical = context.dpToPx(8)
    private val itemMinHeight = context.dpToPx(48)

    init {
        // Важно для отрисовки ViewGroup, если у него нет фона
        setWillNotDraw(false)
    }

    fun setItems(left: List<String>, right: List<String>) {
        // Очистка перед добавлением новых элементов
        removeAllViews()
        leftItemInfos.clear()
        rightItemInfos.clear()
        connections.clear()
        draggingItem = null
        currentDragPoint = null

        this.leftItems = left
        this.rightItems = right.shuffled() // Перемешиваем правые для усложнения

        // Создание View для левых элементов
        this.leftItems.forEach { text ->
            val textView = createTextView(text)
            addView(textView)
            leftItemInfos.add(ItemInfo(view = textView, originalText = text, isLeft = true))
        }

        // Создание View для правых элементов
        this.rightItems.forEach { text ->
            val textView = createTextView(text)
            addView(textView)
            rightItemInfos.add(ItemInfo(view = textView, originalText = text, isLeft = false))
        }

        invalidate() // Перерисовать
        requestLayout() // Пересчитать размеры и положение
    }

    private fun createTextView(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setBackgroundResource(R.drawable.button_unselected) // Начальный фон
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            setPadding(itemPaddingHorizontal, itemPaddingVertical, itemPaddingHorizontal, itemPaddingVertical)
            minHeight = itemMinHeight
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Примерный размер текста
            // Можно добавить другие атрибуты стиля
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val itemWidth = (availableWidth - horizontalSpacing) / 2

        var totalHeight = paddingTop + paddingBottom
        var maxColumnHeight = 0

        // Измеряем левые элементы
        leftItemInfos.forEach { info ->
            measureChild(info.view,
                MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED) // Высота по контенту
            )
            maxColumnHeight += info.view.measuredHeight + verticalSpacing
        }
        // Убираем последний отступ
        if (leftItemInfos.isNotEmpty()) maxColumnHeight -= verticalSpacing

        var rightColumnHeight = 0
        // Измеряем правые элементы
        rightItemInfos.forEach { info ->
            measureChild(info.view,
                MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            rightColumnHeight += info.view.measuredHeight + verticalSpacing
        }
        if (rightItemInfos.isNotEmpty()) rightColumnHeight -= verticalSpacing

        totalHeight += maxOf(maxColumnHeight, rightColumnHeight)

        setMeasuredDimension(
            resolveSize(availableWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val parentLeft = paddingLeft
        val parentRight = r - l - paddingRight
        val parentTop = paddingTop

        val itemWidth = (parentRight - parentLeft - horizontalSpacing) / 2
        var currentLeftY = parentTop
        var currentRightY = parentTop

        // Размещение левых элементов и сохранение их позиций
        leftItemInfos.forEach { info ->
            val view = info.view
            val top = currentLeftY
            val bottom = top + view.measuredHeight
            val left = parentLeft
            val right = left + itemWidth
            view.layout(left, top, right, bottom)

            // Сохраняем границы и центр
            info.bounds.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            info.center.set(info.bounds.right, info.bounds.centerY()) // Центр с правой стороны левого элемента

            currentLeftY += view.measuredHeight + verticalSpacing
        }

        // Размещение правых элементов и сохранение их позиций
        rightItemInfos.forEach { info ->
            val view = info.view
            val top = currentRightY
            val bottom = top + view.measuredHeight
            val left = parentLeft + itemWidth + horizontalSpacing
            val right = left + itemWidth
            view.layout(left, top, right, bottom)

            // Сохраняем границы и центр
            info.bounds.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            info.center.set(info.bounds.left, info.bounds.centerY()) // Центр с левой стороны правого элемента

            currentRightY += view.measuredHeight + verticalSpacing
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Перехватываем событие, если нажатие было на левом элементе
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val item = findItemAt(ev.x, ev.y)
                // Начинаем перетаскивание только с левых элементов
                if (item != null && item.isLeft) {
                    return true // Перехватить событие для onTouchEvent
                }
            }
        }
        return super.onInterceptTouchEvent(ev) // Не перехватывать в остальных случаях
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val item = findItemAt(x, y)
                if (item != null && item.isLeft) {
                    // Если элемент уже соединен, удаляем старое соединение (это и есть "отмена")
                    if (item.isMatched) {
                        removeConnection(item)
                        draggingItem = null // Не начинаем новое перетаскивание сразу
                        invalidate()
                        performClick() // Для accessibility
                        listener?.onMatchChanged(isComplete()) // Уведомить об изменении
                        return true // Событие обработано
                    } else {
                        // Начинаем перетаскивание нового соединения
                        draggingItem = item
                        currentDragPoint = PointF(x, y)
                        invalidate() // Перерисовать для отображения линии перетаскивания
                        return true // Событие обработано
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingItem != null) {
                    currentDragPoint?.set(x, y)
                    invalidate() // Перерисовывать линию во время движения
                    return true // Событие обработано
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggingItem != null) {
                    val targetItem = findItemAt(x, y)
                    // Проверяем, что отпустили над правым элементом, который еще не соединен
                    if (targetItem != null && !targetItem.isLeft && !targetItem.isMatched) {
                        // Успешное соединение
                        addConnection(draggingItem!!, targetItem)
                    }
                    // Сброс состояния перетаскивания в любом случае
                    draggingItem = null
                    currentDragPoint = null
                    invalidate() // Убрать линию перетаскивания или показать новую постоянную
                    performClick() // Для accessibility
                    listener?.onMatchChanged(isComplete()) // Уведомить об изменении
                    return true // Событие обработано
                }
                // Если мы просто тапнули на левый элемент для отмены, ACTION_UP тоже придет сюда
                // и performClick() будет вызван
                draggingItem = null
                currentDragPoint = null
                invalidate()
                return true // Событие обработано (даже если ничего не сделали)

            }
            MotionEvent.ACTION_CANCEL -> {
                // Отмена перетаскивания (например, если палец ушел за пределы View)
                draggingItem = null
                currentDragPoint = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event) // Для других действий или если не обработали
    }

    // Важно для Accessibility и для обработки кликов без перетаскивания
    override fun performClick(): Boolean {
        super.performClick()
        // Здесь можно добавить кастомную логику, если нужно
        return true
    }

    private fun findItemAt(x: Float, y: Float): ItemInfo? {
        // Ищем сначала среди левых, потом среди правых
        return leftItemInfos.find { it.bounds.contains(x, y) }
            ?: rightItemInfos.find { it.bounds.contains(x, y) }
    }

    private fun addConnection(startItem: ItemInfo, endItem: ItemInfo) {
        // Удаляем старые соединения, если они были (на всякий случай)
        removeConnection(startItem)
        removeConnection(endItem)

        // Создаем новое соединение
        connections.add(Connection(startItem, endItem))
        startItem.isMatched = true
        startItem.matchedTo = endItem
        endItem.isMatched = true
        endItem.matchedTo = startItem

        // Визуальное обновление (например, изменение фона)
        startItem.view.setBackgroundResource(R.drawable.button_selected) // Фон соединенного
        endItem.view.setBackgroundResource(R.drawable.button_selected)

        invalidate()
    }

    private fun removeConnection(item: ItemInfo) {
        val connectionToRemove = connections.find { it.startItem == item || it.endItem == item }
        if (connectionToRemove != null) {
            connections.remove(connectionToRemove)

            // Сброс состояния для обоих элементов в соединении
            val start = connectionToRemove.startItem
            val end = connectionToRemove.endItem

            start.isMatched = false
            start.matchedTo = null
            end.isMatched = false
            end.matchedTo = null

            // Возвращаем стандартный фон
            start.view.setBackgroundResource(R.drawable.button_unselected)
            end.view.setBackgroundResource(R.drawable.button_unselected)

            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Сначала рисуем линии соединений
        connections.forEach { connection ->
            val startPoint = connection.startItem.center
            val endPoint = connection.endItem.center
            drawArrowLine(canvas, startPoint, endPoint, linePaint, arrowPaint)
        }

        // Рисуем линию текущего перетаскивания
        if (draggingItem != null && currentDragPoint != null) {
            val startPoint = draggingItem!!.center
            canvas.drawLine(startPoint.x, startPoint.y, currentDragPoint!!.x, currentDragPoint!!.y, draggingLinePaint)
        }

        // Затем рисуем дочерние View (TextView) поверх линий
        super.dispatchDraw(canvas)
    }

    // Функция для рисования линии со стрелкой на конце
    private fun drawArrowLine(canvas: Canvas, start: PointF, end: PointF, linePaint: Paint, arrowPaint: Paint) {
        val angle = Math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val arrowHeadLength = context.dpToPx(10).toFloat() // Длина стрелки

        // Рисуем основную линию
        canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)

        // Рисуем наконечник стрелки
        val path = Path()
        path.moveTo(end.x, end.y) // Кончик стрелки

        val angle1 = angle - Math.toRadians(30.0) // Угол для одной стороны стрелки
        val x1 = end.x - arrowHeadLength * Math.cos(angle1).toFloat()
        val y1 = end.y - arrowHeadLength * Math.sin(angle1).toFloat()
        path.lineTo(x1, y1)

        val angle2 = angle + Math.toRadians(30.0) // Угол для другой стороны стрелки
        val x2 = end.x - arrowHeadLength * Math.cos(angle2).toFloat()
        val y2 = end.y - arrowHeadLength * Math.sin(angle2).toFloat()
        path.lineTo(x2, y2)

        path.close() // Замыкаем путь, чтобы получился треугольник
        canvas.drawPath(path, arrowPaint)
    }

    // Проверяет, все ли левые элементы соединены
    fun isComplete(): Boolean {
        // Все левые элементы должны быть соединены
        return leftItemInfos.isNotEmpty() && leftItemInfos.all { it.isMatched }
    }

    // Возвращает текущие установленные пары (Левый текст -> Правый текст)
    fun getMatchedPairs(): Map<String, String> {
        return connections.associate { it.startItem.originalText to it.endItem.originalText }
    }
}