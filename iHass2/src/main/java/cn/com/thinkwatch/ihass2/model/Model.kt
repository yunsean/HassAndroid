package cn.com.thinkwatch.ihass2.model

import org.xutils.db.table.DbModel
import java.math.BigDecimal
import java.util.*

open class Model {
    companion object {
        fun getString(model: DbModel, column: String, defValue: String = ""): String {
            return if (model.isEmpty(column)) defValue else model.getString(column) ?: defValue
        }
        fun getInt(model: DbModel, column: String, defValue: Int = 0): Int {
            return if (model.isEmpty(column)) defValue else model.getInt(column)
        }
        fun getLong(model: DbModel, column: String, defValue: Long = 0L): Long {
            return if (model.isEmpty(column)) defValue else model.getLong(column)
        }
        fun getBoolean(model: DbModel, column: String, defValue: Boolean = false): Boolean {
            return if (model.isEmpty(column)) defValue else model.getBoolean(column)
        }
        fun getDate(model: DbModel, column: String, defValue: Date? = null): Date? {
            return if (model.isEmpty(column)) defValue else model.getDate(column)
        }
        fun getDouble(model: DbModel, column: String, defValue: Double = .0): Double {
            return if (model.isEmpty(column)) defValue else model.getDouble(column)
        }
        fun getBigDecimal(model: DbModel, column: String, defValue: BigDecimal = BigDecimal("0")): BigDecimal {
            return if (model.isEmpty(column)) defValue else BigDecimal(model.getString(column))
        }
    }
}