package cn.com.thinkwatch.ihass2.db

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.adapter.PanelGroup
import cn.com.thinkwatch.ihass2.bean.BlankBean
import cn.com.thinkwatch.ihass2.enums.*
import cn.com.thinkwatch.ihass2.model.*
import cn.com.thinkwatch.ihass2.model.album.AlbumDownloadItem
import cn.com.thinkwatch.ihass2.model.album.AlbumLocalItem
import cn.com.thinkwatch.ihass2.model.album.AlbumRemoteItem
import cn.com.thinkwatch.ihass2.model.broadcast.Cached
import cn.com.thinkwatch.ihass2.model.broadcast.Favorite
import cn.com.thinkwatch.ihass2.model.deprecated.V3DbEntity
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.cfg
import com.github.promeg.pinyinhelper.Pinyin
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.annotations.Expose
import com.yunsean.dynkotlins.extensions.isSubClassOf
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONObject
import org.xutils.DbManager
import org.xutils.common.util.KeyValue
import org.xutils.db.annotation.Column
import org.xutils.db.converter.ColumnConverter
import org.xutils.db.converter.ColumnConverterFactory
import org.xutils.db.sqlite.ColumnDbType
import org.xutils.db.sqlite.SqlInfo
import org.xutils.db.sqlite.WhereBuilder
import org.xutils.db.table.DbModel
import org.xutils.x
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import java.util.concurrent.TimeUnit

open class LocalStorage(dbDir: String? = null) {

