package com.simple.simpleconvert

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.simple.demo5.EditInfo
import com.simple.demo5.TargetClass
import com.simple.demo5.TargetField
import org.apache.commons.lang3.CharUtils
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * 自定义创建目标信息的工具类
 */
open class CustomizeCreateTargetInfo {
    companion object {
        /**
         * 根据当前事件获取生成信息
         */
        fun getGenerateInfo(event: AnActionEvent): EditInfo {
            // 获取编辑器和文件
            val (editor, psiFile) = getEditorAndPsiFile(event)
            val element = editor?.caretModel?.let { psiFile?.findElementAt(it.offset) }
            val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)!!
            return EditInfo(event, psiClass)
        }

        /**
         * 解析方法的返回类型
         */
        fun resolveReturnType(event: AnActionEvent): TargetClass {
            // 获取编辑器和文件
            val (editor, psiFile) = getEditorAndPsiFile(event)
            val method =
                PsiTreeUtil.getParentOfType(psiFile!!.findElementAt(editor!!.caretModel.offset), PsiMethod::class.java)
                    ?: throw IllegalStateException("当前位置不在方法中")

            val returnPsiType = method.returnType ?: throw IllegalStateException("方法没有返回类型")

            if (!method.hasParameters()) {
                throw IllegalArgumentException("方法必须有参数")
            }

            var resultPsiClass = PsiUtil.resolveClassInType(returnPsiType)
            val returnType = SimpleUtil.getType(returnPsiType)

            // 处理返回值为数组或集合的情况
            if ((returnType == SimpleUtil.ARR || returnType == SimpleUtil.COLLECT) && returnPsiType is PsiClassReferenceType) {
                val optionalPsiType = Arrays.stream(returnPsiType.parameters)
                    .filter { Objects.nonNull(it) }.findFirst()
                if (optionalPsiType.isPresent) {
                    if (SimpleUtil.getType(optionalPsiType.get()) != SimpleUtil.BASE) {
                        resultPsiClass = PsiUtil.resolveClassInType(optionalPsiType.get())
                    } else {
                        throw IllegalArgumentException("不支持基础类型作为返回值")
                    }
                }
            }

            val targetClass = getTargetClass(resultPsiClass!!, TargetClass(), "set")
            targetClass.type = returnType
            return targetClass
        }

        /**
         * 解析方法的入参类型
         */
        fun resolveEnterType(event: AnActionEvent): TargetClass {
            // 获取编辑器和文件
            val (editor, psiFile) = getEditorAndPsiFile(event)
            val method =
                PsiTreeUtil.getParentOfType(psiFile!!.findElementAt(editor!!.caretModel.offset), PsiMethod::class.java)
                    ?: throw IllegalStateException("当前位置不在方法中")

            val enterPsiType = method.parameterList.parameters
            if (enterPsiType.isEmpty()) {
                throw IllegalArgumentException("方法必须有入参")
            }

            val paramType = SimpleUtil.getType(enterPsiType[0].type)
            var paramPsiClass = PsiUtil.resolveClassInType(enterPsiType[0].type)

            // 处理入参为数组或集合的情况
            if ((paramType == SimpleUtil.ARR || paramType == SimpleUtil.COLLECT) && enterPsiType[0].type is PsiClassReferenceType) {
                val psiClassReferenceType = enterPsiType[0].type as PsiClassReferenceType
                val optionalPsiType = Arrays.stream(psiClassReferenceType.parameters)
                    .filter { Objects.nonNull(it) }.findFirst()
                if (optionalPsiType.isPresent) {
                    if (SimpleUtil.getType(optionalPsiType.get()) != SimpleUtil.BASE) {
                        paramPsiClass = PsiUtil.resolveClassInType(optionalPsiType.get())
                    } else {
                        throw IllegalArgumentException("不支持基础类型作为入参")
                    }
                }
            }

            val clazz = TargetClass()
            clazz.paramName = enterPsiType[0].name
            val targetClass = getTargetClass(paramPsiClass!!, clazz, "get")
            targetClass.type = paramType
            return targetClass
        }

