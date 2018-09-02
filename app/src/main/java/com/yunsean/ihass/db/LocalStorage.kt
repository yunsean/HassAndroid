package com.yunsean.ihass.db

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.google.gson.Gson
import org.xutils.db.sqlite.SqlInfo

class LocalStorage: cn.com.thinkwatch.ihass2.db.LocalStorage() {
    fun saveShortcut(entities: List<JsonEntity>) {
        try { dbManager.delete(Shortcut::class.java) } catch (_: Exception) {}
        entities.forEach { dbManager.save(Shortcut(it.entityId, it.displayOrder, it.showName, it.showIcon, it.itemType)) }
    }
    fun readShortcut(): List<JsonEntity> {
        synchronized(entityLock) {
            return try {
                val models = dbManager.findDbModelAll(SqlInfo("select d.*, e.RAW_JSON from HASS_SHORTCUTS d left join HASS_ENTITIES e on d.ENTITY_ID = e.ENTITY_ID"))
                if (models == null || models.size < 1) return listOf()
                val gson = Gson()
                models.map { model ->
                    try {
                        var entity = try { gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java) } catch (_: Exception) { null }
                        if (entity == null) entity = JsonEntity()
                        entity.entityId = model.getString("ENTITY_ID")
                        entity.displayOrder = model.getInt("DISPLAY_ORDER")
                        entity.showIcon = model.getString("SHOW_ICON")
                        entity.showName = model.getString("SHOW_NAME")
                        entity.itemType = ItemType.values().find { it.toString().equals(model.getString("ITEM_TYPE")) } ?: ItemType.entity
                        entity
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        null
                    }
                }.filterNotNull()
            } catch (ex: Exception) {
                listOf()
            }
        }
    }

    private object Holder {
        val INSTANCE = LocalStorage()
    }
    companion object {
        val instance: LocalStorage by lazy { Holder.INSTANCE }
    }
}

inline val Context.db: LocalStorage
    get() = LocalStorage.instance
inline val Fragment.db: LocalStorage
    get() = LocalStorage.instance
