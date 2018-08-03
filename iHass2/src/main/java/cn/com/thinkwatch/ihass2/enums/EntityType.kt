package cn.com.thinkwatch.ihass2.enums

enum class EntityType (val code: String) {
    Switch("switch"),
    Light("light"),
    Fan("fan"),
    Conver("cover"),
    Vacuum("vacuum"),
    Player("media_player"),
    Tracker("device_tracker"),
    Zone("zone"),
    Sun("sun"),
    Sensor("sensor"),
    Climate("climate"),
    Camera("camera"),
    Group("group"),
    Automation("automation"),
    Script("script"),
    Select("input_select"),
    Slider("input_slider"),
    Number("input_number"),
    Alarm("alarm_control_panel"),
    Scene("scene"),
    Boolean("input_boolean"),
    Text("input_text"),
    Datetime("input_datetime"),
    Notification("persistent_notification"),
    Binary("binary_sensor")
}