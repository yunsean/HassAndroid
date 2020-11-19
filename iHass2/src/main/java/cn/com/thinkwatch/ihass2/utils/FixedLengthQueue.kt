package cn.com.thinkwatch.ihass2.utils

import java.util.*

class FixedLengthQueue<T> {
    private var maxSize = Integer.MAX_VALUE
    private val synObj = Any()
    private val items = LinkedList<T>()

    constructor(maxSize: Int) : super() {
        this.maxSize = maxSize
    }

    val size
        get() = items.size
    fun push(addLast: T): T? {
        synchronized(synObj) {
            val obj = if (items.size >= maxSize) items.poll() else null
            items.addLast(addLast)
            return obj
        }
    }
    fun poll(): T? {
        synchronized(synObj) {
            return items.poll()
        }
    }
    fun tryPollAll(action: (T) -> Boolean) {
        synchronized(synObj) {
            val iterable = items.iterator()
            while (iterable.hasNext()) {
                if (action(iterable.next())) {
                    iterable.remove()
                } else {
                    break
                }
            }
        }
    }
}
