package com.simple.demo5

open class TargetClass {
    /**
     * 类型 LIST or OBJECT
     */
    var type: String? = null
    /**
     * 类名
     */
    var className: String? = null

    var paramName: String? = null

    /**
     * 方法
     */
    var targetField: List<TargetField>? = null
}