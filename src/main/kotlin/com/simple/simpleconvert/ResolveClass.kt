package com.simple.simpleconvert

//解析后的信息
open class ResolveClass {
    //名称,唯一值
    var name: String? = null;

    //小写开头名称
    var lowerName: String? = null;

    //是否为数组
    var isArr: Boolean? = false

    //是否为最外层返回值
    var isBase: Boolean? = false

    //下一级信息
    var childer: List<ResolveClass>? = null

    //以下属性只有isBase为true才返回
    //参数名称
    var paramName: String? = null;

    //获取方法 getXXX
    var getMethod: String? = null;

    //设置方法 setXXX
    var setMethod: String? = null;

}