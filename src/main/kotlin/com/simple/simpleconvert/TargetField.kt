package com.simple.demo5

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType

class TargetField {
    /**
     * 类型
     */
    var type: String? = null
    /**
     * 属性名称
     */
    var fieldName: String? = null
    /**
     * 方法字符
     */
    var method: String? = null
    var psiType: PsiType? = null
    var psiClass: PsiClass? = null
    var targetClass: TargetClass? = null
}