        /**
         * 获取目标class信息，包括字段、方法等
         */
        @Throws(Exception::class)
        fun getTargetClass(psiClass: PsiClass, aClass: TargetClass, typeStr: String): TargetClass {
            val psiClassFields = Stream.of(*psiClass.fields).collect(Collectors.toList())

            // 设置类名和参数名
            aClass.className = psiClass.name
            if (aClass.paramName == null) {
                aClass.paramName =
                    aClass.className?.substring(0, 1)?.lowercase(Locale.getDefault()) + aClass.className?.substring(1)
            }

            // 处理超类的字段
            val supers = psiClass.supers
            if (supers.isNotEmpty()) {
                for (aSuper in supers) {
                    // 过滤不需要处理的超类
                    if (aSuper.name == "DTO"
                        || aSuper.name == "Serializable"
                        || aSuper.name == "Object"
                        || aSuper.name == "BigDecimal"
                        || aSuper.name == "Enum"
                    ) {
                        continue
                    }
                    psiClassFields.addAll(Stream.of(*aSuper.fields).collect(Collectors.toList()))
                }
            }

            // 获取字段的get或set方法映射关系
            val methods: Map<String, String> = getMethods(psiClass, typeStr)
            val fields: MutableList<TargetField> = ArrayList()
            for (psiField in psiClassFields) {
                // 过滤静态字段和不必要的字段
                if (SimpleUtil.isStaticMethodOrField(psiField) || psiField.name == "serialVersionUID") {
                    continue
                }
                val field = TargetField()
                field.fieldName = psiField.name

                // 解析字段的类型和相关信息
                val psiType = psiField.type
                val fieldPsiClass = PsiUtil.resolveClassInType(psiType)
                val type = SimpleUtil.getType(psiType)

                // 检查字段是否有对应的get或set方法 todo 有bug,没有检查继承的方法
//                if (!methods.containsKey(psiField.name)) {
//                    throw IllegalArgumentException("${psiClass.name}.${psiField.name}$typeStr 方法命名不支持")
//                }

                field.method = methods[psiField.name]
                field.type = type
                field.psiType = psiType
                field.psiClass = fieldPsiClass

                // 处理复杂类型字段（对象或数组）
                if ((type == SimpleUtil.OBJECT || type == SimpleUtil.ARR) && fieldPsiClass != null) {
                    val sonFieldPsiClass = TargetClass()
                    sonFieldPsiClass.type = type
                    getTargetClass(fieldPsiClass, sonFieldPsiClass, typeStr)
                    field.targetClass = sonFieldPsiClass
                }

                // 处理集合类型字段
                if (type == SimpleUtil.COLLECT && psiType is PsiClassReferenceType) {
                    val sonFieldPsiClass = TargetClass()
                    sonFieldPsiClass.type = type
                    val optionalPsiType = Arrays.stream(psiType.parameters)
                        .filter { Objects.nonNull(it) }.findFirst()
                    if (optionalPsiType.isPresent) {
                        if (SimpleUtil.getType(optionalPsiType.get()) != SimpleUtil.BASE) {
                            getTargetClass(
                                PsiUtil.resolveClassInType(optionalPsiType.get())!!,
                                sonFieldPsiClass,
                                typeStr
                            )
                            field.targetClass = sonFieldPsiClass
                        }
                    }
                }

                fields.add(field)
            }
            aClass.targetField = fields
            return aClass
        }

        /**
         * 获取字段和方法的映射关系
         */
        private fun getMethods(psiClass: PsiClass, typeStr: String): Map<String, String> {
            val fieldMethod: MutableMap<String, String> = HashMap()

            // 获取所有超类和当前类，然后遍历
            val supers = Stream.of(*psiClass.supers).collect(Collectors.toList())
            supers.add(psiClass)

            if (supers.isNotEmpty()) {
                for (aSuper in supers) {
                    // 过滤不需要处理的超类
                    if ((aSuper.name == "DTO") || (aSuper.name == "Serializable") || (aSuper.name == "Object")) {
                        continue
                    }

                    // 遍历所有字段
                    for (field in aSuper.fields) {
                        // 遍历所有方法匹配字段，确保符合标准的
                        for (psiMethod in aSuper.methods) {
                            // 手动拼接get或者set方法
                            val cs = field.name.toCharArray()
                            if (!CharUtils.isAsciiAlphaUpper(cs[0])) {
                                cs[0] = cs[0] - 32
                            }

                            // 判断拼接后的方法名是否在类中存在，或者类中的方法本身就存在
                            if ((typeStr + String(cs)) == psiMethod.name || Pattern.matches(
                                    typeStr + String(cs) + "\\w",
                                    psiMethod.name
                                )
                            ) {
                                fieldMethod[field.name] = psiMethod.name
                            }
                        }
                    }

                    // 如果使用了lombok注解
                    if (isUsedLombokData(aSuper)) {
                        val p = Pattern.compile("static.*?final|final.*?static")
                        val fields = aSuper.fields

                        for (psiField in fields) {
                            val context = psiField.nameIdentifier.context ?: continue
                            val fieldVal = context.text

                            // serialVersionUID 判断
                            if (fieldVal.contains("serialVersionUID")) {
                                continue
                            }

                            // static final 常量判断过滤
                            val matcher = p.matcher(fieldVal)
                            if (matcher.find()) {
                                continue
                            }

                            val name = psiField.nameIdentifier.text

                            // 直接拼接
                            fieldMethod[name] =
                                typeStr + name.substring(0, 1).uppercase(Locale.getDefault()) + name.substring(1)
                        }
                    }
                }
            }
            return fieldMethod
        }

        /**
         * 判断是否使用了Lombok的@Data注解
         */
        private fun isUsedLombokData(psiClass: PsiClass): Boolean {
            return null != psiClass.getAnnotation("lombok.Data")
        }

        /**
         * 获取编辑器和文件对象
         */
        private fun getEditorAndPsiFile(event: AnActionEvent): Pair<Editor?, PsiFile?> {
            val editor = CommonDataKeys.EDITOR.getData(event.dataContext)
            val psiFile = event.getData(LangDataKeys.PSI_FILE)
            return Pair(editor, psiFile)
        }
    }
}
