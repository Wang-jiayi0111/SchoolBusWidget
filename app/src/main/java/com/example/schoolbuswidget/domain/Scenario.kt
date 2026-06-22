package com.example.schoolbuswidget.domain

enum class ScenarioTemplate {
    /** 单套时刻表，每天生效 */
    SIMPLE,

    /** 多套时间表，按规则生效（工作日/假期、每周、日期区间） */
    MULTI_SCHEDULE,

    /** 多子配置（如北/南）× 日期类型，仅内置校巴使用 */
    MULTI_PROFILE,
}

data class Scenario(
    val id: String,
    val name: String,
    val template: ScenarioTemplate,
    val builtIn: Boolean = false,
)

object ScenarioDefaults {
    const val BUS_SCENARIO_ID = "builtin_bus"
}