    protected val entityLock = Any()
    val gson
        get() = Gsons.gson
    protected var dbManager: DbManager
    init {
        registerType()
        var currentVersion = 0
        val daoConfig = DbManager.DaoConfig()
                .setDbName("hass")
                .setDbVersion(11)
                .setAllowTransaction(false)
                .setDbUpgradeListener { db, oldVersion, newVersion ->
                    if (oldVersion < 4) {
                        db.dropTable(Favorite::class.java)
                        val entities = db.findAll(V3DbEntity::class.java)
                        db.dropTable(V3DbEntity::class.java)
                        entities.forEach { db.save(DbEntity(it.entityId, it.rawJson)) }
                    }
                    if (oldVersion < 7) {
                        try { db.execNonQuery("ALTER TABLE HASS_PANELS ADD PANEL_SPELL TEXT") } catch (_: Exception) {}
                        try { db.execNonQuery("ALTER TABLE HASS_PANELS ADD PANEL_SIMILAR TEXT") } catch (_: Exception) {}
                        try { db.execNonQuery("ALTER TABLE HASS_DASHBOARDS ADD ENTITY_SPELL TEXT") } catch (_: Exception) {}
                        try { db.execNonQuery("ALTER TABLE HASS_DASHBOARDS ADD ENTITY_SIMILAR TEXT") } catch (_: Exception) {}
                    }
                    if (oldVersion < 8) {
                        try { db.execNonQuery("ALTER TABLE HASS_OBSERVED ADD DISABLED INTEGER default 0") } catch (_: Exception) {}
                        try { db.execNonQuery("ALTER TABLE HASS_TRIGGER ADD DISABLED INTEGER default 0") } catch (_: Exception) {}
                    }
                    if (oldVersion < 9) {
                        try { db.execNonQuery("ALTER TABLE HASS_PANELS ADD BACK_IMAGE TEXT") } catch (_: Exception) {}
                        try { db.execNonQuery("ALTER TABLE HASS_PANELS ADD TILE_ALPHA FLOAT default 1") } catch (_: Exception) {}
                    }
                    if (oldVersion < 10) {
                        try { db.execNonQuery("DELETE FROM XMLY_FAVORITES") } catch (_: Exception) {}
                        try { db.execNonQuery("ALTER TABLE XMLY_FAVORITES ADD TYPE INTEGER default 0") } catch (_: Exception) {}
                    }
                    if (oldVersion < 11) {
                        try { db.execNonQuery("ALTER TABLE HASS_PANELS ADD PANEL_ICON TEXT") } catch (_: Exception) {}
                    }
                    currentVersion = oldVersion
                }
        if (dbDir != null) daoConfig.setDbDir(File(dbDir))
        dbManager = x.getDb(daoConfig)
        if (currentVersion < 7) {
            dbManager.database.beginTransaction()
            listPanel().forEach { panel->
                try {
                    panel.spell = Pinyin.toPinyin(panel.name, "`").toLowerCase()
                    panel.similar = getSimilar(panel.spell)
                    dbManager.update(panel)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            listDashborad().forEach { dashboard->
                getEntity(dashboard.entityId)?.let { entity->
                    val name = if (dashboard.showName.isNullOrEmpty()) entity.friendlyName else dashboard.showName
                    dashboard.spell = Pinyin.toPinyin(name, "`").toLowerCase()
                    dashboard.similar = getSimilar(dashboard.spell)
                    dbManager.update(dashboard)
                }
            }
            dbManager.database.setTransactionSuccessful()
            dbManager.database.endTransaction()
        }
    }

    fun setConfig(key: String, value: String) = dbManager.saveOrUpdate(ConfigItem(key, value))
    fun getConfig(key: String): String? = try { dbManager.findById(ConfigItem::class.java, key)?.value } catch (_: Exception) { null }
    fun allConfigs(): List<ConfigItem> = try { dbManager.findAll(ConfigItem::class.java) } catch (_: Exception) { listOf() }

    fun initEntities(entities: List<JsonEntity>, reset: Boolean) {
        dbManager.database.beginTransaction()
        try {
            try { dbManager.delete(DbEntity::class.java) } catch (_: Exception) { }
            entities.forEach { dbManager.save(DbEntity(it.entityId, gson.toJson(it))) }
            if (reset) {
                try { dbManager.delete(Panel::class.java) } catch (_: Exception) { }
                try { dbManager.delete(Dashboard::class.java) } catch (_: Exception) { }
                val map = entities.associateBy(JsonEntity::entityId)
                val panels = entities.filter {
                    it.isGroup && it.attributes?.entityIds?.find { !(map.get(it)?.isGroup ?: true) } != null
                }
                panels.forEachIndexed { index, panel ->
                    dbManager.save(Panel(panel.friendlyName, index))
                    val panelId = autoGenId()
                    panel.attributes?.entityIds?.forEachIndexed { index, it ->
                        dbManager.save(Dashboard(panelId, it, index))
                    }
                }
            }
            dbManager.database.setTransactionSuccessful()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        dbManager.database.endTransaction()
    }
    fun saveEntities(entities: JSONArray, onItem: ((entityId: String, state: String?, raw: String?)-> Unit)? = null, clear: Boolean = true) {
        dbManager.database.beginTransaction()
        try {
            if (clear) dbManager.delete(DbEntity::class.java)
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                saveEntity(entity, onItem, clear)
            }
            dbManager.database.setTransactionSuccessful()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        dbManager.database.endTransaction()
    }
    fun saveEntity(entity: JSONObject, onItem: ((entityId: String, state: String?, raw: String?)-> Unit)? = null, clear: Boolean = false) {
        val entityId = entity.optString("entity_id")
        val state = entity.optString("state")
        val raw = entity.toString()
        onItem?.invoke(entityId, state, raw)
        if (clear) dbManager.save(DbEntity(entityId, raw))
        else dbManager.saveOrUpdate(DbEntity(entityId, raw))
    }
    fun saveEntity(entity: String, isArray: Boolean, onItem: ((entityId: String, state: String?, raw: String?)-> Unit)? = null, clear: Boolean) {
        try {
            if (isArray) {
                saveEntities(JSONArray(entity), onItem, clear)
            } else {
                saveEntity(JSONObject(entity), onItem)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    fun listEntity(entityIdPattern: String? = null): List<JsonEntity> {
        return try {
            val sql = StringBuffer("select e.* from HASS_ENTITIES e")
            if (!entityIdPattern.isNullOrBlank()) sql.append(" where e.ENTITY_ID like '${entityIdPattern}'")
            val models = dbManager.findDbModelAll(SqlInfo(sql.toString()))
            if (models == null || models.size < 1) return listOf()
            models.map { model ->
                try {
                    val entity = gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java)
                    entity.entityId = model.getString("ENTITY_ID")
                    entity
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    null
                }
            }.filterNotNull().filter { !it.isGroup }.distinctBy { it.entityId }
        } catch (ex: Exception) {
            ex.printStackTrace()
            listOf()
        }
    }
    fun mapEntityValue(entityIdPattern: String? = null): Map<String, String?> {
        return try {
            val sql = StringBuffer("select e.* from HASS_ENTITIES e")
            if (!entityIdPattern.isNullOrBlank()) sql.append(" where e.ENTITY_ID like '${entityIdPattern}'")
            val models = dbManager.findDbModelAll(SqlInfo(sql.toString()))
            if (models == null || models.size < 1) return mapOf()
            val result = mutableMapOf<String, String?>()
            models.forEach { model ->
                try {
                    val entity = gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java)
                    entity.entityId = model.getString("ENTITY_ID")
                    result.put(entity.entityId, entity.state)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            result
        } catch (ex: Exception) {
            ex.printStackTrace()
            mapOf()
        }
    }
    fun getDbEntity(entityId: String): DbEntity? = try { dbManager.findById(DbEntity::class.java, entityId) } catch (_: Exception) { null }
    fun getEntity(entityId: String): JsonEntity? {
        val entity = try { dbManager.selector(DbEntity::class.java).where("ENTITY_ID", "=", entityId).findFirst() } catch (_: Exception) { null }
        if (entity == null) return null
        try {
            val value = gson.fromJson(entity.rawJson, JsonEntity::class.java)
            value.entityId = entity.entityId
            return value
        } catch (_: Exception) {
            return null
        }
    }
    fun readPanel(panelId: Long): List<JsonEntity> {
        synchronized(entityLock) {
            return try {
                val models = dbManager.findDbModelAll(SqlInfo("select d.*, e.RAW_JSON from HASS_DASHBOARDS d left join HASS_ENTITIES e on d.ENTITY_ID = e.ENTITY_ID where d.PANEL_ID = ${panelId}"))
                if (models == null || models.size < 1) return listOf()
                models.map { model ->
                    try {
                        var entity = try { gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java) } catch (_: Exception) { null }
                        if (entity == null) entity = JsonEntity()
                        entity.entityId = model.getString("ENTITY_ID")
                        entity.displayOrder = model.getInt("DISPLAY_ORDER")
                        entity.showIcon = model.getString("SHOW_ICON")
                        entity.showName = model.getString("SHOW_NAME")
                        entity.columnCount = model.getInt("COLUMN_COUNT")
                        entity.itemType = ItemType.values().find { it.toString().equals(model.getString("ITEM_TYPE")) } ?: ItemType.entity
                        entity.tileType = TileType.values().find { it.toString().equals(model.getString("TILE_TYPE")) } ?: TileType.tile
                        entity
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        null
                    }
                }.filterNotNull()
            } catch (ex: Exception) {
                ex.printStackTrace()
                listOf()
            }
        }
    }
    fun readPanelGroup(panelId: Long): MutableList<PanelGroup> {
        val entities = readPanel(panelId)
        val groups = mutableListOf<PanelGroup>()
        var latest: PanelGroup? = null
        entities.forEach {
            var group: PanelGroup? = null
            var entity: JsonEntity? = null
            if (it.itemType == ItemType.divider) {
                group = PanelGroup(it)
            } else {
                entity = it
            }
            if (group == null && latest == null) {
                latest = PanelGroup()
                groups.add(latest!!)
            }
            if (group != null) {
                latest = group
                groups.add(group)
            } else if (entity != null) {
                latest?.entities?.add(BlankBean())
            }
        }
        return groups
    }

    fun isInited(): Boolean {
        val entitiesCount = try { dbManager.selector(DbEntity::class.java).count() } catch (_: Exception) { 0L }
        val groupCount = try { dbManager.selector(Panel::class.java).count() } catch (_: Exception) { 0L }
        return entitiesCount > 0 && groupCount > 0
    }

    fun addPanel(panel: Panel): Long {
        panel.order = try { dbManager.selector(Panel::class.java).orderBy("PANEL_ORDER", true).findFirst().order + 1 } catch (_: Exception) { 0 }
        dbManager.save(panel)
        return autoGenId()
    }
    fun savePanel(panel: Panel) = dbManager.update(panel)
    fun savePanels(panels: List<Panel>) {
        val exist = try { dbManager.findAll(Panel::class.java) } catch (_: Exception) { listOf<Panel>() }
        panels.forEach { dbManager.update(it) }
        exist.forEach {e-> if (panels.find { it.id == e.id } == null) dbManager.delete(e) }
    }
    fun getPanel(panelId: Long) = try { dbManager.findById(Panel::class.java, panelId) } catch (_: Exception) { null }
    fun listPanel(): List<Panel> = try { dbManager.selector(Panel::class.java).orderBy("PANEL_ORDER").findAll() } catch (_: Exception) { listOf() }

    fun addDashboard(dashboard: Dashboard) = dbManager.save(dashboard)
    fun saveDashboard(panelId: Long, entities: List<JsonEntity>) {
        try { dbManager.delete(Dashboard::class.java, WhereBuilder.b("PANEL_ID", "=", panelId)) } catch (_: Exception) {}
        entities.forEach {
            val name = if (it.showName.isNullOrEmpty()) it.friendlyName else it.showName
            val spell = Pinyin.toPinyin(name, "`").toLowerCase()
            val similar = getSimilar(spell)
            dbManager.save(Dashboard(panelId, it.entityId, it.displayOrder, it.showName, it.showIcon, it.columnCount, it.itemType, it.tileType, spell, similar))
        }
    }
    fun listDashborad(panelId: Long): List<Dashboard> = try { dbManager.selector(Dashboard::class.java).where("PANEL_ID", "=", panelId).findAll() } catch (_:Exception) { listOf() }
    fun listDashborad(): List<Dashboard> = try { dbManager.findAll(Dashboard::class.java) } catch (_:Exception) { listOf() }

    fun addWidget(widgetId: Int, widgetType: WidgetType, backColor: Int = 0, normalColor: Int = 0xff656565.toInt(),
                  activeColor: Int = 0xff0288D1.toInt(), textSize: Int = 12, imageSize: Int = 30,
                  entities: List<JsonEntity>) {
        dbManager.database.beginTransaction()
        try {
            dbManager.deleteById(Widget::class.java, widgetId)
            dbManager.delete(WidgetEntity::class.java, WhereBuilder.b("WIDGET_ID", "=", widgetId))
            dbManager.save(Widget(widgetId, widgetType, backColor, normalColor, activeColor, textSize, imageSize))
            entities.forEachIndexed{ index, it ->  dbManager.save(WidgetEntity(widgetId, it.entityId, index, it.showName, it.showIcon)) }
            dbManager.database.setTransactionSuccessful()
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            dbManager.database.endTransaction()
        }
    }
    fun getWidgetEntity(widgetId: Int): List<JsonEntity> {
        return try {
            val sql = "select w.*, e.RAW_JSON from HASS_WIDGET_ENTITIES w inner join HASS_ENTITIES e on e.ENTITY_ID = w.ENTITY_ID where w.WIDGET_ID = ${widgetId} ORDER BY w.DISPLAY_ORDER"
            val models = dbManager.findDbModelAll(SqlInfo(sql))
            if (models == null || models.size < 1) return listOf()
            models.map { model ->
                try {
                    val entity = try { gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java) } catch (_: Exception) { null }
                    entity?.entityId = model.getString("ENTITY_ID")
                    entity?.displayOrder = model.getInt("DISPLAY_ORDER")
                    entity?.showIcon = model.getString("SHOW_ICON")
                    entity?.showName = model.getString("SHOW_NAME")
                    entity
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    null
                }
            }.filterNotNull()
        } catch (ex: Exception) {
            ex.printStackTrace()
            listOf()
        }
    }
    fun getWidget(widgetId: Int) = try { dbManager.findById(Widget::class.java, widgetId) } catch (_: Exception) { null }
    fun delWidget(widgetId: Int) {
        dbManager.database.beginTransaction()
        try {
            dbManager.deleteById(Widget::class.java, widgetId)
            dbManager.delete(WidgetEntity::class.java, WhereBuilder.b("WIDGET_ID", "=", widgetId))
            dbManager.database.setTransactionSuccessful()
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            dbManager.database.endTransaction()
        }
    }
    fun getAllWidgetEntityIds() = try { dbManager.findAll(WidgetEntity::class.java).map { it.entityId }.toSet() } catch (_: Exception) { null }
    fun getWidgets(): List<Widget> = try { dbManager.findAll(Widget::class.java) } catch (_: Exception) { listOf() }
    fun getWidgets(entityId: String): List<Widget> {
        return try {
            val sql = "select w.* from HASS_WIDGET_ENTITIES we inner join HASS_WIDGETS w on w.WIDGET_ID = we.WIDGET_ID where we.ENTITY_ID = '${entityId}'"
            val models = dbManager.findDbModelAll(SqlInfo(sql))
            if (models == null || models.size < 1) return listOf()
            val widgets = mutableListOf<Widget>()
            models.forEach {
                val widgetId = it.getInt("WIDGET_ID")
                if (widgets.find { it.widgetId == widgetId } != null) return@forEach
                widgets.add(Widget(widgetId, it.getString("WIDGET_TYPE").let { WidgetType.valueOf(it) },
                        it.getInt("BACK_COLOR"), it.getInt("NORMAL_COLOR"), it.getInt("ACTIVE_COLOR")))
            }
            widgets
        } catch (ex: Exception) {
            listOf()
        }
    }
    fun getWidgets(type: WidgetType): List<Widget> = try { dbManager.selector(Widget::class.java).where("WIDGET_TYPE", "=", type.toString()).findAll() } catch (_: Exception) { listOf() }

    fun getTriggers(enableOnly: Boolean = true): List<EventTrigger> {
        return try {
            val triggers = if (enableOnly) dbManager.selector(EventTrigger::class.java).where("DISABLED", "=", "0").findAll() else dbManager.findAll(EventTrigger::class.java) ?: listOf()
            triggers
        } catch (ex: Exception) {
            listOf()
        }
    }
    fun addTrigger(trigger: EventTrigger) = dbManager.save(trigger)
    fun saveTrigger(trigger: EventTrigger) = dbManager.update(trigger)
    fun getTrigger(triggerId: Long): EventTrigger? = try { dbManager.findById(EventTrigger::class.java, triggerId) } catch (_: Exception) { null }
    fun deleteTrigger(triggerId: Long) = try { dbManager.deleteById(EventTrigger::class.java, triggerId) } catch (_: Exception) {}
    fun getTrigger(type: TriggerType, params: String?): List<EventTrigger>? {
        return try {
            val selector = dbManager.selector(EventTrigger::class.java).where("TYPE", "=", type)
            if (params != null) selector.and("PARAMS", "=", params)
            selector.findAll()
        } catch (_: Exception) {
            null
        }
    }

    fun getObserved(enableOnly: Boolean = true): List<Observed> {
        return try {
            val observeds = if (enableOnly) dbManager.selector(Observed::class.java).where("DISABLED", "=", "0").findAll() else dbManager.findAll(Observed::class.java) ?: listOf()
            observeds.forEach { it.entityName = getEntity(it.entityId)?.friendlyName ?: it.entityId }
            observeds
        } catch (ex: Exception) {
            listOf()
        }
    }
    fun getObserved(entityId: String, queryEntityName: Boolean = true): List<Observed>? {
        return try {
            val observeds = dbManager.selector(Observed::class.java).where("ENTITY_ID", "=", entityId).findAll()
            if (queryEntityName) observeds?.forEach { it.entityName = getEntity(it.entityId)?.friendlyName ?: it.entityId }
            observeds
        } catch (ex: Exception) {
            null
        }
    }
    fun getObserved(observedId: Long): Observed? {
        return try {
            dbManager.findById(Observed::class.java, observedId)?.let {
                it.entityName = getEntity(it.entityId)?.friendlyName ?: it.entityId
                it
            }
        } catch (_: Exception) {
            null
        }
    }
    fun addObserved(observed: Observed) = dbManager.save(observed)
    fun saveObserved(observed: Observed) = dbManager.update(observed)
    fun deleteObserved(observedId: Long) = try {dbManager.deleteById(Observed::class.java, observedId) } catch (_: Exception) { null }

    fun addTriggerHistory(history: TriggerHistory) = dbManager.save(history)
    fun getTriggerHistories(keyword: String?, pageIndex: Int): List<TriggerHistory> {
        return try {
            val selector =  dbManager.selector(TriggerHistory::class.java)
            if (!keyword.isNullOrBlank()) selector.where(WhereBuilder.b("SERVICE_ID", "LIKE", "%${keyword}%").or("NAME", "LIKE", "%${keyword}%"))
            selector.orderBy("ID", true).offset(pageIndex * 20).limit(20).findAll()
        } catch (_: Exception) {
            listOf()
        }
    }
    fun truncateTriggerHistory() {
        val history = try { dbManager.selector(TriggerHistory::class.java).offset(200).findFirst() } catch (_: Exception) { null }
        if (history == null) return
        dbManager.delete(TriggerHistory::class.java, WhereBuilder.b("ID", "<", history.id))
    }

    fun addDownloadItems(items: List<AlbumRemoteItem>, userStub: String, currentPath: (path: String)-> String): Boolean {
        var hasNew = false
        dbManager.database.beginTransaction()
        try {
            items.forEach { remote->
                val path = currentPath(remote.name)
                val old = dbManager.selector(AlbumDownloadItem::class.java).where("PATH", "=", path).findFirst()
                if (old == null) {
                    dbManager.save(AlbumDownloadItem(remote.name, path, userStub, remote.size))
                    hasNew = true
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
        return hasNew
    }
    fun getDownloadItem(id: Long) = try { dbManager.findById(AlbumDownloadItem::class.java, id) } catch (_: Exception) { null }
    fun getDownloadItems(pageIndex: Int, desc: Boolean = false) : List<AlbumDownloadItem> {
        return try {
            dbManager.selector(AlbumDownloadItem::class.java).orderBy("TIME", desc).offset(pageIndex * 100).limit(100).findAll()
        } catch (_: Exception) {
            listOf()
        }
    }
    fun getDownloadItemsCount() = try { dbManager.selector(AlbumDownloadItem::class.java).count() } catch (_: Exception) { 0L }
    fun deleteDownloadItems(ids: List<Long>) {
        dbManager.database.beginTransaction()
        try {
            ids.forEach { dbManager.deleteById(AlbumDownloadItem::class.java, it) }
        }  catch (_: Exception) {
        }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
    }
    fun resetDownloadItems(ids: List<Long>) {
        dbManager.database.beginTransaction()
        try {
            ids.forEach {
                dbManager.update(AlbumDownloadItem::class.java, WhereBuilder.b("ID", "=", it.toString()), KeyValue("FAILED", false))
            }
        } catch (_: Exception) {
        }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
    }
    fun cleanDownloadItems() = try { dbManager.delete(AlbumDownloadItem::class.java) } catch (_: Exception) {}
    fun getAlbumFirstDownload(): AlbumDownloadItem? {
        return try {
            dbManager.selector(AlbumDownloadItem::class.java)
                    .where("FAILED", "=", 0)
                    .orderBy("TIME")
                    .findFirst()
        } catch (_: Exception) {
            null
        }
    }
    fun updateDownloadItem(item: AlbumDownloadItem) = try { dbManager.update(item) } catch (_: Exception) {}

    fun addAlbumDownloadedItem(path: String) {
        val file = File(path)
        val old = dbManager.selector(AlbumLocalItem::class.java).where("PATH", "=", path).findFirst()
        if (old != null) {
            old.status = AlbumSyncStatus.download
            old.mtime = file.lastModified()
            dbManager.update(old)
        } else {
            val item = AlbumLocalItem(file.name, path, file.length(), file.lastModified(), AlbumSyncStatus.download)
            dbManager.save(item)
        }
    }
    fun addAlbumItems(items: List<String>, reupload: Boolean): Boolean {
        var hasNew = false
        dbManager.database.beginTransaction()
        try {
            items.forEach { path->
                val file = File(path)
                if (!file.exists() || !file.isFile) return@forEach
                val old = dbManager.selector(AlbumLocalItem::class.java).where("PATH", "=", path).findFirst()
                if (old != null) {
                    if (reupload || file.lastModified() > old.mtime) {
                        old.status = AlbumSyncStatus.waiting
                        old.mtime = file.lastModified()
                        dbManager.update(old)
                        hasNew = true
                    }
                } else {
                    dbManager.save(AlbumLocalItem(file.name, path, file.length(), file.lastModified()))
                    hasNew = true
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
        return hasNew
    }
    fun getAlbumItems(pageIndex: Int, statuses: List<AlbumSyncStatus>? = null, desc: Boolean = false) : List<AlbumLocalItem> {
        return try {
            val selector = dbManager.selector(AlbumLocalItem::class.java)
            if (statuses != null && statuses.isNotEmpty()) {
                selector.where(statuses.fold(WhereBuilder.b()) {wb, it-> wb.or(WhereBuilder.b("STATUS", "=", it.toString())) })
            }
            selector.orderBy("MTIME", desc).offset(pageIndex * 100).limit(100).findAll()
        } catch (_: Exception) {
            listOf()
        }
    }
    fun getAlbumUnsyncCount(): Long {
        return try {
            dbManager.selector(AlbumLocalItem::class.java)
                    .where("STATUS", "=", "waiting")
                    .count()
        } catch (_: Exception) {
            0
        }
    }
    fun getAlbumFirstWaiting(): AlbumLocalItem? {
        return try {
            dbManager.selector(AlbumLocalItem::class.java)
                    .where("STATUS", "=", "waiting")
                    .orderBy("RETRIED")
                    .orderBy("MTIME")
                    .findFirst()
        } catch (_: Exception) {
            null
        }
    }
    fun getAlbumFailedCount(): Long {
        return try {
            dbManager.selector(AlbumLocalItem::class.java)
                    .where("STATUS", "=", "failed")
                    .count()
        } catch (_: Exception) {
            0
        }
    }
    fun resetAlbumFailedItems() {
        try {
            dbManager.update(AlbumLocalItem::class.java, WhereBuilder.b("STATUS", "=", "failed"), KeyValue("STATUS", "waiting"))
        } catch (_: Exception) {
        }
    }
    fun resetAlbumFailedItems(ids: List<Long>) {
        dbManager.database.beginTransaction()
        try {
            ids.forEach {
                dbManager.update(AlbumLocalItem::class.java, WhereBuilder.b("ID", "=", it.toString()), KeyValue("STATUS", "waiting"))
            }
        } catch (_: Exception) {
        }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
    }
    fun getAlbumItemsCount(statuses: List<AlbumSyncStatus>? = null) : Long {
        return try {
            val selector = dbManager.selector(AlbumLocalItem::class.java)
            if (statuses != null && statuses.isNotEmpty()) {
                selector.where(statuses.fold(WhereBuilder.b()) {wb, it-> wb.or(WhereBuilder.b("STATUS", "=", it.toString())) })
            }
            selector.count()
        } catch (_: Exception) {
            0L
        }
    }
    fun cleanAlbumSucceed(): Long? {
        val latest = try { dbManager.selector(AlbumLocalItem::class.java).where("STATUS", "=", "succeed").orderBy("MTIME", true).findFirst() } catch (_: Exception) { null }
        try { dbManager.delete(AlbumLocalItem::class.java, WhereBuilder.b("STATUS", "=", "succeed")) } catch (_: Exception) {}
        return latest?.mtime
    }
    fun updateAlbumItem(item: AlbumLocalItem) = try { dbManager.update(item) } catch (_: Exception) {}
    fun deleteAlbumItem(id: Long) = try { dbManager.deleteById(AlbumLocalItem::class.java, id) } catch (_: Exception) {}
    fun deleteAlbumItems(ids: List<Long>) {
        dbManager.database.beginTransaction()
        try {
            ids.forEach { dbManager.deleteById(AlbumLocalItem::class.java, it) }
        }  catch (_: Exception) {
        }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
    }
    fun clearAlbumItem() = try {dbManager.delete(AlbumLocalItem::class.java) } catch (_: Exception) { }

    fun addXmlyFavorite(favorite: Favorite) = try { dbManager.saveOrUpdate(favorite) } catch (ex: Exception) { ex.printStackTrace() }
    fun delXmlyFavorite(id: Int) = try { dbManager.deleteById(Favorite::class.java, id) } catch (_: Exception) {}
    fun getXmlyFavorite(entityId: String, type: Int): List<Favorite> = try { dbManager.selector(Favorite::class.java).where(WhereBuilder.b("TYPE", "=", type).and("ENTITY_ID", "=", entityId)).findAll() } catch (_: Exception) { listOf() }
    fun getXmlyFavorite(entityId: String): List<Favorite> = try { dbManager.selector(Favorite::class.java).where("ENTITY_ID", "=", entityId).findAll() } catch (_: Exception) { listOf() }

    fun addXmlyCached(cached: Cached) = try { dbManager.saveOrUpdate(cached) } catch (ex: Exception) { ex.printStackTrace() }
    fun getXmlyCached(url: String): Cached? = try { dbManager.selector(Cached::class.java).where("PLAY_URL", "=", url).findFirst() } catch (_: Exception) { null }

    fun saveNotification(entities: List<JsonEntity>) {
        try { dbManager.delete(Notification::class.java) } catch (_: Exception) {}
        entities.forEach { dbManager.save(Notification(it.entityId, it.displayOrder, it.showIcon, it.showName)) }
    }
    fun readNotification(): List<JsonEntity> {
        synchronized(entityLock) {
            return try {
                val models = dbManager.findDbModelAll(SqlInfo("select d.*, e.RAW_JSON from HASS_NOTIFICATIONS d left join HASS_ENTITIES e on d.ENTITY_ID = e.ENTITY_ID"))
                if (models == null || models.size < 1) return listOf()
                models.map { model ->
                    try {
                        var entity = try { gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java) } catch (ex: Exception) { ex.printStackTrace(); null }
                        if (entity == null) entity = JsonEntity()
                        entity.entityId = model.getString("ENTITY_ID")
                        entity.displayOrder = model.getInt("DISPLAY_ORDER")
                        entity.showIcon = model.getString("SHOW_ICON")
                        entity.showName = model.getString("SHOW_NAME")
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
    fun getAllNotificationEntityIds() = try { dbManager.findAll(Notification::class.java).map { it.entityId }.toSet() } catch (_: Exception) { null }

    fun addVoiceHistory(history: VoiceHistory) = dbManager.save(history)
    fun listVoiceHistory(pageIndex: Int): List<VoiceHistory> = try { dbManager.selector(VoiceHistory::class.java).offset(pageIndex * 20).limit(20).orderBy("id", true).findAll() } catch (_: Exception) { listOf() }

    fun hasContact() = try { dbManager.findFirst(Contact::class.java); true } catch (_: Exception) { false }
    fun listContacts() = try { dbManager.findAll(Contact::class.java) } catch (_: Exception) { null }
    fun saveContacts(contacts: List<Contact>) {
        dbManager.database.beginTransaction()
        try { dbManager.delete(Contact::class.java) } catch (_: Exception) {}
        contacts.forEach { dbManager.save(it) }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
    }

    fun <T> async(entry: () -> T): Observable<T> {
        return Observable.create<T> {
            try {
                val values = entry()
                it.onNext(values)
                it.onComplete()
            } catch (ex: Exception) {
                ex.printStackTrace()
                it.onError(ex)
            }
        }.subscribeOn(Schedulers.computation())
    }
    fun timer(delay: Long, unit: TimeUnit, disposable: CompositeDisposable?, entry: () -> Unit): CompositeDisposable{
        val disposable = if (disposable == null) CompositeDisposable() else disposable
        Observable.timer(delay, unit).withNext {
            entry()
        }.subscribe {
            disposable.add(it)
        }
        return disposable
    }

    private fun autoGenId(): Long {
        val consor = dbManager.execQuery("select last_insert_rowid()")
        consor.moveToFirst()
        return consor.getLong(0)
    }
    private fun <T> registerEnum(clazz: Class<T>) {
        ColumnConverterFactory.registerColumnConverter(clazz, object: ColumnConverter<T> {
            override fun getColumnDbType(): ColumnDbType = ColumnDbType.TEXT
            override fun fieldValue2DbValue(fieldValue: T?): Any? = fieldValue?.toString()
            override fun getFieldValue(cursor: Cursor?, index: Int): T? = cursor?.getString(index)?.let { v -> clazz.enumConstants.find { it.toString().equals(v) } }
        })
    }
    private fun registerType() {
        registerEnum(ItemType::class.java)
        registerEnum(TileType::class.java)
        registerEnum(WidgetType::class.java)
        registerEnum(TriggerType::class.java)
        registerEnum(ConditionType::class.java)
        registerEnum(AlarmSoundType::class.java)
        registerEnum(AlarmVibrateType::class.java)
        registerEnum(AlbumSyncStatus::class.java)
    }
    private fun <T> modelConvert(model: DbModel, clazz: Class<T>): T? {
        try {
            val obj = clazz.newInstance()
            clazz.declaredFields.forEach {
                field->
                field.getAnnotation(Column::class.java)?.let {
                    val column = it.name
                    val accessable = field.isAccessible
                    val type = field.type
                    field.isAccessible = true
                    if (model.isEmpty(column)) {

                    } else if (type.isSubClassOf(Int::class.java)) {
                        field.setInt(obj, model.getInt(column))
                    } else if (type.isSubClassOf(Double::class.java)) {
                        field.setDouble(obj, model.getDouble(column))
                    } else if (type.isSubClassOf(Boolean::class.java)) {
                        field.setBoolean(obj, model.getBoolean(column))
                    } else if (type.isSubClassOf(Float::class.java)) {
                        field.setFloat(obj, model.getFloat(column))
                    } else if (type.isSubClassOf(Long::class.java)) {
                        field.setLong(obj, model.getLong(column))
                    } else if (type.isSubClassOf(Short::class.java)) {
                        field.setShort(obj, model.getInt(column).toShort())
                    } else if (type.isSubClassOf(String::class.java)) {
                        field.set(obj, model.getString(column))
                    } else if (type.isSubClassOf(Date::class.java)) {
                        field.set(obj, model.getDate(column))
                    } else if (type.isEnum) {
                        val value = model.getString(column)?.let {v-> type.enumConstants.find { it.toString().equals(v) } }
                        if (value != null) field.set(obj, value)
                    }
                    field.isAccessible = accessable
                }
            }
            return obj
        } catch (_: Exception) {
            return null
        }
    }

    data class HassServer(@Expose val hostUrl: String?,
                          @Expose val password: String?,
                          @Expose val token: String?)
    data class HassPanel(@Expose val name: String?,
                         @Expose val order: Int,
                         @Expose val icon: String?,
                         @Expose val dashboards: List<Dashboard>)
    data class HassConfig(@Expose val server: HassServer,
                          @Expose val panels: List<HassPanel>?,
                          @Expose val widgets: List<Widget>?,
                          @Expose val widgetEntities: List<WidgetEntity>?,
                          @Expose val triggers: List<EventTrigger>?,
                          @Expose val observeds: List<Observed>?,
                          @Expose val notifications: List<Notification>?,
                          @Expose val configs: Map<String, String>)
    fun export(context: Context): String? {
        val allConfigNames = arrayOf(
                cn.com.thinkwatch.ihass2.utils.HassConfig.Connect_ScreenOff,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_HostUrl,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_Password,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_Token,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_LocalUrl,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_LocalBssid,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Ui_PullRefresh,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Ui_HomePanels,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Ui_WebFrist,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Ui_ShowTopbar,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Ui_ShowSidebar,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Gps_Logger,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Gps_DeviceName,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Gps_DeviceId,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Gps_Password,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Gps_AppLogger,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Gps_WebHookId,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Probe_NfcCard,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Probe_BluetoothBle,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Probe_Wifi,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Probe_Gps,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_DoubleHomeKey,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_TripleHomeKey,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_Notification,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ShowWakeup,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOnMode,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOnWifi,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOnBluetooth,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOnCharging,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOffMode,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOffWifi,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOffBluetooth,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_ScreenOffCharging,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_FromBluetooth,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_HeadsetWakeup,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_NoWakeupLock,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_VoiceOpenApp,
                cn.com.thinkwatch.ihass2.utils.HassConfig.Speech_VoiceContact)
        val allConfigs = mutableMapOf<String, String>()
        allConfigNames.forEach { allConfigs.put(it, context.cfg.getString(it)) }
        val hostUrl = context.cfg.getString(cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_HostUrl, "")
        val password = context.cfg.getString(cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_Password, "")
        val token = context.cfg.getString(cn.com.thinkwatch.ihass2.utils.HassConfig.Hass_Token, "")
        val server = HassServer(hostUrl, password, token)
        val panels = listPanel().map {
            val dashborads = listDashborad(it.id)
            HassPanel(it.name, it.order, it.icon, dashborads)
        }
        val widgets = try { dbManager.findAll(Widget::class.java) } catch (_: Exception) { null }
        val widgetEntities = try { dbManager.findAll(WidgetEntity::class.java) } catch (_: Exception) { null }
        val triggers = try { dbManager.findAll(EventTrigger::class.java) } catch (_: Exception) { null }
        val observeds = try { dbManager.findAll(Observed::class.java) } catch (_: Exception) { null }
        val notifications = try { dbManager.findAll(Notification::class.java) } catch (_: Exception) { null }
        val config = HassConfig(server, panels, widgets, widgetEntities, triggers, observeds, notifications, allConfigs)
        val gson = GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(TileType::class.java, JsonSerializer<TileType> {
                    src, typeOfSrc, context -> JsonPrimitive((src?.code ?: 0).toString())
                })
                .registerTypeAdapter(ItemType::class.java, JsonSerializer<ItemType> {
                    src, typeOfSrc, context -> JsonPrimitive((src?.code ?: 0).toString())
                })
                .create()
        try {
            val state = Environment.getExternalStorageState()
            val path = if (state == Environment.MEDIA_MOUNTED) Environment.getExternalStorageDirectory().absolutePath
            else context.getExternalFilesDir("").absolutePath
            val file = path + "/hass.json"
            val writer = FileWriter(file)
            gson.toJson(config, HassConfig::class.java, writer)
            writer.close()
            return file
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
    }
    fun import(uri: String): HassConfig? {
        val gson = GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(TileType::class.java, JsonDeserializer {
                    json, typeOfT, context -> TileType.values().find { it.code == json.asInt }
                })
                .registerTypeAdapter(ItemType::class.java, JsonDeserializer {
                    json, typeOfT, context -> ItemType.values().find { it.code == json.asInt }
                })
                .create()
        try {
            val reader = FileReader(uri)
            val config = gson.fromJson(reader, HassConfig::class.java)
            return config
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
    }
    fun import(context: Context, config: HassConfig) {
        config.configs.forEach { context.cfg.set(it.key, it.value) }
        dbManager.database.beginTransaction()
        try { dbManager.delete(Dashboard::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(Panel::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(Widget::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(WidgetEntity::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(EventTrigger::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(Observed::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(Notification::class.java) } catch (ex: Exception) { }
        config.panels?.forEach {
            dbManager.save(Panel(it.name ?: "", it.order, icon = it.icon))
            val panelId = autoGenId()
            it.dashboards.forEach {
                it.panelId = panelId
                dbManager.save(it)
            }
        }
        config.widgets?.forEach { dbManager.save(it) }
        config.widgetEntities?.forEach { dbManager.save(it) }
        config.triggers?.forEach { dbManager.save(it) }
        config.observeds?.forEach { dbManager.save(it) }
        config.notifications?.forEach { dbManager.save(it) }
        dbManager.database.setTransactionSuccessful()
        dbManager.database.endTransaction()
    }

    private object Holder {
        val INSTANCE = LocalStorage()
    }
    companion object {
        val instance: LocalStorage by lazy { Holder.INSTANCE }
        fun getSimilar(spell: String): String {
            if (spell.isEmpty()) return ""
            val similar = StringBuilder()
            spell.split('`').forEach {
                if (it.length <= 1) {
                    similar.append(it).append("`")
                } else {
                    similar.append(it.replace("^n", "l")
                            .replace("ong", "eng")
                            .replace("zh", "z").replace("ch", "c").replace("sh", "s")
                            .replace("ing", "in").replace("eng", "en").replace("ang", "an"))
                            .append("`")
                }
            }
            return similar.toString()
        }
    }
}

inline val Context.db: LocalStorage
    get() = LocalStorage.instance
inline val Fragment.db: LocalStorage
    get() = LocalStorage.instance

