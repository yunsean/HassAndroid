package cn.com.thinkwatch.ihass2.db

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.adapter.PanelGroup
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.bean.NormalBean
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.enums.TileType
import cn.com.thinkwatch.ihass2.model.*
import com.google.gson.*
import com.google.gson.annotations.Expose
import com.yunsean.dynkotlins.extensions.isSubClassOf
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import org.xutils.DbManager
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

class LocalStorage(dbDir: String? = null) {

    private val entityLock = Any()
    private val gson = Gson()
    var dbManager: DbManager
    init {
        registerType()
        val daoConfig = DbManager.DaoConfig()
                .setDbName("hass")
                .setDbVersion(3)
                .setAllowTransaction(false)
                .setDbUpgradeListener { db, oldVersion, newVersion ->
                }
        if (dbDir != null) daoConfig.setDbDir(File(dbDir))
        dbManager = x.getDb(daoConfig)
    }

    fun initEntities(entities: List<JsonEntity>) {
        try { dbManager.delete(DbEntity::class.java) } catch (_: Exception) {}
        try { dbManager.delete(Panel::class.java) } catch (_: Exception) {}
        try { dbManager.delete(Dashboard::class.java) } catch (_: Exception) {}
        val gson = Gson()
        entities.forEach { dbManager.save(DbEntity(it.entityId, gson.toJson(it))) }
        val map = entities.associateBy(JsonEntity::entityId)
        val panels = entities.filter { it.isGroup &&it.attributes?.entityIds?.find { !(map.get(it)?.isGroup ?: true) } != null }
        panels.forEachIndexed { index, panel ->
            dbManager.save(Panel(panel.friendlyName, index))
            val panelId = autoGenId()
            panel.attributes?.entityIds?.forEachIndexed { index, it ->
                dbManager.save(Dashboard(panelId, it, index))
            }
        }
    }
    fun saveEntities(entities: JSONArray) {
        synchronized(entityLock) {
            try { dbManager.delete(DbEntity::class.java) } catch (ex: Exception) {ex.printStackTrace()}
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                dbManager.save(DbEntity(entity.optString("entity_id"), entity.toString()))
            }
        }
    }
    fun saveEntity(new: JsonEntity) {
        try {
            val old = dbManager.selector(DbEntity::class.java).where("ENTITY_ID", "=", new.entityId).findFirst()
            old.rawJson = gson.toJson(new)
            dbManager.update(old, "RAW_JSON")
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    fun listEntity(): List<JsonEntity> {
        synchronized(entityLock) {
            return try {
                val models = dbManager.findDbModelAll(SqlInfo("select e.* from HASS_ENTITIES e"))
                if (models == null || models.size < 1) return listOf()
                val gson = Gson()
                models.map { model ->
                    try {
                        val entity = gson.fromJson<JsonEntity>(model.getString("RAW_JSON"), JsonEntity::class.java)
                        entity.entityId = model.getString("ENTITY_ID")
                        entity.id = model.getLong("ID")
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
    }
    fun getEntity(entityId: String): JsonEntity? {
        val entity = try { dbManager.selector(DbEntity::class.java).where("ENTITY_ID", "=", entityId).findFirst() } catch (_: Exception) { null }
        if (entity == null) return null
        val value = gson.fromJson(entity.rawJson, JsonEntity::class.java)
        value.id = entity.id
        value.entityId = entity.entityId
        return value
    }
    fun readPanel(panelId: Long): List<JsonEntity> {
        synchronized(entityLock) {
            return try {
                val models = dbManager.findDbModelAll(SqlInfo("select d.*, e.RAW_JSON from HASS_DASHBOARDS d left join HASS_ENTITIES e on d.ENTITY_ID = e.ENTITY_ID where d.PANEL_ID = ${panelId}"))
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
                latest?.entities?.add(NormalBean(entity))
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
        entities.forEach { dbManager.save(Dashboard(panelId, it.entityId, it.displayOrder, it.showName, it.showIcon, it.columnCount, it.itemType, it.tileType)) }
    }
    fun listDashborad(panelId: Long): List<Dashboard> = try { dbManager.selector(Dashboard::class.java).where("PANEL_ID", "=", panelId).findAll() } catch (_:Exception) { listOf() }

    fun addWidget(widget: Widget): Long {
        dbManager.save(widget)
        return autoGenId()
    }
    fun getWidget(widgetId: Long): JsonEntity? {
        val widget = try { dbManager.findById(Widget::class.java, widgetId) } catch (_: Exception) { null }
        if (widget == null) return null
        return getEntity(widget.entityId)
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
                          @Expose val password: String?)
    data class HassPanel(@Expose val name: String?,
                         @Expose val order: Int,
                         @Expose val dashboards: List<Dashboard>)
    data class HassConfig(@Expose val server: HassServer,
                          @Expose val panels: List<HassPanel>)
    fun export(context: Context): String? {
        val server = HassServer(context.app.haHostUrl, context.app.haPassword)
        val panels = listPanel().map {
            val dashborads = listDashborad(it.id)
            HassPanel(it.name, it.order, dashborads)
        }
        val config = HassConfig(server, panels)
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
    fun import(config: HassConfig) {
        try { dbManager.delete(Dashboard::class.java) } catch (ex: Exception) { }
        try { dbManager.delete(Panel::class.java) } catch (ex: Exception) { }
        config.panels.forEach {
            dbManager.save(Panel(it.name ?: "", it.order))
            val panelId = autoGenId()
            it.dashboards.forEach {
                it.panelId = panelId
                dbManager.save(it)
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

